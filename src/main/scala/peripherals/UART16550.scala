package peripherals

import chisel3._
import chisel3.util._
import common.Config._
import bus._

/**
 * UART 16550 兼容外设
 * 简化版本，支持基本的发送和接收功能
 *
 * 特性：
 * - 波特率可配置
 * - 发送FIFO (16字节)
 * - 接收FIFO (16字节)
 * - Wishbone B4 从设备接口
 */
class UART16550 extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)
    val tx = Output(Bool())
    val rx = Input(Bool())
    val interrupt = Output(Bool())
  })

  // ==========================================================================
  // 寄存器定义
  // ==========================================================================

  val divisor_latch = RegInit(UART_DEFAULT_DIV.U(16.W))
  val line_control = RegInit(3.U(8.W))  // 8N1
  val modem_control = RegInit(0.U(8.W))
  val line_status = RegInit("b01100000".U(8.W))  // THRE, TEMT set
  val interrupt_enable = RegInit(0.U(8.W))
  val interrupt_id = RegInit(1.U(8.W))  // No interrupt

  // FIFO
  val tx_fifo = Module(new Queue(UInt(8.W), 16))
  val rx_fifo = Module(new Queue(UInt(8.W), 16))

  // DLAB (Divisor Latch Access Bit)
  val dlab = line_control(7)

  // ==========================================================================
  // Wishbone 接口
  // ==========================================================================

  val reg_addr = io.wb.adr(4, 2)
  val write_en = io.wb.cyc && io.wb.stb && io.wb.we
  val read_en = io.wb.cyc && io.wb.stb && !io.wb.we

  // 写操作
  when(write_en) {
    switch(reg_addr) {
      is(0.U) {  // RBR/THR/DLL
        when(dlab) {
          divisor_latch := Cat(divisor_latch(15, 8), io.wb.dat_w(7, 0))
        }.otherwise {
          // 写入发送FIFO
          tx_fifo.io.enq.valid := true.B
          tx_fifo.io.enq.bits := io.wb.dat_w(7, 0)
        }
      }
      is(1.U) {  // IER/DLH
        when(dlab) {
          divisor_latch := Cat(io.wb.dat_w(7, 0), divisor_latch(7, 0))
        }.otherwise {
          interrupt_enable := io.wb.dat_w(7, 0)
        }
      }
      is(3.U) {  // LCR
        line_control := io.wb.dat_w(7, 0)
      }
      is(4.U) {  // MCR
        modem_control := io.wb.dat_w(7, 0)
      }
    }
  }.otherwise {
    tx_fifo.io.enq.valid := false.B
    tx_fifo.io.enq.bits := 0.U
  }

  // 读操作
  val read_data = WireDefault(0.U(32.W))

  switch(reg_addr) {
    is(0.U) {  // RBR/DLL
      when(dlab) {
        read_data := divisor_latch(7, 0)
      }.otherwise {
        read_data := rx_fifo.io.deq.bits
        rx_fifo.io.deq.ready := read_en
      }
    }
    is(1.U) {  // IER/DLH
      when(dlab) {
        read_data := divisor_latch(15, 8)
      }.otherwise {
        read_data := interrupt_enable
      }
    }
    is(2.U) {  // IIR
      read_data := interrupt_id
    }
    is(3.U) {  // LCR
      read_data := line_control
    }
    is(4.U) {  // MCR
      read_data := modem_control
    }
    is(5.U) {  // LSR
      read_data := line_status
    }
  }

  io.wb.dat_r := read_data
  io.wb.ack := io.wb.cyc && io.wb.stb
  io.wb.err := false.B
  io.wb.rty := false.B

  // ==========================================================================
  // 发送逻辑
  // ==========================================================================

  val tx_counter = RegInit(0.U(16.W))
  val tx_bit_counter = RegInit(0.U(4.W))
  val tx_data = RegInit(0.U(10.W))  // start + 8 data + stop
  val tx_busy = RegInit(false.B)

  tx_fifo.io.deq.ready := !tx_busy

  when(!tx_busy && tx_fifo.io.deq.valid) {
    // 开始新的发送
    tx_busy := true.B
    tx_bit_counter := 0.U
    tx_data := Cat(1.U(1.W), tx_fifo.io.deq.bits, 0.U(1.W))  // stop + data + start
    tx_counter := 0.U
  }.elsewhen(tx_busy) {
    when(tx_counter === divisor_latch) {
      tx_counter := 0.U
      tx_bit_counter := tx_bit_counter + 1.U
      tx_data := tx_data >> 1

      when(tx_bit_counter === 9.U) {
        tx_busy := false.B
      }
    }.otherwise {
      tx_counter := tx_counter + 1.U
    }
  }

  io.tx := Mux(tx_busy, tx_data(0), 1.U)

  // ==========================================================================
  // 接收逻辑（简化版，仅支持轮询）
  // ==========================================================================

  rx_fifo.io.enq.valid := false.B
  rx_fifo.io.enq.bits := 0.U

  // 简化：实际应该实现完整的UART接收状态机
  // 这里仅作为占位符

  // ==========================================================================
  // 状态寄存器更新
  // ==========================================================================

  // LSR[5]: THRE - Transmitter Holding Register Empty
  line_status := Cat(
    line_status(7, 6),
    !tx_fifo.io.deq.valid,  // THRE
    !tx_busy,                // TEMT
    line_status(3, 0)
  )

  // 中断逻辑（简化）
  io.interrupt := false.B
}
