package peripherals

import chisel3._
import chisel3.util._
import bus._

/**
 * I2C Master Controller
 * I2C主控制器
 *
 * 特性：
 * - 支持标准模式(100kHz)和快速模式(400kHz)
 * - 7位地址模式
 * - 读写操作
 * - 中断支持
 * - Wishbone B4从设备接口
 *
 * 寄存器映射：
 * 0x00: CTRL   - 控制寄存器
 * 0x04: STATUS - 状态寄存器
 * 0x08: ADDR   - 地址寄存器（7位地址 + R/W位）
 * 0x0C: DATA   - 数据寄存器
 * 0x10: DIV    - 时钟分频器
 */

object I2CRegs {
  val CTRL   = 0x00.U(4.W)
  val STATUS = 0x04.U(4.W)
  val ADDR   = 0x08.U(4.W)
  val DATA   = 0x0C.U(4.W)
  val DIV    = 0x10.U(4.W)
}

object I2CCtrl {
  val START = 0  // Generate START condition
  val STOP  = 1  // Generate STOP condition
  val READ  = 2  // Read operation
  val WRITE = 3  // Write operation
  val ACK   = 4  // ACK/NACK value (0=ACK, 1=NACK)
  val IE    = 5  // Interrupt enable
}

object I2CStatus {
  val BUSY     = 0  // Transfer in progress
  val DONE     = 1  // Transfer complete
  val ACK_RECV = 2  // ACK received from slave
  val ARB_LOST = 3  // Arbitration lost
}

/**
 * I2C Master Core
 */
class I2CMasterCore extends Module {
  val io = IO(new Bundle {
    // Control
    val start_cmd = Input(Bool())
    val stop_cmd = Input(Bool())
    val read_cmd = Input(Bool())
    val write_cmd = Input(Bool())
    val ack_value = Input(Bool())
    val clk_div = Input(UInt(16.W))

    // Data
    val tx_data = Input(UInt(8.W))
    val rx_data = Output(UInt(8.W))
    val busy = Output(Bool())
    val done = Output(Bool())
    val ack_received = Output(Bool())

    // I2C pins (open drain emulation)
    val scl_out = Output(Bool())
    val scl_in = Input(Bool())
    val sda_out = Output(Bool())
    val sda_in = Input(Bool())
  })

  // State machine
  val sIdle :: sStart :: sAddr :: sWrite :: sRead :: sAck :: sStop :: sDone :: Nil = Enum(8)
  val state = RegInit(sIdle)

  // Registers
  val clk_counter = RegInit(0.U(16.W))
  val bit_counter = RegInit(0.U(3.W))
  val shift_reg = RegInit(0.U(8.W))
  val scl_reg = RegInit(true.B)
  val sda_reg = RegInit(true.B)
  val ack_reg = RegInit(false.B)
  val done_reg = RegInit(false.B)

  // Clock generation
  val quarter_period = io.clk_div >> 2.U
  val half_period = io.clk_div >> 1.U
  val full_period = io.clk_div

  val clk_tick = Wire(Bool())
  clk_tick := false.B

  when(state =/= sIdle) {
    when(clk_counter === full_period) {
      clk_counter := 0.U
      clk_tick := true.B
    }.otherwise {
      clk_counter := clk_counter + 1.U
    }
  }.otherwise {
    clk_counter := 0.U
  }

  // State machine
  done_reg := false.B

  switch(state) {
    is(sIdle) {
      scl_reg := true.B
      sda_reg := true.B

      when(io.start_cmd) {
        state := sStart
      }.elsewhen(io.write_cmd || io.read_cmd) {
        shift_reg := io.tx_data
        bit_counter := 0.U
        when(io.write_cmd) {
          state := sWrite
        }.otherwise {
          state := sRead
        }
      }.elsewhen(io.stop_cmd) {
        state := sStop
      }
    }

    is(sStart) {
      // START condition: SDA falls while SCL is high
      when(clk_counter === quarter_period) {
        sda_reg := false.B
      }.elsewhen(clk_counter === half_period) {
        scl_reg := false.B
      }.elsewhen(clk_tick) {
        state := sIdle
        done_reg := true.B
      }
    }

    is(sWrite) {
      when(clk_counter === quarter_period) {
        sda_reg := shift_reg(7)
      }.elsewhen(clk_counter === half_period) {
        scl_reg := true.B
      }.elsewhen(clk_counter === full_period - quarter_period) {
        scl_reg := false.B
      }.elsewhen(clk_tick) {
        shift_reg := Cat(shift_reg(6, 0), 0.U(1.W))
        bit_counter := bit_counter + 1.U

        when(bit_counter === 7.U) {
          state := sAck
        }
      }
    }

    is(sRead) {
      when(clk_counter === quarter_period) {
        sda_reg := true.B  // Release SDA for slave to drive
      }.elsewhen(clk_counter === half_period) {
        scl_reg := true.B
        shift_reg := Cat(shift_reg(6, 0), io.sda_in)
      }.elsewhen(clk_counter === full_period - quarter_period) {
        scl_reg := false.B
      }.elsewhen(clk_tick) {
        bit_counter := bit_counter + 1.U

        when(bit_counter === 7.U) {
          state := sAck
        }
      }
    }

    is(sAck) {
      when(clk_counter === quarter_period) {
        sda_reg := io.ack_value  // Master drives ACK/NACK
      }.elsewhen(clk_counter === half_period) {
        scl_reg := true.B
        ack_reg := !io.sda_in  // Sample ACK from slave
      }.elsewhen(clk_counter === full_period - quarter_period) {
        scl_reg := false.B
      }.elsewhen(clk_tick) {
        state := sDone
      }
    }

    is(sStop) {
      // STOP condition: SDA rises while SCL is high
      when(clk_counter === quarter_period) {
        sda_reg := false.B
        scl_reg := true.B
      }.elsewhen(clk_counter === half_period) {
        sda_reg := true.B
      }.elsewhen(clk_tick) {
        state := sIdle
        done_reg := true.B
      }
    }

    is(sDone) {
      done_reg := true.B
      state := sIdle
    }
  }

