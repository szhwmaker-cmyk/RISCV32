package peripherals

import chisel3._
import chisel3.util._
import bus._
import common.Config._

// ============================================================================
// RAM 模块 - 256KB SRAM
// ============================================================================

class Ram extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)
  })

  // RAM 大小 = 256KB = 64K words
  val RAM_WORDS = (RAM_ACTUAL / 4).toInt
  val ram = SyncReadMem(RAM_WORDS, UInt(32.W))

  // Wishbone 信号
  val wb_req = io.wb.stb_o && io.wb.cyc_o
  val addr = io.wb.adr_o(17, 2)  // 字地址 (64K words = 2^16)
  val write_en = wb_req && io.wb.we_o

  // 读操作
  val read_data = ram.read(addr, wb_req && !io.wb.we_o)

  // 写操作（支持字节掩码）
  when(write_en) {
    val write_data = Wire(UInt(32.W))
    val old_data = read_data

    // 根据字节选择掩码生成写数据
    val byte0 = Mux(io.wb.sel_o(0), io.wb.dat_o(7, 0),   old_data(7, 0))
    val byte1 = Mux(io.wb.sel_o(1), io.wb.dat_o(15, 8),  old_data(15, 8))
    val byte2 = Mux(io.wb.sel_o(2), io.wb.dat_o(23, 16), old_data(23, 16))
    val byte3 = Mux(io.wb.sel_o(3), io.wb.dat_o(31, 24), old_data(31, 24))

    write_data := Cat(byte3, byte2, byte1, byte0)
    ram.write(addr, write_data)
  }

  // Wishbone 应答
  val ack_reg = RegNext(wb_req, false.B)
  io.wb.ack_o := ack_reg
  io.wb.dat_o := read_data
}
