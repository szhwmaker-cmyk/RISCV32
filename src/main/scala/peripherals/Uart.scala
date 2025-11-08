package peripherals

import chisel3._
import chisel3.util._
import bus._
import common.Config._

// ============================================================================
// UART 外设 - 完整TX/RX实现
// ============================================================================

class Uart extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)

    // UART 外部引脚
    val tx = Output(Bool())
    val rx = Input(Bool())
  })

  // ========== 寄存器 ==========
  val baud_reg  = RegInit(BAUD_DIV.U(16.W))
  val ctrl_reg  = RegInit(3.U(8.W))   // [1:0] = {tx_en, rx_en}, 默认都使能
  val tx_data_reg = RegInit(0.U(8.W))
  val rx_data_reg = RegInit(0.U(8.W))
  val status_reg = Wire(UInt(8.W))

  // ========== TX 状态机 ==========
  val tx_idle :: tx_start :: tx_data :: tx_stop :: Nil = Enum(4)
  val tx_state = RegInit(tx_idle)
  val tx_clk_cnt = RegInit(0.U(16.W))
  val tx_bit_cnt = RegInit(0.U(4.W))
  val tx_shift_reg = RegInit(0.U(8.W))
  val tx_out_reg = RegInit(true.B)
  val tx_busy = RegInit(false.B)

  when(tx_state =/= tx_idle) {
    tx_clk_cnt := tx_clk_cnt + 1.U
  }.otherwise {
    tx_clk_cnt := 0.U
  }

  switch(tx_state) {
    is(tx_idle) {
      tx_out_reg := true.B
      tx_busy := false.B
    }
    is(tx_start) {
      tx_out_reg := false.B  // Start bit
      tx_busy := true.B
      when(tx_clk_cnt >= baud_reg) {
        tx_state := tx_data
        tx_clk_cnt := 0.U
        tx_bit_cnt := 0.U
      }
    }
    is(tx_data) {
      tx_out_reg := tx_shift_reg(0)
      when(tx_clk_cnt >= baud_reg) {
        tx_shift_reg := tx_shift_reg >> 1
        tx_bit_cnt := tx_bit_cnt + 1.U
        tx_clk_cnt := 0.U
        when(tx_bit_cnt === 7.U) {
          tx_state := tx_stop
        }
      }
    }
    is(tx_stop) {
      tx_out_reg := true.B  // Stop bit
      when(tx_clk_cnt >= baud_reg) {
        tx_state := tx_idle
        tx_clk_cnt := 0.U
      }
    }
  }

  io.tx := tx_out_reg

  // ========== RX 状态机 ==========
  val rx_sync = RegInit(VecInit(Seq.fill(3)(true.B)))
  rx_sync(0) := io.rx
  rx_sync(1) := rx_sync(0)
  rx_sync(2) := rx_sync(1)

  val rx_idle :: rx_start :: rx_data :: rx_stop :: Nil = Enum(4)
  val rx_state = RegInit(rx_idle)
  val rx_clk_cnt = RegInit(0.U(16.W))
  val rx_bit_cnt = RegInit(0.U(4.W))
  val rx_shift_reg = RegInit(0.U(8.W))
  val rx_valid = RegInit(false.B)

  when(rx_state =/= rx_idle) {
    rx_clk_cnt := rx_clk_cnt + 1.U
  }

  switch(rx_state) {
    is(rx_idle) {
      rx_clk_cnt := 0.U
      when(!rx_sync(2) && ctrl_reg(1)) {  // Start bit detected
        rx_state := rx_start
      }
    }
    is(rx_start) {
      when(rx_clk_cnt >= (baud_reg >> 1)) {  // Sample in middle
        when(!rx_sync(2)) {  // Still low
          rx_state := rx_data
          rx_clk_cnt := 0.U
          rx_bit_cnt := 0.U
        }.otherwise {
          rx_state := rx_idle  // False start
        }
      }
    }
    is(rx_data) {
      when(rx_clk_cnt >= baud_reg) {
        rx_shift_reg := Cat(rx_sync(2), rx_shift_reg(7, 1))
        rx_bit_cnt := rx_bit_cnt + 1.U
        rx_clk_cnt := 0.U
        when(rx_bit_cnt === 7.U) {
          rx_state := rx_stop
        }
      }
    }
    is(rx_stop) {
      when(rx_clk_cnt >= baud_reg) {
        when(rx_sync(2)) {  // Valid stop bit
          rx_data_reg := rx_shift_reg
          rx_valid := true.B
        }
        rx_state := rx_idle
        rx_clk_cnt := 0.U
      }
    }
  }

  // 状态寄存器
  val tx_empty = !tx_busy
  status_reg := Cat(Fill(4, 0.U), rx_valid, 0.U, tx_empty, tx_busy)

  // ========== Wishbone 接口 ==========
  val ack_reg = RegNext(io.wb.stb_o && io.wb.cyc_o, false.B)
  io.wb.ack_o := ack_reg

  val reg_addr = io.wb.adr_o(7, 0)

  // 读操作
  io.wb.dat_o := MuxLookup(reg_addr, 0.U)(Seq(
    UartReg.TXDATA.U -> 0.U,
    UartReg.RXDATA.U -> rx_data_reg,
    UartReg.STATUS.U -> status_reg,
    UartReg.BAUD.U   -> baud_reg,
    UartReg.CTRL.U   -> ctrl_reg
  ))

  // 写操作
  when(io.wb.stb_o && io.wb.cyc_o && io.wb.we_o) {
    when(reg_addr === UartReg.TXDATA.U && !tx_busy && ctrl_reg(0)) {
      tx_data_reg := io.wb.dat_o(7, 0)
      tx_shift_reg := io.wb.dat_o(7, 0)
      tx_state := tx_start
    }.elsewhen(reg_addr === UartReg.BAUD.U) {
      baud_reg := io.wb.dat_o(15, 0)
    }.elsewhen(reg_addr === UartReg.CTRL.U) {
      ctrl_reg := io.wb.dat_o(7, 0)
    }.elsewhen(reg_addr === UartReg.RXDATA.U) {
      rx_valid := false.B  // 清除valid标志
    }
  }
}
