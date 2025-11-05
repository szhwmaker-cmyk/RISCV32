package rv32e.peripherals

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import rv32e.Config

/**
 * Test suite for GPIO Controller
 */
class GpioSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "Gpio"

  // Register addresses (word-addressed, so shift by 2)
  val ADDR_DATA_IN  = 0x00
  val ADDR_DATA_OUT = 0x04
  val ADDR_DIR      = 0x08
  val ADDR_OE       = 0x0C

  // ========== Reset and Initialization Tests ==========

  it should "initialize all registers to zero" in {
    test(new Gpio) { dut =>
      // After reset, all registers should be 0
      dut.clock.step(1)

      // Check outputs
      dut.io.gpio_out.expect(0.U)
      dut.io.gpio_oe.expect(0.U)

      // Read all registers via Wishbone
      def readReg(addr: Int): Unit = {
        dut.io.wb.cyc.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.we.poke(false.B)
        dut.io.wb.adr.poke(addr.U)
        dut.clock.step(1)
        dut.io.wb.ack.expect(true.B)
        dut.io.wb.dat_i.expect(0.U)
        dut.io.wb.cyc.poke(false.B)
        dut.io.wb.stb.poke(false.B)
        dut.clock.step(1)
      }

      readReg(ADDR_DATA_OUT)
      readReg(ADDR_DIR)
      readReg(ADDR_OE)
    }
  }

  // ========== DATA_OUT Register Tests ==========

  it should "write and read DATA_OUT register" in {
    test(new Gpio) { dut =>
      val testValue = 0x5A5A

      // Write DATA_OUT
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_DATA_OUT.U)
      dut.io.wb.dat_o.poke(testValue.U)
      dut.clock.step(1)
      dut.io.wb.ack.expect(true.B)

      // End transaction
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // Check gpio_out reflects the value
      dut.io.gpio_out.expect(testValue.U)

      // Read back DATA_OUT
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(ADDR_DATA_OUT.U)
      dut.clock.step(1)
      dut.io.wb.ack.expect(true.B)
      dut.io.wb.dat_i.expect(testValue.U)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
    }
  }

  it should "update gpio_out when DATA_OUT is written" in {
    test(new Gpio) { dut =>
      val values = Seq(0x0001, 0xFFFF, 0xAAAA, 0x5555, 0x0F0F, 0xF0F0)

      for (value <- values) {
        // Write DATA_OUT
        dut.io.wb.cyc.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.we.poke(true.B)
        dut.io.wb.adr.poke(ADDR_DATA_OUT.U)
        dut.io.wb.dat_o.poke(value.U)
        dut.clock.step(1)

        // End transaction
        dut.io.wb.cyc.poke(false.B)
        dut.io.wb.stb.poke(false.B)
        dut.clock.step(1)

        // Verify gpio_out
        dut.io.gpio_out.expect(value.U)
      }
    }
  }

  // ========== DIR Register Tests ==========

  it should "write and read DIR register" in {
    test(new Gpio) { dut =>
      val testValue = 0x00FF  // Lower 8 bits as output

      // Write DIR
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_DIR.U)
      dut.io.wb.dat_o.poke(testValue.U)
      dut.clock.step(1)
      dut.io.wb.ack.expect(true.B)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // Read back DIR
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(ADDR_DIR.U)
      dut.clock.step(1)
      dut.io.wb.ack.expect(true.B)
      dut.io.wb.dat_i.expect(testValue.U)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
    }
  }

  // ========== OE Register Tests ==========

  it should "write and read OE register" in {
    test(new Gpio) { dut =>
      val testValue = 0xFF00  // Upper 8 bits enabled

      // Write OE
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_OE.U)
      dut.io.wb.dat_o.poke(testValue.U)
      dut.clock.step(1)
      dut.io.wb.ack.expect(true.B)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // Read back OE
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(ADDR_OE.U)
      dut.clock.step(1)
      dut.io.wb.ack.expect(true.B)
      dut.io.wb.dat_i.expect(testValue.U)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
    }
  }

  // ========== DATA_IN Register Tests ==========

  it should "read physical input pins via DATA_IN" in {
    test(new Gpio) { dut =>
      val testValue = 0x1234

      // Set physical input
      dut.io.gpio_in.poke(testValue.U)
      dut.clock.step(1)

      // Read DATA_IN
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(ADDR_DATA_IN.U)
      dut.clock.step(1)
      dut.io.wb.ack.expect(true.B)
      dut.io.wb.dat_i.expect(testValue.U)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
    }
  }

  it should "reflect changing input pins in DATA_IN reads" in {
    test(new Gpio) { dut =>
      val testValues = Seq(0x0000, 0xFFFF, 0xA5A5, 0x5A5A)

      for (value <- testValues) {
        dut.io.gpio_in.poke(value.U)
        dut.clock.step(1)

        // Read DATA_IN
        dut.io.wb.cyc.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.we.poke(false.B)
        dut.io.wb.adr.poke(ADDR_DATA_IN.U)
        dut.clock.step(1)
        dut.io.wb.dat_i.expect(value.U)

        dut.io.wb.cyc.poke(false.B)
        dut.io.wb.stb.poke(false.B)
        dut.clock.step(1)
      }
    }
  }

  // ========== Output Enable Logic Tests ==========

  it should "compute gpio_oe as DIR & OE" in {
    test(new Gpio) { dut =>
      // Set DIR = 0xFF00 (upper 8 bits as output)
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_DIR.U)
      dut.io.wb.dat_o.poke(0xFF00.U)
      dut.clock.step(1)
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // Set OE = 0xF0F0
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_OE.U)
      dut.io.wb.dat_o.poke(0xF0F0.U)
      dut.clock.step(1)
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // gpio_oe should be 0xFF00 & 0xF0F0 = 0xF000
      dut.io.gpio_oe.expect(0xF000.U)
    }
  }

  it should "disable output when DIR is 0" in {
    test(new Gpio) { dut =>
      // Set OE = 0xFFFF
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_OE.U)
      dut.io.wb.dat_o.poke(0xFFFF.U)
      dut.clock.step(1)
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // Set DIR = 0x0000 (all inputs)
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_DIR.U)
      dut.io.wb.dat_o.poke(0x0000.U)
      dut.clock.step(1)
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // gpio_oe should be 0 (outputs disabled)
      dut.io.gpio_oe.expect(0x0000.U)
    }
  }

  it should "disable output when OE is 0" in {
    test(new Gpio) { dut =>
      // Set DIR = 0xFFFF
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_DIR.U)
      dut.io.wb.dat_o.poke(0xFFFF.U)
      dut.clock.step(1)
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // Set OE = 0x0000
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_OE.U)
      dut.io.wb.dat_o.poke(0x0000.U)
      dut.clock.step(1)
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // gpio_oe should be 0 (outputs disabled)
      dut.io.gpio_oe.expect(0x0000.U)
    }
  }

  it should "enable output only when both DIR and OE are set" in {
    test(new Gpio) { dut =>
      // Test all combinations for bit 0
      val testCases = Seq(
        (0x0000, 0x0000, 0x0000),  // dir=0, oe=0 -> gpio_oe=0
        (0x0001, 0x0000, 0x0000),  // dir=1, oe=0 -> gpio_oe=0
        (0x0000, 0x0001, 0x0000),  // dir=0, oe=1 -> gpio_oe=0
        (0x0001, 0x0001, 0x0001)   // dir=1, oe=1 -> gpio_oe=1
      )

      for ((dir, oe, expected_oe) <- testCases) {
        // Set DIR
        dut.io.wb.cyc.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.we.poke(true.B)
        dut.io.wb.adr.poke(ADDR_DIR.U)
        dut.io.wb.dat_o.poke(dir.U)
        dut.clock.step(1)
        dut.io.wb.cyc.poke(false.B)
        dut.io.wb.stb.poke(false.B)
        dut.clock.step(1)

        // Set OE
        dut.io.wb.cyc.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.we.poke(true.B)
        dut.io.wb.adr.poke(ADDR_OE.U)
        dut.io.wb.dat_o.poke(oe.U)
        dut.clock.step(1)
        dut.io.wb.cyc.poke(false.B)
        dut.io.wb.stb.poke(false.B)
        dut.clock.step(1)

        // Check gpio_oe
        dut.io.gpio_oe.expect(expected_oe.U)
      }
    }
  }

  // ========== Wishbone Protocol Tests ==========

  it should "acknowledge transactions on the next cycle" in {
    test(new Gpio) { dut =>
      // Start write transaction
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_DATA_OUT.U)
      dut.io.wb.dat_o.poke(0x1234.U)

      // ACK should be low before clock edge
      dut.io.wb.ack.expect(false.B)

      dut.clock.step(1)

      // ACK should be high after clock edge
      dut.io.wb.ack.expect(true.B)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // ACK should be low after transaction ends
      dut.io.wb.ack.expect(false.B)
    }
  }

  it should "not acknowledge without strobe" in {
    test(new Gpio) { dut =>
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(false.B)  // No strobe
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(ADDR_DATA_OUT.U)
      dut.clock.step(1)

      // Should not acknowledge
      dut.io.wb.ack.expect(false.B)
    }
  }

  it should "handle back-to-back transactions" in {
    test(new Gpio) { dut =>
      val values = Seq(0x1111, 0x2222, 0x3333, 0x4444)

      for (value <- values) {
        // Write transaction
        dut.io.wb.cyc.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.we.poke(true.B)
        dut.io.wb.adr.poke(ADDR_DATA_OUT.U)
        dut.io.wb.dat_o.poke(value.U)
        dut.clock.step(1)
        dut.io.wb.ack.expect(true.B)

        // End transaction
        dut.io.wb.cyc.poke(false.B)
        dut.io.wb.stb.poke(false.B)
        dut.clock.step(1)

        // Verify output
        dut.io.gpio_out.expect(value.U)
      }
    }
  }

  // ========== Integration Tests ==========

  it should "configure a pin as output and drive a value" in {
    test(new Gpio) { dut =>
      // Configure pin 5 as output: DIR[5] = 1, OE[5] = 1
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_DIR.U)
      dut.io.wb.dat_o.poke(0x0020.U)  // Bit 5
      dut.clock.step(1)
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_OE.U)
      dut.io.wb.dat_o.poke(0x0020.U)  // Bit 5
      dut.clock.step(1)
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // gpio_oe[5] should be 1
      dut.io.gpio_oe.expect(0x0020.U)

      // Write value to DATA_OUT
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_DATA_OUT.U)
      dut.io.wb.dat_o.poke(0x0020.U)  // Set bit 5
      dut.clock.step(1)
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // gpio_out[5] should be 1
      dut.io.gpio_out.expect(0x0020.U)
    }
  }

  it should "read input from a pin configured as input" in {
    test(new Gpio) { dut =>
      // Configure pin 3 as input: DIR[3] = 0 (default)
      // Set physical input
      dut.io.gpio_in.poke(0x0008.U)  // Bit 3
      dut.clock.step(1)

      // Read DATA_IN
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(ADDR_DATA_IN.U)
      dut.clock.step(1)
      dut.io.wb.dat_i.expect(0x0008.U)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
    }
  }

  it should "handle all 16 pins independently" in {
    test(new Gpio) { dut =>
      // Set even pins as output, odd pins as input
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_DIR.U)
      dut.io.wb.dat_o.poke(0x5555.U)  // Even bits = 1
      dut.clock.step(1)
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // Enable all outputs
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_OE.U)
      dut.io.wb.dat_o.poke(0xFFFF.U)
      dut.clock.step(1)
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // gpio_oe should be 0x5555
      dut.io.gpio_oe.expect(0x5555.U)

      // Set output values
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_DATA_OUT.U)
      dut.io.wb.dat_o.poke(0xAAAA.U)
      dut.clock.step(1)
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // gpio_out should be 0xAAAA
      dut.io.gpio_out.expect(0xAAAA.U)

      // Set input values (odd bits)
      dut.io.gpio_in.poke(0x5555.U)
      dut.clock.step(1)

      // Read DATA_IN
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(ADDR_DATA_IN.U)
      dut.clock.step(1)
      dut.io.wb.dat_i.expect(0x5555.U)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
    }
  }
}
