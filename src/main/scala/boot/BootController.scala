package rv32e.boot

import chisel3._
import chisel3.util._
import rv32e.Config

/**
 * Boot Controller
 *
 * Controls the boot sequence of the RV32E SoC:
 * 1. Initialize SPI Flash controller
 * 2. Read boot code from Flash (starting at Flash address 0x0)
 * 3. Copy to RAM (starting at RAM base 0x8000_0000)
 * 4. Release processor to execute from RAM
 *
 * Boot FSM States:
 * - RESET: Initial state, assert CPU reset
 * - INIT_SPI: Configure SPI Flash controller
 * - READ_FLASH: Read data from Flash in chunks
 * - LOAD_MEM: Write data to RAM
 * - BOOT_DONE: Boot complete, release CPU
 * - RUN: Normal execution
 *
 * Memory Map during Boot:
 * - Flash region: 0x1000_0000 (read source)
 * - RAM region:   0x8000_0000 (write destination)
 * - Boot size:    Config.BOOT_COPY_SIZE (default 16KB)
 */

class BootControllerIO extends Bundle {
  // Control signals
  val boot_start = Input(Bool())   // Start boot sequence
  val boot_done  = Output(Bool())  // Boot complete

  // CPU control
  val cpu_reset = Output(Bool())   // Hold CPU in reset during boot
  val cpu_boot_pc = Output(UInt(32.W))  // Boot PC for CPU

  // Memory interface for copying (simplified - could use DMA in real system)
  val flash_addr = Output(UInt(24.W))
  val flash_read = Output(Bool())
  val flash_data = Input(UInt(32.W))
  val flash_valid = Input(Bool())

  val ram_addr = Output(UInt(32.W))
  val ram_data = Output(UInt(32.W))
  val ram_write = Output(Bool())
  val ram_ack = Input(Bool())
}

class BootController extends Module {
  val io = IO(new BootControllerIO)

  // Boot FSM states
  val s_reset :: s_init_spi :: s_read_flash :: s_load_mem :: s_boot_done :: s_run :: Nil = Enum(6)
  val state = RegInit(s_reset)

  // Boot progress counters
  val copy_addr = RegInit(0.U(32.W))  // Current address being copied
  val bytes_copied = RegInit(0.U(32.W))
  val total_bytes = Config.BOOT_COPY_SIZE.U

  // Temporary data buffer
  val data_buffer = RegInit(0.U(32.W))

  // Default outputs
  io.boot_done := false.B
  io.cpu_reset := true.B  // Keep CPU in reset by default
  io.cpu_boot_pc := Config.RAM_BASE.U
  io.flash_addr := 0.U
  io.flash_read := false.B
  io.ram_addr := 0.U
  io.ram_data := 0.U
  io.ram_write := false.B

  // ========== Boot State Machine ==========
  switch(state) {
    is(s_reset) {
      io.cpu_reset := true.B
      copy_addr := 0.U
      bytes_copied := 0.U

      when(io.boot_start) {
        state := s_init_spi
      }
    }

    is(s_init_spi) {
      io.cpu_reset := true.B
      // In a real system, we would configure SPI Flash controller here
      // For this simplified version, assume Flash is ready
      state := s_read_flash
    }

    is(s_read_flash) {
      io.cpu_reset := true.B

      // Request data from Flash at current address
      io.flash_addr := copy_addr(23, 0)
      io.flash_read := true.B

      when(io.flash_valid) {
        // Data received from Flash
        data_buffer := io.flash_data
        state := s_load_mem
      }
    }

    is(s_load_mem) {
      io.cpu_reset := true.B

      // Write data to RAM
      io.ram_addr := Config.RAM_BASE.U + copy_addr
      io.ram_data := data_buffer
      io.ram_write := true.B

      when(io.ram_ack) {
        // RAM write acknowledged
        copy_addr := copy_addr + 4.U
        bytes_copied := bytes_copied + 4.U

        when(bytes_copied >= total_bytes - 4.U) {
          // Boot copy complete
          state := s_boot_done
        }.otherwise {
          // Continue copying
          state := s_read_flash
        }
      }
    }

    is(s_boot_done) {
      io.cpu_reset := false.B  // Release CPU
      io.boot_done := true.B
      io.cpu_boot_pc := Config.RAM_BASE.U
      state := s_run
    }

    is(s_run) {
      // Normal execution - CPU is running
      io.cpu_reset := false.B
      io.boot_done := true.B
      // Boot controller is idle, CPU executes code from RAM
    }
  }
}

object BootController extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.boot.BootController"))
}
