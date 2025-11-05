package rv32e.peripherals

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Test suite for SPI Flash Controller
 *
 * Note: Full SPI transaction tests would require many cycles.
 * These tests focus on register interface and control logic.
 */
class SpiFlashSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "SpiFlash"

  // Register addresses (word-addressed via wb_ctrl interface)
  val ADDR_CTRL = 0x00
  val ADDR_DIV  = 0x04
  val ADDR_ADDR = 0x08
  val ADDR_DATA = 0x0C

  // Control register bit positions
  val CTRL_START = 0
  val CTRL_BUSY  = 1
  val CTRL_DONE  = 2

  // ========== Reset and Initialization Tests ==========

  it should "initialize with default clock divider" in {
    test(new SpiFlash) { dut =>
      // Read DIV register
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(true.B)
      dut.io.wb_ctrl.we.poke(false.B)
      dut.io.wb_ctrl.adr.poke(ADDR_DIV.U)
      dut.clock.step(1)
      dut.io.wb_ctrl.ack.expect(true.B)
      dut.io.wb_ctrl.dat_i.expect(2.U)  // Default divider

      dut.io.wb_ctrl.cyc.poke(false.B)
      dut.io.wb_ctrl.stb.poke(false.B)
      dut.clock.step(1)
    }
  }

  it should "initialize with CS inactive (high)" in {
    test(new SpiFlash) { dut =>
      // CS should be high when idle
      dut.io.spi_cs_n.expect(true.B)
    }
  }

  it should "initialize with SCK low" in {
    test(new SpiFlash) { dut =>
      // SCK should be low initially
      dut.io.spi_sck.expect(false.B)
    }
  }

  it should "show idle state on reset" in {
    test(new SpiFlash) { dut =>
      // Read CTRL register
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(true.B)
      dut.io.wb_ctrl.we.poke(false.B)
      dut.io.wb_ctrl.adr.poke(ADDR_CTRL.U)
      dut.clock.step(1)

      val ctrl = dut.io.wb_ctrl.dat_i.peekInt()
      // start=0, busy=0, done=0
      ((ctrl >> CTRL_START) & 1) should be (0)
      ((ctrl >> CTRL_BUSY) & 1) should be (0)
      ((ctrl >> CTRL_DONE) & 1) should be (0)

      dut.io.wb_ctrl.cyc.poke(false.B)
      dut.io.wb_ctrl.stb.poke(false.B)
      dut.clock.step(1)
    }
  }

  // ========== Clock Divider Register Tests ==========

  it should "write and read DIV register" in {
    test(new SpiFlash) { dut =>
      val test_div = 10

      // Write DIV
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(true.B)
      dut.io.wb_ctrl.we.poke(true.B)
      dut.io.wb_ctrl.adr.poke(ADDR_DIV.U)
      dut.io.wb_ctrl.dat_o.poke(test_div.U)
      dut.clock.step(1)
      dut.io.wb_ctrl.ack.expect(true.B)

      dut.io.wb_ctrl.cyc.poke(false.B)
      dut.io.wb_ctrl.stb.poke(false.B)
      dut.clock.step(1)

      // Read back DIV
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(true.B)
      dut.io.wb_ctrl.we.poke(false.B)
      dut.io.wb_ctrl.adr.poke(ADDR_DIV.U)
      dut.clock.step(1)
      dut.io.wb_ctrl.ack.expect(true.B)
      dut.io.wb_ctrl.dat_i.expect(test_div.U)

      dut.io.wb_ctrl.cyc.poke(false.B)
      dut.io.wb_ctrl.stb.poke(false.B)
      dut.clock.step(1)
    }
  }

  it should "accept various clock divider values" in {
    test(new SpiFlash) { dut =>
      val test_values = Seq(1, 2, 4, 8, 16, 50, 100, 255)

      for (div <- test_values) {
        // Write DIV
        dut.io.wb_ctrl.cyc.poke(true.B)
        dut.io.wb_ctrl.stb.poke(true.B)
        dut.io.wb_ctrl.we.poke(true.B)
        dut.io.wb_ctrl.adr.poke(ADDR_DIV.U)
        dut.io.wb_ctrl.dat_o.poke(div.U)
        dut.clock.step(1)

        dut.io.wb_ctrl.cyc.poke(false.B)
        dut.io.wb_ctrl.stb.poke(false.B)
        dut.clock.step(1)

        // Read back
        dut.io.wb_ctrl.cyc.poke(true.B)
        dut.io.wb_ctrl.stb.poke(true.B)
        dut.io.wb_ctrl.we.poke(false.B)
        dut.io.wb_ctrl.adr.poke(ADDR_DIV.U)
        dut.clock.step(1)
        dut.io.wb_ctrl.dat_i.expect(div.U)

        dut.io.wb_ctrl.cyc.poke(false.B)
        dut.io.wb_ctrl.stb.poke(false.B)
        dut.clock.step(1)
      }
    }
  }

  // ========== Address Register Tests ==========

  it should "write and read ADDR register" in {
    test(new SpiFlash) { dut =>
      val test_addr = 0x123456  // 24-bit address

      // Write ADDR
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(true.B)
      dut.io.wb_ctrl.we.poke(true.B)
      dut.io.wb_ctrl.adr.poke(ADDR_ADDR.U)
      dut.io.wb_ctrl.dat_o.poke(test_addr.U)
      dut.clock.step(1)
      dut.io.wb_ctrl.ack.expect(true.B)

      dut.io.wb_ctrl.cyc.poke(false.B)
      dut.io.wb_ctrl.stb.poke(false.B)
      dut.clock.step(1)

      // Read back ADDR
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(true.B)
      dut.io.wb_ctrl.we.poke(false.B)
      dut.io.wb_ctrl.adr.poke(ADDR_ADDR.U)
      dut.clock.step(1)
      dut.io.wb_ctrl.ack.expect(true.B)
      dut.io.wb_ctrl.dat_i.expect(test_addr.U)

      dut.io.wb_ctrl.cyc.poke(false.B)
      dut.io.wb_ctrl.stb.poke(false.B)
      dut.clock.step(1)
    }
  }

  it should "store only 24 bits of address" in {
    test(new SpiFlash) { dut =>
      val test_addr = 0xFF123456  // More than 24 bits

      // Write ADDR
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(true.B)
      dut.io.wb_ctrl.we.poke(true.B)
      dut.io.wb_ctrl.adr.poke(ADDR_ADDR.U)
      dut.io.wb_ctrl.dat_o.poke(test_addr.U)
      dut.clock.step(1)

      dut.io.wb_ctrl.cyc.poke(false.B)
      dut.io.wb_ctrl.stb.poke(false.B)
      dut.clock.step(1)

      // Read back ADDR (should be masked to 24 bits)
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(true.B)
      dut.io.wb_ctrl.we.poke(false.B)
      dut.io.wb_ctrl.adr.poke(ADDR_ADDR.U)
      dut.clock.step(1)
      dut.io.wb_ctrl.dat_i.expect(0x123456.U)  // Upper bits masked

      dut.io.wb_ctrl.cyc.poke(false.B)
      dut.io.wb_ctrl.stb.poke(false.B)
      dut.clock.step(1)
    }
  }

  // ========== Control Register Tests ==========

  it should "set start bit when written to CTRL" in {
    test(new SpiFlash) { dut =>
      // Write start bit
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(true.B)
      dut.io.wb_ctrl.we.poke(true.B)
      dut.io.wb_ctrl.adr.poke(ADDR_CTRL.U)
      dut.io.wb_ctrl.dat_o.poke(0x01.U)  // Set start bit
      dut.clock.step(1)

      dut.io.wb_ctrl.cyc.poke(false.B)
      dut.io.wb_ctrl.stb.poke(false.B)
      dut.clock.step(1)

      // Read CTRL - should show busy
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(true.B)
      dut.io.wb_ctrl.we.poke(false.B)
      dut.io.wb_ctrl.adr.poke(ADDR_CTRL.U)
      dut.clock.step(1)

      val ctrl = dut.io.wb_ctrl.dat_i.peekInt()
      // busy bit should be set
      ((ctrl >> CTRL_BUSY) & 1) should be (1)

      dut.io.wb_ctrl.cyc.poke(false.B)
      dut.io.wb_ctrl.stb.poke(false.B)
      dut.clock.step(1)
    }
  }

  it should "activate CS when transaction starts" in {
    test(new SpiFlash) { dut =>
      // CS should be high (inactive) initially
      dut.io.spi_cs_n.expect(true.B)

      // Set address
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(true.B)
      dut.io.wb_ctrl.we.poke(true.B)
      dut.io.wb_ctrl.adr.poke(ADDR_ADDR.U)
      dut.io.wb_ctrl.dat_o.poke(0x100000.U)
      dut.clock.step(1)
      dut.io.wb_ctrl.cyc.poke(false.B)
      dut.io.wb_ctrl.stb.poke(false.B)
      dut.clock.step(1)

      // Start transaction
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(true.B)
      dut.io.wb_ctrl.we.poke(true.B)
      dut.io.wb_ctrl.adr.poke(ADDR_CTRL.U)
      dut.io.wb_ctrl.dat_o.poke(0x01.U)  // Set start
      dut.clock.step(1)
      dut.io.wb_ctrl.cyc.poke(false.B)
      dut.io.wb_ctrl.stb.poke(false.B)
      dut.clock.step(1)

      // CS should go low (active)
      dut.io.spi_cs_n.expect(false.B)
    }
  }

  it should "transition through states after start" in {
    test(new SpiFlash) { dut =>
      // Set small clock divider for faster test
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(true.B)
      dut.io.wb_ctrl.we.poke(true.B)
      dut.io.wb_ctrl.adr.poke(ADDR_DIV.U)
      dut.io.wb_ctrl.dat_o.poke(1.U)
      dut.clock.step(1)
      dut.io.wb_ctrl.cyc.poke(false.B)
      dut.io.wb_ctrl.stb.poke(false.B)
      dut.clock.step(1)

      // Set address
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(true.B)
      dut.io.wb_ctrl.we.poke(true.B)
      dut.io.wb_ctrl.adr.poke(ADDR_ADDR.U)
      dut.io.wb_ctrl.dat_o.poke(0xABCDEF.U)
      dut.clock.step(1)
      dut.io.wb_ctrl.cyc.poke(false.B)
      dut.io.wb_ctrl.stb.poke(false.B)
      dut.clock.step(1)

      // Start transaction
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(true.B)
      dut.io.wb_ctrl.we.poke(true.B)
      dut.io.wb_ctrl.adr.poke(ADDR_CTRL.U)
      dut.io.wb_ctrl.dat_o.poke(0x01.U)
      dut.clock.step(1)
      dut.io.wb_ctrl.cyc.poke(false.B)
      dut.io.wb_ctrl.stb.poke(false.B)
      dut.clock.step(1)

      // CS should be active
      dut.io.spi_cs_n.expect(false.B)

      // Clock should start toggling
      var clk_toggled = false
      for (_ <- 0 until 10) {
        val sck = dut.io.spi_sck.peekBoolean()
        dut.clock.step(1)
        if (sck != dut.io.spi_sck.peekBoolean()) {
          clk_toggled = true
        }
      }

      clk_toggled should be (true)
    }
  }

  // ========== DATA Register Tests ==========

  it should "read zero from DATA register initially" in {
    test(new SpiFlash) { dut =>
      // Read DATA
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(true.B)
      dut.io.wb_ctrl.we.poke(false.B)
      dut.io.wb_ctrl.adr.poke(ADDR_DATA.U)
      dut.clock.step(1)
      dut.io.wb_ctrl.ack.expect(true.B)
      dut.io.wb_ctrl.dat_i.expect(0.U)

      dut.io.wb_ctrl.cyc.poke(false.B)
      dut.io.wb_ctrl.stb.poke(false.B)
      dut.clock.step(1)
    }
  }

  // ========== Wishbone Protocol Tests ==========

  it should "acknowledge wb_ctrl transactions on next cycle" in {
    test(new SpiFlash) { dut =>
      // Start transaction
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(true.B)
      dut.io.wb_ctrl.we.poke(false.B)
      dut.io.wb_ctrl.adr.poke(ADDR_CTRL.U)

      // ACK should be low before clock
      dut.io.wb_ctrl.ack.expect(false.B)

      dut.clock.step(1)

      // ACK should be high after clock
      dut.io.wb_ctrl.ack.expect(true.B)

      dut.io.wb_ctrl.cyc.poke(false.B)
      dut.io.wb_ctrl.stb.poke(false.B)
      dut.clock.step(1)

      // ACK should be low after transaction
      dut.io.wb_ctrl.ack.expect(false.B)
    }
  }

  it should "not acknowledge wb_ctrl without strobe" in {
    test(new SpiFlash) { dut =>
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(false.B)  // No strobe
      dut.io.wb_ctrl.we.poke(false.B)
      dut.io.wb_ctrl.adr.poke(ADDR_CTRL.U)
      dut.clock.step(1)

      // Should not acknowledge
      dut.io.wb_ctrl.ack.expect(false.B)
    }
  }

  it should "acknowledge wb_mem transactions (XIP stub)" in {
    test(new SpiFlash) { dut =>
      // Memory interface is stubbed, but should still ACK
      dut.io.wb_mem.cyc.poke(true.B)
      dut.io.wb_mem.stb.poke(true.B)
      dut.io.wb_mem.we.poke(false.B)
      dut.io.wb_mem.adr.poke(0x1000.U)

      dut.io.wb_mem.ack.expect(false.B)

      dut.clock.step(1)

      // Should acknowledge
      dut.io.wb_mem.ack.expect(true.B)
      dut.io.wb_mem.dat_i.expect(0.U)  // Returns zero (stub)

      dut.io.wb_mem.cyc.poke(false.B)
      dut.io.wb_mem.stb.poke(false.B)
      dut.clock.step(1)

      dut.io.wb_mem.ack.expect(false.B)
    }
  }

  it should "handle back-to-back register accesses" in {
    test(new SpiFlash) { dut =>
      val operations = Seq(
        (true, ADDR_DIV, 5),
        (false, ADDR_DIV, 0),
        (true, ADDR_ADDR, 0x100000),
        (false, ADDR_ADDR, 0)
      )

      for ((is_write, addr, data) <- operations) {
        dut.io.wb_ctrl.cyc.poke(true.B)
        dut.io.wb_ctrl.stb.poke(true.B)
        dut.io.wb_ctrl.we.poke(is_write.B)
        dut.io.wb_ctrl.adr.poke(addr.U)
        if (is_write) {
          dut.io.wb_ctrl.dat_o.poke(data.U)
        }
        dut.clock.step(1)
        dut.io.wb_ctrl.ack.expect(true.B)

        dut.io.wb_ctrl.cyc.poke(false.B)
        dut.io.wb_ctrl.stb.poke(false.B)
        dut.clock.step(1)
      }
    }
  }

  // ========== Integration Test ==========

  it should "configure flash read transaction" in {
    test(new SpiFlash) { dut =>
      // 1. Set clock divider
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(true.B)
      dut.io.wb_ctrl.we.poke(true.B)
      dut.io.wb_ctrl.adr.poke(ADDR_DIV.U)
      dut.io.wb_ctrl.dat_o.poke(4.U)
      dut.clock.step(1)
      dut.io.wb_ctrl.cyc.poke(false.B)
      dut.io.wb_ctrl.stb.poke(false.B)
      dut.clock.step(1)

      // 2. Set flash address
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(true.B)
      dut.io.wb_ctrl.we.poke(true.B)
      dut.io.wb_ctrl.adr.poke(ADDR_ADDR.U)
      dut.io.wb_ctrl.dat_o.poke(0x200000.U)
      dut.clock.step(1)
      dut.io.wb_ctrl.cyc.poke(false.B)
      dut.io.wb_ctrl.stb.poke(false.B)
      dut.clock.step(1)

      // 3. Check status (should be idle)
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(true.B)
      dut.io.wb_ctrl.we.poke(false.B)
      dut.io.wb_ctrl.adr.poke(ADDR_CTRL.U)
      dut.clock.step(1)
      val ctrl_before = dut.io.wb_ctrl.dat_i.peekInt()
      ((ctrl_before >> CTRL_BUSY) & 1) should be (0)
      dut.io.wb_ctrl.cyc.poke(false.B)
      dut.io.wb_ctrl.stb.poke(false.B)
      dut.clock.step(1)

      // 4. Start transaction
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(true.B)
      dut.io.wb_ctrl.we.poke(true.B)
      dut.io.wb_ctrl.adr.poke(ADDR_CTRL.U)
      dut.io.wb_ctrl.dat_o.poke(0x01.U)  // Start
      dut.clock.step(1)
      dut.io.wb_ctrl.cyc.poke(false.B)
      dut.io.wb_ctrl.stb.poke(false.B)
      dut.clock.step(1)

      // 5. Check status (should be busy)
      dut.io.wb_ctrl.cyc.poke(true.B)
      dut.io.wb_ctrl.stb.poke(true.B)
      dut.io.wb_ctrl.we.poke(false.B)
      dut.io.wb_ctrl.adr.poke(ADDR_CTRL.U)
      dut.clock.step(1)
      val ctrl_after = dut.io.wb_ctrl.dat_i.peekInt()
      ((ctrl_after >> CTRL_BUSY) & 1) should be (1)
      dut.io.wb_ctrl.cyc.poke(false.B)
      dut.io.wb_ctrl.stb.poke(false.B)
      dut.clock.step(1)

      // 6. Verify CS is active
      dut.io.spi_cs_n.expect(false.B)
    }
  }
}
