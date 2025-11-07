package peripherals

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import common.Config._

/**
 * GPIO 单元测试
 */
class GPIOSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "GPIO"

  it should "write and read back output data" in {
    test(new GPIO) { dut =>
      // 写入输出数据寄存器
      dut.io.wb.adr.poke((GPIO_BASE + 0x04).U)
      dut.io.wb.dat_w.poke("hDEADBEEF".U)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.cyc.poke(true.B)
      dut.clock.step(1)

      dut.io.wb.ack.expect(true.B)

      // 读回输出数据
      dut.io.wb.we.poke(false.B)
      dut.clock.step(1)

      dut.io.wb.dat_r.expect("hDEADBEEF".U)
      dut.io.gpio_out.expect("hDEADBEEF".U)
    }
  }

  it should "configure direction register" in {
    test(new GPIO) { dut =>
      // 设置方向寄存器（所有引脚为输出）
      dut.io.wb.adr.poke((GPIO_BASE + 0x08).U)
      dut.io.wb.dat_w.poke("hFFFFFFFF".U)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.cyc.poke(true.B)
      dut.clock.step(1)

      // 读回方向寄存器
      dut.io.wb.we.poke(false.B)
      dut.clock.step(1)

      dut.io.wb.dat_r.expect("hFFFFFFFF".U)
    }
  }

  it should "read input data" in {
    test(new GPIO) { dut =>
      // 设置输入引脚的值
      dut.io.gpio_in.poke("h12345678".U)

      // 读取输入数据寄存器
      dut.io.wb.adr.poke((GPIO_BASE + 0x00).U)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.cyc.poke(true.B)
      dut.clock.step(1)

      dut.io.wb.dat_r.expect("h12345678".U)
    }
  }

  it should "control output enable" in {
    test(new GPIO) { dut =>
      // 设置输出使能寄存器
      dut.io.wb.adr.poke((GPIO_BASE + 0x0C).U)
      dut.io.wb.dat_w.poke("h0000FFFF".U)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.cyc.poke(true.B)
      dut.clock.step(1)

      // 设置方向寄存器为全输出
      dut.io.wb.adr.poke((GPIO_BASE + 0x08).U)
      dut.io.wb.dat_w.poke("hFFFFFFFF".U)
      dut.clock.step(1)

      // 输出使能应该是 OE & DIR
      dut.io.gpio_oe.expect("h0000FFFF".U)
    }
  }

  it should "respond with ACK on valid access" in {
    test(new GPIO) { dut =>
      dut.io.wb.adr.poke(GPIO_BASE.U)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.cyc.poke(true.B)
      dut.clock.step(1)

      dut.io.wb.ack.expect(true.B)
      dut.io.wb.err.expect(false.B)
    }
  }
}
