package peripherals

import chisel3._
import chisel3.util._
import common.Config._
import bus._

/**
 * SPI Master 简化版本
 * 支持基本的SPI主模式传输
 */
class SPIMaster extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)
    val spi_clk = Output(Bool())
    val spi_mosi = Output(Bool())
    val spi_miso = Input(Bool())
    val spi_cs = Output(UInt(4.W))
  })

  // 寄存器
  val txdata = RegInit(0.U(32.W))
  val rxdata = RegInit(0.U(32.W))
  val ctrl = RegInit(0.U(32.W))
  val divisor = RegInit(10.U(16.W))
  val status = RegInit(0.U(32.W))
  val chip_select = RegInit("b1111".U(4.W))

  // Wishbone 接口（简化）
  io.wb.dat_r := 0.U
  io.wb.ack := io.wb.cyc && io.wb.stb
  io.wb.err := false.B
  io.wb.rty := false.B

  // SPI 信号（简化）
  io.spi_clk := false.B
  io.spi_mosi := false.B
  io.spi_cs := chip_select
}
