package bus

import chisel3._
import chisel3.util._
import common.Config._

// ============================================================================
// Wishbone Interconnect (Crossbar)
// 1 Master - 6 Slaves 互联
// ============================================================================

class WishboneInterconnect extends Module {
  val io = IO(new Bundle {
    // Master 接口 (来自CPU)
    val master = Flipped(new WishboneMaster)

    // Slave 接口
    val flash = new WishboneMaster    // SPI Flash
    val uart  = new WishboneMaster    // UART
    val gpio  = new WishboneMaster    // GPIO
    val spi   = new WishboneMaster    // SPI Master
    val i2c   = new WishboneMaster    // I2C Master
    val ram   = new WishboneMaster    // RAM
  })

  // 地址译码
  val addr = io.master.adr_o

  val sel_flash = (addr >= FLASH_BASE.U && addr < (FLASH_BASE + FLASH_SIZE).U)
  val sel_uart  = (addr >= UART_BASE.U && addr < (UART_BASE + UART_SIZE).U)
  val sel_gpio  = (addr >= GPIO_BASE.U && addr < (GPIO_BASE + GPIO_SIZE).U)
  val sel_spi   = (addr >= SPI_BASE.U && addr < (SPI_BASE + SPI_SIZE).U)
  val sel_i2c   = (addr >= I2C_BASE.U && addr < (I2C_BASE + I2C_SIZE).U)
  val sel_ram   = (addr >= RAM_BASE.U && addr < (RAM_BASE + RAM_SIZE).U)

  // 默认所有从设备不选中
  val slaves = Seq(io.flash, io.uart, io.gpio, io.spi, io.i2c, io.ram)
  slaves.foreach { slave =>
    slave.adr_o := io.master.adr_o
    slave.dat_o := io.master.dat_o
    slave.we_o  := io.master.we_o
    slave.sel_o := io.master.sel_o
    slave.stb_o := false.B
    slave.cyc_o := false.B
  }

  // 根据地址选择从设备
  when(sel_flash) {
    io.flash.stb_o := io.master.stb_o
    io.flash.cyc_o := io.master.cyc_o
    io.master.dat_i := io.flash.dat_i
    io.master.ack_i := io.flash.ack_i
  }.elsewhen(sel_uart) {
    io.uart.stb_o := io.master.stb_o
    io.uart.cyc_o := io.master.cyc_o
    io.master.dat_i := io.uart.dat_i
    io.master.ack_i := io.uart.ack_i
  }.elsewhen(sel_gpio) {
    io.gpio.stb_o := io.master.stb_o
    io.gpio.cyc_o := io.master.cyc_o
    io.master.dat_i := io.gpio.dat_i
    io.master.ack_i := io.gpio.ack_i
  }.elsewhen(sel_spi) {
    io.spi.stb_o := io.master.stb_o
    io.spi.cyc_o := io.master.cyc_o
    io.master.dat_i := io.spi.dat_i
    io.master.ack_i := io.spi.ack_i
  }.elsewhen(sel_i2c) {
    io.i2c.stb_o := io.master.stb_o
    io.i2c.cyc_o := io.master.cyc_o
    io.master.dat_i := io.i2c.dat_i
    io.master.ack_i := io.i2c.ack_i
  }.elsewhen(sel_ram) {
    io.ram.stb_o := io.master.stb_o
    io.ram.cyc_o := io.master.cyc_o
    io.master.dat_i := io.ram.dat_i
    io.master.ack_i := io.ram.ack_i
  }.otherwise {
    // 无效地址，返回0并立即应答
    io.master.dat_i := 0.U
    io.master.ack_i := io.master.stb_o && io.master.cyc_o
  }
}
