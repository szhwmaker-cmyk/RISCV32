package rv32e.soc

import chisel3._
import chisel3.util._
import rv32e.Config
import rv32e.core._
import rv32e.bus._
import rv32e.peripherals._

/**
 * Minimal RV32E SoC
 *
 * Top-level integration of:
 * - RV32E processor core (5-stage pipeline)
 * - Wishbone interconnect
 * - Peripherals: SPI Flash, UART, GPIO, SPI Master, I2C Master
 * - RAM (64KB)
 *
 * Boot Flow:
 * 1. CPU starts at PC = 0x1000_0000 (SPI Flash base)
 * 2. Boot code in Flash copies program to RAM
 * 3. Jump to RAM and execute
 *
 * Alternatively (simplified for initial testing):
 * 1. CPU starts at PC = 0x8000_0000 (RAM base)
 * 2. Program pre-loaded in RAM
 * 3. Execute directly
 */

class MinimalSocIO extends Bundle {
  // UART pins
  val uart_tx = Output(Bool())
  val uart_rx = Input(Bool())

  // GPIO pins
  val gpio_in  = Input(UInt(Config.GPIO_WIDTH.W))
  val gpio_out = Output(UInt(Config.GPIO_WIDTH.W))
  val gpio_oe  = Output(UInt(Config.GPIO_WIDTH.W))

  // SPI Flash pins
  val flash_cs_n = Output(Bool())
  val flash_sck  = Output(Bool())
  val flash_mosi = Output(Bool())
  val flash_miso = Input(Bool())

  // SPI Master pins
  val spi_cs_n = Output(Bool())
  val spi_sck  = Output(Bool())
  val spi_mosi = Output(Bool())
  val spi_miso = Input(Bool())

  // I2C pins
  val i2c_scl_out = Output(Bool())
  val i2c_scl_in  = Input(Bool())
  val i2c_sda_out = Output(Bool())
  val i2c_sda_in  = Input(Bool())

  // Debug outputs
  val debug_pc   = Output(UInt(32.W))
  val debug_inst = Output(UInt(32.W))
}

class MinimalSoc extends Module {
  val io = IO(new MinimalSocIO)

  // ========== Core ==========
  val core = Module(new Core)

  // ========== Wishbone Interconnect ==========
  val interconnect = Module(new Interconnect)

  // ========== Peripherals ==========
  val spi_flash = Module(new SpiFlash)
  val uart = Module(new Uart)
  val gpio = Module(new Gpio)
  val spi_master = Module(new SpiMaster)
  val i2c_master = Module(new I2cMaster)
  val ram = Module(new Ram)

  // ========== Connect Core to Interconnect ==========
  interconnect.io.imem_master <> core.io.imem
  interconnect.io.dmem_master <> core.io.dmem

  // ========== Connect Interconnect to Peripherals ==========
  spi_flash.io.wb_mem <> interconnect.io.spi_flash
  uart.io.wb <> interconnect.io.uart
  gpio.io.wb <> interconnect.io.gpio
  spi_master.io.wb <> interconnect.io.spi
  i2c_master.io.wb <> interconnect.io.i2c
  ram.io.wb <> interconnect.io.ram

  // Flash controller registers (separate interface)
  spi_flash.io.wb_ctrl <> interconnect.io.flash_ctrl

  // ========== Connect Peripheral I/O Pins ==========

  // UART
  io.uart_tx := uart.io.tx
  uart.io.rx := io.uart_rx

  // GPIO
  io.gpio_out := gpio.io.gpio_out
  io.gpio_oe  := gpio.io.gpio_oe
  gpio.io.gpio_in := io.gpio_in

  // SPI Flash
  io.flash_cs_n := spi_flash.io.spi_cs_n
  io.flash_sck  := spi_flash.io.spi_sck
  io.flash_mosi := spi_flash.io.spi_mosi
  spi_flash.io.spi_miso := io.flash_miso

  // SPI Master
  io.spi_cs_n := spi_master.io.spi_cs_n
  io.spi_sck  := spi_master.io.spi_sck
  io.spi_mosi := spi_master.io.spi_mosi
  spi_master.io.spi_miso := io.spi_miso

  // I2C Master
  io.i2c_scl_out := i2c_master.io.scl_out
  io.i2c_sda_out := i2c_master.io.sda_out
  i2c_master.io.scl_in := io.i2c_scl_in
  i2c_master.io.sda_in := io.i2c_sda_in

  // ========== Debug Outputs ==========
  io.debug_pc := core.io.debug_pc
  io.debug_inst := core.io.debug_inst
}

object MinimalSoc extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.soc.MinimalSoc"))
}
