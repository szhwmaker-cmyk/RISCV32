package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/**
 * RegFile 单元测试
 *
 * 测试：
 * 1. x0 始终为 0
 * 2. 读写操作正确性
 * 3. 同时读写不同寄存器
 * 4. 读写相同寄存器（旁路）
 */
class RegFileTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "RegFile"

  it should "keep x0 as zero" in {
    test(new RegFile) { dut =>
      // 尝试写入 x0
      dut.io.wen.poke(true.B)
      dut.io.waddr.poke(0.U)
      dut.io.wdata.poke(0xDEADBEEF.U)
      dut.clock.step()

      // 读取 x0，应该仍然是 0
      dut.io.rs1_addr.poke(0.U)
      dut.io.rs1_data.expect(0.U)
    }
  }

  it should "read and write registers correctly" in {
    test(new RegFile) { dut =>
      // 写入 x1 = 0x12345678
      dut.io.wen.poke(true.B)
      dut.io.waddr.poke(1.U)
      dut.io.wdata.poke(0x12345678.U)
      dut.clock.step()

      // 读取 x1
      dut.io.rs1_addr.poke(1.U)
      dut.io.rs1_data.expect(0x12345678.U)

      // 写入 x2 = 0xABCDEF00
      dut.io.wen.poke(true.B)
      dut.io.waddr.poke(2.U)
      dut.io.wdata.poke(0xABCDEF00.U)
      dut.clock.step()

      // 同时读取 x1 和 x2
      dut.io.rs1_addr.poke(1.U)
      dut.io.rs2_addr.poke(2.U)
      dut.io.rs1_data.expect(0x12345678.U)
      dut.io.rs2_data.expect(0xABCDEF00.U)
    }
  }

  it should "handle write-then-read hazard" in {
    test(new RegFile) { dut =>
      // 写入 x3
      dut.io.wen.poke(true.B)
      dut.io.waddr.poke(3.U)
      dut.io.wdata.poke(0x55555555.U)
      dut.clock.step()

      // 立即读取 x3
      dut.io.rs1_addr.poke(3.U)
      dut.io.rs1_data.expect(0x55555555.U)
    }
  }

  it should "write to all registers" in {
    test(new RegFile) { dut =>
      // 写入所有寄存器
      for (i <- 1 until 16) {
        dut.io.wen.poke(true.B)
        dut.io.waddr.poke(i.U)
        dut.io.wdata.poke((i * 0x11111111).U)
        dut.clock.step()
      }

      // 读取所有寄存器
      for (i <- 1 until 16) {
        dut.io.rs1_addr.poke(i.U)
        dut.io.rs1_data.expect((i * 0x11111111).U)
      }
    }
  }
}
