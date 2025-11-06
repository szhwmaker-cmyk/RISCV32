package peripherals

import chisel3._
import chisel3.util._
import common.Config._
import bus._

/**
 * UART 控制器
 *
 * 支持：
 * - 标准 UART 协议 (8N1: 8 data bits, no parity, 1 stop bit)
 * - 可配置波特率
 * - 发送和接收 FIFO
 * - 状态标志（tx_full, tx_empty, rx_valid）
 *
 * 寄存器映射：
 * 0x00: TXDATA - 发送数据寄存器（写）
 * 0x04: RXDATA - 接收数据寄存器（读）
 * 0x08: STATUS - 状态寄存器（读）
 *       [0]: rx_valid - 接收数据有效
 *       [1]: tx_empty - 发送缓冲区空
 *       [2]: tx_full - 发送缓冲区满
 * 0x0C: BAUD - 波特率分频值（读写）
 * 0x10: CTRL - 控制寄存器（读写）
 *       [0]: tx_en - 发送使能
 *       [1]: rx_en - 接收使能
 */
class UartIO extends Bundle {
  val tx = Output(Bool())
  val rx = Input(Bool())
}

class Uart extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)
    val uart = new UartIO
  })

  // 寄存器
  val baud_div = RegInit(UART_DEFAULT_DIV.U(32.W))
  val tx_enable = RegInit(true.B)
  val rx_enable = RegInit(true.B)

  // 发送器
  val tx = Module(new UartTx)
  tx.io.baud_div := baud_div
  tx.io.enable := tx_enable
  io.uart.tx := tx.io.tx

  // 接收器
  val rx = Module(new UartRx)
  rx.io.baud_div := baud_div
  rx.io.enable := rx_enable
  rx.io.rx := io.uart.rx

  // Wishbone 总线处理
  val wb_addr = io.wb.adr(7, 0)
  val wb_write = io.wb.cyc && io.wb.stb && io.wb.we
  val wb_read = io.wb.cyc && io.wb.stb && !io.wb.we

  // 写操作
  when(wb_write) {
    switch(wb_addr) {
      is(UartRegs.TXDATA.U) {
        tx.io.din := io.wb.dat_w(7, 0)
        tx.io.wr_en := true.B
      }
      is(UartRegs.BAUD.U) {
        baud_div := io.wb.dat_w
      }
      is(UartRegs.CTRL.U) {
        tx_enable := io.wb.dat_w(0)
        rx_enable := io.wb.dat_w(1)
      }
    }
  }.otherwise {
    tx.io.wr_en := false.B
  }

  // 读取 RXDATA 时从接收器读取
  rx.io.rd_en := wb_read && (wb_addr === UartRegs.RXDATA.U)

  // 读操作
  val read_data = MuxLookup(wb_addr, 0.U)(
    Seq(
      UartRegs.RXDATA.U -> Cat(Fill(24, 0.U), rx.io.dout),
      UartRegs.STATUS.U -> Cat(
        Fill(29, 0.U),
        tx.io.tx_full,
        tx.io.tx_empty,
        rx.io.rx_valid
      ),
      UartRegs.BAUD.U -> baud_div,
      UartRegs.CTRL.U -> Cat(Fill(30, 0.U), rx_enable, tx_enable)
    )
  )

  io.wb.dat_r := read_data
  io.wb.ack := io.wb.cyc && io.wb.stb
  io.wb.err := false.B
  io.wb.rty := false.B
}

/**
 * UART 发送器
 */
class UartTx extends Module {
  val io = IO(new Bundle {
    val baud_div = Input(UInt(32.W))
    val enable = Input(Bool())
    val din = Input(UInt(8.W))
    val wr_en = Input(Bool())
    val tx = Output(Bool())
    val tx_empty = Output(Bool())
    val tx_full = Output(Bool())
  })

  // FIFO 深度
  val FIFO_DEPTH = 16

  // 发送 FIFO
  val fifo = Module(new Queue(UInt(8.W), FIFO_DEPTH))
  fifo.io.enq.valid := io.wr_en
  fifo.io.enq.bits := io.din

  io.tx_empty := !fifo.io.deq.valid
  io.tx_full := !fifo.io.enq.ready

