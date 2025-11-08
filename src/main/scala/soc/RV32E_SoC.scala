package soc

import chisel3._
import chisel3.util._
import core._
import bus._
import peripherals._

// ============================================================================
// RV32E SoC 顶层模块
// ============================================================================

class RV32E_SoC extends Module {
  val io = IO(new Bundle {
    // UART 引脚
    val uart_tx = Output(Bool())
    val uart_rx = Input(Bool())

    // GPIO 引脚
    val gpio_in  = Input(UInt(16.W))
    val gpio_out = Output(UInt(16.W))
    val gpio_oe  = Output(UInt(16.W))

    // SPI Master 引脚
    val spi_sclk = Output(Bool())
    val spi_mosi = Output(Bool())
    val spi_miso = Input(Bool())
    val spi_cs_n = Output(UInt(4.W))

    // I2C Master 引脚
    val i2c_scl_o = Output(Bool())
    val i2c_scl_i = Input(Bool())
    val i2c_sda_o = Output(Bool())
    val i2c_sda_i = Input(Bool())

    // SPI Flash 引脚
    val flash_sclk = Output(Bool())
    val flash_mosi = Output(Bool())
    val flash_miso = Input(Bool())
    val flash_cs_n = Output(Bool())
  })

  // ========== 模块实例化 ==========
  val core = Module(new Core)
  val imem_adapter = Module(new WishboneMasterAdapter)
  val dmem_adapter = Module(new WishboneMasterAdapter)
  val interconnect = Module(new WishboneInterconnect)

  val uart  = Module(new Uart)
  val gpio  = Module(new Gpio)
  val spi   = Module(new SpiMaster)
  val i2c   = Module(new I2cMaster)
  val flash = Module(new SpiFlash)
  val ram   = Module(new Ram)

  // ========== 处理器连接 ==========
  // 指令内存通过Wishbone连接
  imem_adapter.io.mem <> core.io.imem

  // 数据内存通过Wishbone连接
  dmem_adapter.io.mem <> core.io.dmem

  // ========== Wishbone互联连接 ==========
  // 将指令和数据请求合并（简化设计，单主设备）
  // 实际设计中可能需要仲裁器
  // 这里采用优先级方式：数据访问优先
  val master_wb = Wire(new WishboneMaster)

  when(dmem_adapter.io.wb.cyc_o) {
    // 数据访问优先
    master_wb.adr_o := dmem_adapter.io.wb.adr_o
    master_wb.dat_o := dmem_adapter.io.wb.dat_o
    master_wb.we_o  := dmem_adapter.io.wb.we_o
    master_wb.sel_o := dmem_adapter.io.wb.sel_o
    master_wb.stb_o := dmem_adapter.io.wb.stb_o
    master_wb.cyc_o := dmem_adapter.io.wb.cyc_o

    dmem_adapter.io.wb.dat_i := master_wb.dat_i
    dmem_adapter.io.wb.ack_i := master_wb.ack_i

    imem_adapter.io.wb.dat_i := 0.U
    imem_adapter.io.wb.ack_i := false.B
  }.otherwise {
    // 指令访问
    master_wb.adr_o := imem_adapter.io.wb.adr_o
    master_wb.dat_o := imem_adapter.io.wb.dat_o
    master_wb.we_o  := imem_adapter.io.wb.we_o
    master_wb.sel_o := imem_adapter.io.wb.sel_o
    master_wb.stb_o := imem_adapter.io.wb.stb_o
    master_wb.cyc_o := imem_adapter.io.wb.cyc_o

    imem_adapter.io.wb.dat_i := master_wb.dat_i
    imem_adapter.io.wb.ack_i := master_wb.ack_i

    dmem_adapter.io.wb.dat_i := 0.U
    dmem_adapter.io.wb.ack_i := false.B
  }

  interconnect.io.master <> master_wb

  // ========== 外设连接 ==========
  // Flash
  flash.io.wb <> interconnect.io.flash
  io.flash_sclk := flash.io.sclk
  io.flash_mosi := flash.io.mosi
  flash.io.miso := io.flash_miso
  io.flash_cs_n := flash.io.cs_n

  // UART
  uart.io.wb <> interconnect.io.uart
  io.uart_tx := uart.io.tx
  uart.io.rx := io.uart_rx

  // GPIO
  gpio.io.wb <> interconnect.io.gpio
  gpio.io.gpio_in := io.gpio_in
  io.gpio_out := gpio.io.gpio_out
  io.gpio_oe  := gpio.io.gpio_oe

  // SPI Master
  spi.io.wb <> interconnect.io.spi
  io.spi_sclk := spi.io.sclk
  io.spi_mosi := spi.io.mosi
  spi.io.miso := io.spi_miso
  io.spi_cs_n := spi.io.cs_n

  // I2C Master
  i2c.io.wb <> interconnect.io.i2c
  io.i2c_scl_o := i2c.io.scl_o
  i2c.io.scl_i := io.i2c_scl_i
  io.i2c_sda_o := i2c.io.sda_o
  i2c.io.sda_i := io.i2c_sda_i

  // RAM
  ram.io.wb <> interconnect.io.ram
}

// ============================================================================
// Verilog 生成入口
// ============================================================================

object SoCMain extends App {
  println("Generating RV32E SoC Verilog...")
  emitVerilog(new RV32E_SoC, Array("--target-dir", "generated"))
  println("Verilog generated in generated/ directory")
}
