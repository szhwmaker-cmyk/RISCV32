package soc

import chisel3._
import chisel3.util._
import core._
import bus._
import peripherals._

/**
 * RV32E SoC Configuration
 * SoC配置参数
 */
case class SoCConfig(
  clockFreq: Int = 50000000,    // 50 MHz
  baudRate: Int = 115200,
  ramSize: Int = 16384,          // 16KB (16K words = 64KB)
  romSize: Int = 4096,           // 4KB (4K words = 16KB)
  uartFifoDepth: Int = 16,
  gpioWidth: Int = 16,
  numTimers: Int = 1
)

/**
 * RV32E SoC Top Module
 * RV32E SoC顶层模块
 *
 * 集成组件：
 * - RV32E 5级流水线处理器核心
 * - Wishbone B4总线互连
 * - Boot ROM
 * - RAM
 * - UART
 * - GPIO
 * - Timer
 *
 * 地址映射：
 * 0x00000000 - 0x00003FFF : Boot ROM (16KB)
 * 0x20000000 - 0x2000FFFF : RAM (64KB)
 * 0x40000000 - 0x40000FFF : UART (4KB)
 * 0x40001000 - 0x40001FFF : GPIO (4KB)
 * 0x40002000 - 0x40002FFF : Timer (4KB)
 */
class RV32ESoC(config: SoCConfig = SoCConfig()) extends Module {
  val io = IO(new Bundle {
    // UART pins
    val uart_tx = Output(Bool())
    val uart_rx = Input(Bool())

    // GPIO pins
    val gpio_in = Input(UInt(config.gpioWidth.W))
    val gpio_out = Output(UInt(config.gpioWidth.W))
    val gpio_oe = Output(UInt(config.gpioWidth.W))

    // Interrupts (for debugging)
    val uart_irq = Output(Bool())
    val gpio_irq = Output(Bool())
    val timer_irq = Output(Bool())

    // Debug signals
    val debug_pc = Output(UInt(32.W))
    val debug_inst = Output(UInt(32.W))
  })

  // ========== CPU Core ==========
  val cpu = Module(new RV32ECore())

  // ========== Memory Devices ==========

  // Boot ROM (Wishbone slave)
  val bootrom = Module(new WishboneMemorySlave(config.romSize))

  // RAM (Wishbone slave)
  val ram = Module(new WishboneMemorySlave(config.ramSize))

  // ========== Peripherals ==========

  // UART
  val uart = Module(new UART(
    clockFreq = config.clockFreq,
    baudRate = config.baudRate,
    fifoDepth = config.uartFifoDepth
  ))

  // GPIO
  val gpio = Module(new GPIO(width = config.gpioWidth))

  // Timer
  val timer = Module(new Timer())

  // ========== Wishbone Master Adapters ==========

  // Instruction bus adapter
  val imem_adapter = Module(new WishboneMasterAdapter())
  imem_adapter.io.mem_addr := cpu.io.imem_addr
  imem_adapter.io.mem_wdata := 0.U
  imem_adapter.io.mem_ren := true.B
  imem_adapter.io.mem_wen := false.B
  imem_adapter.io.mem_size := 2.U  // Word
  cpu.io.imem_data := imem_adapter.io.mem_rdata
  cpu.io.imem_valid := imem_adapter.io.mem_valid

  // Data bus adapter
  val dmem_adapter = Module(new WishboneMasterAdapter())
  dmem_adapter.io.mem_addr := cpu.io.dmem_addr
  dmem_adapter.io.mem_wdata := cpu.io.dmem_wdata
  dmem_adapter.io.mem_ren := cpu.io.dmem_ren
  dmem_adapter.io.mem_wen := cpu.io.dmem_wen
  dmem_adapter.io.mem_size := cpu.io.dmem_size
  cpu.io.dmem_rdata := dmem_adapter.io.mem_rdata

  // ========== Address Mapping ==========

  val addrMaps = Seq(
    AddressMap(base = 0x00000000L, size = 0x00004000L, name = "Boot ROM"),
    AddressMap(base = 0x20000000L, size = 0x00010000L, name = "RAM"),
    AddressMap(base = 0x40000000L, size = 0x00001000L, name = "UART"),
    AddressMap(base = 0x40001000L, size = 0x00001000L, name = "GPIO"),
    AddressMap(base = 0x40002000L, size = 0x00001000L, name = "Timer")
  )

  // ========== Instruction Bus Crossbar ==========

  val imem_crossbar = Module(new WishboneCrossbar1toN(addrMaps.take(2)))  // ROM and RAM only
  imem_crossbar.io.master <> imem_adapter.io.wb

  // Connect ROM and RAM to instruction crossbar
  bootrom.io.wb <> imem_crossbar.io.slaves(0)
  ram.io.wb <> imem_crossbar.io.slaves(1)

  // ========== Data Bus Crossbar ==========

  val dmem_crossbar = Module(new WishboneCrossbar1toN(addrMaps))
  dmem_crossbar.io.master <> dmem_adapter.io.wb

  // Connect slaves to data crossbar
  // Note: ROM and RAM are shared, need to handle multiple masters properly
  // For simplicity, we'll allow both instruction and data access to ROM/RAM
  // In a real design, you'd use a more sophisticated interconnect

  // Slave 0: Boot ROM (also connected to imem_crossbar, shared read-only)
  // For now, we'll connect it to dmem_crossbar as well
  // This is a simplification - proper multi-master support would require arbitration

  // Create separate ROM/RAM instances for data bus (simplification)
  val data_bootrom = Module(new WishboneMemorySlave(config.romSize))
  val data_ram = Module(new WishboneMemorySlave(config.ramSize))

  data_bootrom.io.wb <> dmem_crossbar.io.slaves(0)
  data_ram.io.wb <> dmem_crossbar.io.slaves(1)
  uart.io.wb <> dmem_crossbar.io.slaves(2)
  gpio.io.wb <> dmem_crossbar.io.slaves(3)
  timer.io.wb <> dmem_crossbar.io.slaves(4)

  // ========== Peripheral Connections ==========

  // UART
  io.uart_tx := uart.io.tx
  uart.io.rx := io.uart_rx
  io.uart_irq := uart.io.irq

  // GPIO
  gpio.io.gpio_in := io.gpio_in
  io.gpio_out := gpio.io.gpio_out
  io.gpio_oe := gpio.io.gpio_oe
  io.gpio_irq := gpio.io.irq

  // Timer
  io.timer_irq := timer.io.irq

  // ========== Debug Signals ==========

  io.debug_pc := cpu.io.debug_pc
  io.debug_inst := cpu.io.debug_inst
}

/**
 * Minimal SoC for Testing
 * 最小化SoC用于测试
 */
class MinimalSoC extends Module {
  val io = IO(new Bundle {
    val uart_tx = Output(Bool())
    val uart_rx = Input(Bool())
    val gpio = Output(UInt(16.W))
  })

  val soc = Module(new RV32ESoC(SoCConfig(
    ramSize = 1024,    // Smaller for faster simulation
    romSize = 256
  )))

  io.uart_tx := soc.io.uart_tx
  soc.io.uart_rx := io.uart_rx

  io.gpio := soc.io.gpio_out

  // Tie off unused inputs
  soc.io.gpio_in := 0.U
}
