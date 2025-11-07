package soc

import chisel3._
import chisel3.util._
import common.Config._
import core._
import bus._
import peripherals._

/**
 * Wishbone SoC 顶层模块
 * 集成 RV32E 处理器核心和所有外设
 *
 * 系统组成：
 * - RV32E 5级流水线处理器核心
 * - 64KB BootROM (0x0000_0000)
 * - 128KB SRAM (0x2000_0000)
 * - UART 16550 (0x1000_0000)
 * - SPI Master (0x1000_1000)
 * - I2C Master (0x1000_2000)
 * - GPIO (0x1000_3000)
 * - Timer (0x1000_4000)
 */
class WishboneSoc extends Module {
  val io = IO(new Bundle {
    // UART 接口
    val uart_tx = Output(Bool())
    val uart_rx = Input(Bool())

    // GPIO 接口
    val gpio_in = Input(UInt(32.W))
    val gpio_out = Output(UInt(32.W))
    val gpio_oe = Output(UInt(32.W))

    // SPI 接口
    val spi_clk = Output(Bool())
    val spi_mosi = Output(Bool())
    val spi_miso = Input(Bool())
    val spi_cs = Output(UInt(4.W))

    // I2C 接口
    val scl_out = Output(Bool())
    val scl_in = Input(Bool())
    val sda_out = Output(Bool())
    val sda_in = Input(Bool())

    // 中断输出（用于调试）
    val timer_interrupt = Output(Bool())
  })

  // ==========================================================================
  // 模块实例化
  // ==========================================================================

  val core = Module(new RV32ECore)
  val interconnect = Module(new WishboneInterconnect)
  val bootrom = Module(new BootROM())
  val sram = Module(new SRAM)
  val uart = Module(new UART16550)
  val gpio = Module(new GPIO)
  val timer = Module(new Timer)
  val spi = Module(new SPIMaster)
  val i2c = Module(new I2CMaster)

  // ==========================================================================
  // 总线连接
  // ==========================================================================

  // 连接处理器核心到互联模块
  WishboneConnect(core.io.imem, interconnect.io.imem)
  WishboneConnect(core.io.dmem, interconnect.io.dmem)

  // 连接外设到互联模块
  WishboneConnect(interconnect.io.bootrom, bootrom.io.wb)
  WishboneConnect(interconnect.io.sram, sram.io.wb)
  WishboneConnect(interconnect.io.uart, uart.io.wb)
  WishboneConnect(interconnect.io.gpio, gpio.io.wb)
  WishboneConnect(interconnect.io.timer, timer.io.wb)
  WishboneConnect(interconnect.io.spi, spi.io.wb)
  WishboneConnect(interconnect.io.i2c, i2c.io.wb)

  // ==========================================================================
  // 外设 I/O 连接
  // ==========================================================================

  // UART
  io.uart_tx := uart.io.tx
  uart.io.rx := io.uart_rx

  // GPIO
  gpio.io.gpio_in := io.gpio_in
  io.gpio_out := gpio.io.gpio_out
  io.gpio_oe := gpio.io.gpio_oe

  // SPI
  io.spi_clk := spi.io.spi_clk
  io.spi_mosi := spi.io.spi_mosi
  spi.io.spi_miso := io.spi_miso
  io.spi_cs := spi.io.spi_cs

  // I2C
  io.scl_out := i2c.io.scl_out
  i2c.io.scl_in := io.scl_in
  io.sda_out := i2c.io.sda_out
  i2c.io.sda_in := io.sda_in

  // Timer 中断
  io.timer_interrupt := timer.io.interrupt

  // 中断连接到核心（简化：仅连接timer中断）
  core.io.interrupts := Cat(Fill(15, 0.U), timer.io.interrupt)
}

/**
 * 生成 Verilog 的主程序
 */
object WishboneSocMain extends App {
  println("=" * 80)
  println("Generating RV32E SoC Verilog...")
  println("=" * 80)

  val verilog = chisel3.emitVerilog(new WishboneSoc)

  // 确保输出目录存在
  val outputDir = new java.io.File("generated")
  if (!outputDir.exists()) {
    outputDir.mkdirs()
  }

  // 写入文件
  import java.io.PrintWriter
  val writer = new PrintWriter("generated/WishboneSoc.v")
  writer.write(verilog)
  writer.close()

  println("Verilog generated successfully!")
  println("Output: generated/WishboneSoc.v")
  println("=" * 80)
}
