package rv32e.peripherals

import chisel3._
import chisel3.util._
import rv32e.bus._
import rv32e.Config

/**
 * I2C Master Controller
 *
 * Simple I2C master supporting:
 * - Standard mode (100kHz) and Fast mode (400kHz)
 * - 7-bit addressing
 * - START/STOP condition generation
 * - ACK/NACK detection
 *
 * Register Map:
 * 0x00: CTRL    - Control register
 *                 [0] start - Generate START condition
 *                 [1] stop  - Generate STOP condition
 *                 [2] write - Write operation
 *                 [3] read  - Read operation
 *                 [4] ack   - ACK bit (0=ACK, 1=NACK for reads)
 * 0x04: DIV     - Clock divider (i2c_scl = sys_clk / (4 * (DIV + 1)))
 * 0x08: TXDATA  - Transmit data/address (write)
 * 0x0C: RXDATA  - Receive data (read)
 * 0x10: STATUS  - Status register
 *                 [0] busy     - Operation in progress
 *                 [1] ack_recv - ACK received from slave
 *                 [2] arb_lost - Arbitration lost (not implemented)
 */

class I2cMasterIO extends Bundle {
  // Wishbone slave interface
  val wb = Flipped(new WishboneMasterIO)

  // Physical I2C pins (open-drain, need external pull-ups)
  val scl_out = Output(Bool())
  val scl_in  = Input(Bool())
  val sda_out = Output(Bool())
  val sda_in  = Input(Bool())
}

class I2cMaster extends Module {
  val io = IO(new I2cMasterIO)

  // ========== Registers ==========
  val ctrl_start = RegInit(false.B)
  val ctrl_stop  = RegInit(false.B)
  val ctrl_write = RegInit(false.B)
  val ctrl_read  = RegInit(false.B)
  val ctrl_ack   = RegInit(false.B)

  val clk_div = RegInit(125.U(16.W))  // Default for 100kHz @ 50MHz
  val tx_data = RegInit(0.U(8.W))
  val rx_data = RegInit(0.U(8.W))

  val status_busy = RegInit(false.B)
  val status_ack_recv = RegInit(false.B)

  // ========== I2C State Machine ==========
  val shift_reg = RegInit(0.U(8.W))
  val bit_count = RegInit(0.U(4.W))
  val clk_counter = RegInit(0.U(16.W))
  val scl_reg = RegInit(true.B)
  val sda_reg = RegInit(true.B)

  val s_idle :: s_start :: s_data :: s_ack :: s_stop :: Nil = Enum(5)
  val state = RegInit(s_idle)

  // Clock enable (I2C operates at 1/4 of the divided clock)
  val clk_en = WireDefault(false.B)
  when(clk_counter === clk_div) {
    clk_counter := 0.U
    clk_en := true.B
  }.elsewhen(state =/= s_idle) {
    clk_counter := clk_counter + 1.U
  }

  val i2c_phase = RegInit(0.U(2.W))  // 0=low, 1=rising, 2=high, 3=falling

  // ========== I2C Outputs (open-drain emulation) ==========
  io.scl_out := scl_reg
  io.sda_out := sda_reg

