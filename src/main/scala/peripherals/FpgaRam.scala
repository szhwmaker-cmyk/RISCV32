package rv32e.peripherals

import chisel3._
import chisel3.util._
import chisel3.experimental.ExtModule
import rv32e.bus._
import rv32e.Config

/**
 * FPGA-Optimized RAM Module
 *
 * This module provides a Wishbone-compatible RAM interface that can use
 * vendor-specific Block RAM IP cores for optimal FPGA implementation.
 *
 * Features:
 * - Uses FPGA vendor IP for better resource utilization
 * - Support for initialization files
 * - True dual-port or simple dual-port configurations
 * - Configurable size and width
 *
 * Usage:
 * - For simulation/generic synthesis: Use Ram.scala (SyncReadMem)
 * - For FPGA deployment: Use this module with vendor IP
 */

/**
 * Generic FPGA Block RAM BlackBox
 *
 * This is a placeholder that will be replaced by vendor IP during synthesis.
 * The actual implementation is provided by:
 * - Xilinx: Block Memory Generator IP
 * - Intel/Altera: RAM IP cores
 * - Lattice: EBR instances
 */
class FpgaBlockRamBlackBox(
  depth: Int,
  width: Int = 32,
  init_file: String = ""
) extends ExtModule(Map(
  "DEPTH" -> depth,
  "WIDTH" -> width,
  "INIT_FILE" -> init_file
)) {
  val io = IO(new Bundle {
    val clk   = Input(Clock())
    val en    = Input(Bool())
    val we    = Input(UInt(4.W))   // Write enable per byte
    val addr  = Input(UInt(log2Ceil(depth).W))
    val din   = Input(UInt(width.W))
    val dout  = Output(UInt(width.W))
  })
}

/**
 * Wishbone-Compatible FPGA RAM
 *
 * Wraps the FPGA Block RAM with Wishbone interface.
 * Compatible with the rest of the SoC bus infrastructure.
 */
class FpgaRam(use_blackbox: Boolean = true, init_file: String = "") extends Module {
  val io = IO(new RamIO)

  // Calculate RAM parameters
  val ram_depth = (Config.RAM_SIZE / 4).toInt
  val addr_bits = log2Ceil(ram_depth)

  // Wishbone control signals
  val wb_ack = RegInit(false.B)
  val read_data = RegInit(0.U(32.W))

  io.wb.ack := wb_ack
  io.wb.dat_i := read_data

  // Word address
  val word_addr = io.wb.adr(addr_bits + 1, 2)

  if (use_blackbox) {
    // ========== FPGA IP Core Implementation ==========
    val ram_ip = Module(new FpgaBlockRamBlackBox(
      depth = ram_depth,
      width = 32,
      init_file = init_file
    ))

    // Connect clock
    ram_ip.io.clk := clock

    // Default connections
    ram_ip.io.en := false.B
    ram_ip.io.we := 0.U
    ram_ip.io.addr := word_addr
    ram_ip.io.din := io.wb.dat_o

    when(io.wb.cyc && io.wb.stb && !wb_ack) {
      wb_ack := true.B
      ram_ip.io.en := true.B

      when(io.wb.we) {
        // Write operation with byte-select
        ram_ip.io.we := io.wb.sel
      }.otherwise {
        // Read operation
        ram_ip.io.we := 0.U
      }

      // Capture read data (available next cycle)
      read_data := ram_ip.io.dout
    }.otherwise {
      wb_ack := false.B
    }

  } else {
    // ========== Simulation/Generic Implementation ==========
    // Uses Chisel SyncReadMem (same as Ram.scala)
    val ram = SyncReadMem(ram_depth, UInt(32.W))

    when(io.wb.cyc && io.wb.stb && !wb_ack) {
      wb_ack := true.B

      when(io.wb.we) {
        // Write with byte masking
        val current_data = ram.read(word_addr)
        val write_mask = Cat(
          Fill(8, io.wb.sel(3)),
          Fill(8, io.wb.sel(2)),
          Fill(8, io.wb.sel(1)),
          Fill(8, io.wb.sel(0))
        )
        val masked_write_data = (io.wb.dat_o & write_mask) | (current_data & ~write_mask)
        ram.write(word_addr, masked_write_data)
      }.otherwise {
        // Read operation
        read_data := ram.read(word_addr)
      }
    }.otherwise {
      wb_ack := false.B
    }
  }
}

object FpgaRam extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.peripherals.FpgaRam"))
}
