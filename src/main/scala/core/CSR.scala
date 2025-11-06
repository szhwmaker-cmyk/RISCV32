package rv32e.core

import chisel3._
import chisel3.util._
import rv32e.Config

/**
 * CSR (Control and Status Register) File
 *
 * Implements minimal Machine-mode CSRs for exception handling:
 * - mtvec  (0x305): Trap vector base address
 * - mepc   (0x341): Exception program counter
 * - mcause (0x342): Exception cause
 * - mstatus (0x300): Machine status register (simplified)
 */
object CSRAddr {
  // Machine-mode CSRs
  val MSTATUS = 0x300.U(12.W)
  val MTVEC   = 0x305.U(12.W)
  val MEPC    = 0x341.U(12.W)
  val MCAUSE  = 0x342.U(12.W)
}

object ExceptionCause {
  val BREAKPOINT = 3.U(32.W)   // EBREAK
  val ECALL_M    = 11.U(32.W)  // Environment call from M-mode
}

class CSRIO extends Bundle {
  // Read/Write interface
  val addr      = Input(UInt(12.W))   // CSR address
  val wdata     = Input(UInt(32.W))   // Write data
  val wen       = Input(Bool())        // Write enable
  val rdata     = Output(UInt(32.W))  // Read data

  // Exception interface
  val exception      = Input(Bool())       // Exception occurred
  val exception_pc   = Input(UInt(32.W))   // PC where exception occurred
  val exception_cause = Input(UInt(32.W))  // Exception cause code

  // Exception vector output
  val epc         = Output(UInt(32.W))  // Exception PC (for MRET)
  val trap_vector = Output(UInt(32.W))  // Trap handler address
}

class CSR extends Module {
  val io = IO(new CSRIO)

  // ========== CSR Registers ==========
  // FIXED: Use Config values for initialization (Problem #7)
  val mstatus = RegInit(Config.MSTATUS_INIT.U(32.W))  // Machine status (MIE=0, MPIE=0)
  val mtvec   = RegInit(Config.MTVEC_BASE.U(32.W))    // Default trap vector from Config
  val mepc    = RegInit(0.U(32.W))  // Machine exception PC
  val mcause  = RegInit(0.U(32.W))  // Machine exception cause

  // ========== Exception Handling ==========
  when(io.exception) {
    mepc := io.exception_pc
    mcause := io.exception_cause
  }

  // ========== CSR Write ==========
  when(io.wen) {
    switch(io.addr) {
      is(CSRAddr.MSTATUS) { mstatus := io.wdata }
      is(CSRAddr.MTVEC)   { mtvec := io.wdata }
      is(CSRAddr.MEPC)    { mepc := io.wdata }
      is(CSRAddr.MCAUSE)  { mcause := io.wdata }
    }
  }

  // ========== CSR Read ==========
  io.rdata := MuxLookup(io.addr, 0.U)(Seq(
    CSRAddr.MSTATUS -> mstatus,
    CSRAddr.MTVEC   -> mtvec,
    CSRAddr.MEPC    -> mepc,
    CSRAddr.MCAUSE  -> mcause
  ))

  // ========== Outputs ==========
  io.epc := mepc
  io.trap_vector := mtvec
}

object CSR extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.core.CSR"))
}
