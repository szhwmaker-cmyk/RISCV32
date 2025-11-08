package peripherals

import chisel3._
import chisel3.util._
import bus._
import common.Config._

// ============================================================================
// I2C Master 外设 - 完整协议实现
// ============================================================================

class I2cMaster extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)

    // I2C 外部引脚 (开漏输出)
    val scl_o = Output(Bool())
    val scl_i = Input(Bool())
    val sda_o = Output(Bool())
    val sda_i = Input(Bool())
  })

  // ========== 寄存器 ==========
  val ctrl_reg   = RegInit(0.U(8.W))   // [0]=start, [1]=stop, [2]=write, [3]=read
  val div_reg    = RegInit(250.U(16.W))  // 时钟分频 (100kHz @ 50MHz: 500)
  val addr_reg   = RegInit(0.U(8.W))   // 7-bit地址 + R/W
  val data_reg   = RegInit(0.U(8.W))
  val status_reg = Wire(UInt(8.W))

  // ========== I2C 状态机 ==========
  val i2c_idle :: i2c_start :: i2c_addr :: i2c_ack_addr ::
      i2c_data :: i2c_ack_data :: i2c_stop :: Nil = Enum(7)
  val i2c_state = RegInit(i2c_idle)

  val clk_cnt = RegInit(0.U(16.W))
  val bit_cnt = RegInit(0.U(4.W))
  val shift_reg = RegInit(0.U(8.W))
  val ack_bit = RegInit(false.B)
  val i2c_busy = RegInit(false.B)

  val scl_reg = RegInit(true.B)
  val sda_reg = RegInit(true.B)

  val sda_sync = RegNext(io.sda_i, true.B)
  val scl_sync = RegNext(io.scl_i, true.B)

  val quarter_period = div_reg >> 2

  // 时钟计数
  when(i2c_state =/= i2c_idle) {
    when(clk_cnt >= div_reg) {
      clk_cnt := 0.U
    }.otherwise {
      clk_cnt := clk_cnt + 1.U
    }
  }.otherwise {
    clk_cnt := 0.U
  }

  // I2C 状态机
  switch(i2c_state) {
    is(i2c_idle) {
      scl_reg := true.B
      sda_reg := true.B
      i2c_busy := false.B
    }

    is(i2c_start) {
      i2c_busy := true.B
      when(clk_cnt < quarter_period) {
        scl_reg := true.B
        sda_reg := true.B
      }.elsewhen(clk_cnt < quarter_period * 2.U) {
        scl_reg := true.B
        sda_reg := false.B  // START: SDA falls while SCL high
      }.elsewhen(clk_cnt < quarter_period * 3.U) {
        scl_reg := false.B
        sda_reg := false.B
      }.elsewhen(clk_cnt >= div_reg) {
        i2c_state := i2c_addr
        shift_reg := addr_reg
        bit_cnt := 0.U
      }
    }

    is(i2c_addr) {
      when(clk_cnt < quarter_period) {
        scl_reg := false.B
        sda_reg := shift_reg(7)
      }.elsewhen(clk_cnt < quarter_period * 3.U) {
        scl_reg := true.B
      }.elsewhen(clk_cnt >= div_reg) {
        shift_reg := shift_reg << 1
        bit_cnt := bit_cnt + 1.U
        when(bit_cnt === 7.U) {
          i2c_state := i2c_ack_addr
        }
      }
    }

    is(i2c_ack_addr) {
      when(clk_cnt < quarter_period) {
        scl_reg := false.B
        sda_reg := true.B  // Release SDA
      }.elsewhen(clk_cnt === quarter_period * 2.U) {
        ack_bit := !sda_sync  // Sample ACK
      }.elsewhen(clk_cnt < quarter_period * 3.U) {
        scl_reg := true.B
      }.elsewhen(clk_cnt >= div_reg) {
        when(ctrl_reg(2)) {  // Write
          i2c_state := i2c_data
          shift_reg := data_reg
          bit_cnt := 0.U
        }.elsewhen(ctrl_reg(3)) {  // Read
          i2c_state := i2c_data
          bit_cnt := 0.U
        }.otherwise {
          i2c_state := i2c_stop
        }
      }
    }

    is(i2c_data) {
      when(ctrl_reg(2)) {  // Write
        when(clk_cnt < quarter_period) {
          scl_reg := false.B
          sda_reg := shift_reg(7)
        }.elsewhen(clk_cnt < quarter_period * 3.U) {
          scl_reg := true.B
        }.elsewhen(clk_cnt >= div_reg) {
          shift_reg := shift_reg << 1
          bit_cnt := bit_cnt + 1.U
          when(bit_cnt === 7.U) {
            i2c_state := i2c_ack_data
          }
        }
      }.otherwise {  // Read
        when(clk_cnt < quarter_period) {
          scl_reg := false.B
          sda_reg := true.B
        }.elsewhen(clk_cnt === quarter_period * 2.U) {
          shift_reg := Cat(shift_reg(6, 0), sda_sync)
        }.elsewhen(clk_cnt < quarter_period * 3.U) {
          scl_reg := true.B
        }.elsewhen(clk_cnt >= div_reg) {
          bit_cnt := bit_cnt + 1.U
          when(bit_cnt === 7.U) {
            data_reg := shift_reg
            i2c_state := i2c_ack_data
          }
        }
      }
    }

    is(i2c_ack_data) {
      when(clk_cnt < quarter_period) {
        scl_reg := false.B
        sda_reg := Mux(ctrl_reg(2), true.B, false.B)  // Write: read ACK, Read: send ACK
      }.elsewhen(clk_cnt === quarter_period * 2.U && ctrl_reg(2)) {
        ack_bit := !sda_sync
      }.elsewhen(clk_cnt < quarter_period * 3.U) {
        scl_reg := true.B
      }.elsewhen(clk_cnt >= div_reg) {
        i2c_state := i2c_stop
      }
    }

    is(i2c_stop) {
      when(clk_cnt < quarter_period) {
        scl_reg := false.B
        sda_reg := false.B
      }.elsewhen(clk_cnt < quarter_period * 2.U) {
        scl_reg := true.B
        sda_reg := false.B
      }.elsewhen(clk_cnt < quarter_period * 3.U) {
        scl_reg := true.B
        sda_reg := true.B  // STOP: SDA rises while SCL high
      }.elsewhen(clk_cnt >= div_reg) {
        i2c_state := i2c_idle
      }
    }
  }

  // I2C 输出
  io.scl_o := scl_reg
  io.sda_o := sda_reg

  // 状态寄存器
  val i2c_done = (i2c_state === i2c_idle && i2c_busy)
  status_reg := Cat(Fill(5, 0.U), !ack_bit, i2c_done, i2c_busy)

  // ========== Wishbone 接口 ==========
  val ack_reg = RegNext(io.wb.stb_o && io.wb.cyc_o, false.B)
  io.wb.ack_o := ack_reg

  val reg_addr = io.wb.adr_o(7, 0)

  // 读操作
  io.wb.dat_o := MuxLookup(reg_addr, 0.U)(Seq(
    I2cReg.CTRL.U   -> ctrl_reg,
    I2cReg.DIV.U    -> div_reg,
    I2cReg.ADDR.U   -> addr_reg,
    I2cReg.DATA.U   -> data_reg,
    I2cReg.STATUS.U -> status_reg
  ))

  // 写操作
  when(io.wb.stb_o && io.wb.cyc_o && io.wb.we_o) {
    when(reg_addr === I2cReg.CTRL.U && !i2c_busy) {
      ctrl_reg := io.wb.dat_o(7, 0)
      when(io.wb.dat_o(0)) {  // START
        i2c_state := i2c_start
      }
    }.elsewhen(reg_addr === I2cReg.DIV.U) {
      div_reg := io.wb.dat_o(15, 0)
    }.elsewhen(reg_addr === I2cReg.ADDR.U) {
      addr_reg := io.wb.dat_o(7, 0)
    }.elsewhen(reg_addr === I2cReg.DATA.U) {
      data_reg := io.wb.dat_o(7, 0)
    }
  }
}
