package peripherals

import chisel3._
import chisel3.util._
import bus._
import common.Config._

/**
 * UART 控制器
 *
 * 寄存器映射：
 * 0x00: TXDATA - 发送数据寄存器
 * 0x04: RXDATA - 接收数据寄存器
 * 0x08: STATUS - 状态寄存器
 * 0x0C: BAUD   - 波特率分频值
 * 0x10: CTRL   - 控制寄存器
 */
class Uart extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)

    // UART 物理接口
    val tx = Output(Bool())
    val rx = Input(Bool())
  })

  // 寄存器
  val txdata_reg = RegInit(0.U(8.W))
  val rxdata_reg = RegInit(0.U(8.W))
  val status_reg = RegInit(0x02.U(8.W))  // tx_empty = 1
  val baud_reg   = RegInit(UART_DEFAULT_DIV.U(16.W))
  val ctrl_reg   = RegInit(0x03.U(8.W))  // tx_en = 1, rx_en = 1

  // 简化实现：TX 始终为高（空闲状态）
  io.tx := true.B

  // 地址译码
  val addr_offset = io.wb.adr_o - UART_BASE.U

  // 写操作
  when(io.wb.cyc_o && io.wb.stb_o && io.wb.we_o) {
    switch(addr_offset) {
      is(UartRegs.TXDATA.U) { txdata_reg := io.wb.dat_o(7, 0) }
      is(UartRegs.BAUD.U)   { baud_reg   := io.wb.dat_o(15, 0) }
      is(UartRegs.CTRL.U)   { ctrl_reg   := io.wb.dat_o(7, 0) }
    }
  }

  // 读操作
  io.wb.dat_i := MuxLookup(addr_offset, 0.U)(Seq(
    UartRegs.TXDATA.U -> txdata_reg,
    UartRegs.RXDATA.U -> rxdata_reg,
    UartRegs.STATUS.U -> status_reg,
    UartRegs.BAUD.U   -> baud_reg,
    UartRegs.CTRL.U   -> ctrl_reg
  ))

  // 应答信号
  io.wb.ack_i := RegNext(io.wb.cyc_o && io.wb.stb_o, false.B)
}
