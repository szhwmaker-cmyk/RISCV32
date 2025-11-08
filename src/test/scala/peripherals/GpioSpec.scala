package peripherals

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import common.Config._

class GpioSpec extends AnyFlatSpec with ChiselScalatestTester {
  "GPIO" should "write and read correctly" in {
    test(new Gpio) { dut =>
      // 写入输出数据寄存器
      dut.io.wb.adr_o.poke(GpioReg.DATA_OUT.U)
      dut.io.wb.dat_o.poke(0xA5A5.U)
      dut.io.wb.we_o.poke(true.B)
      dut.io.wb.stb_o.poke(true.B)
      dut.io.wb.cyc_o.poke(true.B)
      dut.clock.step()
      dut.io.wb.stb_o.poke(false.B)
      dut.io.wb.cyc_o.poke(false.B)
      dut.clock.step()

      // 验证输出
      dut.io.gpio_out.expect(0xA5A5.U)

      // 设置方向寄存器
      dut.io.wb.adr_o.poke(GpioReg.DIR.U)
      dut.io.wb.dat_o.poke(0xFF00.U)
      dut.io.wb.we_o.poke(true.B)
      dut.io.wb.stb_o.poke(true.B)
      dut.io.wb.cyc_o.poke(true.B)
      dut.clock.step()
      dut.io.wb.stb_o.poke(false.B)
      dut.io.wb.cyc_o.poke(false.B)
      dut.clock.step()

      dut.io.gpio_oe.expect(0xFF00.U)
    }
  }

  it should "read input correctly" in {
    test(new Gpio) { dut =>
      // 设置输入
      dut.io.gpio_in.poke(0x1234.U)
      dut.clock.step(2)

      // 读取输入寄存器
      dut.io.wb.adr_o.poke(GpioReg.DATA_IN.U)
      dut.io.wb.we_o.poke(false.B)
      dut.io.wb.stb_o.poke(true.B)
      dut.io.wb.cyc_o.poke(true.B)
      dut.clock.step(2)

      dut.io.wb.dat_o.expect(0x1234.U)

      dut.io.wb.stb_o.poke(false.B)
      dut.io.wb.cyc_o.poke(false.B)
    }
  }
}
