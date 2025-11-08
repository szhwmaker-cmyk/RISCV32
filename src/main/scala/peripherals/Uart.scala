package peripherals

import chisel3._
import chisel3.util._
import bus._
import common.Config._

/**
 * UART 控制器 - 完整实现
 *
 * 寄存器映射：
 * 0x00: TXDATA - 发送数据寄存器（写触发发送）
 * 0x04: RXDATA - 接收数据寄存器（读清除rx_valid）
 * 0x08: STATUS - 状态寄存器
 *       [0]: tx_busy   - 发送忙
 *       [1]: tx_empty  - 发送FIFO空
 *       [2]: rx_valid  - 接收数据有效
 *       [3]: rx_full   - 接收FIFO满
 * 0x0C: BAUD   - 波特率分频值 (系统时钟 / 波特率)
 * 0x10: CTRL   - 控制寄存器
 *       [0]: tx_en - 发送使能
 *       [1]: rx_en - 接收使能
 *
 * UART 格式: 8-N-1 (8位数据，无奇偶校验，1位停止位)
 */
class Uart extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)

    // UART 物理接口
    val tx = Output(Bool())
    val rx = Input(Bool())
  })

  // ============================================================================
  // 寄存器定义
  // ============================================================================

  val baud_reg = RegInit(UART_DEFAULT_DIV.U(16.W))
  val ctrl_reg = RegInit(0x03.U(8.W))  // tx_en = 1, rx_en = 1

  // ============================================================================
  // TX 发送逻辑
  // ============================================================================

  // TX 状态机
  val tx_idle :: tx_start :: tx_data :: tx_stop :: Nil = Enum(4)
  val tx_state = RegInit(tx_idle)

  val tx_data_reg = RegInit(0.U(8.W))
  val tx_bit_cnt = RegInit(0.U(3.W))
  val tx_clk_cnt = RegInit(0.U(16.W))
  val tx_reg = RegInit(true.B)  // TX线空闲时为高
  val tx_busy = WireDefault(false.B)

  // 发送FIFO（简化：单字节缓冲）
  val tx_fifo_valid = RegInit(false.B)
  val tx_fifo_data = RegInit(0.U(8.W))

  io.tx := tx_reg

  // TX 状态机
  switch(tx_state) {
    is(tx_idle) {
      tx_reg := true.B
      when(tx_fifo_valid && ctrl_reg(0)) {  // tx_en
        tx_state := tx_start
        tx_data_reg := tx_fifo_data
        tx_fifo_valid := false.B
        tx_clk_cnt := 0.U
      }
    }

    is(tx_start) {
      // 发送起始位（0）
      tx_reg := false.B
      tx_busy := true.B
      when(tx_clk_cnt === baud_reg - 1.U) {
        tx_clk_cnt := 0.U
        tx_state := tx_data
        tx_bit_cnt := 0.U
      }.otherwise {
        tx_clk_cnt := tx_clk_cnt + 1.U
      }
    }

    is(tx_data) {
      // 发送数据位（LSB first）
      tx_reg := tx_data_reg(0)
      tx_busy := true.B
      when(tx_clk_cnt === baud_reg - 1.U) {
        tx_clk_cnt := 0.U
        tx_data_reg := tx_data_reg >> 1
        when(tx_bit_cnt === 7.U) {
          tx_state := tx_stop
        }.otherwise {
          tx_bit_cnt := tx_bit_cnt + 1.U
        }
      }.otherwise {
        tx_clk_cnt := tx_clk_cnt + 1.U
      }
    }

    is(tx_stop) {
      // 发送停止位（1）
      tx_reg := true.B
      tx_busy := true.B
      when(tx_clk_cnt === baud_reg - 1.U) {
        tx_clk_cnt := 0.U
        tx_state := tx_idle
      }.otherwise {
        tx_clk_cnt := tx_clk_cnt + 1.U
      }
    }
  }

  // ============================================================================
  // RX 接收逻辑
  // ============================================================================

  // RX 状态机
  val rx_idle :: rx_start :: rx_data :: rx_stop :: Nil = Enum(4)
  val rx_state = RegInit(rx_idle)

  val rx_data_reg = RegInit(0.U(8.W))
  val rx_bit_cnt = RegInit(0.U(3.W))
  val rx_clk_cnt = RegInit(0.U(16.W))
  val rx_sync = RegInit(VecInit(Seq.fill(3)(true.B)))

  // 接收FIFO（简化：单字节缓冲）
  val rx_fifo_valid = RegInit(false.B)
  val rx_fifo_data = RegInit(0.U(8.W))

  // RX 输入同步（防止亚稳态）
  rx_sync(0) := io.rx
  rx_sync(1) := rx_sync(0)
  rx_sync(2) := rx_sync(1)
  val rx_in = rx_sync(2)

  // RX 状态机
  switch(rx_state) {
    is(rx_idle) {
      when(ctrl_reg(1) && !rx_in) {  // rx_en && 检测到起始位（下降沿）
        rx_state := rx_start
        rx_clk_cnt := 0.U
      }
    }

    is(rx_start) {
      // 等待到起始位中间采样
      when(rx_clk_cnt === (baud_reg >> 1)) {
        when(!rx_in) {  // 确认起始位
          rx_clk_cnt := 0.U
          rx_state := rx_data
          rx_bit_cnt := 0.U
          rx_data_reg := 0.U
        }.otherwise {
          // 虚假起始位，回到idle
          rx_state := rx_idle
        }
      }.otherwise {
        rx_clk_cnt := rx_clk_cnt + 1.U
      }
    }

    is(rx_data) {
      // 接收数据位（LSB first）
      when(rx_clk_cnt === baud_reg - 1.U) {
        rx_clk_cnt := 0.U
        // 采样数据位
        rx_data_reg := Cat(rx_in, rx_data_reg(7, 1))
        when(rx_bit_cnt === 7.U) {
          rx_state := rx_stop
        }.otherwise {
          rx_bit_cnt := rx_bit_cnt + 1.U
        }
      }.otherwise {
        rx_clk_cnt := rx_clk_cnt + 1.U
      }
    }

    is(rx_stop) {
      // 接收停止位
      when(rx_clk_cnt === baud_reg - 1.U) {
        rx_clk_cnt := 0.U
        when(rx_in) {  // 停止位应该是1
          // 接收成功，存入FIFO
          when(!rx_fifo_valid) {
            rx_fifo_data := rx_data_reg
            rx_fifo_valid := true.B
          }
        }
        rx_state := rx_idle
      }.otherwise {
        rx_clk_cnt := rx_clk_cnt + 1.U
      }
    }
  }

  // ============================================================================
  // Wishbone 总线接口
  // ============================================================================

  val addr_offset = io.wb.adr_o - UART_BASE.U

  // 写操作
  when(io.wb.cyc_o && io.wb.stb_o && io.wb.we_o) {
    switch(addr_offset) {
      is(UartRegs.TXDATA.U) {
        // 写入发送FIFO
        when(!tx_fifo_valid) {
          tx_fifo_data := io.wb.dat_o(7, 0)
          tx_fifo_valid := true.B
        }
      }
      is(UartRegs.BAUD.U) {
        baud_reg := io.wb.dat_o(15, 0)
      }
      is(UartRegs.CTRL.U) {
        ctrl_reg := io.wb.dat_o(7, 0)
      }
    }
  }

  // 读操作
  val status_reg = Cat(
    0.U(4.W),           // 保留
    rx_fifo_valid,      // [3] rx_full (简化版就是valid)
    rx_fifo_valid,      // [2] rx_valid
    !tx_fifo_valid,     // [1] tx_empty
    tx_busy             // [0] tx_busy
  )

  io.wb.dat_i := MuxLookup(addr_offset, 0.U)(Seq(
    UartRegs.TXDATA.U -> tx_fifo_data,
    UartRegs.RXDATA.U -> rx_fifo_data,
    UartRegs.STATUS.U -> status_reg,
    UartRegs.BAUD.U   -> baud_reg,
    UartRegs.CTRL.U   -> ctrl_reg
  ))

  // 读RXDATA时清除rx_valid
  when(io.wb.cyc_o && io.wb.stb_o && !io.wb.we_o && addr_offset === UartRegs.RXDATA.U) {
    rx_fifo_valid := false.B
  }

  // 应答信号
  io.wb.ack_i := RegNext(io.wb.cyc_o && io.wb.stb_o, false.B)
}
