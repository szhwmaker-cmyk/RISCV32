package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/**
 * RegFile 单元测试
 */
class RegFileSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "RegFile"

  it should "read and write registers correctly" in {
    test(new RegFile) { dut =>
      // 写入寄存器 x1
      dut.io.wen.poke(true.B)
      dut.io.rd_addr.poke(1.U)
      dut.io.rd_data.poke(0x1234.U)
      dut.clock.step()

      // 读取寄存器 x1
      dut.io.rs1_addr.poke(1.U)
      dut.clock.step()
      dut.io.rs1_data.expect(0x1234.U)
    }
  }

  it should "always return 0 for x0" in {
    test(new RegFile) { dut =>
      // 尝试写入 x0
      dut.io.wen.poke(true.B)
      dut.io.rd_addr.poke(0.U)
      dut.io.rd_data.poke(0xFFFF.U)
      dut.clock.step()

      // 读取 x0，应该始终为 0
      dut.io.rs1_addr.poke(0.U)
      dut.io.rs1_data.expect(0.U)
    }
  }

  it should "support simultaneous read on two ports" in {
    test(new RegFile) { dut =>
      // 写入 x1 和 x2
      dut.io.wen.poke(true.B)
      dut.io.rd_addr.poke(1.U)
      dut.io.rd_data.poke(0xAAAA.U)
      dut.clock.step()

      dut.io.rd_addr.poke(2.U)
      dut.io.rd_data.poke(0xBBBB.U)
      dut.clock.step()

      // 同时读取 x1 和 x2
      dut.io.rs1_addr.poke(1.U)
      dut.io.rs2_addr.poke(2.U)
      dut.io.rs1_data.expect(0xAAAA.U)
      dut.io.rs2_data.expect(0xBBBB.U)
    }
  }
}
