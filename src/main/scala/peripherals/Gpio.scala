package peripherals

import chisel3._
import chisel3.util._
import bus._
import common.Config._

/**
 * GPIO 控制器
 *
 * 寄存器映射：
 * 0x00: DATA_IN  - 输入数据寄存器
 * 0x04: DATA_OUT - 输出数据寄存器
 * 0x08: DIR      - 方向控制（0=输入, 1=输出）
 * 0x0C: OE       - 输出使能
 */
class Gpio extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)

    // GPIO 物理接口
    val gpio_in  = Input(UInt(16.W))
    val gpio_out = Output(UInt(16.W))
    val gpio_oe  = Output(UInt(16.W))
  })

  // 寄存器
  val data_out_reg = RegInit(0.U(16.W))
  val dir_reg      = RegInit(0.U(16.W))
  val oe_reg       = RegInit(0.U(16.W))

  // 地址译码
  val addr_offset = io.wb.adr_o - GPIO_BASE.U

  // 写操作
  when(io.wb.cyc_o && io.wb.stb_o && io.wb.we_o) {
    switch(addr_offset) {
      is(GpioRegs.DATA_OUT.U) { data_out_reg := io.wb.dat_o(15, 0) }
      is(GpioRegs.DIR.U)      { dir_reg      := io.wb.dat_o(15, 0) }
      is(GpioRegs.OE.U)       { oe_reg       := io.wb.dat_o(15, 0) }
    }
  }

  // 读操作
  io.wb.dat_i := MuxLookup(addr_offset, 0.U)(Seq(
    GpioRegs.DATA_IN.U  -> io.gpio_in,
    GpioRegs.DATA_OUT.U -> data_out_reg,
    GpioRegs.DIR.U      -> dir_reg,
    GpioRegs.OE.U       -> oe_reg
  ))

  // 输出
  io.gpio_out := data_out_reg
  io.gpio_oe  := oe_reg

  // 应答信号
  io.wb.ack_i := RegNext(io.wb.cyc_o && io.wb.stb_o, false.B)
}
