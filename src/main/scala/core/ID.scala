package rv32e.core

import chisel3._
import chisel3.util._

/**
 * Instruction Decode (ID) Stage
 *
 * Responsibilities:
 * - Decode instruction
 * - Read register file
 * - Generate immediate values
 * - Generate control signals
 * - Pass everything to EX stage
 */
class IDIO extends Bundle {
  // Input from IF stage
  val if_id = Input(new IF_ID_Reg)

  // Register file interface
  val rf_rs1_addr = Output(UInt(4.W))
  val rf_rs2_addr = Output(UInt(4.W))
  val rf_rs1_data = Input(UInt(32.W))
  val rf_rs2_data = Input(UInt(32.W))

  // Output to EX stage
  val id_ex = Output(new ID_EX_Reg)

  // Control inputs
  val stall = Input(Bool())
  val flush = Input(Bool())

  // For AUIPC instruction (needs PC)
  val pc = Input(UInt(32.W))
}

class ID extends Module {
  val io = IO(new IDIO)

  // ========== Instruction Decoder ==========
  val decoder = Module(new Decode)
  decoder.io.inst := io.if_id.inst

  // ========== Register File Read ==========
  io.rf_rs1_addr := decoder.io.rs1
  io.rf_rs2_addr := decoder.io.rs2

  // ========== ID/EX Pipeline Register ==========
  val id_ex_reg = RegInit(PipelineRegs.invalidIDEX())

  when(io.flush) {
    // Insert bubble on flush
    id_ex_reg := PipelineRegs.invalidIDEX()
  }.elsewhen(!io.stall) {
    // Normal operation
    id_ex_reg.pc := io.if_id.pc
    id_ex_reg.rs1_data := io.rf_rs1_data
    id_ex_reg.rs2_data := io.rf_rs2_data
    id_ex_reg.imm := decoder.io.imm
    id_ex_reg.rs1_addr := decoder.io.rs1
    id_ex_reg.rs2_addr := decoder.io.rs2
    id_ex_reg.rd_addr := decoder.io.rd
    id_ex_reg.ctrl := decoder.io.ctrl
    id_ex_reg.valid := io.if_id.valid

    // Special handling for AUIPC: replace rs1_data with PC
    when(decoder.io.ctrl.alu_op === ALUOp.ADD &&
         decoder.io.inst(6, 0) === Opcode.AUIPC) {
      id_ex_reg.rs1_data := io.if_id.pc
    }
  }

  io.id_ex := id_ex_reg
}

object ID extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.core.ID"))
}
