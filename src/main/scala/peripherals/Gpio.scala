package peripherals

import chisel3._
import chisel3.util._
import common.Config._
import bus._

/**
 * GPIO 控制器
 *
 * 支持 16 个可配置的 I/O 引脚
 *
 * 寄存器映射：
 * 0x00: DATA_IN - 输入数据寄存器（只读）
 * 0x04: DATA_OUT - 输出数据寄存器（读写）
 * 0x08: DIR - 方向控制寄存器（读写）
 *       0: 输入
 *       1: 输出
 * 0x0C: OE - 输出使能寄存器（读写）
 *       0: 禁用输出（高阻态）
 *       1: 使能输出
 */
class GpioIO extends Bundle {
  val pins_in = Input(UInt(16.W))
  val pins_out = Output(UInt(16.W))
  val pins_oe = Output(UInt(16.W)) // Output Enable
}

class Gpio extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)
    val gpio = new GpioIO
  })

  // 寄存器
  val data_out = RegInit(0.U(16.W))
  val direction = RegInit(0.U(16.W)) // 0: input, 1: output
  val output_enable = RegInit(0.U(16.W))

  // GPIO 输出
  io.gpio.pins_out := data_out
  io.gpio.pins_oe := output_enable & direction

  // Wishbone 总线处理
  val wb_addr = io.wb.adr(7, 0)
  val wb_write = io.wb.cyc && io.wb.stb && io.wb.we
  val wb_read = io.wb.cyc && io.wb.stb && !io.wb.we

  // 写操作
  when(wb_write) {
    switch(wb_addr) {
      is(GpioRegs.DATA_OUT.U) {
        data_out := io.wb.dat_w(15, 0)
      }
      is(GpioRegs.DIR.U) {
        direction := io.wb.dat_w(15, 0)
      }
      is(GpioRegs.OE.U) {
        output_enable := io.wb.dat_w(15, 0)
      }
    }
  }

  // 读操作
  val read_data = MuxLookup(wb_addr, 0.U)(
    Seq(
      GpioRegs.DATA_IN.U -> Cat(Fill(16, 0.U), io.gpio.pins_in),
      GpioRegs.DATA_OUT.U -> Cat(Fill(16, 0.U), data_out),
      GpioRegs.DIR.U -> Cat(Fill(16, 0.U), direction),
      GpioRegs.OE.U -> Cat(Fill(16, 0.U), output_enable)
    )
  )

  io.wb.dat_r := read_data
  io.wb.ack := io.wb.cyc && io.wb.stb
  io.wb.err := false.B
  io.wb.rty := false.B
}

/**
 * GPIO 位操作辅助模块
 *
 * 提供单个引脚的便捷访问
 */
class GpioBit extends Bundle {
  val in = Input(Bool())
  val out = Output(Bool())
  val oe = Output(Bool())
}

object GpioHelpers {
  /**
   * 从 GPIO 总线中提取单个引脚
   */
  def extractBit(gpio: GpioIO, bit: Int): GpioBit = {
    val pin = Wire(new GpioBit)
    pin.in := gpio.pins_in(bit)
    gpio.pins_out := DontCare
    gpio.pins_oe := DontCare
    // 注意：这只是一个示例，实际使用时需要正确连接
    pin
  }
}
