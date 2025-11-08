package peripherals

import chisel3._
import chisel3.util._
import bus._
import common.Config._

/**
 * SPI Master 控制器
 *
 * 简化实现
 */
class SpiMaster extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)

    // SPI 物理接口
    val spi_clk  = Output(Bool())
    val spi_mosi = Output(Bool())
    val spi_miso = Input(Bool())
    val spi_cs   = Output(UInt(4.W))
  })

  // 寄存器
  val txdata_reg = RegInit(0.U(8.W))
  val rxdata_reg = RegInit(0.U(8.W))
  val ctrl_reg   = RegInit(0.U(8.W))
  val div_reg    = RegInit(4.U(16.W))
  val status_reg = RegInit(0.U(8.W))
  val ss_reg     = RegInit("b1111".U(4.W))

  // 地址译码
  val addr_offset = io.wb.adr_o - SPI_BASE.U

  // 写操作
  when(io.wb.cyc_o && io.wb.stb_o && io.wb.we_o) {
    switch(addr_offset) {
      is(SpiRegs.TXDATA.U) { txdata_reg := io.wb.dat_o(7, 0) }
      is(SpiRegs.CTRL.U)   { ctrl_reg   := io.wb.dat_o(7, 0) }
      is(SpiRegs.DIV.U)    { div_reg    := io.wb.dat_o(15, 0) }
      is(SpiRegs.SS.U)     { ss_reg     := io.wb.dat_o(3, 0) }
    }
  }

  // 读操作
  io.wb.dat_i := MuxLookup(addr_offset, 0.U)(Seq(
    SpiRegs.TXDATA.U -> txdata_reg,
    SpiRegs.RXDATA.U -> rxdata_reg,
    SpiRegs.CTRL.U   -> ctrl_reg,
    SpiRegs.DIV.U    -> div_reg,
    SpiRegs.STATUS.U -> status_reg,
    SpiRegs.SS.U     -> ss_reg
  ))

  // 简化：SPI 信号默认值
  io.spi_clk  := false.B
  io.spi_mosi := false.B
  io.spi_cs   := ss_reg

  // 应答信号
  io.wb.ack_i := RegNext(io.wb.cyc_o && io.wb.stb_o, false.B)
}
