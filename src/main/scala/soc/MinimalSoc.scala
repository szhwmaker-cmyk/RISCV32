package soc

import chisel3._
import chisel3.util._
import core._
import bus._
import peripherals._
import boot._
import common.Config._

/**
 * RV32E SoC 顶层模块
 *
 * 集成：
 * - RV32E 处理器核心
 * - Wishbone 总线互联
 * - SPI Flash 控制器
 * - UART 控制器
 * - GPIO 控制器
 * - SPI Master 控制器
 * - I2C Master 控制器
 * - 64KB RAM
 * - Boot 控制器
 */
class MinimalSoc extends Module {
  val io = IO(new Bundle {
    // UART 接口
    val uart_tx = Output(Bool())
    val uart_rx = Input(Bool())

    // GPIO 接口
    val gpio_in  = Input(UInt(16.W))
    val gpio_out = Output(UInt(16.W))
    val gpio_oe  = Output(UInt(16.W))

    // SPI Flash 接口
    val flash_clk  = Output(Bool())
    val flash_cs   = Output(Bool())
    val flash_mosi = Output(Bool())
    val flash_miso = Input(Bool())

    // SPI Master 接口
    val spi_clk  = Output(Bool())
    val spi_mosi = Output(Bool())
    val spi_miso = Input(Bool())
    val spi_cs   = Output(UInt(4.W))

    // I2C 接口
    val scl_o = Output(Bool())
    val scl_i = Input(Bool())
    val sda_o = Output(Bool())
    val sda_i = Input(Bool())
  })

  // ============================================================================
  // 模块实例化
  // ============================================================================

  val core = Module(new Core)
  val imem_adapter = Module(new WishboneMasterAdapter)
  val dmem_adapter = Module(new WishboneMasterAdapter)
  val interconnect_imem = Module(new WishboneInterconnect)
  val interconnect_dmem = Module(new WishboneInterconnect)

  val spi_flash = Module(new SpiFlash)
  val uart      = Module(new Uart)
  val gpio      = Module(new Gpio)
  val spi       = Module(new SpiMaster)
  val i2c       = Module(new I2cMaster)
  val ram       = Module(new Ram)

  val boot_ctrl = Module(new BootController)

  // ============================================================================
  // 处理器核心连接
  // ============================================================================

  // 指令存储器接口 → Wishbone
  imem_adapter.io.mem_addr  := core.io.imem_addr
  imem_adapter.io.mem_wdata := 0.U
  imem_adapter.io.mem_wen   := false.B
  imem_adapter.io.mem_ren   := true.B
  imem_adapter.io.mem_size  := MemSize.WORD
  core.io.imem_rdata        := imem_adapter.io.mem_rdata

  // 数据存储器接口 → Wishbone
  dmem_adapter.io.mem_addr  := core.io.dmem_addr
  dmem_adapter.io.mem_wdata := core.io.dmem_wdata
  dmem_adapter.io.mem_wen   := core.io.dmem_wen
  dmem_adapter.io.mem_ren   := core.io.dmem_ren
  dmem_adapter.io.mem_size  := core.io.dmem_size
  core.io.dmem_rdata        := dmem_adapter.io.mem_rdata

  // ============================================================================
  // Wishbone 总线互联
  // ============================================================================

  // 指令总线互联
  interconnect_imem.io.master <> imem_adapter.io.wb
  interconnect_imem.io.slaves(0) <> spi_flash.io.wb  // SPI Flash
  interconnect_imem.io.slaves(1) <> uart.io.wb       // UART
  interconnect_imem.io.slaves(2) <> gpio.io.wb       // GPIO
  interconnect_imem.io.slaves(3) <> spi.io.wb        // SPI
  interconnect_imem.io.slaves(4) <> i2c.io.wb        // I2C
  interconnect_imem.io.slaves(5) <> ram.io.wb        // RAM

  // 数据总线互联
  interconnect_dmem.io.master <> dmem_adapter.io.wb

  // 为数据总线创建独立的外设实例或共享（这里简化为共享）
  // 实际设计中需要处理总线仲裁
  interconnect_dmem.io.slaves(0) <> DontCare  // Flash (通常只从指令总线访问)
  interconnect_dmem.io.slaves(1) <> DontCare  // UART (简化)
  interconnect_dmem.io.slaves(2) <> DontCare  // GPIO (简化)
  interconnect_dmem.io.slaves(3) <> DontCare  // SPI (简化)
  interconnect_dmem.io.slaves(4) <> DontCare  // I2C (简化)
  interconnect_dmem.io.slaves(5).adr_o := dmem_adapter.io.wb.adr_o
  interconnect_dmem.io.slaves(5).dat_o := dmem_adapter.io.wb.dat_o
  interconnect_dmem.io.slaves(5).we_o  := dmem_adapter.io.wb.we_o
  interconnect_dmem.io.slaves(5).sel_o := dmem_adapter.io.wb.sel_o
  interconnect_dmem.io.slaves(5).stb_o := dmem_adapter.io.wb.stb_o &&
    (dmem_adapter.io.wb.adr_o >= RAM_BASE.U && dmem_adapter.io.wb.adr_o <= RAM_END.U)
  interconnect_dmem.io.slaves(5).cyc_o := dmem_adapter.io.wb.cyc_o

  // ============================================================================
  // 外设连接
  // ============================================================================

  // UART
  io.uart_tx := uart.io.tx
  uart.io.rx := io.uart_rx

  // GPIO
  gpio.io.gpio_in  := io.gpio_in
  io.gpio_out      := gpio.io.gpio_out
  io.gpio_oe       := gpio.io.gpio_oe

  // SPI Flash
  io.flash_clk  := spi_flash.io.spi_clk
  io.flash_cs   := spi_flash.io.spi_cs
  io.flash_mosi := spi_flash.io.spi_mosi
  spi_flash.io.spi_miso := io.flash_miso

  // SPI Master
  io.spi_clk  := spi.io.spi_clk
  io.spi_mosi := spi.io.spi_mosi
  io.spi_cs   := spi.io.spi_cs
  spi.io.spi_miso := io.spi_miso

  // I2C
  io.scl_o := i2c.io.scl_o
  io.sda_o := i2c.io.sda_o
  i2c.io.scl_i := io.scl_i
  i2c.io.sda_i := io.sda_i
}