  // 状态机
  val sIdle :: sStart :: sData :: sStop :: Nil = Enum(4)
  val state = RegInit(sIdle)

  val baud_counter = RegInit(0.U(32.W))
  val bit_counter = RegInit(0.U(4.W))
  val shift_reg = RegInit(0.U(8.W))
  val tx_reg = RegInit(true.B) // 空闲时为高

  io.tx := tx_reg

  // 波特率计数器
  val baud_tick = (baud_counter === 0.U)
  when(state =/= sIdle) {
    when(baud_counter === 0.U) {
      baud_counter := io.baud_div - 1.U
    }.otherwise {
      baud_counter := baud_counter - 1.U
    }
  }

  // 状态机
  fifo.io.deq.ready := false.B

  switch(state) {
    is(sIdle) {
      tx_reg := true.B
      when(io.enable && fifo.io.deq.valid) {
        shift_reg := fifo.io.deq.bits
        fifo.io.deq.ready := true.B
        baud_counter := io.baud_div - 1.U
        state := sStart
      }
    }

    is(sStart) {
      tx_reg := false.B // Start bit (0)
      when(baud_tick) {
        bit_counter := 0.U
        state := sData
      }
    }

    is(sData) {
      tx_reg := shift_reg(0)
      when(baud_tick) {
        shift_reg := shift_reg >> 1
        bit_counter := bit_counter + 1.U
        when(bit_counter === 7.U) {
          state := sStop
        }
      }
    }

    is(sStop) {
      tx_reg := true.B // Stop bit (1)
      when(baud_tick) {
        state := sIdle
      }
    }
  }
}

/**
 * UART 接收器
 */
class UartRx extends Module {
  val io = IO(new Bundle {
    val baud_div = Input(UInt(32.W))
    val enable = Input(Bool())
    val rx = Input(Bool())
    val dout = Output(UInt(8.W))
    val rd_en = Input(Bool())
    val rx_valid = Output(Bool())
  })

  // FIFO 深度
  val FIFO_DEPTH = 16

  // 接收 FIFO
  val fifo = Module(new Queue(UInt(8.W), FIFO_DEPTH))

  io.dout := fifo.io.deq.bits
  io.rx_valid := fifo.io.deq.valid
  fifo.io.deq.ready := io.rd_en

  // RX 输入同步和边沿检测
  val rx_sync = RegNext(RegNext(io.rx, true.B), true.B)
  val rx_prev = RegNext(rx_sync, true.B)
  val rx_falling = rx_prev && !rx_sync

  // 状态机
  val sIdle :: sStart :: sData :: sStop :: Nil = Enum(4)
  val state = RegInit(sIdle)

  val baud_counter = RegInit(0.U(32.W))
  val bit_counter = RegInit(0.U(4.W))
  val shift_reg = RegInit(0.U(8.W))

  // 波特率计数器（采样点在中间）
  val baud_tick = (baud_counter === 0.U)
  when(state =/= sIdle) {
    when(baud_counter === 0.U) {
      baud_counter := io.baud_div - 1.U
    }.otherwise {
      baud_counter := baud_counter - 1.U
    }
  }

  // 状态机
  fifo.io.enq.valid := false.B
  fifo.io.enq.bits := 0.U

  switch(state) {
    is(sIdle) {
      when(io.enable && rx_falling) {
        // 检测到起始位
        baud_counter := (io.baud_div >> 1) - 1.U // 半个波特周期后采样
        bit_counter := 0.U
        state := sStart
      }
    }

    is(sStart) {
      when(baud_tick) {
        // 采样起始位
        when(!rx_sync) {
          // 起始位有效
          state := sData
        }.otherwise {
          // 起始位无效，返回空闲
          state := sIdle
        }
      }
    }

    is(sData) {
      when(baud_tick) {
        // 采样数据位
        shift_reg := Cat(rx_sync, shift_reg(7, 1))
        bit_counter := bit_counter + 1.U
        when(bit_counter === 7.U) {
          state := sStop
        }
      }
    }

    is(sStop) {
      when(baud_tick) {
        // 采样停止位
        when(rx_sync) {
          // 停止位有效，数据接收完成
          fifo.io.enq.valid := true.B
          fifo.io.enq.bits := Cat(rx_sync, shift_reg(7, 1))
        }
        state := sIdle
      }
    }
  }
}
