package soc

import chisel3._
import chisel3.util._
import common.Config._
import bus._
import core._
import peripherals._

/**
 * RV32E SoC 顶层模块
 *
 * 集成：
 * - RV32E 处理器核心
 * - Wishbone 总线互连
 * - SPI Flash 控制器
 * - UART
 * - GPIO
 * - SPI Master
 * - I2C Master
 * - RAM
 */
class RV32ESoC extends Module {
  val io = IO(new Bundle {
    // SPI Flash 接口
    val spi_flash = new SpiFlashIO

    // UART 接口
    val uart = new UartIO

    // GPIO 接口
    val gpio = new GpioIO

    // SPI Master 接口
    val spi = new SpiMasterIO

    // I2C 接口
    val i2c = new I2cIO

    // 调试接口（可选）
    val debug = if (DEBUG_ENABLE) Some(new Bundle {
      val pc = Output(UInt(XLEN.W))
      val inst = Output(UInt(INST_WIDTH.W))
      val regs = Output(Vec(REG_NUM, UInt(XLEN.W)))
    }) else None
  })

  // ============================================================================
  // 模块实例化
  // ============================================================================

  val core = Module(new Core)
  val interconnect = Module(new WishboneInterconnect)
  val spi_flash_ctrl = Module(new SpiFlashCtrl)
  val uart = Module(new Uart)
  val gpio = Module(new Gpio)
  val spi_master = Module(new SpiMaster)
  val i2c = Module(new I2c)
  val ram = Module(new Ram)

  // ============================================================================
  // 处理器核心连接
  // ============================================================================

  // 连接指令和数据总线到互连
  interconnect.io.master <> core.io.imem
  // 注意：这里简化处理，实际应该有独立的指令和数据总线
  // 但为了简化，我们使用单一互连，core 需要仲裁访问

  // 由于 core 有两个总线接口（imem 和 dmem），我们需要一个仲裁器
  // 或者使用两个互连。这里简化为串行访问。
  // 实际实现中应该使用更复杂的互连结构。

  // 简化方案：使用一个多路复用器选择 imem 或 dmem
  val use_dmem = core.io.dmem.cyc
  val selected_master = Wire(new WishboneMaster)

  selected_master.adr := Mux(use_dmem, core.io.dmem.adr, core.io.imem.adr)
  selected_master.dat_w := Mux(use_dmem, core.io.dmem.dat_w, core.io.imem.dat_w)
  selected_master.we := Mux(use_dmem, core.io.dmem.we, core.io.imem.we)
  selected_master.sel := Mux(use_dmem, core.io.dmem.sel, core.io.imem.sel)
  selected_master.stb := Mux(use_dmem, core.io.dmem.stb, core.io.imem.stb)
  selected_master.cyc := core.io.imem.cyc || core.io.dmem.cyc

  core.io.imem.dat_r := selected_master.dat_r
  core.io.imem.ack := selected_master.ack && !use_dmem
  core.io.imem.err := selected_master.err
  core.io.imem.rty := selected_master.rty

  core.io.dmem.dat_r := selected_master.dat_r
  core.io.dmem.ack := selected_master.ack && use_dmem
  core.io.dmem.err := selected_master.err
  core.io.dmem.rty := selected_master.rty

  interconnect.io.master <> selected_master

  // ============================================================================
  // 外设连接
  // ============================================================================

  // SPI Flash
  spi_flash_ctrl.io.wb <> interconnect.io.spiFlash
  io.spi_flash <> spi_flash_ctrl.io.spi

  // UART
  uart.io.wb <> interconnect.io.uart
  io.uart <> uart.io.uart

  // GPIO
  gpio.io.wb <> interconnect.io.gpio
  io.gpio <> gpio.io.gpio

  // SPI Master
  spi_master.io.wb <> interconnect.io.spi
  io.spi <> spi_master.io.spi

  // I2C
  i2c.io.wb <> interconnect.io.i2c
  io.i2c <> i2c.io.i2c

  // RAM
  ram.io.wb <> interconnect.io.ram

  // ============================================================================
  // 调试接口
  // ============================================================================

  if (DEBUG_ENABLE) {
    io.debug.get <> core.io.debug.get
  }
}

/**
 * 简化的 SoC 用于快速测试
 *
 * 只包含核心、RAM 和 UART
 */
class MinimalSoC extends Module {
  val io = IO(new Bundle {
    val uart = new UartIO

    val debug = if (DEBUG_ENABLE) Some(new Bundle {
      val pc = Output(UInt(XLEN.W))
      val inst = Output(UInt(INST_WIDTH.W))
    }) else None
  })

  val core = Module(new Core)
  val ram = Module(new Ram)
  val uart = Module(new Uart)

  // 简化的地址译码
  val addr = core.io.dmem.adr
  val sel_ram = addr >= RAM_BASE.U && addr < (RAM_BASE + RAM_SIZE).U
  val sel_uart = addr >= UART_BASE.U && addr < (UART_BASE + UART_SIZE).U

  // 指令存储器（使用 RAM）
  ram.io.wb.adr := Mux(core.io.dmem.cyc, core.io.dmem.adr, core.io.imem.adr)
  ram.io.wb.dat_w := core.io.dmem.dat_w
  ram.io.wb.we := core.io.dmem.we && sel_ram
  ram.io.wb.sel := core.io.dmem.sel
  ram.io.wb.stb := Mux(core.io.dmem.cyc && sel_ram, core.io.dmem.stb, core.io.imem.stb)
  ram.io.wb.cyc := core.io.dmem.cyc || core.io.imem.cyc

  // UART 连接
  uart.io.wb.adr := core.io.dmem.adr
  uart.io.wb.dat_w := core.io.dmem.dat_w
  uart.io.wb.we := core.io.dmem.we
  uart.io.wb.sel := core.io.dmem.sel
  uart.io.wb.stb := core.io.dmem.stb && sel_uart
  uart.io.wb.cyc := core.io.dmem.cyc && sel_uart

  // 响应多路复用
  val wb_dat_r = Mux(sel_uart, uart.io.wb.dat_r, ram.io.wb.dat_r)
  val wb_ack = Mux(sel_uart, uart.io.wb.ack, ram.io.wb.ack)

  core.io.imem.dat_r := ram.io.wb.dat_r
  core.io.imem.ack := ram.io.wb.ack && !core.io.dmem.cyc
  core.io.imem.err := false.B
  core.io.imem.rty := false.B

  core.io.dmem.dat_r := wb_dat_r
  core.io.dmem.ack := wb_ack
  core.io.dmem.err := false.B
  core.io.dmem.rty := false.B

  io.uart <> uart.io.uart

  if (DEBUG_ENABLE) {
    io.debug.get.pc := core.io.debug.get.pc
    io.debug.get.inst := core.io.debug.get.inst
  }
}
