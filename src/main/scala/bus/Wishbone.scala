package bus

import chisel3._
import chisel3.util._
import common.Config._

/**
 * Wishbone B4 Pipeline 总线接口定义
 *
 * 符合 Wishbone B4 规范的标准接口
 * 支持流水线传输模式，提高总线利用率
 */
class WishboneMaster extends Bundle {
  // Master → Slave 信号
  val adr = Output(UInt(ADDR_WIDTH.W))      // 地址线
  val dat_w = Output(UInt(BUS_WIDTH.W))     // 写数据
  val we = Output(Bool())                    // 写使能 (1=写, 0=读)
  val sel = Output(UInt(SEL_WIDTH.W))       // 字节选择 (4 bits for 32-bit bus)
  val stb = Output(Bool())                   // Strobe 信号 (传输有效)
  val cyc = Output(Bool())                   // Cycle 信号 (总线周期有效)

  // Slave → Master 信号
  val dat_r = Input(UInt(BUS_WIDTH.W))      // 读数据
  val ack = Input(Bool())                    // 应答信号 (传输完成)
  val err = Input(Bool())                    // 错误信号
  val rty = Input(Bool())                    // 重试信号
}

class WishboneSlave extends Bundle {
  // Slave 视角的信号方向相反
  val adr = Input(UInt(ADDR_WIDTH.W))
  val dat_w = Input(UInt(BUS_WIDTH.W))
  val we = Input(Bool())
  val sel = Input(UInt(SEL_WIDTH.W))
  val stb = Input(Bool())
  val cyc = Input(Bool())

  val dat_r = Output(UInt(BUS_WIDTH.W))
  val ack = Output(Bool())
  val err = Output(Bool())
  val rty = Output(Bool())
}

/**
 * Wishbone 主从连接辅助函数
 */
object Wishbone {
  /**
   * 连接主设备和从设备
   */
  def connect(master: WishboneMaster, slave: WishboneSlave): Unit = {
    slave.adr := master.adr
    slave.dat_w := master.dat_w
    slave.we := master.we
    slave.sel := master.sel
    slave.stb := master.stb
    slave.cyc := master.cyc

    master.dat_r := slave.dat_r
    master.ack := slave.ack
    master.err := slave.err
    master.rty := slave.rty
  }

  /**
   * 创建空闲的从设备响应（未选中时的默认响应）
   */
  def idleSlaveResponse(): (UInt, Bool, Bool, Bool) = {
    (0.U(BUS_WIDTH.W), false.B, false.B, false.B)
  }
}

/**
 * Wishbone 互连模块 (Crossbar)
 *
 * 支持单主多从配置
 * 从设备：
 *   0: SPI Flash Controller
 *   1: UART
 *   2: GPIO
 *   3: SPI Master
 *   4: I2C Master
 *   5: RAM
 */
class WishboneInterconnect extends Module {
  val io = IO(new Bundle {
    // 主设备接口（处理器）
    val master = Flipped(new WishboneMaster)

    // 从设备接口
    val spiFlash = new WishboneMaster
    val uart = new WishboneMaster
    val gpio = new WishboneMaster
    val spi = new WishboneMaster
    val i2c = new WishboneMaster
    val ram = new WishboneMaster
  })

  // 地址译码
  val addr = io.master.adr
  val sel_flash = inRange(addr, SPI_FLASH_BASE, SPI_FLASH_SIZE)
  val sel_uart = inRange(addr, UART_BASE, UART_SIZE)
  val sel_gpio = inRange(addr, GPIO_BASE, GPIO_SIZE)
  val sel_spi = inRange(addr, SPI_BASE, SPI_SIZE)
  val sel_i2c = inRange(addr, I2C_BASE, I2C_SIZE)
  val sel_ram = inRange(addr, RAM_BASE, RAM_SPACE_SIZE)

  // 默认连接到所有从设备（地址和控制信号）
  val slaves = Seq(io.spiFlash, io.uart, io.gpio, io.spi, io.i2c, io.ram)
  slaves.foreach { slave =>
    slave.adr := io.master.adr
    slave.dat_w := io.master.dat_w
    slave.we := io.master.we
    slave.sel := io.master.sel
  }

  // Strobe 信号根据地址选择分配
  io.spiFlash.stb := io.master.stb && sel_flash
  io.uart.stb := io.master.stb && sel_uart
  io.gpio.stb := io.master.stb && sel_gpio
  io.spi.stb := io.master.stb && sel_spi
  io.i2c.stb := io.master.stb && sel_i2c
  io.ram.stb := io.master.stb && sel_ram

  // Cycle 信号广播到所有从设备
  slaves.foreach { slave =>
    slave.cyc := io.master.cyc
  }

  // 从设备响应多路复用
  io.master.dat_r := MuxCase(0.U, Seq(
    sel_flash -> io.spiFlash.dat_r,
    sel_uart -> io.uart.dat_r,
    sel_gpio -> io.gpio.dat_r,
    sel_spi -> io.spi.dat_r,
    sel_i2c -> io.i2c.dat_r,
    sel_ram -> io.ram.dat_r
  ))

  io.master.ack := MuxCase(false.B, Seq(
    sel_flash -> io.spiFlash.ack,
    sel_uart -> io.uart.ack,
    sel_gpio -> io.gpio.ack,
    sel_spi -> io.spi.ack,
    sel_i2c -> io.i2c.ack,
    sel_ram -> io.ram.ack
  ))

  // 错误和重试信号（暂未使用）
  io.master.err := false.B
  io.master.rty := false.B
}

/**
 * Wishbone 从设备适配器基类
 *
 * 简化从设备实现，自动处理总线握手
 */
abstract class WishbonePeripheral(addrWidth: Int = ADDR_WIDTH) extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)
  })

  // 从设备需要实现的接口
  def read(addr: UInt): UInt
  def write(addr: UInt, data: UInt, sel: UInt): Unit

  // 默认实现：单周期响应
  val doRead = io.wb.cyc && io.wb.stb && !io.wb.we
  val doWrite = io.wb.cyc && io.wb.stb && io.wb.we

  io.wb.ack := io.wb.cyc && io.wb.stb
  io.wb.dat_r := Mux(doRead, read(io.wb.adr), 0.U)
  io.wb.err := false.B
  io.wb.rty := false.B

  when(doWrite) {
    write(io.wb.adr, io.wb.dat_w, io.wb.sel)
  }
}
