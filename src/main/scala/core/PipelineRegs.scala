package rv32e.core

import chisel3._
import chisel3.util._

/**
 * Control Signals Bundle
 *
 * All control signals needed throughout the pipeline
 */
class ControlSignals extends Bundle {
  // ALU control
  val alu_op    = UInt(4.W)      // ALU operation
  val alu_src   = Bool()          // ALU source: 0=rs2, 1=immediate

  // Memory control
  val mem_read  = Bool()          // Memory read enable
  val mem_write = Bool()          // Memory write enable
  val mem_size  = UInt(2.W)       // Memory access size: 0=byte, 1=half, 2=word
  val mem_unsigned = Bool()       // Unsigned load

  // Branch/Jump control
  val branch    = Bool()          // Branch instruction
  val jump      = Bool()          // Jump instruction (JAL/JALR)
  val branch_op = UInt(3.W)       // Branch operation type

  // Write-back control
  val reg_write = Bool()          // Register write enable
  val wb_sel    = UInt(2.W)       // Write-back source: 0=ALU, 1=MEM, 2=PC+4

  // Exception control
  val is_ecall  = Bool()          // ECALL instruction
  val is_ebreak = Bool()          // EBREAK instruction
  val is_fence  = Bool()          // FENCE instruction (NOP in this impl)
}

object WBSel {
  val ALU = 0.U(2.W)
  val MEM = 1.U(2.W)
  val PC4 = 2.U(2.W)
}

/**
 * Branch Operations
 */
object BranchOp {
  val BEQ  = 0.U(3.W)  // Branch if equal
  val BNE  = 1.U(3.W)  // Branch if not equal
  val BLT  = 2.U(3.W)  // Branch if less than (signed)
  val BGE  = 3.U(3.W)  // Branch if greater or equal (signed)
  val BLTU = 4.U(3.W)  // Branch if less than (unsigned)
  val BGEU = 5.U(3.W)  // Branch if greater or equal (unsigned)
}

/**
 * IF/ID Pipeline Register
 */
class IF_ID_Reg extends Bundle {
  val pc   = UInt(32.W)  // Program counter
  val inst = UInt(32.W)  // Instruction
  val valid = Bool()     // Valid instruction (not flushed)
}

/**
 * ID/EX Pipeline Register
 */
class ID_EX_Reg extends Bundle {
  val pc       = UInt(32.W)  // Program counter
  val rs1_data = UInt(32.W)  // Register source 1 data
  val rs2_data = UInt(32.W)  // Register source 2 data
  val imm      = UInt(32.W)  // Immediate value
  val rs1_addr = UInt(4.W)   // Source register 1 address (for forwarding)
  val rs2_addr = UInt(4.W)   // Source register 2 address (for forwarding)
  val rd_addr  = UInt(4.W)   // Destination register address
  val ctrl     = new ControlSignals  // Control signals
  val valid    = Bool()      // Valid instruction
}

/**
 * EX/MEM Pipeline Register
 */
class EX_MEM_Reg extends Bundle {
  val pc          = UInt(32.W)  // Program counter
  val alu_result  = UInt(32.W)  // ALU computation result
  val rs2_data    = UInt(32.W)  // Register source 2 (for store)
  val rd_addr     = UInt(4.W)   // Destination register address
  val mem_read    = Bool()
  val mem_write   = Bool()
  val mem_size    = UInt(2.W)
  val mem_unsigned = Bool()
  val reg_write   = Bool()
  val wb_sel      = UInt(2.W)
  val valid       = Bool()
}

/**
 * MEM/WB Pipeline Register
 */
class MEM_WB_Reg extends Bundle {
  val pc          = UInt(32.W)
  val alu_result  = UInt(32.W)  // ALU result
  val mem_data    = UInt(32.W)  // Memory load data
  val rd_addr     = UInt(4.W)   // Destination register
  val reg_write   = Bool()
  val wb_sel      = UInt(2.W)   // Write-back data selector
  val valid       = Bool()
}

/**
 * Helper object to create default/invalid pipeline registers
 */
object PipelineRegs {
  def defaultControl(): ControlSignals = {
    val ctrl = Wire(new ControlSignals)
    ctrl.alu_op := 0.U
    ctrl.alu_src := false.B
    ctrl.mem_read := false.B
    ctrl.mem_write := false.B
    ctrl.mem_size := 0.U
    ctrl.mem_unsigned := false.B
    ctrl.branch := false.B
    ctrl.jump := false.B
    ctrl.branch_op := 0.U
    ctrl.reg_write := false.B
    ctrl.wb_sel := 0.U
    ctrl.is_ecall := false.B
    ctrl.is_ebreak := false.B
    ctrl.is_fence := false.B
    ctrl
  }

  def invalidIFID(): IF_ID_Reg = {
    val reg = Wire(new IF_ID_Reg)
    reg.pc := 0.U
    reg.inst := 0.U  // NOP
    reg.valid := false.B
    reg
  }

  def invalidIDEX(): ID_EX_Reg = {
    val reg = Wire(new ID_EX_Reg)
    reg.pc := 0.U
    reg.rs1_data := 0.U
    reg.rs2_data := 0.U
    reg.imm := 0.U
    reg.rs1_addr := 0.U
    reg.rs2_addr := 0.U
    reg.rd_addr := 0.U
    reg.ctrl := defaultControl()
    reg.valid := false.B
    reg
  }

  def invalidEXMEM(): EX_MEM_Reg = {
    val reg = Wire(new EX_MEM_Reg)
    reg.pc := 0.U
    reg.alu_result := 0.U
    reg.rs2_data := 0.U
    reg.rd_addr := 0.U
    reg.mem_read := false.B
    reg.mem_write := false.B
    reg.mem_size := 0.U
    reg.mem_unsigned := false.B
    reg.reg_write := false.B
    reg.wb_sel := 0.U
    reg.valid := false.B
    reg
  }

  def invalidMEMWB(): MEM_WB_Reg = {
    val reg = Wire(new MEM_WB_Reg)
    reg.pc := 0.U
    reg.alu_result := 0.U
    reg.mem_data := 0.U
    reg.rd_addr := 0.U
    reg.reg_write := false.B
    reg.wb_sel := 0.U
    reg.valid := false.B
    reg
  }
}
