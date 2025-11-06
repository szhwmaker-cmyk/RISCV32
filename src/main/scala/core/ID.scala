package core

import chisel3._
import chisel3.util._
import common.Config._

/**
 * ID (Instruction Decode) 译码阶段
 *
 * 功能：
 * 1. 指令译码
 * 2. 寄存器读取
 * 3. 立即数生成
 * 4. 控制信号生成
 */
class ID extends Module {
  val io = IO(new Bundle {
    // 来自 IF 阶段
    val pc = Input(UInt(XLEN.W))
    val inst = Input(UInt(INST_WIDTH.W))
    val valid = Input(Bool())

    // 寄存器文件接口
    val rs1_addr = Output(UInt(REG_ADDR_WIDTH.W))
    val rs1_data = Input(UInt(XLEN.W))
    val rs2_addr = Output(UInt(REG_ADDR_WIDTH.W))
    val rs2_data = Input(UInt(XLEN.W))

    // 输出到 EX 阶段
    val ex_pc = Output(UInt(XLEN.W))
    val ex_rs1_data = Output(UInt(XLEN.W))
    val ex_rs2_data = Output(UInt(XLEN.W))
    val ex_imm = Output(UInt(XLEN.W))
    val ex_rd = Output(UInt(REG_ADDR_WIDTH.W))
    val ex_rs1 = Output(UInt(REG_ADDR_WIDTH.W))
    val ex_rs2 = Output(UInt(REG_ADDR_WIDTH.W))
    val ex_ctrl = Output(new ControlSignals)
    val ex_valid = Output(Bool())
  })

  // 实例化译码器
  val decoder = Module(new Decoder)
  decoder.io.inst := io.inst

  // 寄存器读取
  io.rs1_addr := decoder.io.rs1
  io.rs2_addr := decoder.io.rs2

  // 输出到 EX 阶段
  io.ex_pc := io.pc
  io.ex_rs1_data := io.rs1_data
  io.ex_rs2_data := io.rs2_data
  io.ex_imm := decoder.io.imm
  io.ex_rd := decoder.io.rd
  io.ex_rs1 := decoder.io.rs1
  io.ex_rs2 := decoder.io.rs2
  io.ex_ctrl := decoder.io.ctrl
  io.ex_valid := io.valid
}

/**
 * ID/EX 流水线寄存器
 */
class ID_EX_Reg extends Module {
  val io = IO(new Bundle {
    val stall = Input(Bool())
    val flush = Input(Bool())

    // 输入 (来自 ID)
    val id_pc = Input(UInt(XLEN.W))
    val id_rs1_data = Input(UInt(XLEN.W))
    val id_rs2_data = Input(UInt(XLEN.W))
    val id_imm = Input(UInt(XLEN.W))
    val id_rd = Input(UInt(REG_ADDR_WIDTH.W))
    val id_rs1 = Input(UInt(REG_ADDR_WIDTH.W))
    val id_rs2 = Input(UInt(REG_ADDR_WIDTH.W))
    val id_ctrl = Input(new ControlSignals)
    val id_valid = Input(Bool())

    // 输出 (到 EX)
    val ex_pc = Output(UInt(XLEN.W))
    val ex_rs1_data = Output(UInt(XLEN.W))
    val ex_rs2_data = Output(UInt(XLEN.W))
    val ex_imm = Output(UInt(XLEN.W))
    val ex_rd = Output(UInt(REG_ADDR_WIDTH.W))
    val ex_rs1 = Output(UInt(REG_ADDR_WIDTH.W))
    val ex_rs2 = Output(UInt(REG_ADDR_WIDTH.W))
    val ex_ctrl = Output(new ControlSignals)
    val ex_valid = Output(Bool())
  })

  val pc_reg = RegInit(0.U(XLEN.W))
  val rs1_data_reg = RegInit(0.U(XLEN.W))
  val rs2_data_reg = RegInit(0.U(XLEN.W))
  val imm_reg = RegInit(0.U(XLEN.W))
  val rd_reg = RegInit(0.U(REG_ADDR_WIDTH.W))
  val rs1_reg = RegInit(0.U(REG_ADDR_WIDTH.W))
  val rs2_reg = RegInit(0.U(REG_ADDR_WIDTH.W))
  val ctrl_reg = RegInit(0.U.asTypeOf(new ControlSignals))
  val valid_reg = RegInit(false.B)

  when(io.flush) {
    valid_reg := false.B
    ctrl_reg.reg_write := false.B
    ctrl_reg.mem_read := false.B
    ctrl_reg.mem_write := false.B
    ctrl_reg.branch := false.B
    ctrl_reg.jump := false.B
  }.elsewhen(!io.stall) {
    pc_reg := io.id_pc
    rs1_data_reg := io.id_rs1_data
    rs2_data_reg := io.id_rs2_data
    imm_reg := io.id_imm
    rd_reg := io.id_rd
    rs1_reg := io.id_rs1
    rs2_reg := io.id_rs2
    ctrl_reg := io.id_ctrl
    valid_reg := io.id_valid
  }

  io.ex_pc := pc_reg
  io.ex_rs1_data := rs1_data_reg
  io.ex_rs2_data := rs2_data_reg
  io.ex_imm := imm_reg
  io.ex_rd := rd_reg
  io.ex_rs1 := rs1_reg
  io.ex_rs2 := rs2_reg
  io.ex_ctrl := ctrl_reg
  io.ex_valid := valid_reg
}
