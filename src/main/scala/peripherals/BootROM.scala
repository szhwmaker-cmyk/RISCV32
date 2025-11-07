package peripherals

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFile
import common.Config._
import bus._

/**
 * BootROM 模块
 * 64KB 只读存储器，用于存储启动代码和RT-Thread镜像
 *
 * 特性：
 * - 单周期读取
 * - Wishbone B4 从设备接口
 * - 可从文件加载初始内容
 */
class BootROM(initFile: String = "") extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)
  })

  // ROM 存储器 (64KB = 16K words)
  val rom = Mem(BOOTROM_SIZE / 4, UInt(32.W))

  // 如果提供了初始化文件，则加载
  if (initFile.nonEmpty) {
    loadMemoryFromFile(rom, initFile)
  } else {
    // 默认启动代码：跳转到SRAM基地址
    // 这是一个简单的跳转指令，将PC设置到SRAM起始地址
    rom(0) := "h20000137".U  // lui x2, 0x20000
    rom(1) := "h00010113".U  // addi x2, x2, 0
    rom(2) := "h00010067".U  // jalr x0, 0(x2)
  }

  // 地址计算（字地址）
  val word_addr = io.wb.adr(BOOTROM_ADDR_WIDTH - 1, 2)

  // 读取数据
  val read_data = rom.read(word_addr)

  // Wishbone 响应
  io.wb.dat_r := read_data
  io.wb.ack := io.wb.cyc && io.wb.stb
  io.wb.err := false.B
  io.wb.rty := false.B
}
