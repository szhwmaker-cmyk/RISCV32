package rv32e.peripherals

import chisel3._
import chisel3.util._
import rv32e.bus._
import rv32e.Config

/**
 * UART Controller
 *
 * Simple UART with configurable baud rate.
 * Format: 8 data bits, no parity, 1 stop bit (8N1)
 *
 * Features:
 * - Configurable baud rate
 * - 16-byte TX and RX FIFOs
 * - Status flags: tx_full, tx_empty, rx_valid, rx_empty
 *
 * Register Map:
 * 0x00: TXDATA  - Transmit data (write only)
 * 0x04: RXDATA  - Receive data (read only)
 * 0x08: STATUS  - Status register (read only)
 *                 [31:4] Reserved
 *                 [3] rx_empty
 *                 [2] rx_valid
 *                 [1] tx_empty
 *                 [0] tx_full
 * 0x0C: BAUD    - Baud rate divisor (read/write)
 *                 divisor = clk_freq / baud_rate
 */

class UartIO extends Bundle {
  // Wishbone slave interface
  val wb = Flipped(new WishboneMasterIO)

  // Physical UART pins
  val tx = Output(Bool())
  val rx = Input(Bool())
}

class Uart extends Module {
  val io = IO(new UartIO)

  // ========== Configuration ==========
  val default_divisor = (Config.UART_CLOCK_FREQ / Config.UART_DEFAULT_BAUD).U
  val baud_div = RegInit(default_divisor)

  // ========== TX FIFO ==========
  val tx_fifo = Module(new Queue(UInt(8.W), Config.UART_FIFO_DEPTH))
  val tx_busy = RegInit(false.B)

  // ========== RX FIFO ==========
  val rx_fifo = Module(new Queue(UInt(8.W), Config.UART_FIFO_DEPTH))

  // ========== TX State Machine ==========
  val tx_shift_reg = RegInit(0xFF.U(10.W))  // Start + 8 data + stop
  val tx_bit_count = RegInit(0.U(4.W))
  val tx_baud_count = RegInit(0.U(16.W))

  val tx_idle :: tx_transmit :: Nil = Enum(2)
  val tx_state = RegInit(tx_idle)

  io.tx := tx_shift_reg(0)

  tx_fifo.io.deq.ready := false.B

  switch(tx_state) {
    is(tx_idle) {
      when(tx_fifo.io.deq.valid) {
        // Load data into shift register: stop bit + data + start bit
        tx_shift_reg := Cat(1.U(1.W), tx_fifo.io.deq.bits, 0.U(1.W))
        tx_bit_count := 0.U
        tx_baud_count := 0.U
        tx_state := tx_transmit
        tx_fifo.io.deq.ready := true.B
        tx_busy := true.B
      }.otherwise {
        tx_busy := false.B
      }
    }

    is(tx_transmit) {
      when(tx_baud_count === baud_div) {
        tx_baud_count := 0.U
        tx_shift_reg := Cat(1.U(1.W), tx_shift_reg(9, 1))
        tx_bit_count := tx_bit_count + 1.U

        when(tx_bit_count === 9.U) {
          tx_state := tx_idle
          tx_busy := false.B
        }
      }.otherwise {
        tx_baud_count := tx_baud_count + 1.U
      }
    }
  }

  // ========== RX State Machine ==========
  val rx_shift_reg = RegInit(0.U(8.W))
  val rx_bit_count = RegInit(0.U(4.W))
  val rx_baud_count = RegInit(0.U(16.W))
  val rx_sync = RegInit(VecInit(Seq.fill(3)(true.B)))

  // Synchronize RX input
  rx_sync(0) := io.rx
  rx_sync(1) := rx_sync(0)
  rx_sync(2) := rx_sync(1)
  val rx_synced = rx_sync(2)

  val rx_idle :: rx_start :: rx_data :: rx_stop :: Nil = Enum(4)
  val rx_state = RegInit(rx_idle)

  rx_fifo.io.enq.valid := false.B
  rx_fifo.io.enq.bits := 0.U

  switch(rx_state) {
    is(rx_idle) {
      when(!rx_synced) {  // Start bit detected (falling edge)
        rx_state := rx_start
        rx_baud_count := 0.U
      }
    }

    is(rx_start) {
      when(rx_baud_count === (baud_div >> 1)) {
        // Sample at middle of start bit
        when(!rx_synced) {
          rx_state := rx_data
          rx_baud_count := 0.U
          rx_bit_count := 0.U
        }.otherwise {
          // False start, return to idle
          rx_state := rx_idle
        }
      }.otherwise {
        rx_baud_count := rx_baud_count + 1.U
      }
    }

    is(rx_data) {
      when(rx_baud_count === baud_div) {
        rx_baud_count := 0.U
        rx_shift_reg := Cat(rx_synced, rx_shift_reg(7, 1))
        rx_bit_count := rx_bit_count + 1.U

        when(rx_bit_count === 7.U) {
          rx_state := rx_stop
        }
      }.otherwise {
        rx_baud_count := rx_baud_count + 1.U
      }
    }

    is(rx_stop) {
      when(rx_baud_count === baud_div) {
        when(rx_synced) {  // Valid stop bit
          rx_fifo.io.enq.valid := true.B
          rx_fifo.io.enq.bits := rx_shift_reg
        }
        rx_state := rx_idle
      }.otherwise {
        rx_baud_count := rx_baud_count + 1.U
      }
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
        is(0.U) { // TXDATA
          tx_fifo.io.enq.valid := true.B
          tx_fifo.io.enq.bits := io.wb.dat_o(7, 0)
        }
        is(3.U) { // BAUD
          baud_div := io.wb.dat_o(15, 0)
        }
      }
    }.otherwise {
      // Read operation
      switch(io.wb.adr(3, 2)) {
        is(1.U) { // RXDATA
          io.wb.dat_i := rx_fifo.io.deq.bits
          rx_fifo.io.deq.ready := true.B
        }
        is(2.U) { // STATUS
          io.wb.dat_i := Cat(
            0.U(28.W),
            !rx_fifo.io.deq.valid,  // rx_empty
            rx_fifo.io.deq.valid,   // rx_valid
            !tx_fifo.io.enq.ready,  // tx_full
            tx_fifo.io.count === 0.U  // tx_empty
          )
        }
        is(3.U) { // BAUD
          io.wb.dat_i := baud_div
        }
      }
    }
  }.otherwise {
    wb_ack := false.B
  }

  // Default FIFO connections
  when(!io.wb.cyc || !io.wb.stb || wb_ack) {
    tx_fifo.io.enq.valid := false.B
    rx_fifo.io.deq.ready := false.B
  }
}

object Uart extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.peripherals.Uart"))
}
