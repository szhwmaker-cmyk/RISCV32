package bus

import chisel3._
import chisel3.util._

/**
 * Address Mapping Configuration
 * 地址映射配置
 */
case class AddressMap(
  base: BigInt,     // Base address
  size: BigInt,     // Size of address space
  name: String      // Device name
) {
  def matches(addr: UInt): Bool = {
    val addrVal = addr.asUInt
    (addrVal >= base.U) && (addrVal < (base + size).U)
  }
}

/**
 * Wishbone 1-to-N Crossbar
 * 1个Master连接N个Slave的交叉开关
 *
 * 功能：
 * - 根据地址映射选择目标Slave
 * - 仲裁和路由总线事务
 * - 错误检测（访问未映射地址）
 */
class WishboneCrossbar1toN(
  addrMaps: Seq[AddressMap]
) extends Module {
  val numSlaves = addrMaps.length

  val io = IO(new Bundle {
    // Master port
    val master = Flipped(new WishboneMaster())

    // Slave ports
    val slaves = Vec(numSlaves, new WishboneMaster())
  })

  // Address decode - determine which slave to select
  val slave_sel = Wire(UInt(log2Ceil(numSlaves + 1).W))
  slave_sel := numSlaves.U  // Default: no slave selected (error)

  for (i <- 0 until numSlaves) {
    when(addrMaps(i).matches(io.master.adr_o)) {
      slave_sel := i.U
    }
  }

  // Connect master to selected slave
  for (i <- 0 until numSlaves) {
    when(slave_sel === i.U) {
      // Forward master signals to slave
      io.slaves(i).adr_o := io.master.adr_o
      io.slaves(i).dat_o := io.master.dat_o
      io.slaves(i).we_o := io.master.we_o
      io.slaves(i).sel_o := io.master.sel_o
      io.slaves(i).stb_o := io.master.stb_o
      io.slaves(i).cyc_o := io.master.cyc_o
      io.slaves(i).lock_o := io.master.lock_o
      io.slaves(i).cti_o := io.master.cti_o
      io.slaves(i).bte_o := io.master.bte_o

      // Forward slave responses to master
      io.master.dat_i := io.slaves(i).dat_i
      io.master.ack_i := io.slaves(i).ack_i
      io.master.err_i := io.slaves(i).err_i
      io.master.rty_i := io.slaves(i).rty_i
    }.otherwise {
      // Deassert signals to non-selected slaves
      io.slaves(i).adr_o := 0.U
      io.slaves(i).dat_o := 0.U
      io.slaves(i).we_o := false.B
      io.slaves(i).sel_o := 0.U
      io.slaves(i).stb_o := false.B
      io.slaves(i).cyc_o := false.B
      io.slaves(i).lock_o := false.B
      io.slaves(i).cti_o := WB_CTI.CLASSIC
      io.slaves(i).bte_o := WB_BTE.LINEAR
    }
  }

  // Handle error case (no slave selected)
  when(slave_sel === numSlaves.U) {
    io.master.dat_i := 0.U
    io.master.ack_i := false.B
    io.master.err_i := io.master.cyc_o && io.master.stb_o
    io.master.rty_i := false.B
  }

  // If no slave is selected, ensure default master inputs
  when(slave_sel >= numSlaves.U) {
    io.master.dat_i := 0.U
    io.master.ack_i := false.B
    io.master.err_i := io.master.cyc_o && io.master.stb_o
    io.master.rty_i := false.B
  }
}

/**
 * Standard Address Mappings for RV32E SoC
 * RV32E SoC标准地址映射
 */
object SoCAddressMaps {
  // Memory regions
  val BootROM = AddressMap(
    base = 0x00000000L,
    size = 0x00010000L,  // 64KB
    name = "Boot ROM"
  )

  val RAM = AddressMap(
    base = 0x20000000L,
    size = 0x00010000L,  // 64KB
    name = "RAM"
  )

  val SPIFlash = AddressMap(
    base = 0x80000000L,
    size = 0x10000000L,  // 256MB
    name = "SPI Flash"
  )

  // Peripheral regions
  val UART = AddressMap(
    base = 0x40000000L,
    size = 0x00001000L,  // 4KB
    name = "UART"
  )

  val GPIO = AddressMap(
    base = 0x40001000L,
    size = 0x00001000L,  // 4KB
    name = "GPIO"
  )

  val Timer = AddressMap(
    base = 0x40002000L,
    size = 0x00001000L,  // 4KB
    name = "Timer"
  )

  val SPI = AddressMap(
    base = 0x40003000L,
    size = 0x00001000L,  // 4KB
    name = "SPI Master"
  )

  val I2C = AddressMap(
    base = 0x40004000L,
    size = 0x00001000L,  // 4KB
    name = "I2C Master"
  )

  // Standard peripheral configuration
  def standardPeripherals: Seq[AddressMap] = Seq(
    BootROM,
    RAM,
    UART,
    GPIO,
    Timer
  )

  // Full configuration with all peripherals
  def fullConfiguration: Seq[AddressMap] = Seq(
    BootROM,
    RAM,
    SPIFlash,
    UART,
    GPIO,
    Timer,
    SPI,
    I2C
  )
}
