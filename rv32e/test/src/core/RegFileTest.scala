package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/**
 * Register File 测试
 */
class RegFileTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "RegFile"

  it should "read zero from x0 register" in {
    test(new RegFile()) { dut =>
      dut.io.rs1_addr.poke(0.U)
      dut.io.rs1_data.expect(0.U)

      dut.io.rs2_addr.poke(0.U)
      dut.io.rs2_data.expect(0.U)
    }
  }

  it should "write to x0 should have no effect" in {
    test(new RegFile()) { dut =>
      dut.io.rd_addr.poke(0.U)
      dut.io.rd_data.poke(0x12345678L.U)
      dut.io.rd_wen.poke(true.B)
      dut.clock.step(1)

      // x0 should still be zero
      dut.io.rs1_addr.poke(0.U)
      dut.io.rs1_data.expect(0.U)
    }
  }

  it should "write and read from general purpose registers" in {
    test(new RegFile()) { dut =>
      // Write to x1
      dut.io.rd_addr.poke(1.U)
      dut.io.rd_data.poke(0xDEADBEEFL.U)
      dut.io.rd_wen.poke(true.B)
      dut.clock.step(1)

      // Read from x1
      dut.io.rs1_addr.poke(1.U)
      dut.io.rs1_data.expect(0xDEADBEEFL.U)

      // Write to x15 (last register in RV32E)
      dut.io.rd_addr.poke(15.U)
      dut.io.rd_data.poke(0xCAFEBABEL.U)
      dut.io.rd_wen.poke(true.B)
      dut.clock.step(1)

      // Read from x15
      dut.io.rs2_addr.poke(15.U)
      dut.io.rs2_data.expect(0xCAFEBABEL.U)

      // x1 should still have its value
      dut.io.rs1_addr.poke(1.U)
      dut.io.rs1_data.expect(0xDEADBEEFL.U)
    }
  }

  it should "handle simultaneous dual reads" in {
    test(new RegFile()) { dut =>
      // Write to x5
      dut.io.rd_addr.poke(5.U)
      dut.io.rd_data.poke(0x11111111L.U)
      dut.io.rd_wen.poke(true.B)
      dut.clock.step(1)

      // Write to x7
      dut.io.rd_addr.poke(7.U)
      dut.io.rd_data.poke(0x22222222L.U)
      dut.io.rd_wen.poke(true.B)
      dut.clock.step(1)

      // Read both simultaneously
      dut.io.rs1_addr.poke(5.U)
      dut.io.rs2_addr.poke(7.U)
      dut.io.rs1_data.expect(0x11111111L.U)
      dut.io.rs2_data.expect(0x22222222L.U)
    }
  }

  it should "disable write when wen is false" in {
    test(new RegFile()) { dut =>
      // Initialize x3 with a value
      dut.io.rd_addr.poke(3.U)
      dut.io.rd_data.poke(0xAAAAAAAAL.U)
      dut.io.rd_wen.poke(true.B)
      dut.clock.step(1)

      // Try to write with wen=false
      dut.io.rd_addr.poke(3.U)
      dut.io.rd_data.poke(0xBBBBBBBBL.U)
      dut.io.rd_wen.poke(false.B)
      dut.clock.step(1)

      // x3 should retain its original value
      dut.io.rs1_addr.poke(3.U)
      dut.io.rs1_data.expect(0xAAAAAAAAL.U)
    }
  }

  it should "overwrite register values correctly" in {
    test(new RegFile()) { dut =>
      // Write initial value to x10
      dut.io.rd_addr.poke(10.U)
      dut.io.rd_data.poke(0x00000001L.U)
      dut.io.rd_wen.poke(true.B)
      dut.clock.step(1)

      // Overwrite x10 with new value
      dut.io.rd_addr.poke(10.U)
      dut.io.rd_data.poke(0xFFFFFFFFL.U)
      dut.io.rd_wen.poke(true.B)
      dut.clock.step(1)

      // Read should return new value
      dut.io.rs1_addr.poke(10.U)
      dut.io.rs1_data.expect(0xFFFFFFFFL.U)
    }
  }

  it should "test all 16 registers of RV32E" in {
    test(new RegFile()) { dut =>
      // Write unique values to all 16 registers
      for (i <- 0 until 16) {
        dut.io.rd_addr.poke(i.U)
        dut.io.rd_data.poke((i * 0x11111111L).U)
        dut.io.rd_wen.poke(true.B)
        dut.clock.step(1)
      }

      // Verify all values (except x0 which is always 0)
      for (i <- 1 until 16) {
        dut.io.rs1_addr.poke(i.U)
        dut.io.rs1_data.expect((i * 0x11111111L).U)
      }

      // Verify x0 is still 0
      dut.io.rs1_addr.poke(0.U)
      dut.io.rs1_data.expect(0.U)
    }
  }
}

