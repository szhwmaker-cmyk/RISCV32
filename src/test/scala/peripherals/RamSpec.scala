package rv32e.peripherals

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec

/**
 * Test suite for RAM module
 *
 * Tests synchronous RAM with Wishbone interface:
 * - Word read/write (sel=1111)
 * - Halfword read/write (sel=0011, sel=1100)
 * - Byte read/write (sel=0001, sel=0010, sel=0100, sel=1000)
 * - Address boundary conditions
 * - Data persistence across cycles
 * - Wishbone protocol compliance
 */
class RamSpec extends AnyFreeSpec with ChiselScalatestTester {

  "Ram" - {

    "should write and read back a full word (sel=1111)" in {
      test(new Ram) { dut =>
        val test_addr = 0x00000100.U
        val test_data = 0xDEADBEEF.U

        // Write word
        dut.io.wb.adr.poke(test_addr)
        dut.io.wb.dat_o.poke(test_data)
        dut.io.wb.sel.poke("b1111".U)  // All bytes
        dut.io.wb.we.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)

        // Wait for ACK
        dut.io.wb.ack.expect(true.B, "RAM should ACK write")
        dut.io.wb.stb.poke(false.B)
        dut.io.wb.cyc.poke(false.B)
        dut.clock.step(1)

        // Read back
        dut.io.wb.adr.poke(test_addr)
        dut.io.wb.sel.poke("b1111".U)
        dut.io.wb.we.poke(false.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)

        dut.io.wb.ack.expect(true.B, "RAM should ACK read")
        dut.io.wb.dat_i.expect(test_data, "Should read back written data")
      }
    }

    "should handle byte write (sel=0001)" in {
      test(new Ram) { dut =>
        val test_addr = 0x00000200.U

        // Write full word first
        dut.io.wb.adr.poke(test_addr)
        dut.io.wb.dat_o.poke(0x00000000.U)
        dut.io.wb.sel.poke("b1111".U)
        dut.io.wb.we.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.stb.poke(false.B)
        dut.io.wb.cyc.poke(false.B)
        dut.clock.step(1)

        // Write only lowest byte (sel=0001)
        dut.io.wb.adr.poke(test_addr)
        dut.io.wb.dat_o.poke(0x12345678.U)
        dut.io.wb.sel.poke("b0001".U)  // Only byte 0
        dut.io.wb.we.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.stb.poke(false.B)
        dut.io.wb.cyc.poke(false.B)
        dut.clock.step(1)

        // Read back - should only have byte 0 modified
        dut.io.wb.adr.poke(test_addr)
        dut.io.wb.we.poke(false.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)

        dut.io.wb.dat_i.expect(0x00000078.U, "Only byte 0 should be written")
      }
    }

    "should handle byte write (sel=0010)" in {
      test(new Ram) { dut =>
        val test_addr = 0x00000204.U

        // Initialize with zeros
        dut.io.wb.adr.poke(test_addr)
        dut.io.wb.dat_o.poke(0x00000000.U)
        dut.io.wb.sel.poke("b1111".U)
        dut.io.wb.we.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.stb.poke(false.B)
        dut.io.wb.cyc.poke(false.B)
        dut.clock.step(1)

        // Write only byte 1 (sel=0010)
        dut.io.wb.adr.poke(test_addr)
        dut.io.wb.dat_o.poke(0xAABBCCDD.U)
        dut.io.wb.sel.poke("b0010".U)  // Only byte 1
        dut.io.wb.we.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.stb.poke(false.B)
        dut.io.wb.cyc.poke(false.B)
        dut.clock.step(1)

        // Read back
        dut.io.wb.adr.poke(test_addr)
        dut.io.wb.we.poke(false.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)

        dut.io.wb.dat_i.expect(0x0000CC00.U, "Only byte 1 should be written")
      }
    }

    "should handle halfword write (sel=0011)" in {
      test(new Ram) { dut =>
        val test_addr = 0x00000300.U

        // Initialize
        dut.io.wb.adr.poke(test_addr)
        dut.io.wb.dat_o.poke(0xFFFFFFFF.U)
        dut.io.wb.sel.poke("b1111".U)
        dut.io.wb.we.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.stb.poke(false.B)
        dut.io.wb.cyc.poke(false.B)
        dut.clock.step(1)

        // Write lower halfword (sel=0011)
        dut.io.wb.adr.poke(test_addr)
        dut.io.wb.dat_o.poke(0x12345678.U)
        dut.io.wb.sel.poke("b0011".U)  // Lower halfword
        dut.io.wb.we.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.stb.poke(false.B)
        dut.io.wb.cyc.poke(false.B)
        dut.clock.step(1)

        // Read back
        dut.io.wb.adr.poke(test_addr)
        dut.io.wb.we.poke(false.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)

        dut.io.wb.dat_i.expect(0xFFFF5678.U, "Only lower halfword should be written")
      }
    }

