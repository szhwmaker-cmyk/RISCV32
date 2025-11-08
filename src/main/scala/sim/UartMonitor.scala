package sim

import chisel3._
import chisel3.util._

// ============================================================================
// UART 监控模型
// 用于在仿真中捕获UART输出
// ============================================================================

class UartMonitor(baud_div: Int = 434) extends Module {
  val io = IO(new Bundle {
    val rx = Input(Bool())
    val char_valid = Output(Bool())
    val char_data = Output(UInt(8.W))
  })

  // RX 同步
  val rx_sync = RegInit(VecInit(Seq.fill(3)(true.B)))
  rx_sync(0) := io.rx
  rx_sync(1) := rx_sync(0)
  rx_sync(2) := rx_sync(1)

  // RX 状态机
  val rx_idle :: rx_start :: rx_data :: rx_stop :: Nil = Enum(4)
  val rx_state = RegInit(rx_idle)

  val clk_cnt = RegInit(0.U(16.W))
  val bit_cnt = RegInit(0.U(4.W))
  val shift_reg = RegInit(0.U(8.W))
  val char_reg = RegInit(0.U(8.W))
  val valid_reg = RegInit(false.B)

  valid_reg := false.B  // 单周期有效

  when(rx_state =/= rx_idle) {
    clk_cnt := clk_cnt + 1.U
  }

  switch(rx_state) {
    is(rx_idle) {
      clk_cnt := 0.U
      when(!rx_sync(2)) {  // Start bit
        rx_state := rx_start
      }
    }

    is(rx_start) {
      when(clk_cnt >= (baud_div.U >> 1)) {
        when(!rx_sync(2)) {
          rx_state := rx_data
          clk_cnt := 0.U
          bit_cnt := 0.U
        }.otherwise {
          rx_state := rx_idle
        }
      }
    }

    is(rx_data) {
      when(clk_cnt >= baud_div.U) {
        shift_reg := Cat(rx_sync(2), shift_reg(7, 1))
        bit_cnt := bit_cnt + 1.U
        clk_cnt := 0.U
        when(bit_cnt === 7.U) {
          rx_state := rx_stop
        }
      }
    }

    is(rx_stop) {
      when(clk_cnt >= baud_div.U) {
        when(rx_sync(2)) {  // Valid stop bit
          char_reg := shift_reg
          valid_reg := true.B
        }
        rx_state := rx_idle
        clk_cnt := 0.U
      }
    }
  }

  io.char_valid := valid_reg
  io.char_data := char_reg
}
