package bus

import chisel3._
import chisel3.util._
import common.Config._

/**
 * Wishbone B4 总线互联模块
 * 实现地址解码和路由，连接处理器核心到各个外设和存储器
 *
 * 地址映射：
 * 0x0000_0000 - 0x0000_FFFF: BootROM (64KB)
 * 0x1000_0000 - 0x1000_0FFF: UART0
 * 0x1000_1000 - 0x1000_1FFF: SPI0
 * 0x1000_2000 - 0x1000_2FFF: I2C0
 * 0x1000_3000 - 0x1000_3FFF: GPIO
 * 0x1000_4000 - 0x1000_4FFF: Timer
 * 0x2000_0000 - 0x2001_FFFF: SRAM (128KB)
 */
class WishboneInterconnect extends Module {
  val io = IO(new Bundle {
    // 主设备接口（处理器核心）
    val imem = Flipped(new WishboneMaster)  // 指令总线
    val dmem = Flipped(new WishboneMaster)  // 数据总线

    // 从设备接口
    val bootrom = new WishboneMaster
    val uart = new WishboneMaster
    val spi = new WishboneMaster
    val i2c = new WishboneMaster
    val gpio = new WishboneMaster
    val timer = new WishboneMaster
    val sram = new WishboneMaster
  })

  // ============================================================================
  // 地址解码逻辑
  // ============================================================================

  // 指令总线地址解码
  val (imem_sel_bootrom, imem_sel_uart, imem_sel_gpio, imem_sel_spi,
       imem_sel_i2c, imem_sel_timer, imem_sel_sram) = decodeAddr(io.imem.adr)

  // 数据总线地址解码
  val (dmem_sel_bootrom, dmem_sel_uart, dmem_sel_gpio, dmem_sel_spi,
       dmem_sel_i2c, dmem_sel_timer, dmem_sel_sram) = decodeAddr(io.dmem.adr)

  // ============================================================================
  // 总线仲裁逻辑（简单优先级：数据总线优先）
  // ============================================================================

  // 确定哪个主设备在访问哪个从设备
  val imem_accessing = io.imem.cyc && io.imem.stb
  val dmem_accessing = io.dmem.cyc && io.dmem.stb

  // BootROM 仲裁
  val bootrom_grant_dmem = dmem_accessing && dmem_sel_bootrom
  val bootrom_grant_imem = imem_accessing && imem_sel_bootrom && !bootrom_grant_dmem

  // 其他外设只能被数据总线访问（通常情况）
  val uart_grant = dmem_accessing && dmem_sel_uart
  val spi_grant = dmem_accessing && dmem_sel_spi
  val i2c_grant = dmem_accessing && dmem_sel_i2c
  val gpio_grant = dmem_accessing && dmem_sel_gpio
  val timer_grant = dmem_accessing && dmem_sel_timer

  // SRAM 仲裁（数据总线优先）
  val sram_grant_dmem = dmem_accessing && dmem_sel_sram
  val sram_grant_imem = imem_accessing && imem_sel_sram && !sram_grant_dmem

  // ============================================================================
  // BootROM 连接
  // ============================================================================

  when(bootrom_grant_dmem) {
    io.bootrom.adr := io.dmem.adr
    io.bootrom.dat_w := io.dmem.dat_w
    io.bootrom.we := io.dmem.we
    io.bootrom.sel := io.dmem.sel
    io.bootrom.stb := io.dmem.stb
    io.bootrom.cyc := io.dmem.cyc
  }.elsewhen(bootrom_grant_imem) {
    io.bootrom.adr := io.imem.adr
    io.bootrom.dat_w := 0.U
    io.bootrom.we := false.B
    io.bootrom.sel := "b1111".U
    io.bootrom.stb := io.imem.stb
    io.bootrom.cyc := io.imem.cyc
  }.otherwise {
    io.bootrom.adr := 0.U
    io.bootrom.dat_w := 0.U
    io.bootrom.we := false.B
    io.bootrom.sel := 0.U
    io.bootrom.stb := false.B
    io.bootrom.cyc := false.B
  }

  // ============================================================================
  // UART 连接
  // ============================================================================

  when(uart_grant) {
    io.uart.adr := io.dmem.adr
    io.uart.dat_w := io.dmem.dat_w
    io.uart.we := io.dmem.we
    io.uart.sel := io.dmem.sel
    io.uart.stb := io.dmem.stb
    io.uart.cyc := io.dmem.cyc
  }.otherwise {
    io.uart.adr := 0.U
    io.uart.dat_w := 0.U
    io.uart.we := false.B
    io.uart.sel := 0.U
    io.uart.stb := false.B
    io.uart.cyc := false.B
  }

  // ============================================================================
  // SPI 连接
  // ============================================================================

  when(spi_grant) {
    io.spi.adr := io.dmem.adr
    io.spi.dat_w := io.dmem.dat_w
    io.spi.we := io.dmem.we
    io.spi.sel := io.dmem.sel
    io.spi.stb := io.dmem.stb
    io.spi.cyc := io.dmem.cyc
  }.otherwise {
    io.spi.adr := 0.U
    io.spi.dat_w := 0.U
    io.spi.we := false.B
    io.spi.sel := 0.U
    io.spi.stb := false.B
    io.spi.cyc := false.B
  }

  // ============================================================================
  // I2C 连接
  // ============================================================================

  when(i2c_grant) {
    io.i2c.adr := io.dmem.adr
    io.i2c.dat_w := io.dmem.dat_w
    io.i2c.we := io.dmem.we
    io.i2c.sel := io.dmem.sel
    io.i2c.stb := io.dmem.stb
    io.i2c.cyc := io.dmem.cyc
  }.otherwise {
    io.i2c.adr := 0.U
    io.i2c.dat_w := 0.U
    io.i2c.we := false.B
    io.i2c.sel := 0.U
    io.i2c.stb := false.B
    io.i2c.cyc := false.B
  }

