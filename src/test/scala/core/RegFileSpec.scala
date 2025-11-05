package rv32e.core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Test suite for Register File
 */
class RegFileSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "RegFile"

  it should "read x0 as zero always" in {
    test(new RegFile) { dut =>
      // Try to write to x0
      dut.io.rd_addr.poke(0.U)
      dut.io.rd_data.poke(0xDEADBEEF.U)
      dut.io.rd_wen.poke(true.B)
      dut.clock.step(1)

      // Read x0 from both ports
      dut.io.rs1_addr.poke(0.U)
      dut.io.rs2_addr.poke(0.U)
      dut.clock.step(1)

      // x0 should always be 0
      dut.io.rs1_data.expect(0.U)
      dut.io.rs2_data.expect(0.U)
    }
  }

  it should "write and read registers correctly" in {
    test(new RegFile) { dut =>
      // Write to register x1
      dut.io.rd_addr.poke(1.U)
      dut.io.rd_data.poke(0x12345678.U)
      dut.io.rd_wen.poke(true.B)
      dut.clock.step(1)

      // Write to register x2
      dut.io.rd_addr.poke(2.U)
      dut.io.rd_data.poke(0xABCDEF00.U)
      dut.io.rd_wen.poke(true.B)
      dut.clock.step(1)

      // Disable write
      dut.io.rd_wen.poke(false.B)

      // Read x1 from rs1
      dut.io.rs1_addr.poke(1.U)
      dut.clock.step(1)
      dut.io.rs1_data.expect(0x12345678.U)

      // Read x2 from rs2
      dut.io.rs2_addr.poke(2.U)
      dut.clock.step(1)
      dut.io.rs2_data.expect(0xABCDEF00.U)

      // Read both simultaneously
      dut.io.rs1_addr.poke(1.U)
      dut.io.rs2_addr.poke(2.U)
      dut.clock.step(1)
      dut.io.rs1_data.expect(0x12345678.U)
      dut.io.rs2_data.expect(0xABCDEF00.U)
    }
  }

  it should "write to all 16 registers" in {
    test(new RegFile) { dut =>
      // Write unique values to all 16 registers
      for (i <- 0 until 16) {
        dut.io.rd_addr.poke(i.U)
        dut.io.rd_data.poke((0x100 * i).U)
        dut.io.rd_wen.poke(true.B)
        dut.clock.step(1)
      }

      dut.io.rd_wen.poke(false.B)

      // Verify all registers (except x0)
      for (i <- 1 until 16) {
        dut.io.rs1_addr.poke(i.U)
        dut.clock.step(1)
        dut.io.rs1_data.expect((0x100 * i).U)
      }
    }
  }

  it should "handle write-through correctly" in {
    test(new RegFile) { dut =>
      // Write to x5 and read it in the same cycle
      dut.io.rd_addr.poke(5.U)
      dut.io.rd_data.poke(0xCAFEBABE.U)
      dut.io.rd_wen.poke(true.B)
      dut.io.rs1_addr.poke(5.U)
      dut.io.rs2_addr.poke(5.U)

      dut.clock.step(1)

      // Should read the newly written value immediately
      dut.io.rs1_data.expect(0xCAFEBABE.U)
      dut.io.rs2_data.expect(0xCAFEBABE.U)
    }
  }

  it should "not write when write enable is false" in {
    test(new RegFile) { dut =>
      // Write initial value to x3
      dut.io.rd_addr.poke(3.U)
      dut.io.rd_data.poke(0x11111111.U)
      dut.io.rd_wen.poke(true.B)
      dut.clock.step(1)

      // Try to overwrite with write enable = false
      dut.io.rd_addr.poke(3.U)
      dut.io.rd_data.poke(0x22222222.U)
      dut.io.rd_wen.poke(false.B)
      dut.clock.step(1)

      // Read x3 - should still have old value
      dut.io.rs1_addr.poke(3.U)
      dut.clock.step(1)
      dut.io.rs1_data.expect(0x11111111.U)
    }
  }

  it should "handle simultaneous read and write to different registers" in {
    test(new RegFile) { dut =>
      // Initialize x4 and x6
      dut.io.rd_addr.poke(4.U)
      dut.io.rd_data.poke(0x44444444.U)
      dut.io.rd_wen.poke(true.B)
      dut.clock.step(1)

      dut.io.rd_addr.poke(6.U)
      dut.io.rd_data.poke(0x66666666.U)
      dut.io.rd_wen.poke(true.B)
      dut.clock.step(1)

      // Write to x7 while reading x4 and x6
      dut.io.rd_addr.poke(7.U)
      dut.io.rd_data.poke(0x77777777.U)
      dut.io.rd_wen.poke(true.B)
      dut.io.rs1_addr.poke(4.U)
      dut.io.rs2_addr.poke(6.U)
      dut.clock.step(1)

      dut.io.rs1_data.expect(0x44444444.U)
      dut.io.rs2_data.expect(0x66666666.U)

      // Verify x7 was written
      dut.io.rd_wen.poke(false.B)
      dut.io.rs1_addr.poke(7.U)
      dut.clock.step(1)
      dut.io.rs1_data.expect(0x77777777.U)
    }
  }
}
