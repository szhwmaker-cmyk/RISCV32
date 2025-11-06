package rv32e.peripherals

import chisel3._
import chisel3.util._
import rv32e.bus._

/**
 * Flash Memory Model for Simulation
 *
 * This module simulates SPI Flash memory for testing the Boot flow.
 * It provides a simple memory-mapped interface that responds to reads
 * with data from an internal memory array.
 *
 * Features:
 * - 1MB addressable Flash memory (0x00000000 - 0x000FFFFF)
 * - Wishbone interface compatible with SoC interconnect
 * - Pre-loadable with test programs
 * - Single-cycle read access for simulation speed
 *
 * Usage in tests:
 * ```
 * val flash = Module(new FlashMemoryModel)
 * flash.io.wb <> interconnect.io.spi_flash
 *
 * // Load test program
 * flash.loadProgram(Seq(0x12345678.U, 0xDEADBEEF.U, ...))
 * ```
 */
class FlashMemoryModel extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMasterIO)
  })

  // Flash memory: 1MB = 256K words
  val FLASH_SIZE_WORDS = 256 * 1024
  val flash_mem = SyncReadMem(FLASH_SIZE_WORDS, UInt(32.W))

  // Wishbone control
  val wb_ack = RegInit(false.B)
  val read_data = RegInit(0.U(32.W))

  io.wb.ack := wb_ack
  io.wb.dat_i := read_data

  // Word address (byte address / 4)
  val word_addr = io.wb.adr(21, 2)  // 20 bits = 1MB address space

  when(io.wb.cyc && io.wb.stb && !wb_ack) {
    wb_ack := true.B

    when(io.wb.we) {
      // Write operation (for test setup)
      // Flash is normally read-only, but allow writes in simulation
      // to pre-load test programs
      flash_mem.write(word_addr, io.wb.dat_o)
      read_data := 0.U
    }.otherwise {
      // Read operation
      read_data := flash_mem.read(word_addr)
    }
  }.otherwise {
    wb_ack := false.B
  }

  /**
   * Helper function to load a program into Flash memory
   * Called from test code via reflection/poke
   */
  def loadProgramViaWishbone(dut: FlashMemoryModel, program: Seq[Long], base_addr: Long = 0): Unit = {
    import chiseltest._

    for ((data, offset) <- program.zipWithIndex) {
      val addr = base_addr + (offset * 4)

      // Perform Wishbone write
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(addr.U)
      dut.io.wb.dat_o.poke(data.U)
      dut.io.wb.sel.poke(0xF.U)
      dut.clock.step(1)

      // Wait for ACK
      dut.io.wb.ack.expect(true.B)

      // Clear transaction
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.io.wb.we.poke(false.B)
      dut.clock.step(1)
    }
  }
}

object FlashMemoryModel extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.peripherals.FlashMemoryModel"))
}
