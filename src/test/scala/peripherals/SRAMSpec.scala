package peripherals

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import common.Config._

/**
 * SRAM 单元测试
 */
class SRAMSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "SRAM"

  it should "write and read back a word" in {
    test(new SRAM) { dut =>
      // 写入一个字
      dut.io.wb.adr.poke((SRAM_BASE + 0x100).U)
      dut.io.wb.dat_w.poke("hDEADBEEF".U)
      dut.io.wb.sel.poke("b1111".U)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.cyc.poke(true.B)
      dut.clock.step(1)

      // 等待写入完成
      dut.io.wb.stb.poke(false.B)
      dut.io.wb.cyc.poke(false.B)
      dut.clock.step(1)

      // 读回
      dut.io.wb.adr.poke((SRAM_BASE + 0x100).U)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.cyc.poke(true.B)
      dut.clock.step(1)

      // 等待ACK
      while (!dut.io.wb.ack.peek().litToBoolean) {
        dut.clock.step(1)
      }

      dut.io.wb.dat_r.expect("hDEADBEEF".U)
    }
  }

  it should "support byte write and read" in {
    test(new SRAM) { dut =>
      // 写入字节到低位
      dut.io.wb.adr.poke((SRAM_BASE + 0x200).U)
      dut.io.wb.dat_w.poke("h000000AB".U)
      dut.io.wb.sel.poke("b0001".U)  // 只写入最低字节
      dut.io.wb.we.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.cyc.poke(true.B)
      dut.clock.step(2)

      // 写入字节到高位
      dut.io.wb.adr.poke((SRAM_BASE + 0x200).U)
      dut.io.wb.dat_w.poke("hCD000000".U)
      dut.io.wb.sel.poke("b1000".U)  // 只写入最高字节
      dut.clock.step(2)

      // 读回整个字
      dut.io.wb.adr.poke((SRAM_BASE + 0x200).U)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.sel.poke("b1111".U)
      dut.clock.step(1)

      while (!dut.io.wb.ack.peek().litToBoolean) {
        dut.clock.step(1)
      }

      val result = dut.io.wb.dat_r.peek().litValue
      // 应该包含我们写入的字节
      assert((result & 0xFF) == 0xAB, "Low byte should be 0xAB")
      assert(((result >> 24) & 0xFF) == 0xCD, "High byte should be 0xCD")
    }
  }

  it should "handle multiple write/read cycles" in {
    test(new SRAM) { dut =>
      val testData = Seq(
        (SRAM_BASE + 0x00, 0x11111111),
        (SRAM_BASE + 0x04, 0x22222222),
        (SRAM_BASE + 0x08, 0x33333333),
        (SRAM_BASE + 0x0C, 0x44444444)
      )

      // 写入所有数据
      for ((addr, data) <- testData) {
        dut.io.wb.adr.poke(addr.U)
        dut.io.wb.dat_w.poke(data.U)
        dut.io.wb.sel.poke("b1111".U)
        dut.io.wb.we.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(2)
      }

      // 读回并验证
      for ((addr, expectedData) <- testData) {
        dut.io.wb.adr.poke(addr.U)
        dut.io.wb.we.poke(false.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)

        while (!dut.io.wb.ack.peek().litToBoolean) {
          dut.clock.step(1)
        }

        dut.io.wb.dat_r.expect(expectedData.U,
          s"Address 0x${addr.toHexString} should contain 0x${expectedData.toHexString}")
      }
    }
  }

  it should "respond with ACK after write" in {
    test(new SRAM) { dut =>
      dut.io.wb.adr.poke(SRAM_BASE.U)
      dut.io.wb.dat_w.poke("h12345678".U)
      dut.io.wb.sel.poke("b1111".U)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.cyc.poke(true.B)
      dut.clock.step(1)

      // 下一个周期应该有 ACK
      dut.clock.step(1)
      dut.io.wb.ack.expect(true.B)
    }
  }

  it should "preserve data across different addresses" in {
    test(new SRAM) { dut =>
      // 写入地址A
      dut.io.wb.adr.poke((SRAM_BASE + 0x1000).U)
      dut.io.wb.dat_w.poke("hAAAAAAAA".U)
      dut.io.wb.sel.poke("b1111".U)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.cyc.poke(true.B)
      dut.clock.step(2)

      // 写入地址B
      dut.io.wb.adr.poke((SRAM_BASE + 0x2000).U)
      dut.io.wb.dat_w.poke("h55555555".U)
      dut.clock.step(2)

      // 读回地址A，应该保持不变
      dut.io.wb.adr.poke((SRAM_BASE + 0x1000).U)
      dut.io.wb.we.poke(false.B)
      dut.clock.step(1)

      while (!dut.io.wb.ack.peek().litToBoolean) {
        dut.clock.step(1)
      }

      dut.io.wb.dat_r.expect("hAAAAAAAA".U, "Address A should preserve its data")
    }
  }
}
