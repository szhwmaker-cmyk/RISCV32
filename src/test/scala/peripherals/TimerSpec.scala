package peripherals

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import common.Config._

/**
 * Timer 单元测试
 */
class TimerSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Timer"

  it should "increment mtime counter" in {
    test(new Timer) { dut =>
      // 读取初始值
      dut.io.wb.adr.poke((TIMER_BASE + 0x00).U)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.cyc.poke(true.B)
      dut.clock.step(1)

      val initial = dut.io.wb.dat_r.peek().litValue

      // 等待几个周期
      dut.io.wb.stb.poke(false.B)
      dut.io.wb.cyc.poke(false.B)
      dut.clock.step(10)

      // 再次读取
      dut.io.wb.adr.poke((TIMER_BASE + 0x00).U)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.cyc.poke(true.B)
      dut.clock.step(1)

      val after = dut.io.wb.dat_r.peek().litValue

      assert(after > initial, "Timer should increment")
    }
  }

  it should "write and read mtimecmp" in {
    test(new Timer) { dut =>
      // 写入 mtimecmp 低位
      dut.io.wb.adr.poke((TIMER_BASE + 0x08).U)
      dut.io.wb.dat_w.poke("h12345678".U)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.cyc.poke(true.B)
      dut.clock.step(1)

      // 写入 mtimecmp 高位
      dut.io.wb.adr.poke((TIMER_BASE + 0x0C).U)
      dut.io.wb.dat_w.poke("hABCDEF00".U)
      dut.clock.step(1)

      // 读回低位
      dut.io.wb.adr.poke((TIMER_BASE + 0x08).U)
      dut.io.wb.we.poke(false.B)
      dut.clock.step(1)
      dut.io.wb.dat_r.expect("h12345678".U)

      // 读回高位
      dut.io.wb.adr.poke((TIMER_BASE + 0x0C).U)
      dut.clock.step(1)
      dut.io.wb.dat_r.expect("hABCDEF00".U)
    }
  }

  it should "generate interrupt when mtime >= mtimecmp" in {
    test(new Timer) { dut =>
      // 设置 mtimecmp 为一个小值
      dut.io.wb.adr.poke((TIMER_BASE + 0x08).U)
      dut.io.wb.dat_w.poke(10.U)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.cyc.poke(true.B)
      dut.clock.step(1)

      dut.io.wb.adr.poke((TIMER_BASE + 0x0C).U)
      dut.io.wb.dat_w.poke(0.U)
      dut.clock.step(1)

      // 等待计数器超过 mtimecmp
      dut.io.wb.stb.poke(false.B)
      dut.io.wb.cyc.poke(false.B)
      dut.clock.step(15)

      // 应该产生中断
      dut.io.interrupt.expect(true.B, "Should generate interrupt when mtime >= mtimecmp")
    }
  }

  it should "allow disabling the counter" in {
    test(new Timer) { dut =>
      // 禁用计数器
      dut.io.wb.adr.poke((TIMER_BASE + 0x10).U)
      dut.io.wb.dat_w.poke(0.U)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.cyc.poke(true.B)
      dut.clock.step(1)

      // 读取 mtime
      dut.io.wb.adr.poke((TIMER_BASE + 0x00).U)
      dut.io.wb.we.poke(false.B)
      dut.clock.step(1)
      val before = dut.io.wb.dat_r.peek().litValue

      // 等待几个周期
      dut.io.wb.stb.poke(false.B)
      dut.io.wb.cyc.poke(false.B)
      dut.clock.step(10)

      // 再次读取
      dut.io.wb.adr.poke((TIMER_BASE + 0x00).U)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.cyc.poke(true.B)
      dut.clock.step(1)
      val after = dut.io.wb.dat_r.peek().litValue

      assert(after == before, "Timer should not increment when disabled")
    }
  }
}
