package core

import chisel3._
import chisel3.util._
import common._

// ============================================================================
// ID Stage - Instruction Decode
// 译码阶段：指令解码和控制信号生成
// ============================================================================

class ID extends Module {
  val io = IO(new Bundle {
    // 来自IF/ID寄存器
    val inst = Input(UInt(32.W))
    val pc = Input(UInt(32.W))

    // 寄存器文件读接口
    val rs1_addr = Output(UInt(4.W))
    val rs2_addr = Output(UInt(4.W))
    val rs1_data = Input(UInt(32.W))
    val rs2_data = Input(UInt(32.W))

    // 输出到ID/EX寄存器
    val imm = Output(UInt(32.W))
    val rd_addr = Output(UInt(4.W))
    val ctrl = Output(new ControlSignals)
  })

  // 指令解析
  val opcode = io.inst(6, 0)
  val rd  = io.inst(11, 7)
  val rs1 = io.inst(19, 15)
  val rs2 = io.inst(24, 20)
  val funct3 = io.inst(14, 12)
  val funct7 = io.inst(31, 25)

  // 寄存器地址输出（只取低4位，RV32E只有16个寄存器）
  io.rs1_addr := rs1(3, 0)
  io.rs2_addr := rs2(3, 0)
  io.rd_addr := rd(3, 0)

  // 立即数生成
  val imm_i = Cat(Fill(20, io.inst(31)), io.inst(31, 20))
  val imm_s = Cat(Fill(20, io.inst(31)), io.inst(31, 25), io.inst(11, 7))
  val imm_b = Cat(Fill(19, io.inst(31)), io.inst(31), io.inst(7), io.inst(30, 25), io.inst(11, 8), 0.U(1.W))
  val imm_u = Cat(io.inst(31, 12), Fill(12, 0.U))
  val imm_j = Cat(Fill(11, io.inst(31)), io.inst(31), io.inst(19, 12), io.inst(20), io.inst(30, 21), 0.U(1.W))

  // 默认控制信号
  val ctrl = Wire(new ControlSignals)
  ctrl.reg_write := false.B
  ctrl.mem_read := false.B
  ctrl.mem_write := false.B
  ctrl.alu_op := AluOp.ADD
  ctrl.alu_src1 := 0.U  // rs1
  ctrl.alu_src2 := 0.U  // rs2
  ctrl.branch := BranchType.NO_BR
  ctrl.jump := false.B
  ctrl.wb_src := WBSrc.ALU_RESULT
  ctrl.mem_type := MemType.WORD

  io.imm := 0.U

  // 指令译码
  switch(opcode) {
    // LUI
    is("b0110111".U) {
      ctrl.reg_write := true.B
      ctrl.alu_op := AluOp.COPY
      ctrl.alu_src1 := 0.U
      ctrl.alu_src2 := 1.U
      io.imm := imm_u
    }

    // AUIPC
    is("b0010111".U) {
      ctrl.reg_write := true.B
      ctrl.alu_op := AluOp.ADD
      ctrl.alu_src1 := 1.U  // PC
      ctrl.alu_src2 := 1.U  // imm
      io.imm := imm_u
    }

    // JAL
    is("b1101111".U) {
      ctrl.reg_write := true.B
      ctrl.jump := true.B
      ctrl.wb_src := WBSrc.PC_PLUS_4
      ctrl.alu_op := AluOp.ADD
      ctrl.alu_src1 := 1.U  // PC
      ctrl.alu_src2 := 1.U  // imm
      io.imm := imm_j
    }

    // JALR
    is("b1100111".U) {
      ctrl.reg_write := true.B
      ctrl.jump := true.B
      ctrl.wb_src := WBSrc.PC_PLUS_4
      ctrl.alu_op := AluOp.ADD
      ctrl.alu_src1 := 0.U  // rs1
      ctrl.alu_src2 := 1.U  // imm
      io.imm := imm_i
    }

    // Branch
    is("b1100011".U) {
      io.imm := imm_b
      ctrl.alu_op := AluOp.ADD
      ctrl.alu_src1 := 1.U  // PC
      ctrl.alu_src2 := 1.U  // imm
      ctrl.branch := MuxLookup(funct3, BranchType.NO_BR)(Seq(
        "b000".U -> BranchType.BEQ,
        "b001".U -> BranchType.BNE,
        "b100".U -> BranchType.BLT,
        "b101".U -> BranchType.BGE,
        "b110".U -> BranchType.BLTU,
        "b111".U -> BranchType.BGEU
      ))
    }

    // Load
    is("b0000011".U) {
      ctrl.reg_write := true.B
      ctrl.mem_read := true.B
      ctrl.wb_src := WBSrc.MEM_DATA
      ctrl.alu_op := AluOp.ADD
      ctrl.alu_src2 := 1.U  // imm
      io.imm := imm_i
      ctrl.mem_type := MuxLookup(funct3, MemType.WORD)(Seq(
        "b000".U -> MemType.BYTE,
        "b001".U -> MemType.HALF,
        "b010".U -> MemType.WORD,
        "b100".U -> MemType.BYTEU,
        "b101".U -> MemType.HALFU
      ))
    }

    // Store
    is("b0100011".U) {
      ctrl.mem_write := true.B
      ctrl.alu_op := AluOp.ADD
      ctrl.alu_src2 := 1.U  // imm
      io.imm := imm_s
      ctrl.mem_type := MuxLookup(funct3, MemType.WORD)(Seq(
        "b000".U -> MemType.BYTE,
        "b001".U -> MemType.HALF,
        "b010".U -> MemType.WORD
      ))
    }

    // ALU I-type
    is("b0010011".U) {
      ctrl.reg_write := true.B
      ctrl.alu_src2 := 1.U  // imm
      io.imm := imm_i
      ctrl.alu_op := MuxLookup(funct3, AluOp.ADD)(Seq(
        "b000".U -> AluOp.ADD,   // ADDI
        "b010".U -> AluOp.SLT,   // SLTI
        "b011".U -> AluOp.SLTU,  // SLTIU
        "b100".U -> AluOp.XOR,   // XORI
        "b110".U -> AluOp.OR,    // ORI
        "b111".U -> AluOp.AND,   // ANDI
        "b001".U -> AluOp.SLL,   // SLLI
        "b101".U -> Mux(funct7(5), AluOp.SRA, AluOp.SRL)  // SRAI/SRLI
      ))
    }

    // ALU R-type
    is("b0110011".U) {
      ctrl.reg_write := true.B
      ctrl.alu_op := MuxLookup(Cat(funct7(5), funct3), AluOp.ADD)(Seq(
        "b0000".U -> AluOp.ADD,   // ADD
        "b1000".U -> AluOp.SUB,   // SUB
        "b0001".U -> AluOp.SLL,   // SLL
        "b0010".U -> AluOp.SLT,   // SLT
        "b0011".U -> AluOp.SLTU,  // SLTU
        "b0100".U -> AluOp.XOR,   // XOR
        "b0101".U -> AluOp.SRL,   // SRL
        "b1101".U -> AluOp.SRA,   // SRA
        "b0110".U -> AluOp.OR,    // OR
        "b0111".U -> AluOp.AND    // AND
      ))
    }
  }

  io.ctrl := ctrl
}
