package core

import chisel3._
import chisel3.util._
import common.Config._

/**
 * EX 执行阶段
 *
 * 功能：
 * 1. ALU 运算
 * 2. 分支条件判断
 * 3. 分支/跳转目标地址计算
 * 4. 处理数据转发
 */
class EX extends Module {
  val io = IO(new Bundle {
    // 来自 ID 阶段
    val id_ex_reg     = Input(new ID_EX_Reg)
    val flush         = Input(Bool())

    // 数据转发接口
    val forward_a     = Input(UInt(2.W))  // 00: no forward, 01: from MEM, 10: from WB
    val forward_b     = Input(UInt(2.W))
    val ex_mem_data   = Input(UInt(XLEN.W))  // 来自 EX/MEM 的数据
    val mem_wb_data   = Input(UInt(XLEN.W))  // 来自 MEM/WB 的数据

    // 输出到 MEM 阶段
    val ex_mem_reg    = Output(new EX_MEM_Reg)
  })

  // ALU 实例
  val alu = Module(new ALU)

  // 选择 ALU 操作数1（带转发）
  val alu_src1 = MuxCase(io.id_ex_reg.rs1_data, Seq(
    (io.forward_a === 1.U) -> io.ex_mem_data,
    (io.forward_a === 2.U) -> io.mem_wb_data
  ))

  // 选择 ALU 操作数2（带转发和立即数选择）
  val rs2_forwarded = MuxCase(io.id_ex_reg.rs2_data, Seq(
    (io.forward_b === 1.U) -> io.ex_mem_data,
    (io.forward_b === 2.U) -> io.mem_wb_data
  ))

  val alu_src2 = Mux(io.id_ex_reg.alu_src, io.id_ex_reg.imm, rs2_forwarded)

  // 连接 ALU
  alu.io.op   := io.id_ex_reg.alu_op
  alu.io.src1 := alu_src1
  alu.io.src2 := alu_src2

  // 分支/跳转目标地址计算
  val branch_target = io.id_ex_reg.pc + io.id_ex_reg.imm

  // 对于 JALR，目标地址是 (rs1 + imm) & ~1
  val jalr_target = (alu_src1 + io.id_ex_reg.imm) & ~1.U(XLEN.W)

  val jump_target = Mux(
    io.id_ex_reg.alu_op === AluOp.ADD && io.id_ex_reg.jump,  // JALR 的特征
    jalr_target,
    branch_target
  )

  // 分支判断
  val funct3 = io.id_ex_reg.pc(2, 0)  // 从某处获取 funct3（这里简化处理）
  // 实际应该从指令中提取，这里用 ALU 结果判断
  val branch_taken = io.id_ex_reg.branch && MuxLookup(io.id_ex_reg.alu_op, false.B)(Seq(
    AluOp.SUB  -> alu.io.zero,         // BEQ: rs1 == rs2
    AluOp.SLT  -> (alu.io.out === 1.U), // BLT: rs1 < rs2
    AluOp.SLTU -> (alu.io.out === 1.U)  // BLTU: rs1 < rs2 (unsigned)
  )) || io.id_ex_reg.jump

  // 输出到 MEM 阶段
  when(io.flush) {
    io.ex_mem_reg.pc            := 0.U
    io.ex_mem_reg.alu_result    := 0.U
    io.ex_mem_reg.rs2_data      := 0.U
    io.ex_mem_reg.rd_addr       := 0.U
    io.ex_mem_reg.branch_taken  := false.B
    io.ex_mem_reg.branch_target := 0.U
    io.ex_mem_reg.mem_read      := false.B
    io.ex_mem_reg.mem_write     := false.B
    io.ex_mem_reg.mem_size      := MemSize.WORD
    io.ex_mem_reg.mem_unsigned  := false.B
    io.ex_mem_reg.reg_write     := false.B
    io.ex_mem_reg.wb_src        := WbSrc.ALU
  }.otherwise {
    io.ex_mem_reg.pc            := io.id_ex_reg.pc
    io.ex_mem_reg.alu_result    := alu.io.out
    io.ex_mem_reg.rs2_data      := rs2_forwarded
    io.ex_mem_reg.rd_addr       := io.id_ex_reg.rd_addr
    io.ex_mem_reg.branch_taken  := branch_taken
    io.ex_mem_reg.branch_target := jump_target
    io.ex_mem_reg.mem_read      := io.id_ex_reg.mem_read
    io.ex_mem_reg.mem_write     := io.id_ex_reg.mem_write
    io.ex_mem_reg.mem_size      := io.id_ex_reg.mem_size
    io.ex_mem_reg.mem_unsigned  := io.id_ex_reg.mem_unsigned
    io.ex_mem_reg.reg_write     := io.id_ex_reg.reg_write
    io.ex_mem_reg.wb_src        := io.id_ex_reg.wb_src
  }
}
