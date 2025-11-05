package rv32e.peripherals

import chisel3._
import chisel3.util._
import rv32e.bus._
import rv32e.Config

/**
 * GPIO Controller
 *
 * Simple 16-bit bi-directional GPIO controller with:
 * - Configurable direction per pin
 * - Output enable control
 * - Input value reading
 *
 * Register Map:
 * 0x00: DATA_IN  - Input data (read only)
 * 0x04: DATA_OUT - Output data (read/write)
 * 0x08: DIR      - Direction (read/write)
 *                  0 = input, 1 = output
 * 0x0C: OE       - Output enable (read/write)
 *                  0 = disabled, 1 = enabled
 */

class GpioIO extends Bundle {
  // Wishbone slave interface
  val wb = Flipped(new WishboneMasterIO)

  // Physical GPIO pins (tri-state)
  val gpio_in  = Input(UInt(Config.GPIO_WIDTH.W))
  val gpio_out = Output(UInt(Config.GPIO_WIDTH.W))
  val gpio_oe  = Output(UInt(Config.GPIO_WIDTH.W))  // Output enable
}

class Gpio extends Module {
  val io = IO(new GpioIO)

  // ========== Registers ==========
  val data_out = RegInit(0.U(Config.GPIO_WIDTH.W))
  val dir      = RegInit(0.U(Config.GPIO_WIDTH.W))  // 0=input, 1=output
  val oe       = RegInit(0.U(Config.GPIO_WIDTH.W))  // Output enable

  // ========== GPIO Outputs ==========
  io.gpio_out := data_out
  io.gpio_oe := dir & oe

  // ========== Wishbone Interface ==========
  val wb_ack = RegInit(false.B)
  io.wb.ack := wb_ack
  io.wb.dat_i := 0.U

  when(io.wb.cyc && io.wb.stb && !wb_ack) {
    wb_ack := true.B

    when(io.wb.we) {
      // Write operation
      switch(io.wb.adr(3, 2)) {
        is(1.U) { // DATA_OUT
          data_out := io.wb.dat_o(Config.GPIO_WIDTH-1, 0)
        }
        is(2.U) { // DIR
          dir := io.wb.dat_o(Config.GPIO_WIDTH-1, 0)
        }
        is(3.U) { // OE
          oe := io.wb.dat_o(Config.GPIO_WIDTH-1, 0)
        }
      }
    }.otherwise {
      // Read operation
      switch(io.wb.adr(3, 2)) {
        is(0.U) { // DATA_IN
          io.wb.dat_i := io.gpio_in
        }
        is(1.U) { // DATA_OUT
          io.wb.dat_i := data_out
        }
        is(2.U) { // DIR
          io.wb.dat_i := dir
        }
        is(3.U) { // OE
          io.wb.dat_i := oe
        }
      }
    }
  }.otherwise {
    wb_ack := false.B
  }
}

object Gpio extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.peripherals.Gpio"))
}
