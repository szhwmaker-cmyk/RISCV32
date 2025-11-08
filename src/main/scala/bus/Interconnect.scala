package bus

import chisel3._
import chisel3.util._
import common.Config._

/**
 * Wishbone 总线互联模块
 *
 * 支持单主多从配置：
 * - 1 个主设备（处理器核心）
 * - 多个从设备（SPI Flash, UART, GPIO, SPI, I2C, RAM）
 *
 * 采用地址译码自动路由到对应的从设备
 */
class WishboneInterconnect(numSlaves: Int = 6) extends Module {
  val io = IO(new Bundle {
    // 主设备接口
    val master = Flipped(new WishboneMaster)

    // 从设备接口数组
    val slaves = Vec(numSlaves, new WishboneMaster)
  })

  // 地址译码：根据地址选择从设备
  // 0: SPI Flash (0x1000_0000 - 0x1FFF_FFFF)
  // 1: UART      (0x2000_0000 - 0x2000_FFFF)
  // 2: GPIO      (0x2001_0000 - 0x2001_FFFF)
  // 3: SPI       (0x2002_0000 - 0x2002_FFFF)
  // 4: I2C       (0x2003_0000 - 0x2003_FFFF)
  // 5: RAM       (0x8000_0000 - 0x8FFF_FFFF)

  val slave_sel = WireDefault(0.U(numSlaves.W))

  when(io.master.adr_o >= SPI_FLASH_BASE.U && io.master.adr_o <= SPI_FLASH_END.U) {
    slave_sel := "b000001".U
  }.elsewhen(io.master.adr_o >= UART_BASE.U && io.master.adr_o <= UART_END.U) {
    slave_sel := "b000010".U
  }.elsewhen(io.master.adr_o >= GPIO_BASE.U && io.master.adr_o <= GPIO_END.U) {
    slave_sel := "b000100".U
  }.elsewhen(io.master.adr_o >= SPI_BASE.U && io.master.adr_o <= SPI_END.U) {
    slave_sel := "b001000".U
  }.elsewhen(io.master.adr_o >= I2C_BASE.U && io.master.adr_o <= I2C_END.U) {
    slave_sel := "b010000".U
  }.elsewhen(io.master.adr_o >= RAM_BASE.U && io.master.adr_o <= RAM_END.U) {
    slave_sel := "b100000".U
  }

  // 连接主设备到所有从设备
  for (i <- 0 until numSlaves) {
    io.slaves(i).adr_o := io.master.adr_o
    io.slaves(i).dat_o := io.master.dat_o
    io.slaves(i).we_o  := io.master.we_o
    io.slaves(i).sel_o := io.master.sel_o
    io.slaves(i).stb_o := io.master.stb_o && slave_sel(i)
    io.slaves(i).cyc_o := io.master.cyc_o && slave_sel(i)
  }

  // 从设备响应多路复用
  io.master.dat_i := MuxCase(0.U, (0 until numSlaves).map(i =>
    (slave_sel(i) === true.B) -> io.slaves(i).dat_i
  ))

  io.master.ack_i := MuxCase(false.B, (0 until numSlaves).map(i =>
    (slave_sel(i) === true.B) -> io.slaves(i).ack_i
  ))
}
