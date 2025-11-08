package core

import chisel3._
import chisel3.util._
import common._

// ============================================================================
// EX Stage - Execute
// 执行阶段：ALU运算和分支判断
// ============================================================================

class EX extends Module {
  val io = IO(new Bundle {
    // 来自ID/EX寄存器
    val pc = Input(UInt(32.W))
    val rs1_data = Input(UInt(32.W))
    val rs2_data = Input(UInt(32.W))
    val imm = Input(UInt(32.W))
    val ctrl = Input(new ControlSignals)

    // 数据转发输入
    val forward_a = Input(UInt(2.W))  // 0=no forward, 1=from EX/MEM, 2=from MEM/WB
    val forward_b = Input(UInt(2.W))
    val ex_mem_alu_result = Input(UInt(32.W))
    val mem_wb_write_data = Input(UInt(32.W))

    // 输出
    val alu_result = Output(UInt(32.W))
    val branch_taken = Output(Bool())
    val branch_target = Output(UInt(32.W))
    val rs2_data_out = Output(UInt(32.W))  // 转发后的rs2（用于store）
  })

  // ALU 模块
  val alu = Module(new ALU)

  // 数据转发后的操作数
  val forward_rs1 = MuxLookup(io.forward_a, io.rs1_data)(Seq(
    1.U -> io.ex_mem_alu_result,
    2.U -> io.mem_wb_write_data
  ))

  val forward_rs2 = MuxLookup(io.forward_b, io.rs2_data)(Seq(
    1.U -> io.ex_mem_alu_result,
    2.U -> io.mem_wb_write_data
  ))

  // ALU 输入选择
  val alu_src1 = Mux(io.ctrl.alu_src1 === 1.U, io.pc, forward_rs1)
  val alu_src2 = Mux(io.ctrl.alu_src2 === 1.U, io.imm, forward_rs2)

  // ALU 连接
  alu.io.op := io.ctrl.alu_op
  alu.io.src1 := alu_src1
  alu.io.src2 := alu_src2

  io.alu_result := alu.io.out

  // 分支判断
  val branch_taken = WireDefault(false.B)

  when(io.ctrl.jump) {
    // JAL / JALR
    branch_taken := true.B
  }.elsewhen(io.ctrl.branch =/= BranchType.NO_BR) {
    // Branch 指令
    branch_taken := MuxLookup(io.ctrl.branch, false.B)(Seq(
      BranchType.BEQ  -> (forward_rs1 === forward_rs2),
      BranchType.BNE  -> (forward_rs1 =/= forward_rs2),
      BranchType.BLT  -> (forward_rs1.asSInt < forward_rs2.asSInt),
      BranchType.BGE  -> (forward_rs1.asSInt >= forward_rs2.asSInt),
      BranchType.BLTU -> (forward_rs1 < forward_rs2),
      BranchType.BGEU -> (forward_rs1 >= forward_rs2)
    ))
  }

  io.branch_taken := branch_taken

  // 分支目标地址
  // 对于JALR，需要将结果的最低位清零
  val jalr_target = Cat(alu.io.out(31, 1), 0.U(1.W))
  io.branch_target := Mux(io.ctrl.jump && io.ctrl.alu_src1 === 0.U, jalr_target, alu.io.out)

  io.rs2_data_out := forward_rs2
}