  // ============================================================================
  // GPIO 连接
  // ============================================================================

  when(gpio_grant) {
    io.gpio.adr := io.dmem.adr
    io.gpio.dat_w := io.dmem.dat_w
    io.gpio.we := io.dmem.we
    io.gpio.sel := io.dmem.sel
    io.gpio.stb := io.dmem.stb
    io.gpio.cyc := io.dmem.cyc
  }.otherwise {
    io.gpio.adr := 0.U
    io.gpio.dat_w := 0.U
    io.gpio.we := false.B
    io.gpio.sel := 0.U
    io.gpio.stb := false.B
    io.gpio.cyc := false.B
  }

  // ============================================================================
  // Timer 连接
  // ============================================================================

  when(timer_grant) {
    io.timer.adr := io.dmem.adr
    io.timer.dat_w := io.dmem.dat_w
    io.timer.we := io.dmem.we
    io.timer.sel := io.dmem.sel
    io.timer.stb := io.dmem.stb
    io.timer.cyc := io.dmem.cyc
  }.otherwise {
    io.timer.adr := 0.U
    io.timer.dat_w := 0.U
    io.timer.we := false.B
    io.timer.sel := 0.U
    io.timer.stb := false.B
    io.timer.cyc := false.B
  }

  // ============================================================================
  // SRAM 连接
  // ============================================================================

  when(sram_grant_dmem) {
    io.sram.adr := io.dmem.adr
    io.sram.dat_w := io.dmem.dat_w
    io.sram.we := io.dmem.we
    io.sram.sel := io.dmem.sel
    io.sram.stb := io.dmem.stb
    io.sram.cyc := io.dmem.cyc
  }.elsewhen(sram_grant_imem) {
    io.sram.adr := io.imem.adr
    io.sram.dat_w := 0.U
    io.sram.we := false.B
    io.sram.sel := "b1111".U
    io.sram.stb := io.imem.stb
    io.sram.cyc := io.imem.cyc
  }.otherwise {
    io.sram.adr := 0.U
    io.sram.dat_w := 0.U
    io.sram.we := false.B
    io.sram.sel := 0.U
    io.sram.stb := false.B
    io.sram.cyc := false.B
  }

  // ============================================================================
  // 响应信号路由 - 数据总线
  // ============================================================================

  io.dmem.dat_r := 0.U
  io.dmem.ack := false.B
  io.dmem.err := false.B
  io.dmem.rty := false.B

  when(dmem_sel_bootrom) {
    io.dmem.dat_r := io.bootrom.dat_r
    io.dmem.ack := io.bootrom.ack
    io.dmem.err := io.bootrom.err
    io.dmem.rty := io.bootrom.rty
  }.elsewhen(dmem_sel_uart) {
    io.dmem.dat_r := io.uart.dat_r
    io.dmem.ack := io.uart.ack
    io.dmem.err := io.uart.err
    io.dmem.rty := io.uart.rty
  }.elsewhen(dmem_sel_spi) {
    io.dmem.dat_r := io.spi.dat_r
    io.dmem.ack := io.spi.ack
    io.dmem.err := io.spi.err
    io.dmem.rty := io.spi.rty
  }.elsewhen(dmem_sel_i2c) {
    io.dmem.dat_r := io.i2c.dat_r
    io.dmem.ack := io.i2c.ack
    io.dmem.err := io.i2c.err
    io.dmem.rty := io.i2c.rty
  }.elsewhen(dmem_sel_gpio) {
    io.dmem.dat_r := io.gpio.dat_r
    io.dmem.ack := io.gpio.ack
    io.dmem.err := io.gpio.err
    io.dmem.rty := io.gpio.rty
  }.elsewhen(dmem_sel_timer) {
    io.dmem.dat_r := io.timer.dat_r
    io.dmem.ack := io.timer.ack
    io.dmem.err := io.timer.err
    io.dmem.rty := io.timer.rty
  }.elsewhen(dmem_sel_sram) {
    io.dmem.dat_r := io.sram.dat_r
    io.dmem.ack := io.sram.ack
    io.dmem.err := io.sram.err
    io.dmem.rty := io.sram.rty
  }.otherwise {
    // 无效地址，返回错误
    io.dmem.err := dmem_accessing
  }

  // ============================================================================
  // 响应信号路由 - 指令总线
  // ============================================================================

  io.imem.dat_r := 0.U
  io.imem.ack := false.B
  io.imem.err := false.B
  io.imem.rty := false.B

  when(imem_sel_bootrom && !bootrom_grant_dmem) {
    io.imem.dat_r := io.bootrom.dat_r
    io.imem.ack := io.bootrom.ack
    io.imem.err := io.bootrom.err
    io.imem.rty := io.bootrom.rty
  }.elsewhen(imem_sel_sram && !sram_grant_dmem) {
    io.imem.dat_r := io.sram.dat_r
    io.imem.ack := io.sram.ack
    io.imem.err := io.sram.err
    io.imem.rty := io.sram.rty
  }.elsewhen(imem_accessing && (bootrom_grant_dmem || sram_grant_dmem)) {
    // 总线冲突，需要重试
    io.imem.rty := true.B
  }.otherwise {
    // 无效地址或其他情况，返回错误
    when(imem_accessing && !imem_sel_bootrom && !imem_sel_sram) {
      io.imem.err := true.B
    }
  }
}
