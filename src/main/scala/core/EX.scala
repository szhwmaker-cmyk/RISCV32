package core

import chisel3._
import chisel3.util._
import common.Config._

/**
 * EX (Execute) 执行阶段
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
    val pc = Input(UInt(XLEN.W))
    val rs1_data = Input(UInt(XLEN.W))
    val rs2_data = Input(UInt(XLEN.W))
    val imm = Input(UInt(XLEN.W))
    val rd = Input(UInt(REG_ADDR_WIDTH.W))
    val ctrl = Input(new ControlSignals)
    val valid = Input(Bool())

    // 数据转发接口
    val forward_a = Input(UInt(2.W)) // 0: no forward, 1: from EX/MEM, 2: from MEM/WB
    val forward_b = Input(UInt(2.W))
    val ex_mem_alu_result = Input(UInt(XLEN.W))
    val mem_wb_write_data = Input(UInt(XLEN.W))

    // 输出到 MEM 阶段
    val mem_alu_result = Output(UInt(XLEN.W))
    val mem_rs2_data = Output(UInt(XLEN.W)) // 用于 STORE 指令
    val mem_rd = Output(UInt(REG_ADDR_WIDTH.W))
    val mem_ctrl = Output(new ControlSignals)
    val mem_valid = Output(Bool())

    // 分支控制
    val branch_taken = Output(Bool())
    val branch_target = Output(UInt(XLEN.W))
  })

  // 数据转发后的操作数
  val alu_src1_forwarded = MuxCase(io.rs1_data, Seq(
    (io.forward_a === 1.U) -> io.ex_mem_alu_result,
    (io.forward_a === 2.U) -> io.mem_wb_write_data
  ))

  val alu_src2_forwarded = MuxCase(io.rs2_data, Seq(
    (io.forward_b === 1.U) -> io.ex_mem_alu_result,
    (io.forward_b === 2.U) -> io.mem_wb_write_data
  ))

  // ALU 输入选择
  val alu_in1 = MuxLookup(io.ctrl.alu_src1, alu_src1_forwarded)(
    Seq(
      0.U -> alu_src1_forwarded, // rs1
      1.U -> io.pc, // pc
      2.U -> 0.U // 0
    )
  )

  val alu_in2 = MuxLookup(io.ctrl.alu_src2, alu_src2_forwarded)(
    Seq(
      0.U -> alu_src2_forwarded, // rs2
      1.U -> io.imm, // imm
      2.U -> 4.U // 4
    )
  )

  // 实例化 ALU
  val alu = Module(new ALU)
  alu.io.op := io.ctrl.alu_op
  alu.io.src1 := alu_in1
  alu.io.src2 := alu_in2

  // 实例化分支比较单元
  val branch_comp = Module(new BranchComp)
  branch_comp.io.src1 := alu_src1_forwarded
  branch_comp.io.src2 := alu_src2_forwarded
  branch_comp.io.br_type := io.ctrl.br_type

  // 分支控制
  val branch_or_jump = io.ctrl.branch || io.ctrl.jump
  val branch_condition = Mux(io.ctrl.branch, branch_comp.io.br_taken, true.B)
  io.branch_taken := branch_or_jump && branch_condition && io.valid
  io.branch_target := alu.io.out

  // 输出到 MEM 阶段
  io.mem_alu_result := alu.io.out
  io.mem_rs2_data := alu_src2_forwarded
  io.mem_rd := io.rd
  io.mem_ctrl := io.ctrl
  io.mem_valid := io.valid
}

/**
 * EX/MEM 流水线寄存器
 */
class EX_MEM_Reg extends Module {
  val io = IO(new Bundle {
    val stall = Input(Bool())
    val flush = Input(Bool())

    // 输入 (来自 EX)
    val ex_alu_result = Input(UInt(XLEN.W))
    val ex_rs2_data = Input(UInt(XLEN.W))
    val ex_rd = Input(UInt(REG_ADDR_WIDTH.W))
    val ex_ctrl = Input(new ControlSignals)
    val ex_valid = Input(Bool())

    // 输出 (到 MEM)
    val mem_alu_result = Output(UInt(XLEN.W))
    val mem_rs2_data = Output(UInt(XLEN.W))
    val mem_rd = Output(UInt(REG_ADDR_WIDTH.W))
    val mem_ctrl = Output(new ControlSignals)
    val mem_valid = Output(Bool())
  })

  val alu_result_reg = RegInit(0.U(XLEN.W))
  val rs2_data_reg = RegInit(0.U(XLEN.W))
  val rd_reg = RegInit(0.U(REG_ADDR_WIDTH.W))
  val ctrl_reg = RegInit(0.U.asTypeOf(new ControlSignals))
  val valid_reg = RegInit(false.B)

  when(io.flush) {
    valid_reg := false.B
    ctrl_reg.reg_write := false.B
    ctrl_reg.mem_read := false.B
    ctrl_reg.mem_write := false.B
  }.elsewhen(!io.stall) {
    alu_result_reg := io.ex_alu_result
    rs2_data_reg := io.ex_rs2_data
    rd_reg := io.ex_rd
    ctrl_reg := io.ex_ctrl
    valid_reg := io.ex_valid
  }

  io.mem_alu_result := alu_result_reg
  io.mem_rs2_data := rs2_data_reg
  io.mem_rd := rd_reg
  io.mem_ctrl := ctrl_reg
  io.mem_valid := valid_reg
}
