package core

import chisel3._
import chisel3.util._
import common.Config._
import bus._

/**
 * RV32E 5级流水线处理器核心
 *
 * 流水线阶段：
 * 1. IF  - 指令取指
 * 2. ID  - 指令译码
 * 3. EX  - 执行
 * 4. MEM - 访存
 * 5. WB  - 写回
 *
 * 特性：
 * - 数据转发
 * - 冒险检测
 * - 分支预测（静态不跳转）
 */
class RV32ECore extends Module {
  val io = IO(new Bundle {
    // Wishbone 指令总线
    val imem = new WishboneMaster

    // Wishbone 数据总线
    val dmem = new WishboneMaster

    // 中断输入
    val interrupts = Input(UInt(16.W))
  })

  // ==========================================================================
  // 模块实例化
  // ==========================================================================

  val regfile = Module(new RegFile)
  val decoder = Module(new Decoder)
  val alu = Module(new ALU)
  val hazard = Module(new HazardUnit)
  val branch_unit = Module(new BranchUnit)

  // ==========================================================================
  // 流水线寄存器
  // ==========================================================================

  val if_id = RegInit(0.U.asTypeOf(new IFIDReg))
  val id_ex = RegInit(0.U.asTypeOf(new IDEXReg))
  val ex_mem = RegInit(0.U.asTypeOf(new EXMEMReg))
  val mem_wb = RegInit(0.U.asTypeOf(new MEMWBReg))

  // ==========================================================================
  // IF 阶段 - 指令取指
  // ==========================================================================

  val pc = RegInit(PC_RESET.U(XLEN.W))
  val pc_plus_4 = pc + 4.U

  // 分支/跳转目标
  val branch_target = Wire(UInt(XLEN.W))
  val jump_target = Wire(UInt(XLEN.W))

  // PC 更新逻辑
  val next_pc = WireDefault(pc_plus_4)

  when(hazard.io.stall_if) {
    next_pc := pc  // 暂停
  }.elsewhen(id_ex.ctrl.jump) {
    next_pc := jump_target  // 跳转
  }.elsewhen(branch_unit.io.taken) {
    next_pc := branch_target  // 分支跳转
  }

  pc := next_pc

  // 指令内存访问
  io.imem.adr := pc
  io.imem.dat_w := 0.U
  io.imem.we := false.B
  io.imem.sel := "b1111".U
  io.imem.stb := true.B
  io.imem.cyc := true.B

  // IF/ID 寄存器更新
  when(hazard.io.flush_if) {
    if_id.valid := false.B
  }.elsewhen(!hazard.io.stall_if && io.imem.ack) {
    if_id.pc := pc
    if_id.inst := io.imem.dat_r
    if_id.valid := true.B
  }

  // ==========================================================================
  // ID 阶段 - 指令译码
  // ==========================================================================

  // 译码器
  decoder.io.inst := if_id.inst

  // 寄存器堆读取
  regfile.io.rs1_addr := decoder.io.rs1
  regfile.io.rs2_addr := decoder.io.rs2

  // 数据转发到ID阶段（用于分支判断）
  val id_rs1_data = WireDefault(regfile.io.rs1_data)
  val id_rs2_data = WireDefault(regfile.io.rs2_data)

  when(hazard.io.forward_a === 1.U) {
    id_rs1_data := ex_mem.alu_out
  }.elsewhen(hazard.io.forward_a === 2.U) {
    id_rs1_data := Mux(mem_wb.wb_sel === 1.U, mem_wb.mem_data, mem_wb.alu_out)
  }

  when(hazard.io.forward_b === 1.U) {
    id_rs2_data := ex_mem.alu_out
  }.elsewhen(hazard.io.forward_b === 2.U) {
    id_rs2_data := Mux(mem_wb.wb_sel === 1.U, mem_wb.mem_data, mem_wb.alu_out)
  }

  // 分支判断
  branch_unit.io.funct3 := if_id.inst(14, 12)
  branch_unit.io.rs1_data := id_rs1_data
  branch_unit.io.rs2_data := id_rs2_data
  branch_unit.io.branch := decoder.io.ctrl.branch

  // 分支/跳转目标计算
  branch_target := if_id.pc + decoder.io.imm
  jump_target := Mux(
    decoder.io.ctrl.alu_src1 === 1.U,
    if_id.pc + decoder.io.imm,
    id_rs1_data + decoder.io.imm
  ) & "hFFFFFFFE".U  // 清除最低位

  // 冒险检测
  hazard.io.id_rs1 := decoder.io.rs1
  hazard.io.id_rs2 := decoder.io.rs2
  hazard.io.ex_rd := id_ex.rd_addr
  hazard.io.ex_reg_write := id_ex.ctrl.reg_write
  hazard.io.ex_mem_read := id_ex.ctrl.mem_read
  hazard.io.mem_rd := ex_mem.rd_addr
  hazard.io.mem_reg_write := ex_mem.reg_write
  hazard.io.wb_rd := mem_wb.rd_addr
  hazard.io.wb_reg_write := mem_wb.reg_write
  hazard.io.branch_taken := branch_unit.io.taken
  hazard.io.jump_taken := decoder.io.ctrl.jump

