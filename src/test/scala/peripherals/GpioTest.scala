package peripherals

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import common.Config._

/**
 * GPIO 单元测试
 */
class GpioTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Gpio"

  it should "read input pins" in {
    test(new Gpio) { dut =>
      // 设置输入值
      dut.io.gpio.pins_in.poke(0xABCD.U)

      // 读取 DATA_IN 寄存器
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(GpioRegs.DATA_IN.U)
      dut.io.wb.sel.poke(0xF.U)
      dut.clock.step()

      dut.io.wb.dat_r.expect(0xABCD.U)
      dut.io.wb.ack.expect(true.B)
    }
  }

  it should "write output pins" in {
    test(new Gpio) { dut =>
      // 设置为输出模式
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(GpioRegs.DIR.U)
      dut.io.wb.dat_w.poke(0xFFFF.U)
      dut.io.wb.sel.poke(0xF.U)
      dut.clock.step()

      // 使能输出
      dut.io.wb.adr.poke(GpioRegs.OE.U)
      dut.io.wb.dat_w.poke(0xFFFF.U)
      dut.clock.step()

      // 写入输出数据
      dut.io.wb.adr.poke(GpioRegs.DATA_OUT.U)
      dut.io.wb.dat_w.poke(0x5555.U)
      dut.clock.step()

      // 检查输出
      dut.io.gpio.pins_out.expect(0x5555.U)
      dut.io.gpio.pins_oe.expect(0xFFFF.U)
    }
  }

  it should "support mixed input/output configuration" in {
    test(new Gpio) { dut =>
      // 低 8 位输入，高 8 位输出
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(GpioRegs.DIR.U)
      dut.io.wb.dat_w.poke(0xFF00.U)
      dut.io.wb.sel.poke(0xF.U)
      dut.clock.step()

      // 使能高 8 位输出
      dut.io.wb.adr.poke(GpioRegs.OE.U)
      dut.io.wb.dat_w.poke(0xFF00.U)
      dut.clock.step()

      // 写入输出数据
      dut.io.wb.adr.poke(GpioRegs.DATA_OUT.U)
      dut.io.wb.dat_w.poke(0xAA55.U)
      dut.clock.step()

      // 只有高 8 位应该输出
      dut.io.gpio.pins_oe.expect(0xFF00.U)
    }
  }

  it should "handle read-write-read sequence" in {
    test(new Gpio) { dut =>
      // 写入数据
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(GpioRegs.DATA_OUT.U)
      dut.io.wb.dat_w.poke(0x1234.U)
      dut.io.wb.sel.poke(0xF.U)
      dut.clock.step()

      // 读回数据
      dut.io.wb.we.poke(false.B)
      dut.clock.step()
      dut.io.wb.dat_r.expect(0x1234.U)

      // 再次写入不同数据
      dut.io.wb.we.poke(true.B)
      dut.io.wb.dat_w.poke(0x5678.U)
      dut.clock.step()

      // 读回验证
      dut.io.wb.we.poke(false.B)
      dut.clock.step()
      dut.io.wb.dat_r.expect(0x5678.U)
    }
  }
}
