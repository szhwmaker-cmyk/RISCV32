package peripherals

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFileInline
import bus._
import common.Config._

/**
 * RAM 模块
 *
 * 64KB SRAM，基地址 0x8000_0000
 * 支持 Wishbone 接口访问
 */
class Ram extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)
  })

  // 64KB RAM = 16K words (32-bit)
  val mem = SyncReadMem(RAM_SIZE / 4, UInt(32.W))

  // 地址映射：去掉基地址，按字对齐
  val addr = (io.wb.adr_o - RAM_BASE.U) >> 2

  // 写操作
  when(io.wb.cyc_o && io.wb.stb_o && io.wb.we_o) {
    // 根据字节选择信号写入
    val write_data = Wire(UInt(32.W))
    val old_data = mem.read(addr)

    write_data := MuxCase(io.wb.dat_o, Seq(
      (io.wb.sel_o === "b0001".U) -> Cat(old_data(31, 8), io.wb.dat_o(7, 0)),
      (io.wb.sel_o === "b0010".U) -> Cat(old_data(31, 16), io.wb.dat_o(15, 8), old_data(7, 0)),
      (io.wb.sel_o === "b0100".U) -> Cat(old_data(31, 24), io.wb.dat_o(23, 16), old_data(15, 0)),
      (io.wb.sel_o === "b1000".U) -> Cat(io.wb.dat_o(31, 24), old_data(23, 0)),
      (io.wb.sel_o === "b0011".U) -> Cat(old_data(31, 16), io.wb.dat_o(15, 0)),
      (io.wb.sel_o === "b1100".U) -> Cat(io.wb.dat_o(31, 16), old_data(15, 0)),
      (io.wb.sel_o === "b1111".U) -> io.wb.dat_o
    ))

    mem.write(addr, write_data)
  }

  // 读操作
  io.wb.dat_i := mem.read(addr)

  // 应答信号（简化实现：单周期响应）
  io.wb.ack_i := RegNext(io.wb.cyc_o && io.wb.stb_o, false.B)
}
