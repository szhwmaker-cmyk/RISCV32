package peripherals

import chisel3._
import chisel3.util._
import common.Config._
import bus._

/**
 * GPIO 外设
 * 32位可配置输入输出端口
 *
 * 寄存器映射：
 * 0x00: DATA_IN  - 输入数据
 * 0x04: DATA_OUT - 输出数据
 * 0x08: DIR      - 方向控制 (0=输入, 1=输出)
 * 0x0C: OE       - 输出使能
 */
class GPIO extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)
    val gpio_in = Input(UInt(32.W))
    val gpio_out = Output(UInt(32.W))
    val gpio_oe = Output(UInt(32.W))
  })

  // 寄存器
  val data_out = RegInit(0.U(32.W))
  val direction = RegInit(0.U(32.W))
  val output_enable = RegInit(0.U(32.W))

  // Wishbone 接口
  val reg_addr = io.wb.adr(3, 2)
  val write_en = io.wb.cyc && io.wb.stb && io.wb.we
  val read_en = io.wb.cyc && io.wb.stb && !io.wb.we

  // 写操作
  when(write_en) {
    switch(reg_addr) {
      is(1.U) { data_out := io.wb.dat_w }
      is(2.U) { direction := io.wb.dat_w }
      is(3.U) { output_enable := io.wb.dat_w }
    }
  }

  // 读操作
  val read_data = MuxLookup(reg_addr, 0.U)(Seq(
    0.U -> io.gpio_in,
    1.U -> data_out,
    2.U -> direction,
    3.U -> output_enable
  ))

  io.wb.dat_r := read_data
  io.wb.ack := io.wb.cyc && io.wb.stb
  io.wb.err := false.B
  io.wb.rty := false.B

  // GPIO 输出
  io.gpio_out := data_out
  io.gpio_oe := output_enable & direction
}
