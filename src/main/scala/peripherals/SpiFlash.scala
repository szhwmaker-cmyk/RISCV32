package peripherals

import chisel3._
import chisel3.util._
import bus._
import common.Config._

/**
 * SPI Flash 控制器
 *
 * 简化实现：支持基本的 SPI Flash 读取
 * 实际应用中需要实现完整的 SPI 协议
 */
class SpiFlash extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)

    // SPI Flash 物理接口
    val spi_clk  = Output(Bool())
    val spi_cs   = Output(Bool())
    val spi_mosi = Output(Bool())
    val spi_miso = Input(Bool())
  })

  // 简化实现：使用 ROM 模拟 Flash
  val flash_mem = SyncReadMem(256, UInt(32.W))

  // 地址映射
  val addr = (io.wb.adr_o - SPI_FLASH_BASE.U) >> 2

  // 读操作
  io.wb.dat_i := flash_mem.read(addr)

  // 简化：SPI 信号默认值
  io.spi_clk  := false.B
  io.spi_cs   := true.B
  io.spi_mosi := false.B

  // 应答信号（简化：2周期延迟模拟 SPI 读取）
  val ack_cnt = RegInit(0.U(2.W))
  when(io.wb.cyc_o && io.wb.stb_o) {
    ack_cnt := Mux(ack_cnt === 1.U, 0.U, ack_cnt + 1.U)
  }.otherwise {
    ack_cnt := 0.U
  }
  io.wb.ack_i := ack_cnt === 1.U
}
