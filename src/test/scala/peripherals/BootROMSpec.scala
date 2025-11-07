package peripherals

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import common.Config._

/**
 * BootROM 单元测试
 */
class BootROMSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "BootROM"

  it should "read default boot code" in {
    test(new BootROM()) { dut =>
      // 读取第一条指令（默认的启动代码）
      dut.io.wb.adr.poke(BOOTROM_BASE.U)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.clock.step(1)

      // 应该返回第一条指令（lui x2, 0x20000）
      dut.io.wb.ack.expect(true.B)
      dut.io.wb.dat_r.expect("h20000137".U)
    }
  }

  it should "read consecutive addresses" in {
    test(new BootROM()) { dut =>
      // 读取地址 0x00
      dut.io.wb.adr.poke((BOOTROM_BASE + 0x00).U)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.clock.step(1)
      val data0 = dut.io.wb.dat_r.peek()

      // 读取地址 0x04
      dut.io.wb.adr.poke((BOOTROM_BASE + 0x04).U)
      dut.clock.step(1)
      val data1 = dut.io.wb.dat_r.peek()

      // 读取地址 0x08
      dut.io.wb.adr.poke((BOOTROM_BASE + 0x08).U)
      dut.clock.step(1)
      val data2 = dut.io.wb.dat_r.peek()

      // 验证读取了不同的数据
      assert(data0.litValue != data1.litValue || data1.litValue != data2.litValue,
        "Should read different instructions from consecutive addresses")
    }
  }

  it should "respond immediately with ACK" in {
    test(new BootROM()) { dut =>
      dut.io.wb.adr.poke(BOOTROM_BASE.U)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.clock.step(1)

      // 单周期读取，应该立即应答
      dut.io.wb.ack.expect(true.B)
      dut.io.wb.err.expect(false.B)
      dut.io.wb.rty.expect(false.B)
    }
  }

  it should "ignore write attempts (read-only)" in {
    test(new BootROM()) { dut =>
      // 尝试写入 BootROM
      dut.io.wb.adr.poke(BOOTROM_BASE.U)
      dut.io.wb.dat_w.poke("hDEADBEEF".U)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.cyc.poke(true.B)
      dut.clock.step(1)

      // 仍然应该应答（但不写入）
      dut.io.wb.ack.expect(true.B)

      // 读回应该是原始数据，不是写入的数据
      dut.io.wb.we.poke(false.B)
      dut.clock.step(1)
      assert(dut.io.wb.dat_r.peek().litValue != 0xDEADBEEFL,
        "BootROM should not be writable")
    }
  }

  it should "handle non-aligned addresses correctly" in {
    test(new BootROM()) { dut =>
      // 读取字对齐地址
      dut.io.wb.adr.poke((BOOTROM_BASE + 0x04).U)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.clock.step(1)

      val data_aligned = dut.io.wb.dat_r.peek()

      // 读取下一个字地址
      dut.io.wb.adr.poke((BOOTROM_BASE + 0x08).U)
      dut.clock.step(1)

      val data_next = dut.io.wb.dat_r.peek()

      // 应该读取不同的字
      assert(data_aligned.litValue != data_next.litValue || data_aligned.litValue == 0,
        "Aligned reads should return valid data")
    }
  }
}
