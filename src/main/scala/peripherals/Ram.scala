package peripherals

import chisel3._
import chisel3.util._
import common.Config._
import bus._

/**
 * RAM 模块
 *
 * 单端口 RAM，支持字节选择
 * 默认大小：64 KB
 */
class Ram(size: Int = RAM_SIZE) extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)
  })

  // 计算地址宽度
  val addrWidth = log2Ceil(size / 4) // 按字寻址

  // 使用 SyncReadMem 创建 RAM
  val mem = SyncReadMem(size / 4, UInt(32.W))

  // 地址（字地址）
  val addr = io.wb.adr(addrWidth + 1, 2)

  // 读取数据
  val read_data = mem.read(addr)

  // 写入数据（支持字节选择）
  when(io.wb.cyc && io.wb.stb && io.wb.we) {
    val write_data = Wire(UInt(32.W))
    val old_data = mem.read(addr)

    // 根据字节选择信号组装写入数据
    write_data := Cat(
      Mux(io.wb.sel(3), io.wb.dat_w(31, 24), old_data(31, 24)),
      Mux(io.wb.sel(2), io.wb.dat_w(23, 16), old_data(23, 16)),
      Mux(io.wb.sel(1), io.wb.dat_w(15, 8), old_data(15, 8)),
      Mux(io.wb.sel(0), io.wb.dat_w(7, 0), old_data(7, 0))
    )

    mem.write(addr, write_data)
  }

  // Wishbone 响应（单周期响应）
  io.wb.dat_r := read_data
  io.wb.ack := RegNext(io.wb.cyc && io.wb.stb, false.B)
  io.wb.err := false.B
  io.wb.rty := false.B
}

/**
 * 带初始化的 RAM 模块
 *
 * 支持从文件加载初始数据
 */
class InitRam(size: Int = RAM_SIZE, initFile: String = "") extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)
  })

  val addrWidth = log2Ceil(size / 4)

  // 创建 RAM
  val mem = if (initFile.nonEmpty) {
    SyncReadMem(size / 4, UInt(32.W), SyncReadMem.Inline)
  } else {
    SyncReadMem(size / 4, UInt(32.W))
  }

  // 如果提供了初始化文件，加载数据
  if (initFile.nonEmpty) {
    loadMemoryFromFile(mem, initFile)
  }

  val addr = io.wb.adr(addrWidth + 1, 2)
  val read_data = mem.read(addr)

  when(io.wb.cyc && io.wb.stb && io.wb.we) {
    val write_data = Wire(UInt(32.W))
    val old_data = mem.read(addr)

    write_data := Cat(
      Mux(io.wb.sel(3), io.wb.dat_w(31, 24), old_data(31, 24)),
      Mux(io.wb.sel(2), io.wb.dat_w(23, 16), old_data(23, 16)),
      Mux(io.wb.sel(1), io.wb.dat_w(15, 8), old_data(15, 8)),
      Mux(io.wb.sel(0), io.wb.dat_w(7, 0), old_data(7, 0))
    )

    mem.write(addr, write_data)
  }

  io.wb.dat_r := read_data
  io.wb.ack := RegNext(io.wb.cyc && io.wb.stb, false.B)
  io.wb.err := false.B
  io.wb.rty := false.B
}

/**
 * 双端口 RAM
 *
 * 提供两个独立的 Wishbone 接口
 */
class DualPortRam(size: Int = RAM_SIZE) extends Module {
  val io = IO(new Bundle {
    val port_a = Flipped(new WishboneMaster)
    val port_b = Flipped(new WishboneMaster)
  })

  val addrWidth = log2Ceil(size / 4)

  // 使用两个独立的内存实例或共享内存
  val mem = SyncReadMem(size / 4, UInt(32.W))

  // Port A
  val addr_a = io.port_a.adr(addrWidth + 1, 2)
  val read_data_a = mem.read(addr_a, io.port_a.cyc && io.port_a.stb && !io.port_a.we)

  when(io.port_a.cyc && io.port_a.stb && io.port_a.we) {
    mem.write(addr_a, io.port_a.dat_w, io.port_a.sel.asBools)
  }

  io.port_a.dat_r := read_data_a
  io.port_a.ack := RegNext(io.port_a.cyc && io.port_a.stb, false.B)
  io.port_a.err := false.B
  io.port_a.rty := false.B

  // Port B
  val addr_b = io.port_b.adr(addrWidth + 1, 2)
  val read_data_b = mem.read(addr_b, io.port_b.cyc && io.port_b.stb && !io.port_b.we)

  when(io.port_b.cyc && io.port_b.stb && io.port_b.we) {
    mem.write(addr_b, io.port_b.dat_w, io.port_b.sel.asBools)
  }

  io.port_b.dat_r := read_data_b
  io.port_b.ack := RegNext(io.port_b.cyc && io.port_b.stb, false.B)
  io.port_b.err := false.B
  io.port_b.rty := false.B
}