  // ========== State Machine ==========
  switch(state) {
    is(s_idle) {
      scl_reg := true.B
      sda_reg := true.B
      status_busy := false.B
      i2c_phase := 0.U

      when(ctrl_start) {
        ctrl_start := false.B
        status_busy := true.B
        state := s_start
        clk_counter := 0.U
      }
    }

    is(s_start) {
      // START condition: SDA falls while SCL is high
      when(clk_en) {
        switch(i2c_phase) {
          is(0.U) {  // Ensure SCL is high
            scl_reg := true.B
            i2c_phase := 1.U
          }
          is(1.U) {  // Pull SDA low (START)
            sda_reg := false.B
            i2c_phase := 2.U
          }
          is(2.U) {  // Pull SCL low
            scl_reg := false.B
            i2c_phase := 0.U

            // Check next operation
            when(ctrl_write) {
              ctrl_write := false.B
              shift_reg := tx_data
              bit_count := 0.U
              state := s_data
            }.elsewhen(ctrl_read) {
              ctrl_read := false.B
              shift_reg := 0.U
              bit_count := 0.U
              state := s_data
            }.elsewhen(ctrl_stop) {
              state := s_stop
            }
          }
        }
      }
    }

    is(s_data) {
      when(clk_en) {
        switch(i2c_phase) {
          is(0.U) {  // Setup data
            when(ctrl_write || bit_count =/= 0.U) {
              sda_reg := shift_reg(7)
            }
            i2c_phase := 1.U
          }
          is(1.U) {  // SCL rising edge
            scl_reg := true.B
            i2c_phase := 2.U
          }
          is(2.U) {  // Sample data (for reads)
            when(ctrl_read) {
              shift_reg := Cat(shift_reg(6, 0), io.sda_in)
            }
            i2c_phase := 3.U
          }
          is(3.U) {  // SCL falling edge
            scl_reg := false.B
            shift_reg := Cat(shift_reg(6, 0), 0.U(1.W))
            bit_count := bit_count + 1.U
            i2c_phase := 0.U

            when(bit_count === 7.U) {
              state := s_ack
              bit_count := 0.U
              when(ctrl_read) {
                rx_data := shift_reg
              }
            }
          }
        }
      }
    }

    is(s_ack) {
      when(clk_en) {
        switch(i2c_phase) {
          is(0.U) {  // Setup ACK/NACK
            when(ctrl_read) {
              sda_reg := ctrl_ack  // Master sends ACK/NACK
            }.otherwise {
              sda_reg := true.B  // Release SDA to receive ACK
            }
            i2c_phase := 1.U
          }
          is(1.U) {  // SCL rising
            scl_reg := true.B
            i2c_phase := 2.U
          }
          is(2.U) {  // Sample ACK
            when(!ctrl_read) {
              status_ack_recv := !io.sda_in
            }
            i2c_phase := 3.U
          }
          is(3.U) {  // SCL falling
            scl_reg := false.B
            i2c_phase := 0.U

            // Check next operation
            when(ctrl_write) {
              ctrl_write := false.B
              shift_reg := tx_data
              bit_count := 0.U
              state := s_data
            }.elsewhen(ctrl_read) {
              ctrl_read := false.B
              shift_reg := 0.U
              bit_count := 0.U
              state := s_data
            }.elsewhen(ctrl_stop) {
              state := s_stop
            }.otherwise {
              state := s_idle
            }
          }
        }
      }
    }

    is(s_stop) {
      // STOP condition: SDA rises while SCL is high
      when(clk_en) {
        switch(i2c_phase) {
          is(0.U) {  // Pull SDA low
            sda_reg := false.B
            i2c_phase := 1.U
          }
          is(1.U) {  // Pull SCL high
            scl_reg := true.B
            i2c_phase := 2.U
          }
          is(2.U) {  // Pull SDA high (STOP)
            sda_reg := true.B
            i2c_phase := 0.U
            ctrl_stop := false.B
            state := s_idle
          }
        }
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
        is(0.U) { // CTRL
          ctrl_start := io.wb.dat_o(0)
          ctrl_stop  := io.wb.dat_o(1)
          ctrl_write := io.wb.dat_o(2)
          ctrl_read  := io.wb.dat_o(3)
          ctrl_ack   := io.wb.dat_o(4)
        }
        is(1.U) { // DIV
          clk_div := io.wb.dat_o(15, 0)
        }
        is(2.U) { // TXDATA
          tx_data := io.wb.dat_o(7, 0)
        }
      }
    }.otherwise {
      // Read operation
      switch(io.wb.adr(3, 2)) {
        is(0.U) { // CTRL
          io.wb.dat_i := Cat(
            0.U(27.W),
            ctrl_ack,
            ctrl_read,
            ctrl_write,
            ctrl_stop,
            ctrl_start
          )
        }
        is(1.U) { // DIV
          io.wb.dat_i := clk_div
        }
        is(3.U) { // RXDATA
          io.wb.dat_i := rx_data
        }
        is(4.U) { // STATUS
          io.wb.dat_i := Cat(
            0.U(29.W),
            0.U(1.W),  // arb_lost (not implemented)
            status_ack_recv,
            status_busy
          )
        }
      }
    }
  }.otherwise {
    wb_ack := false.B
  }
}

object I2cMaster extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.peripherals.I2cMaster"))
}
