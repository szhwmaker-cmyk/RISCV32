package rv32e.core

import chisel3._
import chisel3.util._
import rv32e.Config

/**
 * Register File for RV32E
 *
 * Features:
 * - 16 general-purpose registers (x0-x15) per RV32E specification
 * - x0 is hardwired to zero
 * - 2 asynchronous read ports
 * - 1 synchronous write port
 * - Write-through: reads reflect writes in the same cycle
 *
 * Interface:
 * - rs1_addr, rs2_addr: Read port addresses
 * - rs1_data, rs2_data: Read port data outputs
 * - rd_addr: Write port address
 * - rd_data: Write port data input
 * - rd_wen: Write enable
 */
class RegFileIO extends Bundle {
  val rs1_addr = Input(UInt(4.W))   // Read port 1 address (4 bits for 16 regs)
  val rs2_addr = Input(UInt(4.W))   // Read port 2 address
  val rs1_data = Output(UInt(32.W)) // Read port 1 data
  val rs2_data = Output(UInt(32.W)) // Read port 2 data

  val rd_addr = Input(UInt(4.W))    // Write port address
  val rd_data = Input(UInt(32.W))   // Write port data
  val rd_wen  = Input(Bool())        // Write enable
}

class RegFile extends Module {
  val io = IO(new RegFileIO)

  // Register file: 16 registers x 32 bits
  // Using Mem for synthesizable register array
  val regfile = Mem(Config.REG_NUM, UInt(32.W))

  // ========== Read Ports (Asynchronous) ==========
  // x0 always reads as 0, other registers read from regfile
  io.rs1_data := Mux(io.rs1_addr === 0.U, 0.U, regfile(io.rs1_addr))
  io.rs2_data := Mux(io.rs2_addr === 0.U, 0.U, regfile(io.rs2_addr))

  // ========== Write Port (Synchronous) ==========
  // Only write if write enable is high AND destination is not x0
  when(io.rd_wen && io.rd_addr =/= 0.U) {
    regfile(io.rd_addr) := io.rd_data
  }

  // ========== Internal Forwarding (Write-Through) ==========
  // If reading the same register being written, forward the write data
  // This handles the case where a read occurs in the same cycle as a write
  when(io.rd_wen && io.rd_addr =/= 0.U) {
    when(io.rs1_addr === io.rd_addr) {
      io.rs1_data := io.rd_data
    }
    when(io.rs2_addr === io.rd_addr) {
      io.rs2_data := io.rd_data
    }
  }
}

object RegFile extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.core.RegFile"))
}
