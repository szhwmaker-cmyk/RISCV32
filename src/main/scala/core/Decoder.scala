package core

import chisel3._
import chisel3.util._
import common.Config._

/**
 * 控制信号 Bundle
 */
class ControlSignals extends Bundle {
  // ALU 控制
  val alu_op = UInt(4.W)
  val alu_src1 = UInt(2.W) // 0: rs1, 1: pc, 2: 0
  val alu_src2 = UInt(2.W) // 0: rs2, 1: imm, 2: 4

  // 分支和跳转
  val branch = Bool() // 是否为分支指令
  val jump = Bool() // 是否为跳转指令
  val br_type = UInt(3.W) // 分支类型

  // 内存访问
  val mem_read = Bool()
  val mem_write = Bool()
  val mem_width = UInt(2.W) // 0: byte, 1: half, 2: word
  val mem_signed = Bool() // 符号扩展

  // 写回控制
  val reg_write = Bool()
  val wb_sel = UInt(2.W) // 0: alu, 1: mem, 2: pc+4
}

/**
 * 指令译码器
 *
 * 将 32 位指令解码为控制信号和操作数
 */
class Decoder extends Module {
  val io = IO(new Bundle {
    val inst = Input(UInt(INST_WIDTH.W))

    // 提取的字段
    val opcode = Output(UInt(7.W))
    val rd = Output(UInt(REG_ADDR_WIDTH.W))
    val rs1 = Output(UInt(REG_ADDR_WIDTH.W))
    val rs2 = Output(UInt(REG_ADDR_WIDTH.W))
    val funct3 = Output(UInt(3.W))
    val funct7 = Output(UInt(7.W))
    val imm = Output(UInt(XLEN.W))

    // 控制信号
    val ctrl = Output(new ControlSignals)
  })

  // 指令字段提取
  val opcode = io.inst(6, 0)
  val rd = io.inst(11, 7)
  val rs1 = io.inst(19, 15)
  val rs2 = io.inst(24, 20)
  val funct3 = io.inst(14, 12)
  val funct7 = io.inst(31, 25)

  // 输出字段
  io.opcode := opcode
  io.rd := rd
  io.rs1 := rs1
  io.rs2 := rs2
  io.funct3 := funct3
  io.funct7 := funct7

  // Opcode 定义
  val OP_LUI = "b0110111".U
  val OP_AUIPC = "b0010111".U
  val OP_JAL = "b1101111".U
  val OP_JALR = "b1100111".U
  val OP_BRANCH = "b1100011".U
  val OP_LOAD = "b0000011".U
  val OP_STORE = "b0100011".U
  val OP_IMM = "b0010011".U
  val OP_REG = "b0110011".U
  val OP_FENCE = "b0001111".U
  val OP_SYSTEM = "b1110011".U

  // 立即数生成
  val imm_i = Cat(Fill(20, io.inst(31)), io.inst(31, 20))
  val imm_s = Cat(Fill(20, io.inst(31)), io.inst(31, 25), io.inst(11, 7))
  val imm_b = Cat(Fill(19, io.inst(31)), io.inst(31), io.inst(7), io.inst(30, 25), io.inst(11, 8), 0.U(1.W))
  val imm_u = Cat(io.inst(31, 12), Fill(12, 0.U))
  val imm_j = Cat(Fill(11, io.inst(31)), io.inst(31), io.inst(19, 12), io.inst(20), io.inst(30, 21), 0.U(1.W))

  io.imm := MuxLookup(opcode, 0.U)(
    Seq(
      OP_LUI -> imm_u,
      OP_AUIPC -> imm_u,
      OP_JAL -> imm_j,
      OP_JALR -> imm_i,
      OP_BRANCH -> imm_b,
      OP_LOAD -> imm_i,
      OP_STORE -> imm_s,
      OP_IMM -> imm_i,
      OP_REG -> 0.U
    )
  )

  // 控制信号生成
  val ctrl = Wire(new ControlSignals)