  // ID/EX 寄存器更新
  when(hazard.io.flush_id) {
    id_ex.valid := false.B
    id_ex.ctrl.reg_write := false.B
    id_ex.ctrl.mem_read := false.B
    id_ex.ctrl.mem_write := false.B
  }.elsewhen(!hazard.io.stall_id) {
    id_ex.pc := if_id.pc
    id_ex.inst := if_id.inst
    id_ex.rs1_data := id_rs1_data
    id_ex.rs2_data := id_rs2_data
    id_ex.imm := decoder.io.imm
    id_ex.rs1_addr := decoder.io.rs1
    id_ex.rs2_addr := decoder.io.rs2
    id_ex.rd_addr := decoder.io.rd
    id_ex.ctrl := decoder.io.ctrl
    id_ex.valid := if_id.valid
  }

  // ==========================================================================
  // EX 阶段 - 执行
  // ==========================================================================

  // ALU 源操作数选择
  val alu_src1 = Mux(id_ex.ctrl.alu_src1 === 1.U, id_ex.pc, id_ex.rs1_data)
  val alu_src2 = MuxLookup(id_ex.ctrl.alu_src2, id_ex.rs2_data)(Seq(
    1.U -> id_ex.imm,
    2.U -> 4.U
  ))

  // ALU 执行
  alu.io.op := id_ex.ctrl.alu_op
  alu.io.src1 := alu_src1
  alu.io.src2 := alu_src2

  // EX/MEM 寄存器更新
  when(hazard.io.flush_ex) {
    ex_mem.valid := false.B
    ex_mem.reg_write := false.B
    ex_mem.mem_read := false.B
    ex_mem.mem_write := false.B
  }.otherwise {
    ex_mem.pc := id_ex.pc
    ex_mem.alu_out := alu.io.out
    ex_mem.rs2_data := id_ex.rs2_data
    ex_mem.rd_addr := id_ex.rd_addr
    ex_mem.mem_read := id_ex.ctrl.mem_read
    ex_mem.mem_write := id_ex.ctrl.mem_write
    ex_mem.mem_width := id_ex.ctrl.mem_width
    ex_mem.mem_unsigned := id_ex.ctrl.mem_unsigned
    ex_mem.reg_write := id_ex.ctrl.reg_write
    ex_mem.wb_sel := id_ex.ctrl.wb_sel
    ex_mem.pc_plus_4 := id_ex.pc + 4.U
    ex_mem.valid := id_ex.valid
  }

  // ==========================================================================
  // MEM 阶段 - 访存
  // ==========================================================================

  // 数据内存访问
  io.dmem.adr := ex_mem.alu_out
  io.dmem.we := ex_mem.mem_write
  io.dmem.stb := ex_mem.mem_read || ex_mem.mem_write
  io.dmem.cyc := ex_mem.mem_read || ex_mem.mem_write

  // 写数据和字节选择
  val store_data = WireDefault(0.U(XLEN.W))
  val byte_sel = WireDefault("b1111".U(4.W))

  switch(ex_mem.mem_width) {
    is(0.U) {  // Byte
      val offset = ex_mem.alu_out(1, 0)
      store_data := ex_mem.rs2_data(7, 0) << (offset << 3)
      byte_sel := (1.U << offset)
    }
    is(1.U) {  // Half
      val offset = ex_mem.alu_out(1)
      store_data := ex_mem.rs2_data(15, 0) << (offset << 4)
      byte_sel := Mux(offset === 0.U, "b0011".U, "b1100".U)
    }
    is(2.U) {  // Word
      store_data := ex_mem.rs2_data
      byte_sel := "b1111".U
    }
  }

  io.dmem.dat_w := store_data
  io.dmem.sel := byte_sel

  // 读数据处理
  val load_data = WireDefault(0.U(XLEN.W))

  when(io.dmem.ack && ex_mem.mem_read) {
    val raw_data = io.dmem.dat_r
    val offset = ex_mem.alu_out(1, 0)

    switch(ex_mem.mem_width) {
      is(0.U) {  // Byte
        val byte_data = (raw_data >> (offset << 3))(7, 0)
        load_data := Mux(ex_mem.mem_unsigned, byte_data, byte_data.asSInt.pad(XLEN).asUInt)
      }
      is(1.U) {  // Half
        val half_offset = ex_mem.alu_out(1)
        val half_data = (raw_data >> (half_offset << 4))(15, 0)
        load_data := Mux(ex_mem.mem_unsigned, half_data, half_data.asSInt.pad(XLEN).asUInt)
      }
      is(2.U) {  // Word
        load_data := raw_data
      }
    }
  }

  // MEM/WB 寄存器更新
  when(ex_mem.mem_read && !io.dmem.ack) {
    // 等待内存响应，不更新
  }.otherwise {
    mem_wb.alu_out := ex_mem.alu_out
    mem_wb.mem_data := load_data
    mem_wb.rd_addr := ex_mem.rd_addr
    mem_wb.reg_write := ex_mem.reg_write
    mem_wb.wb_sel := ex_mem.wb_sel
    mem_wb.pc_plus_4 := ex_mem.pc_plus_4
    mem_wb.valid := ex_mem.valid
  }

  // ==========================================================================
  // WB 阶段 - 写回
  // ==========================================================================

  // 写回数据选择
  val wb_data = MuxLookup(mem_wb.wb_sel, mem_wb.alu_out)(Seq(
    1.U -> mem_wb.mem_data,
    2.U -> mem_wb.pc_plus_4
  ))

  // 寄存器堆写入
  regfile.io.rd_addr := mem_wb.rd_addr
  regfile.io.rd_data := wb_data
  regfile.io.rd_wen := mem_wb.reg_write && mem_wb.valid
}
