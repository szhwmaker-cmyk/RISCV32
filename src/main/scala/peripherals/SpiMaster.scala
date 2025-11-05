package rv32e.peripherals

import chisel3._
import chisel3.util._
import rv32e.bus._
import rv32e.Config

/**
 * SPI Master Controller
 *
 * Configurable SPI master supporting:
 * - Standard SPI modes (CPOL/CPHA configuration)
 * - 8/16/32-bit transfers
 * - Configurable clock divider
 *
 * Register Map:
 * 0x00: CTRL    - Control register
 *                 [0] start  - Start transfer (write 1)
 *                 [1] busy   - Transfer in progress (read only)
 *                 [2] done   - Transfer complete (read only)
 *                 [3] cpol   - Clock polarity
 *                 [4] cpha   - Clock phase
 *                 [6:5] size - Transfer size: 00=8bit, 01=16bit, 10=32bit
 * 0x04: DIV     - Clock divider (spi_clk = sys_clk / (2 * (DIV + 1)))
 * 0x08: TXDATA  - Transmit data (write)
 * 0x0C: RXDATA  - Receive data (read)
 */

class SpiMasterIO extends Bundle {
  // Wishbone slave interface
  val wb = Flipped(new WishboneMasterIO)

  // Physical SPI pins
  val spi_sck  = Output(Bool())
  val spi_mosi = Output(Bool())
  val spi_miso = Input(Bool())
  val spi_cs_n = Output(Bool())
}

class SpiMaster extends Module {
  val io = IO(new SpiMasterIO)

  // ========== Registers ==========
  val ctrl_start = RegInit(false.B)
  val ctrl_busy = RegInit(false.B)
  val ctrl_done = RegInit(false.B)
  val ctrl_cpol = RegInit(false.B)
  val ctrl_cpha = RegInit(false.B)
  val ctrl_size = RegInit(0.U(2.W))  // 0=8bit, 1=16bit, 2=32bit

  val clk_div = RegInit(4.U(8.W))  // Default divider
  val tx_data = RegInit(0.U(32.W))
  val rx_data = RegInit(0.U(32.W))

  // ========== SPI State Machine ==========
  val shift_reg = RegInit(0.U(32.W))
  val bit_count = RegInit(0.U(6.W))
  val clk_counter = RegInit(0.U(8.W))
  val spi_clk = RegInit(false.B)
  val spi_phase = RegInit(false.B)

  val transfer_bits = MuxLookup(ctrl_size, 8.U)(Seq(
    0.U -> 8.U,
    1.U -> 16.U,
    2.U -> 32.U
  ))

  val s_idle :: s_transfer :: s_done :: Nil = Enum(3)
  val state = RegInit(s_idle)

  // ========== SPI Clock Generation ==========
  val clk_en = WireDefault(false.B)
  when(clk_counter === clk_div) {
    clk_counter := 0.U
    clk_en := true.B
  }.elsewhen(state === s_transfer) {
    clk_counter := clk_counter + 1.U
  }

  // ========== SPI Outputs ==========
  io.spi_cs_n := state =/= s_transfer
  io.spi_sck := spi_clk ^ ctrl_cpol  // Apply CPOL
  io.spi_mosi := shift_reg(31)

  // ========== State Machine ==========
  switch(state) {
    is(s_idle) {
      ctrl_busy := false.B
      ctrl_done := false.B
      spi_clk := false.B
      spi_phase := ctrl_cpha

      when(ctrl_start) {
        ctrl_start := false.B
        ctrl_busy := true.B
        shift_reg := tx_data
        bit_count := 0.U
        clk_counter := 0.U
        state := s_transfer
      }
    }

    is(s_transfer) {
      when(clk_en) {
        when(ctrl_cpha === 0.U) {
          // CPHA=0: sample on first edge, shift on second
          when(!spi_phase) {
            // First edge: sample
            spi_clk := true.B
            spi_phase := true.B
            shift_reg := Cat(shift_reg(30, 0), io.spi_miso)
          }.otherwise {
            // Second edge: shift
            spi_clk := false.B
            spi_phase := false.B
            bit_count := bit_count + 1.U

            when(bit_count === transfer_bits - 1.U) {
              state := s_done
              rx_data := shift_reg
            }
          }
        }.otherwise {
          // CPHA=1: shift on first edge, sample on second
          when(!spi_phase) {
            // First edge: shift
            spi_clk := true.B
            spi_phase := true.B
          }.otherwise {
            // Second edge: sample
            spi_clk := false.B
            spi_phase := false.B
            shift_reg := Cat(shift_reg(30, 0), io.spi_miso)
            bit_count := bit_count + 1.U

            when(bit_count === transfer_bits - 1.U) {
              state := s_done
              rx_data := shift_reg
            }
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

  // ========== Wishbone Interface ==========
  val wb_ack = RegInit(false.B)
  io.wb.ack := wb_ack
  io.wb.dat_i := 0.U

  when(io.wb.cyc && io.wb.stb && !wb_ack) {
    wb_ack := true.B

    when(io.wb.we) {
      // Write operation
      switch(io.wb.adr(3, 2)) {
        is(0.U) { // CTRL
          ctrl_start := io.wb.dat_o(0)
          ctrl_cpol := io.wb.dat_o(3)
          ctrl_cpha := io.wb.dat_o(4)
          ctrl_size := io.wb.dat_o(6, 5)
        }
        is(1.U) { // DIV
          clk_div := io.wb.dat_o(7, 0)
        }
        is(2.U) { // TXDATA
          tx_data := io.wb.dat_o
        }
      }
    }.otherwise {
      // Read operation
      switch(io.wb.adr(3, 2)) {
        is(0.U) { // CTRL
          io.wb.dat_i := Cat(
            0.U(25.W),
            ctrl_size,
            ctrl_cpha,
            ctrl_cpol,
            ctrl_done,
            ctrl_busy,
            ctrl_start
          )
        }
        is(1.U) { // DIV
          io.wb.dat_i := clk_div
        }
        is(3.U) { // RXDATA
          io.wb.dat_i := rx_data
        }
      }
    }
  }.otherwise {
    wb_ack := false.B
  }
}

object SpiMaster extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.peripherals.SpiMaster"))
}