  // 默认值
  ctrl.alu_op := AluOp.NOP
  ctrl.alu_src1 := 0.U // rs1
  ctrl.alu_src2 := 0.U // rs2
  ctrl.branch := false.B
  ctrl.jump := false.B
  ctrl.br_type := 0.U
  ctrl.mem_read := false.B
  ctrl.mem_write := false.B
  ctrl.mem_width := 2.U // word
  ctrl.mem_signed := false.B
  ctrl.reg_write := false.B
  ctrl.wb_sel := 0.U // alu

  // 根据 opcode 设置控制信号
  switch(opcode) {
    is(OP_LUI) {
      ctrl.alu_op := AluOp.COPY2
      ctrl.alu_src2 := 1.U // imm
      ctrl.reg_write := true.B
    }
    is(OP_AUIPC) {
      ctrl.alu_op := AluOp.ADD
      ctrl.alu_src1 := 1.U // pc
      ctrl.alu_src2 := 1.U // imm
      ctrl.reg_write := true.B
    }
    is(OP_JAL) {
      ctrl.jump := true.B
      ctrl.alu_op := AluOp.ADD
      ctrl.alu_src1 := 1.U // pc
      ctrl.alu_src2 := 1.U // imm
      ctrl.reg_write := true.B
      ctrl.wb_sel := 2.U // pc+4
    }
    is(OP_JALR) {
      ctrl.jump := true.B
      ctrl.alu_op := AluOp.ADD
      ctrl.alu_src1 := 0.U // rs1
      ctrl.alu_src2 := 1.U // imm
      ctrl.reg_write := true.B
      ctrl.wb_sel := 2.U // pc+4
    }
    is(OP_BRANCH) {
      ctrl.branch := true.B
      ctrl.br_type := funct3
      ctrl.alu_op := AluOp.ADD
      ctrl.alu_src1 := 1.U // pc
      ctrl.alu_src2 := 1.U // imm
    }
    is(OP_LOAD) {
      ctrl.mem_read := true.B
      ctrl.mem_width := funct3(1, 0)
      ctrl.mem_signed := !funct3(2)
      ctrl.alu_op := AluOp.ADD
      ctrl.alu_src2 := 1.U // imm
      ctrl.reg_write := true.B
      ctrl.wb_sel := 1.U // mem
    }
    is(OP_STORE) {
      ctrl.mem_write := true.B
      ctrl.mem_width := funct3(1, 0)
      ctrl.alu_op := AluOp.ADD
      ctrl.alu_src2 := 1.U // imm
    }
    is(OP_IMM) {
      ctrl.alu_src2 := 1.U // imm
      ctrl.reg_write := true.B
      // ALU 操作根据 funct3
      ctrl.alu_op := MuxLookup(funct3, AluOp.NOP)(
        Seq(
          "b000".U -> AluOp.ADD, // ADDI
          "b010".U -> AluOp.SLT, // SLTI
          "b011".U -> AluOp.SLTU, // SLTIU
          "b100".U -> AluOp.XOR, // XORI
          "b110".U -> AluOp.OR, // ORI
          "b111".U -> AluOp.AND, // ANDI
          "b001".U -> AluOp.SLL, // SLLI
          "b101".U -> Mux(funct7(5), AluOp.SRA, AluOp.SRL) // SRLI/SRAI
        )
      )
    }
    is(OP_REG) {
      ctrl.reg_write := true.B
      // ALU 操作根据 funct3 和 funct7
      val is_sub = funct7(5) && (funct3 === "b000".U)
      val is_sra = funct7(5) && (funct3 === "b101".U)

      ctrl.alu_op := MuxLookup(funct3, AluOp.NOP)(
        Seq(
          "b000".U -> Mux(is_sub, AluOp.SUB, AluOp.ADD), // ADD/SUB
          "b001".U -> AluOp.SLL, // SLL
          "b010".U -> AluOp.SLT, // SLT
          "b011".U -> AluOp.SLTU, // SLTU
          "b100".U -> AluOp.XOR, // XOR
          "b101".U -> Mux(is_sra, AluOp.SRA, AluOp.SRL), // SRL/SRA
          "b110".U -> AluOp.OR, // OR
          "b111".U -> AluOp.AND // AND
        )
      )
    }
  }

  io.ctrl := ctrl
}