/**
 * Register File with Forwarding 测试
 */
class RegFileWithForwardingTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "RegFileWithForwarding"

  it should "forward data when reading the register being written" in {
    test(new RegFileWithForwarding()) { dut =>
      // Write to x5 and read it simultaneously
      dut.io.rd_addr.poke(5.U)
      dut.io.rd_data.poke(0xABCDABCDL.U)
      dut.io.rd_wen.poke(true.B)

      // Forwarding should provide the new data immediately
      dut.io.rs1_addr.poke(5.U)
      dut.io.rs1_data.expect(0xABCDABCDL.U)

      dut.io.rs2_addr.poke(5.U)
      dut.io.rs2_data.expect(0xABCDABCDL.U)
    }
  }

  it should "not forward when wen is false" in {
    test(new RegFileWithForwarding()) { dut =>
      // Initialize x7 with a value
      dut.io.rd_addr.poke(7.U)
      dut.io.rd_data.poke(0x11111111L.U)
      dut.io.rd_wen.poke(true.B)
      dut.clock.step(1)

      // Try to write with wen=false
      dut.io.rd_addr.poke(7.U)
      dut.io.rd_data.poke(0x22222222L.U)
      dut.io.rd_wen.poke(false.B)

      // Should read the old value (no forwarding)
      dut.io.rs1_addr.poke(7.U)
      dut.io.rs1_data.expect(0x11111111L.U)
    }
  }

  it should "forward to x0 should still return zero" in {
    test(new RegFileWithForwarding()) { dut =>
      // Try to write to x0 and read simultaneously
      dut.io.rd_addr.poke(0.U)
      dut.io.rd_data.poke(0x99999999L.U)
      dut.io.rd_wen.poke(true.B)

      // x0 should always be zero (forwarding should respect this)
      dut.io.rs1_addr.poke(0.U)
      dut.io.rs1_data.expect(0.U)

      dut.io.rs2_addr.poke(0.U)
      dut.io.rs2_data.expect(0.U)
    }
  }

  it should "forward to one port but not the other" in {
    test(new RegFileWithForwarding()) { dut =>
      // Initialize x3 and x4
      dut.io.rd_addr.poke(3.U)
      dut.io.rd_data.poke(0x33333333L.U)
      dut.io.rd_wen.poke(true.B)
      dut.clock.step(1)

      dut.io.rd_addr.poke(4.U)
      dut.io.rd_data.poke(0x44444444L.U)
      dut.io.rd_wen.poke(true.B)
      dut.clock.step(1)

      // Write to x3 and read x3 and x4
      dut.io.rd_addr.poke(3.U)
      dut.io.rd_data.poke(0xAAAAAAAAL.U)
      dut.io.rd_wen.poke(true.B)

      dut.io.rs1_addr.poke(3.U)  // Should forward
      dut.io.rs2_addr.poke(4.U)  // Should not forward

      dut.io.rs1_data.expect(0xAAAAAAAAL.U)
      dut.io.rs2_data.expect(0x44444444L.U)
    }
  }
}
