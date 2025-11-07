package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import common.Config._

/**
 * RegFile 单元测试
 * 测试寄存器堆的读写功能
 */
class RegFileSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "RegFile"

  it should "read zero from x0" in {
    test(new RegFile) { dut =>
      dut.io.rs1_addr.poke(0.U)
      dut.io.rs2_addr.poke(0.U)
      dut.clock.step(1)

      dut.io.rs1_data.expect(0.U, "x0 should always be 0")
      dut.io.rs2_data.expect(0.U, "x0 should always be 0")
    }
  }

  it should "write and read back from register x1" in {
    test(new RegFile) { dut =>
      // 写入 x1 = 0xDEADBEEF
      dut.io.rd_addr.poke(1.U)
      dut.io.rd_data.poke("hDEADBEEF".U)
      dut.io.rd_wen.poke(true.B)
      dut.clock.step(1)

      // 读取 x1
      dut.io.rs1_addr.poke(1.U)
      dut.io.rd_wen.poke(false.B)
      dut.clock.step(1)

      dut.io.rs1_data.expect("hDEADBEEF".U, "Should read back written value")
    }
  }

  it should "not write to x0" in {
    test(new RegFile) { dut =>
      // 尝试写入 x0
      dut.io.rd_addr.poke(0.U)
      dut.io.rd_data.poke("hFFFFFFFF".U)
      dut.io.rd_wen.poke(true.B)
      dut.clock.step(1)

      // 读取 x0
      dut.io.rs1_addr.poke(0.U)
      dut.io.rd_wen.poke(false.B)
      dut.clock.step(1)

      dut.io.rs1_data.expect(0.U, "x0 should remain 0 even after write attempt")
    }
  }

  it should "support simultaneous dual-port read" in {
    test(new RegFile) { dut =>
      // 写入 x2 = 0x1234
      dut.io.rd_addr.poke(2.U)
      dut.io.rd_data.poke("h1234".U)
      dut.io.rd_wen.poke(true.B)
      dut.clock.step(1)

      // 写入 x3 = 0x5678
      dut.io.rd_addr.poke(3.U)
      dut.io.rd_data.poke("h5678".U)
      dut.io.rd_wen.poke(true.B)
      dut.clock.step(1)

      // 同时读取 x2 和 x3
      dut.io.rs1_addr.poke(2.U)
      dut.io.rs2_addr.poke(3.U)
      dut.io.rd_wen.poke(false.B)
      dut.clock.step(1)

      dut.io.rs1_data.expect("h1234".U, "Should read x2")
      dut.io.rs2_data.expect("h5678".U, "Should read x3")
    }
  }

  it should "support write-then-read forwarding" in {
    test(new RegFile) { dut =>
      // 写入 x5 = 0xABCD
      dut.io.rd_addr.poke(5.U)
      dut.io.rd_data.poke("hABCD".U)
      dut.io.rd_wen.poke(true.B)

      // 同时读取 x5（写后立即读）
      dut.io.rs1_addr.poke(5.U)

      dut.clock.step(1)

      // 应该读到刚写入的值
      dut.io.rs1_data.expect("hABCD".U, "Should forward written value immediately")
    }
  }

  it should "write to all 16 registers correctly" in {
    test(new RegFile) { dut =>
      // 写入所有寄存器
      for (i <- 1 until REG_NUM) {
        dut.io.rd_addr.poke(i.U)
        dut.io.rd_data.poke((i * 0x1000).U)
        dut.io.rd_wen.poke(true.B)
        dut.clock.step(1)
      }

      // 读回并验证
      dut.io.rd_wen.poke(false.B)
      for (i <- 1 until REG_NUM) {
        dut.io.rs1_addr.poke(i.U)
        dut.clock.step(1)
        dut.io.rs1_data.expect((i * 0x1000).U, s"Register x$i should contain correct value")
      }
    }
  }
}