  // Outputs
  io.scl_out := scl_reg
  io.sda_out := sda_reg
  io.rx_data := shift_reg
  io.busy := state =/= sIdle
  io.done := done_reg
  io.ack_received := ack_reg
}

/**
 * I2C Master with Wishbone Interface
 */
class I2CMaster extends Module {
  val io = IO(new Bundle {
    // Wishbone interface
    val wb = new WishboneSlave()

    // I2C pins (open drain, need external pull-ups)
    val scl_out = Output(Bool())
    val scl_in = Input(Bool())
    val sda_out = Output(Bool())
    val sda_in = Input(Bool())

    // Interrupt
    val irq = Output(Bool())
  })

  // I2C core
  val i2c_core = Module(new I2CMasterCore())

  // Registers
  val ctrl_reg = RegInit(0.U(32.W))
  val status_reg = Wire(UInt(32.W))
  val addr_reg = RegInit(0.U(32.W))
  val data_reg = RegInit(0.U(32.W))
  val div_reg = RegInit(100.U(32.W))  // Default divider

  // Extract control bits
  val start_cmd = ctrl_reg(I2CCtrl.START)
  val stop_cmd = ctrl_reg(I2CCtrl.STOP)
  val read_cmd = ctrl_reg(I2CCtrl.READ)
  val write_cmd = ctrl_reg(I2CCtrl.WRITE)
  val ack_value = ctrl_reg(I2CCtrl.ACK)
  val irq_en = ctrl_reg(I2CCtrl.IE)

  // Connect I2C core
  i2c_core.io.start_cmd := start_cmd
  i2c_core.io.stop_cmd := stop_cmd
  i2c_core.io.read_cmd := read_cmd
  i2c_core.io.write_cmd := write_cmd
  i2c_core.io.ack_value := ack_value
  i2c_core.io.clk_div := div_reg(15, 0)
  i2c_core.io.tx_data := data_reg(7, 0)
  i2c_core.io.scl_in := io.scl_in
  i2c_core.io.sda_in := io.sda_in

  // Clear command bits when operation starts
  when((start_cmd || stop_cmd || read_cmd || write_cmd) && i2c_core.io.busy) {
    ctrl_reg := ctrl_reg & ~((1.U << I2CCtrl.START) | (1.U << I2CCtrl.STOP) |
                              (1.U << I2CCtrl.READ) | (1.U << I2CCtrl.WRITE))
  }

  // Update data register when done
  when(i2c_core.io.done) {
    data_reg := Cat(0.U(24.W), i2c_core.io.rx_data)
  }

  // I2C pins
  io.scl_out := i2c_core.io.scl_out
  io.sda_out := i2c_core.io.sda_out

  // Status register
  status_reg := Cat(
    0.U(28.W),
    0.U(1.W),  // ARB_LOST (not implemented)
    i2c_core.io.ack_received,
    i2c_core.io.done,
    i2c_core.io.busy
  )

  // Wishbone interface
  val ack_reg = RegInit(false.B)
  ack_reg := io.wb.cyc_i && io.wb.stb_i && !ack_reg

  io.wb.ack_o := ack_reg
  io.wb.err_o := false.B
  io.wb.rty_o := false.B

  val reg_addr = io.wb.adr_i(5, 2)

  // Read logic
  io.wb.dat_o := MuxLookup(reg_addr, 0.U)(Seq(
    I2CRegs.CTRL   -> ctrl_reg,
    I2CRegs.STATUS -> status_reg,
    I2CRegs.ADDR   -> addr_reg,
    I2CRegs.DATA   -> data_reg,
    I2CRegs.DIV    -> div_reg
  ))

  // Write logic
  when(io.wb.cyc_i && io.wb.stb_i && io.wb.we_i) {
    switch(reg_addr) {
      is(I2CRegs.CTRL) {
        ctrl_reg := io.wb.dat_i
      }
      is(I2CRegs.ADDR) {
        addr_reg := io.wb.dat_i
      }
      is(I2CRegs.DATA) {
        data_reg := io.wb.dat_i
      }
      is(I2CRegs.DIV) {
        div_reg := io.wb.dat_i
      }
    }
  }

  // Interrupt
  io.irq := irq_en && i2c_core.io.done
}
