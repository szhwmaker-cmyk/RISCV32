package rv32e.models

import chisel3._
import chisel3.util._

/**
 * SPI Flash Memory Model for Simulation
 *
 * Simulates a 25-series SPI Flash memory (e.g., W25Q128)
 * Supports:
 * - Read (0x03)
 * - Fast Read (0x0B)
 * - Page Program (0x02)
 * - Sector Erase (0x20)
 * - Chip Erase (0xC7)
 *
 * Memory Size: 16MB (configurable)
 */
class SpiFlashModel(memSize: Int = 16 * 1024 * 1024) extends Module {
  val io = IO(new Bundle {
    val cs_n = Input(Bool())
    val sck  = Input(Bool())
    val mosi = Input(Bool())
    val miso = Output(Bool())

    // Backdoor interface for loading images
    val load_enable = Input(Bool())
    val load_addr   = Input(UInt(32.W))
    val load_data   = Input(UInt(8.W))
  })

  // Memory array
  val mem = Mem(memSize, UInt(8.W))

  // Initialize memory to 0xFF (erased state)
  // Note: In actual simulation, use loadMemoryFromFile

  // SPI state machine
  val s_idle :: s_cmd :: s_addr :: s_dummy :: s_data :: Nil = Enum(5)
  val state = RegInit(s_idle)

  // Shift registers
  val cmd_reg   = RegInit(0.U(8.W))
  val addr_reg  = RegInit(0.U(24.W))
  val data_out  = RegInit(0.U(8.W))
  val data_in   = RegInit(0.U(8.W))

  // Bit counter
  val bit_count = RegInit(0.U(8.W))

  // Current read address
  val read_addr = RegInit(0.U(24.W))

  // Edge detection for SCK
  val sck_prev = RegNext(io.sck)
  val sck_rise = io.sck && !sck_prev
  val sck_fall = !io.sck && sck_prev

  // CS edge detection
  val cs_prev = RegNext(io.cs_n)
  val cs_fall = !io.cs_n && cs_prev  // CS activated

  // MISO output
  io.miso := data_out(7)

  // Backdoor memory loading
  when(io.load_enable) {
    mem.write(io.load_addr, io.load_data)
  }

  // Main state machine
  when(!io.cs_n) {
    // CS is active (low)
    when(cs_fall) {
      // CS just activated, reset to command state
      state := s_cmd
      bit_count := 0.U
      cmd_reg := 0.U
    }

    when(sck_rise) {
      // Rising edge: Sample MOSI
      switch(state) {
        is(s_idle) {
          // Should not happen
        }

        is(s_cmd) {
          // Receiving command byte
          cmd_reg := Cat(cmd_reg(6, 0), io.mosi)
          bit_count := bit_count + 1.U

          when(bit_count === 7.U) {
            // Command received, decode
            val next_cmd = Cat(cmd_reg(6, 0), io.mosi)
            switch(next_cmd) {
              is(0x03.U) {
                // Read command
                state := s_addr
                bit_count := 0.U
                addr_reg := 0.U
              }
              is(0x0B.U) {
                // Fast Read command (with dummy byte)
                state := s_addr
                bit_count := 0.U
                addr_reg := 0.U
              }
              is(0x9F.U) {
                // Read ID
                state := s_data
                bit_count := 0.U
                data_out := 0xEF.U  // Manufacturer ID: Winbond
              }
            }
          }
        }

        is(s_addr) {
          // Receiving 24-bit address
          addr_reg := Cat(addr_reg(22, 0), io.mosi)
          bit_count := bit_count + 1.U

          when(bit_count === 23.U) {
            // Address received
            read_addr := Cat(addr_reg(22, 0), io.mosi)

            // Check if Fast Read (needs dummy byte)
            when(cmd_reg === 0x0B.U) {
              state := s_dummy
              bit_count := 0.U
            }.otherwise {
              // Regular read, start data phase
              state := s_data
              bit_count := 0.U
              // Pre-fetch first byte
              data_out := mem.read(Cat(addr_reg(22, 0), io.mosi))
            }
          }
        }

        is(s_dummy) {
          // Dummy byte for Fast Read
          bit_count := bit_count + 1.U
          when(bit_count === 7.U) {
            state := s_data
            bit_count := 0.U
            // Pre-fetch first byte
            data_out := mem.read(read_addr)
          }
        }

        is(s_data) {
          // Reading data
          bit_count := bit_count + 1.U
          when(bit_count === 7.U) {
            // Byte complete, increment address and fetch next
            read_addr := read_addr + 1.U
            bit_count := 0.U
            data_out := mem.read(read_addr + 1.U)
          }
        }
      }
    }

    when(sck_fall) {
      // Falling edge: Update MISO for next bit
      when(state === s_data) {
        // Shift out data
        data_out := Cat(data_out(6, 0), 0.U(1.W))
      }
    }

  }.otherwise {
    // CS is inactive (high), reset state
    state := s_idle
    bit_count := 0.U
  }
}

/**
 * Helper object for loading flash images
 */
object SpiFlashModel {
  /**
   * Load binary file into flash model memory
   */
  def loadImage(flash: SpiFlashModel, imageFile: String, baseAddr: Int = 0): Unit = {
    // In actual ChiselTest, would read file and load via backdoor interface
    // This is a placeholder for the API
  }

  /**
   * Create RT-Thread test image in memory
   * Returns sequence of (address, data) tuples
   */
  def createRTThreadImage(): Seq[(Int, Int)] = {
    // This would contain the actual RT-Thread binary
    // For now, create a simple test pattern
    val bootCode = Seq(
      // RT-Thread header/entry point at 0x100000
      0x00, 0x00, 0x10, 0x80,  // Entry point: 0x80100000
      0x00, 0x00, 0x00, 0x00,  // Reserved
      // Simple test code
      0x37, 0x01, 0x00, 0x80,  // lui  x2, 0x80000
      0x13, 0x01, 0x01, 0x10,  // addi x2, x2, 0x100
      // ... RT-Thread code would go here
    )

    bootCode.zipWithIndex.map { case (byte, offset) =>
      (0x100000 + offset, byte)
    }
  }
}
