package peripherals

import chisel3._
import chisel3.util._
import bus._
import common.Config._

// ============================================================================
// GPIO 外设 - 16-bit GPIO
// ============================================================================

class Gpio extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)

    // GPIO 外部引脚
    val gpio_in  = Input(UInt(16.W))
    val gpio_out = Output(UInt(16.W))
    val gpio_oe  = Output(UInt(16.W))  // 输出使能
  })

  // ========== 寄存器 ==========
  val data_in_reg  = RegNext(io.gpio_in, 0.U)
  val data_out_reg = RegInit(0.U(16.W))
  val dir_reg      = RegInit(0.U(16.W))  // 0=输入, 1=输出
  val oe_reg       = RegInit(0.U(16.W))  // 输出使能

  io.gpio_out := data_out_reg
  io.gpio_oe := oe_reg

  // ========== Wishbone 接口 ==========
  val ack_reg = RegNext(io.wb.stb_o && io.wb.cyc_o, false.B)
  io.wb.ack_o := ack_reg

  val reg_addr = io.wb.adr_o(7, 0)

  // 读操作
  io.wb.dat_o := MuxLookup(reg_addr, 0.U)(Seq(
    GpioReg.DATA_IN.U  -> data_in_reg,
    GpioReg.DATA_OUT.U -> data_out_reg,
    GpioReg.DIR.U      -> dir_reg,
    GpioReg.OE.U       -> oe_reg
  ))

  // 写操作
  when(io.wb.stb_o && io.wb.cyc_o && io.wb.we_o) {
    when(reg_addr === GpioReg.DATA_OUT.U) {
      data_out_reg := io.wb.dat_o(15, 0)
    }.elsewhen(reg_addr === GpioReg.DIR.U) {
      dir_reg := io.wb.dat_o(15, 0)
      oe_reg := io.wb.dat_o(15, 0)  // 同步更新OE
    }.elsewhen(reg_addr === GpioReg.OE.U) {
      oe_reg := io.wb.dat_o(15, 0)
    }
  }
}
