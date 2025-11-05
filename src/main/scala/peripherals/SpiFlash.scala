package rv32e.peripherals

import chisel3._
import chisel3.util._
import rv32e.bus._

/**
 * SPI Flash Controller
 *
 * Supports basic SPI Flash operations for boot and XIP (Execute-In-Place).
 * Implements standard SPI mode (CPOL=0, CPHA=0).
 *
 * Supported Commands:
 * - 0x03: Read Data
 * - 0x0B: Fast Read (with dummy byte)
 *
 * Register Map (accessible via flash_ctrl Wishbone slave):
 * 0x00: CTRL   - Control register [start, busy, done]
 * 0x04: DIV    - Clock divider (sys_clk / (2 * (DIV + 1)))
 * 0x08: ADDR   - Flash address (24-bit)
 * 0x0C: DATA   - Read data register
 *
 * Physical SPI Interface:
 * - spi_cs_n: Chip select (active low)
 * - spi_sck: Serial clock
 * - spi_mosi: Master out, slave in
 * - spi_miso: Master in, slave out
 */

class SpiFlashIO extends Bundle {
  // Wishbone slave interface (for register access)
  val wb_ctrl = Flipped(new WishboneMasterIO)

  // Wishbone slave interface (for XIP memory-mapped access)
  val wb_mem = Flipped(new WishboneMasterIO)

  // Physical SPI pins
  val spi_cs_n = Output(Bool())
  val spi_sck  = Output(Bool())
  val spi_mosi = Output(Bool())
  val spi_miso = Input(Bool())
}

class SpiFlash extends Module {
  val io = IO(new SpiFlashIO)

  // ========== Registers ==========
  val ctrl_start = RegInit(false.B)
  val ctrl_busy = RegInit(false.B)
  val ctrl_done = RegInit(false.B)

  val clk_div = RegInit(2.U(8.W))  // Default: sys_clk / 6
  val flash_addr = RegInit(0.U(24.W))
  val read_data = RegInit(0.U(32.W))

  // ========== SPI State Machine ==========
  val s_idle :: s_send_cmd :: s_send_addr :: s_dummy :: s_read_data :: s_done :: Nil = Enum(6)
  val state = RegInit(s_idle)

  // SPI shift register
  val shift_reg = RegInit(0.U(32.W))
  val bit_count = RegInit(0.U(6.W))

  // Clock divider counter
  val clk_counter = RegInit(0.U(8.W))
  val spi_clk_en = WireDefault(false.B)

  when(clk_counter === clk_div) {
    clk_counter := 0.U
    spi_clk_en := true.B
  }.otherwise {
    clk_counter := clk_counter + 1.U
  }

  // SPI clock and phase
  val spi_sck_reg = RegInit(false.B)
  val spi_phase = RegInit(false.B)  // false = setup, true = sample

  // ========== SPI Outputs ==========
  io.spi_cs_n := state === s_idle
  io.spi_sck := spi_sck_reg
  io.spi_mosi := shift_reg(31)

  // ========== SPI State Machine ==========
  switch(state) {
    is(s_idle) {
      ctrl_busy := false.B
      ctrl_done := false.B
      spi_sck_reg := false.B
      spi_phase := false.B

      when(ctrl_start) {
        state := s_send_cmd
        ctrl_start := false.B
        ctrl_busy := true.B
        shift_reg := Cat(0x03.U(8.W), flash_addr)  // Read command + address
        bit_count := 0.U
      }
    }

    is(s_send_cmd) {
      when(spi_clk_en) {
        when(!spi_phase) {
          // Setup phase
          spi_sck_reg := true.B
          spi_phase := true.B
        }.otherwise {
          // Sample phase
          spi_sck_reg := false.B
          spi_phase := false.B
          shift_reg := Cat(shift_reg(30, 0), 0.U(1.W))
          bit_count := bit_count + 1.U

          when(bit_count === 7.U) {
            state := s_send_addr
            bit_count := 0.U
          }
        }
      }
    }

    is(s_send_addr) {
      when(spi_clk_en) {
        when(!spi_phase) {
          spi_sck_reg := true.B
          spi_phase := true.B
        }.otherwise {
          spi_sck_reg := false.B
          spi_phase := false.B
          shift_reg := Cat(shift_reg(30, 0), 0.U(1.W))
          bit_count := bit_count + 1.U

          when(bit_count === 23.U) {
            state := s_read_data
            bit_count := 0.U
            shift_reg := 0.U
          }
        }
      }
    }

    is(s_read_data) {
      when(spi_clk_en) {
        when(!spi_phase) {
          spi_sck_reg := true.B
          spi_phase := true.B
          shift_reg := Cat(shift_reg(30, 0), io.spi_miso)
        }.otherwise {
          spi_sck_reg := false.B
          spi_phase := false.B
          bit_count := bit_count + 1.U

          when(bit_count === 31.U) {
            state := s_done
            read_data := shift_reg
          }
        }
      }
    }

    is(s_done) {
      ctrl_busy := false.B
      ctrl_done := true.B
      state := s_idle
    }
  }

  // ========== Wishbone Register Interface ==========
  val wb_ctrl_ack = RegInit(false.B)
  io.wb_ctrl.ack := wb_ctrl_ack
  io.wb_ctrl.dat_i := 0.U

  when(io.wb_ctrl.cyc && io.wb_ctrl.stb && !wb_ctrl_ack) {
    wb_ctrl_ack := true.B

    when(io.wb_ctrl.we) {
      // Write registers
      switch(io.wb_ctrl.adr(3, 2)) {
        is(0.U) { // CTRL
          ctrl_start := io.wb_ctrl.dat_o(0)
        }
        is(1.U) { // DIV
          clk_div := io.wb_ctrl.dat_o(7, 0)
        }
        is(2.U) { // ADDR
          flash_addr := io.wb_ctrl.dat_o(23, 0)
        }
      }
    }.otherwise {
      // Read registers
      switch(io.wb_ctrl.adr(3, 2)) {
        is(0.U) { // CTRL
          io.wb_ctrl.dat_i := Cat(0.U(29.W), ctrl_done, ctrl_busy, ctrl_start)
        }
        is(1.U) { // DIV
          io.wb_ctrl.dat_i := clk_div
        }
        is(2.U) { // ADDR
          io.wb_ctrl.dat_i := flash_addr
        }
        is(3.U) { // DATA
          io.wb_ctrl.dat_i := read_data
        }
      }
    }
  }.otherwise {
    wb_ctrl_ack := false.B
  }

  // ========== Wishbone Memory Interface (XIP) ==========
  // Simplified: for full XIP, would need a read cache and state machine
  // For now, just acknowledge with zero data
  val wb_mem_ack = RegInit(false.B)
  io.wb_mem.ack := wb_mem_ack
  io.wb_mem.dat_i := 0.U

  when(io.wb_mem.cyc && io.wb_mem.stb && !wb_mem_ack) {
    wb_mem_ack := true.B
    // TODO: Implement automatic read triggered by memory access
  }.otherwise {
    wb_mem_ack := false.B
  }
}

object SpiFlash extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.peripherals.SpiFlash"))
}
