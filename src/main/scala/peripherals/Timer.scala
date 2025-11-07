package peripherals

import chisel3._
import chisel3.util._
import common.Config._
import bus._

/**
 * 64位系统定时器
 * 兼容RISC-V的mtime和mtimecmp
 *
 * 寄存器映射：
 * 0x00: MTIME_LO   - 时间寄存器低32位
 * 0x04: MTIME_HI   - 时间寄存器高32位
 * 0x08: MTIMECMP_LO - 比较寄存器低32位
 * 0x0C: MTIMECMP_HI - 比较寄存器高32位
 * 0x10: CTRL       - 控制寄存器
 */
class Timer extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)
    val interrupt = Output(Bool())
  })

  // 64位计数器
  val mtime = RegInit(0.U(64.W))
  val mtimecmp = RegInit(~0.U(64.W))
  val ctrl = RegInit(1.U(32.W))  // 默认使能

  // 计数器递增
  when(ctrl(0)) {
    mtime := mtime + 1.U
  }

  // 中断生成
  io.interrupt := mtime >= mtimecmp

  // Wishbone 接口
  val reg_addr = io.wb.adr(4, 2)
  val write_en = io.wb.cyc && io.wb.stb && io.wb.we

  // 写操作
  when(write_en) {
    switch(reg_addr) {
      is(0.U) { mtime := Cat(mtime(63, 32), io.wb.dat_w) }
      is(1.U) { mtime := Cat(io.wb.dat_w, mtime(31, 0)) }
      is(2.U) { mtimecmp := Cat(mtimecmp(63, 32), io.wb.dat_w) }
      is(3.U) { mtimecmp := Cat(io.wb.dat_w, mtimecmp(31, 0)) }
      is(4.U) { ctrl := io.wb.dat_w }
    }
  }

  // 读操作
  val read_data = MuxLookup(reg_addr, 0.U)(Seq(
    0.U -> mtime(31, 0),
    1.U -> mtime(63, 32),
    2.U -> mtimecmp(31, 0),
    3.U -> mtimecmp(63, 32),
    4.U -> ctrl
  ))

  io.wb.dat_r := read_data
  io.wb.ack := io.wb.cyc && io.wb.stb
  io.wb.err := false.B
  io.wb.rty := false.B
}
