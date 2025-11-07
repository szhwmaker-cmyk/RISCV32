package peripherals

import chisel3._
import chisel3.util._
import common.Config._
import bus._

/**
 * SRAM 模块
 * 128KB 读写存储器
 *
 * 特性：
 * - 单周期读写
 * - 支持字节、半字、字访问
 * - Wishbone B4 从设备接口
 */
class SRAM extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)
  })

  // SRAM 存储器 (128KB = 32K words)
  val mem = SyncReadMem(SRAM_SIZE / 4, UInt(32.W))

  // 地址计算（字地址）
  val word_addr = io.wb.adr(SRAM_ADDR_WIDTH - 1, 2)

  // 读取操作
  val read_data = mem.read(word_addr, io.wb.cyc && io.wb.stb && !io.wb.we)

  // 写入操作
  when(io.wb.cyc && io.wb.stb && io.wb.we) {
    val write_mask = Wire(Vec(4, Bool()))
    write_mask(0) := io.wb.sel(0)
    write_mask(1) := io.wb.sel(1)
    write_mask(2) := io.wb.sel(2)
    write_mask(3) := io.wb.sel(3)

    val masked_data = Wire(Vec(4, UInt(8.W)))
    val old_data = mem.read(word_addr)

    for (i <- 0 until 4) {
      masked_data(i) := Mux(write_mask(i),
        io.wb.dat_w(8 * i + 7, 8 * i),
        old_data(8 * i + 7, 8 * i))
    }

    mem.write(word_addr, masked_data.asUInt)
  }

  // Wishbone 响应
  io.wb.dat_r := read_data
  io.wb.ack := RegNext(io.wb.cyc && io.wb.stb, false.B)
  io.wb.err := false.B
  io.wb.rty := false.B
}
