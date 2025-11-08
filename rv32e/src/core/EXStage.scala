package core

import chisel3._
import chisel3.util._

/**
 * EX (Execute) Stage
 * 执行阶段
 *
 * 功能：
 * - ALU运算
 * - 分支判断和目标地址计算
 * - 数据前递(Forwarding)
 * - 将结果传递到EX/MEM流水线寄存器
 */
class EXStage extends Module {
  val io = IO(new Bundle {
    // 来自ID阶段
    val id_ex = Input(new ID_EX_Reg())

    // 控制信号
    val flush = Input(Bool())

    // 前递控制
    val forward_a = Input(UInt(2.W))
    val forward_b = Input(UInt(2.W))

    // 前递数据源
    val ex_mem_alu_result = Input(UInt(32.W))  // 来自EX/MEM
    val mem_wb_result = Input(UInt(32.W))      // 来自MEM/WB

    // 分支控制输出
    val branch_taken = Output(Bool())
    val branch_target = Output(UInt(32.W))

    // 输出到MEM阶段
    val ex_mem = Output(new EX_MEM_Reg())
  })

  // 前递多路选择器 - 源操作数A (rs1)
  val forward_a_data = MuxCase(io.id_ex.rs1_data, Seq(
    (io.forward_a === 1.U) -> io.ex_mem_alu_result,  // 从EX/MEM前递
    (io.forward_a === 2.U) -> io.mem_wb_result       // 从MEM/WB前递
  ))

  // 前递多路选择器 - 源操作数B (rs2)
  val forward_b_data = MuxCase(io.id_ex.rs2_data, Seq(
    (io.forward_b === 1.U) -> io.ex_mem_alu_result,
    (io.forward_b === 2.U) -> io.mem_wb_result
  ))

  // ALU源操作数选择
  val alu_src1 = MuxCase(forward_a_data, Seq(
    (io.id_ex.ctrl.alu_src1 === 1.U) -> io.id_ex.pc,    // PC
    (io.id_ex.ctrl.alu_src1 === 2.U) -> 0.U             // 0
  ))

  val alu_src2 = MuxCase(forward_b_data, Seq(
    (io.id_ex.ctrl.alu_src2 === 1.U) -> io.id_ex.imm.asUInt,  // imm
    (io.id_ex.ctrl.alu_src2 === 2.U) -> 4.U                   // 4 (for PC+4)
  ))

  // ALU实例化
  val alu = Module(new ALU())
  alu.io.op := io.id_ex.ctrl.alu_op
  alu.io.a := alu_src1
  alu.io.b := alu_src2

  // 分支单元
  val branch_unit = Module(new BranchUnit())
  branch_unit.io.rs1_data := forward_a_data
  branch_unit.io.rs2_data := forward_b_data
  branch_unit.io.pc := io.id_ex.pc
  branch_unit.io.imm := io.id_ex.imm
  branch_unit.io.branch_type := io.id_ex.ctrl.branch_type

  // 分支控制输出
  io.branch_taken := io.id_ex.ctrl.branch && branch_unit.io.taken && io.id_ex.valid
  io.branch_target := branch_unit.io.target

  // EX/MEM Pipeline Register
  val ex_mem_reg = RegInit(0.U.asTypeOf(new EX_MEM_Reg()))

  when(io.flush) {
    // 冲刷：插入bubble
    ex_mem_reg := 0.U.asTypeOf(new EX_MEM_Reg())
    ex_mem_reg.valid := false.B
  }.otherwise {
    // 正常更新
    ex_mem_reg.pc := io.id_ex.pc

    // 传递控制信号到MEM阶段
    ex_mem_reg.mem_read := io.id_ex.ctrl.mem_read
    ex_mem_reg.mem_write := io.id_ex.ctrl.mem_write
    ex_mem_reg.mem_size := io.id_ex.ctrl.mem_size
    ex_mem_reg.mem_unsigned := io.id_ex.ctrl.mem_unsigned
    ex_mem_reg.reg_write := io.id_ex.ctrl.reg_write
    ex_mem_reg.wb_src := io.id_ex.ctrl.wb_src

    // ALU结果
    ex_mem_reg.alu_result := alu.io.out
    ex_mem_reg.zero := alu.io.zero

    // 分支信息
    ex_mem_reg.branch_taken := branch_unit.io.taken
    ex_mem_reg.branch_target := branch_unit.io.target

    // 写回信息
    ex_mem_reg.rd_addr := io.id_ex.rd_addr
    ex_mem_reg.rs2_data := forward_b_data  // STORE需要

    ex_mem_reg.valid := io.id_ex.valid
  }

  io.ex_mem := ex_mem_reg
}
