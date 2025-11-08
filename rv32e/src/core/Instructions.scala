package core

import chisel3._
import chisel3.util._

/**
 * RISC-V Instruction Opcodes
 * RV32I基本指令集的操作码
 */
object Opcode {
  val LOAD      = "b0000011".U(7.W)
  val LOAD_FP   = "b0000111".U(7.W)
  val MISC_MEM  = "b0001111".U(7.W)
  val OP_IMM    = "b0010011".U(7.W)
  val AUIPC     = "b0010111".U(7.W)
  val STORE     = "b0100011".U(7.W)
  val STORE_FP  = "b0100111".U(7.W)
  val AMO       = "b0101111".U(7.W)
  val OP        = "b0110011".U(7.W)
  val LUI       = "b0110111".U(7.W)
  val BRANCH    = "b1100011".U(7.W)
  val JALR      = "b1100111".U(7.W)
  val JAL       = "b1101111".U(7.W)
  val SYSTEM    = "b1110011".U(7.W)
}

/**
 * RISC-V Instruction Formats
 * 指令格式定义
 */
object InstFormat {
  // R-type: OP
  // I-type: OP_IMM, JALR, LOAD
  // S-type: STORE
  // B-type: BRANCH
  // U-type: LUI, AUIPC
  // J-type: JAL
}

/**
 * Function3 codes for different instruction types
 */
object Funct3 {
  // OP_IMM and OP
  val ADD_SUB = "b000".U(3.W)
  val SLL     = "b001".U(3.W)
  val SLT     = "b010".U(3.W)
  val SLTU    = "b011".U(3.W)
  val XOR     = "b100".U(3.W)
  val SRL_SRA = "b101".U(3.W)
  val OR      = "b110".U(3.W)
  val AND     = "b111".U(3.W)

  // BRANCH
  val BEQ  = "b000".U(3.W)
  val BNE  = "b001".U(3.W)
  val BLT  = "b100".U(3.W)
  val BGE  = "b101".U(3.W)
  val BLTU = "b110".U(3.W)
  val BGEU = "b111".U(3.W)

  // LOAD
  val LB  = "b000".U(3.W)
  val LH  = "b001".U(3.W)
  val LW  = "b010".U(3.W)
  val LBU = "b100".U(3.W)
  val LHU = "b101".U(3.W)

  // STORE
  val SB = "b000".U(3.W)
  val SH = "b001".U(3.W)
  val SW = "b010".U(3.W)
}

/**
 * Function7 codes
 */
object Funct7 {
  val NORMAL = "b0000000".U(7.W)  // Used for ADD, SRL, etc.
  val ALT    = "b0100000".U(7.W)  // Used for SUB, SRA
}

/**
 * Instruction Decoder Helper
 * 提供指令解码的辅助功能
 */
object InstructionDecoder {
  def getOpcode(inst: UInt): UInt = inst(6, 0)
  def getRd(inst: UInt): UInt = inst(11, 7)
  def getFunct3(inst: UInt): UInt = inst(14, 12)
  def getRs1(inst: UInt): UInt = inst(19, 15)
  def getRs2(inst: UInt): UInt = inst(24, 20)
  def getFunct7(inst: UInt): UInt = inst(31, 25)

  // Check if instruction is RV32E compatible (only uses x0-x15)
  def isRV32E(inst: UInt): Bool = {
    val rd_valid = getRd(inst) < 16.U
    val rs1_valid = getRs1(inst) < 16.U
    val rs2_valid = getRs2(inst) < 16.U
    rd_valid && rs1_valid && rs2_valid
  }
}

/**
 * Control Signals Bundle
 * 控制信号集合，用于流水线各级之间传递控制信息
 */
class ControlSignals extends Bundle {
  // EX stage control
  val alu_op = UInt(ALUOp.width.W)
  val alu_src1 = UInt(2.W)  // 0: rs1, 1: PC, 2: 0
  val alu_src2 = UInt(2.W)  // 0: rs2, 1: imm, 2: 4
  val branch = Bool()
  val branch_type = UInt(BranchType.width.W)

  // MEM stage control
  val mem_read = Bool()
  val mem_write = Bool()
  val mem_size = UInt(2.W)  // 0: byte, 1: half, 2: word
  val mem_unsigned = Bool()

  // WB stage control
  val reg_write = Bool()
  val wb_src = UInt(2.W)  // 0: ALU, 1: MEM, 2: PC+4
}