    "should handle halfword write (sel=1100)" in {
      test(new Ram) { dut =>
        val test_addr = 0x00000304.U

        // Initialize
        dut.io.wb.adr.poke(test_addr)
        dut.io.wb.dat_o.poke(0x00000000.U)
        dut.io.wb.sel.poke("b1111".U)
        dut.io.wb.we.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.stb.poke(false.B)
        dut.io.wb.cyc.poke(false.B)
        dut.clock.step(1)

        // Write upper halfword (sel=1100)
        dut.io.wb.adr.poke(test_addr)
        dut.io.wb.dat_o.poke(0xABCDEF12.U)
        dut.io.wb.sel.poke("b1100".U)  // Upper halfword
        dut.io.wb.we.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.stb.poke(false.B)
        dut.io.wb.cyc.poke(false.B)
        dut.clock.step(1)

        // Read back
        dut.io.wb.adr.poke(test_addr)
        dut.io.wb.we.poke(false.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)

        dut.io.wb.dat_i.expect(0xABCD0000.U, "Only upper halfword should be written")
      }
    }

    "should maintain data across multiple cycles" in {
      test(new Ram) { dut =>
        val test_addr = 0x00001000.U
        val test_data = 0xCAFEBABE.U

        // Write data
        dut.io.wb.adr.poke(test_addr)
        dut.io.wb.dat_o.poke(test_data)
        dut.io.wb.sel.poke("b1111".U)
        dut.io.wb.we.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.stb.poke(false.B)
        dut.io.wb.cyc.poke(false.B)

        // Wait many cycles
        dut.clock.step(100)

        // Read back - data should persist
        dut.io.wb.adr.poke(test_addr)
        dut.io.wb.we.poke(false.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)

        dut.io.wb.dat_i.expect(test_data, "Data should persist across cycles")
      }
    }

    "should handle different addresses independently" in {
      test(new Ram) { dut =>
        val addr1 = 0x00000100.U
        val addr2 = 0x00000200.U
        val addr3 = 0x00000300.U

        val data1 = 0x11111111.U
        val data2 = 0x22222222.U
        val data3 = 0x33333333.U

        // Write to addr1
        dut.io.wb.adr.poke(addr1)
        dut.io.wb.dat_o.poke(data1)
        dut.io.wb.sel.poke("b1111".U)
        dut.io.wb.we.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.stb.poke(false.B)
        dut.io.wb.cyc.poke(false.B)
        dut.clock.step(1)

        // Write to addr2
        dut.io.wb.adr.poke(addr2)
        dut.io.wb.dat_o.poke(data2)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.stb.poke(false.B)
        dut.io.wb.cyc.poke(false.B)
        dut.clock.step(1)

        // Write to addr3
        dut.io.wb.adr.poke(addr3)
        dut.io.wb.dat_o.poke(data3)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.stb.poke(false.B)
        dut.io.wb.cyc.poke(false.B)
        dut.clock.step(1)

        // Read back in different order
        dut.io.wb.adr.poke(addr2)
        dut.io.wb.we.poke(false.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.dat_i.expect(data2)
        dut.io.wb.stb.poke(false.B)
        dut.io.wb.cyc.poke(false.B)
        dut.clock.step(1)

        dut.io.wb.adr.poke(addr3)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.dat_i.expect(data3)
        dut.io.wb.stb.poke(false.B)
        dut.io.wb.cyc.poke(false.B)
        dut.clock.step(1)

        dut.io.wb.adr.poke(addr1)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.dat_i.expect(data1)
      }
    }

    "should respect write enable signal" in {
      test(new Ram) { dut =>
        val test_addr = 0x00000400.U

        // Initialize with known value
        dut.io.wb.adr.poke(test_addr)
        dut.io.wb.dat_o.poke(0xAAAAAAAA.U)
        dut.io.wb.sel.poke("b1111".U)
        dut.io.wb.we.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.stb.poke(false.B)
        dut.io.wb.cyc.poke(false.B)
        dut.clock.step(1)

        // Attempt write with we=0 (should not write)
        dut.io.wb.adr.poke(test_addr)
        dut.io.wb.dat_o.poke(0xBBBBBBBB.U)
        dut.io.wb.we.poke(false.B)  // Write disabled
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.stb.poke(false.B)
        dut.io.wb.cyc.poke(false.B)
        dut.clock.step(1)

        // Read back - should still have original value
        dut.io.wb.adr.poke(test_addr)
        dut.io.wb.we.poke(false.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)

        dut.io.wb.dat_i.expect(0xAAAAAAAA.U, "Data should not change when we=0")
      }
    }

    "should not respond when cyc=0" in {
      test(new Ram) { dut =>
        val test_addr = 0x00000500.U

        // Try to write with cyc=0
        dut.io.wb.adr.poke(test_addr)
        dut.io.wb.dat_o.poke(0x12345678.U)
        dut.io.wb.sel.poke("b1111".U)
        dut.io.wb.we.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(false.B)  // cyc not active
        dut.clock.step(1)

        // Should not ACK
        dut.io.wb.ack.expect(false.B, "Should not ACK when cyc=0")
      }
    }

    "should not respond when stb=0" in {
      test(new Ram) { dut =>
        val test_addr = 0x00000600.U

        // Try to write with stb=0
        dut.io.wb.adr.poke(test_addr)
        dut.io.wb.dat_o.poke(0xABCDEF12.U)
        dut.io.wb.sel.poke("b1111".U)
        dut.io.wb.we.poke(true.B)
        dut.io.wb.stb.poke(false.B)  // stb not active
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)

        // Should not ACK
        dut.io.wb.ack.expect(false.B, "Should not ACK when stb=0")
      }
    }

