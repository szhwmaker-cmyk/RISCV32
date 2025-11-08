package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class RegFileSpec extends AnyFlatSpec with ChiselScalatestTester {
  "RegFile" should "write and read correctly" in {
    test(new RegFile) { dut =>
      // 写入寄存器1
      dut.io.wen.poke(true.B)
      dut.io.rd_addr.poke(1.U)
      dut.io.rd_data.poke(0x12345678.U)
      dut.clock.step()

      // 读取寄存器1
      dut.io.rs1_addr.poke(1.U)
      dut.io.wen.poke(false.B)
      dut.clock.step()
      dut.io.rs1_data.expect(0x12345678.U)

      // 写入寄存器2
      dut.io.wen.poke(true.B)
      dut.io.rd_addr.poke(2.U)
      dut.io.rd_data.poke(0xABCDEF00.U)
      dut.clock.step()

      // 同时读取两个寄存器
      dut.io.rs1_addr.poke(1.U)
      dut.io.rs2_addr.poke(2.U)
      dut.io.wen.poke(false.B)
      dut.clock.step()
      dut.io.rs1_data.expect(0x12345678.U)
      dut.io.rs2_data.expect(0xABCDEF00.U)
    }
  }

  it should "always return 0 for x0" in {
    test(new RegFile) { dut =>
      // 尝试写入x0
      dut.io.wen.poke(true.B)
      dut.io.rd_addr.poke(0.U)
      dut.io.rd_data.poke(0xDEADBEEF.U)
      dut.clock.step()

      // 读取x0，应该仍然是0
      dut.io.rs1_addr.poke(0.U)
      dut.clock.step()
      dut.io.rs1_data.expect(0.U)
    }
  }
}