    "should handle back-to-back writes" in {
      test(new Ram) { dut =>
        val addr1 = 0x00001000.U
        val addr2 = 0x00001004.U
        val addr3 = 0x00001008.U

        // Write 1
        dut.io.wb.adr.poke(addr1)
        dut.io.wb.dat_o.poke(0x11111111.U)
        dut.io.wb.sel.poke("b1111".U)
        dut.io.wb.we.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.ack.expect(true.B)

        // Write 2 (immediately after ACK)
        dut.io.wb.adr.poke(addr2)
        dut.io.wb.dat_o.poke(0x22222222.U)
        dut.clock.step(1)
        dut.io.wb.ack.expect(true.B)

        // Write 3
        dut.io.wb.adr.poke(addr3)
        dut.io.wb.dat_o.poke(0x33333333.U)
        dut.clock.step(1)
        dut.io.wb.ack.expect(true.B)

        // Deassert
        dut.io.wb.stb.poke(false.B)
        dut.io.wb.cyc.poke(false.B)
        dut.clock.step(1)

        // Verify all writes succeeded
        dut.io.wb.adr.poke(addr1)
        dut.io.wb.we.poke(false.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.dat_i.expect(0x11111111.U)
        dut.io.wb.stb.poke(false.B)
        dut.io.wb.cyc.poke(false.B)
        dut.clock.step(1)

        dut.io.wb.adr.poke(addr2)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.dat_i.expect(0x22222222.U)
        dut.io.wb.stb.poke(false.B)
        dut.io.wb.cyc.poke(false.B)
        dut.clock.step(1)

        dut.io.wb.adr.poke(addr3)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.dat_i.expect(0x33333333.U)
      }
    }

    "should handle address boundaries (start of RAM)" in {
      test(new Ram) { dut =>
        val first_addr = 0x00000000.U
        val test_data = 0xFEEDFACE.U

        // Write to first address
        dut.io.wb.adr.poke(first_addr)
        dut.io.wb.dat_o.poke(test_data)
        dut.io.wb.sel.poke("b1111".U)
        dut.io.wb.we.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.ack.expect(true.B)
        dut.io.wb.stb.poke(false.B)
        dut.io.wb.cyc.poke(false.B)
        dut.clock.step(1)

        // Read back
        dut.io.wb.adr.poke(first_addr)
        dut.io.wb.we.poke(false.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)
        dut.io.wb.dat_i.expect(test_data)
      }
    }

    "should handle sequential address pattern" in {
      test(new Ram) { dut =>
        val base_addr = 0x00002000.U
        val num_words = 16

        // Write sequential pattern
        for (i <- 0 until num_words) {
          dut.io.wb.adr.poke((base_addr.litValue + i * 4).U)
          dut.io.wb.dat_o.poke((0x1000 + i).U)
          dut.io.wb.sel.poke("b1111".U)
          dut.io.wb.we.poke(true.B)
          dut.io.wb.stb.poke(true.B)
          dut.io.wb.cyc.poke(true.B)
          dut.clock.step(1)
          dut.io.wb.stb.poke(false.B)
          dut.io.wb.cyc.poke(false.B)
          dut.clock.step(1)
        }

        // Read back and verify
        for (i <- 0 until num_words) {
          dut.io.wb.adr.poke((base_addr.litValue + i * 4).U)
          dut.io.wb.we.poke(false.B)
          dut.io.wb.stb.poke(true.B)
          dut.io.wb.cyc.poke(true.B)
          dut.clock.step(1)
          dut.io.wb.dat_i.expect((0x1000 + i).U, s"Word $i mismatch")
          dut.io.wb.stb.poke(false.B)
          dut.io.wb.cyc.poke(false.B)
          dut.clock.step(1)
        }
      }
    }

    "should ACK deassert after transaction complete" in {
      test(new Ram) { dut =>
        val test_addr = 0x00003000.U

        // Perform write
        dut.io.wb.adr.poke(test_addr)
        dut.io.wb.dat_o.poke(0x99999999.U)
        dut.io.wb.sel.poke("b1111".U)
        dut.io.wb.we.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.cyc.poke(true.B)
        dut.clock.step(1)

        // ACK should be high
        dut.io.wb.ack.expect(true.B)

        // End transaction
        dut.io.wb.stb.poke(false.B)
        dut.io.wb.cyc.poke(false.B)
        dut.clock.step(1)

        // ACK should deassert
        dut.io.wb.ack.expect(false.B, "ACK should deassert when stb=0")
      }
    }
  }
}
