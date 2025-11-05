package rv32e.peripherals

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import rv32e.Config

/**
 * Test suite for UART Controller
 *
 * Note: Full serial transmission/reception tests would require
 * thousands of cycles. These tests focus on register interface
 * and FIFO behavior.
 */
class UartSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "Uart"

  // Register addresses (word-addressed, shift by 2)
  val ADDR_TXDATA = 0x00
  val ADDR_RXDATA = 0x04
  val ADDR_STATUS = 0x08
  val ADDR_BAUD   = 0x0C

  // Status register bit positions
  val STATUS_TX_EMPTY = 0
  val STATUS_TX_FULL  = 1
  val STATUS_RX_VALID = 2
  val STATUS_RX_EMPTY = 3

  // ========== Reset and Initialization Tests ==========

  it should "initialize with default baud divisor" in {
    test(new Uart) { dut =>
      val expected_div = (Config.UART_CLOCK_FREQ / Config.UART_DEFAULT_BAUD).toInt

      // Read BAUD register
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(ADDR_BAUD.U)
      dut.clock.step(1)
      dut.io.wb.ack.expect(true.B)
      dut.io.wb.dat_i.expect(expected_div.U)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
    }
  }

  it should "initialize with TX line high (idle)" in {
    test(new Uart) { dut =>
      // UART TX idle state is high
      dut.io.tx.expect(true.B)
    }
  }

  it should "show TX FIFO empty on reset" in {
    test(new Uart) { dut =>
      // Read STATUS register
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(ADDR_STATUS.U)
      dut.clock.step(1)
      dut.io.wb.ack.expect(true.B)

      val status = dut.io.wb.dat_i.peekInt()
      // Check tx_empty bit (bit 0)
      ((status >> STATUS_TX_EMPTY) & 1) should be (1)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
    }
  }

  it should "show RX FIFO empty on reset" in {
    test(new Uart) { dut =>
      // Read STATUS register
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(ADDR_STATUS.U)
      dut.clock.step(1)

      val status = dut.io.wb.dat_i.peekInt()
      // Check rx_empty bit (bit 3)
      ((status >> STATUS_RX_EMPTY) & 1) should be (1)
      // Check rx_valid bit (bit 2) should be 0
      ((status >> STATUS_RX_VALID) & 1) should be (0)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
    }
  }

  // ========== BAUD Register Tests ==========

  it should "write and read BAUD register" in {
    test(new Uart) { dut =>
      val test_divisor = 217  // Common divisor for 115200 baud @ 25MHz

      // Write BAUD
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_BAUD.U)
      dut.io.wb.dat_o.poke(test_divisor.U)
      dut.clock.step(1)
      dut.io.wb.ack.expect(true.B)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // Read back BAUD
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(ADDR_BAUD.U)
      dut.clock.step(1)
      dut.io.wb.ack.expect(true.B)
      dut.io.wb.dat_i.expect(test_divisor.U)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
    }
  }

  it should "update baud divisor with different values" in {
    test(new Uart) { dut =>
      val test_values = Seq(100, 200, 434, 868, 1736)  // Various divisors

      for (divisor <- test_values) {
        // Write BAUD
        dut.io.wb.cyc.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.we.poke(true.B)
        dut.io.wb.adr.poke(ADDR_BAUD.U)
        dut.io.wb.dat_o.poke(divisor.U)
        dut.clock.step(1)

        dut.io.wb.cyc.poke(false.B)
        dut.io.wb.stb.poke(false.B)
        dut.clock.step(1)

        // Read back
        dut.io.wb.cyc.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.we.poke(false.B)
        dut.io.wb.adr.poke(ADDR_BAUD.U)
        dut.clock.step(1)
        dut.io.wb.dat_i.expect(divisor.U)

        dut.io.wb.cyc.poke(false.B)
        dut.io.wb.stb.poke(false.B)
        dut.clock.step(1)
      }
    }
  }

  // ========== TX FIFO Tests ==========

  it should "accept data written to TXDATA" in {
    test(new Uart) { dut =>
      val test_byte = 0x42

      // Write to TXDATA
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_TXDATA.U)
      dut.io.wb.dat_o.poke(test_byte.U)
      dut.clock.step(1)
      dut.io.wb.ack.expect(true.B)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // STATUS should show TX not empty
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(ADDR_STATUS.U)
      dut.clock.step(1)

      val status = dut.io.wb.dat_i.peekInt()
      // tx_empty bit should be 0 (not empty)
      ((status >> STATUS_TX_EMPTY) & 1) should be (0)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
    }
  }

  it should "indicate TX FIFO full when 16 bytes written" in {
    test(new Uart).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      // Set a very large baud divisor to slow down transmission
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_BAUD.U)
      dut.io.wb.dat_o.poke(10000.U)  // Very slow
      dut.clock.step(1)
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // Write 16 bytes (FIFO depth)
      for (i <- 0 until Config.UART_FIFO_DEPTH) {
        dut.io.wb.cyc.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.we.poke(true.B)
        dut.io.wb.adr.poke(ADDR_TXDATA.U)
        dut.io.wb.dat_o.poke(i.U)
        dut.clock.step(1)

        dut.io.wb.cyc.poke(false.B)
        dut.io.wb.stb.poke(false.B)
        dut.clock.step(1)
      }

      // STATUS should show TX full
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(ADDR_STATUS.U)
      dut.clock.step(1)

      val status = dut.io.wb.dat_i.peekInt()
      // tx_full bit should be 1
      ((status >> STATUS_TX_FULL) & 1) should be (1)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
    }
  }

  // ========== STATUS Register Tests ==========

  it should "read STATUS register correctly" in {
    test(new Uart) { dut =>
      // Read STATUS
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(ADDR_STATUS.U)
      dut.clock.step(1)
      dut.io.wb.ack.expect(true.B)

      // On reset, should have tx_empty=1, rx_empty=1
      val status = dut.io.wb.dat_i.peekInt()

      // Bit 0: tx_empty = 1
      ((status >> 0) & 1) should be (1)
      // Bit 1: tx_full = 0
      ((status >> 1) & 1) should be (0)
      // Bit 2: rx_valid = 0
      ((status >> 2) & 1) should be (0)
      // Bit 3: rx_empty = 1
      ((status >> 3) & 1) should be (1)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
    }
  }

  it should "update STATUS when TX FIFO has data" in {
    test(new Uart) { dut =>
      // Set large baud divisor
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_BAUD.U)
      dut.io.wb.dat_o.poke(10000.U)
      dut.clock.step(1)
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // Write one byte to TXDATA
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_TXDATA.U)
      dut.io.wb.dat_o.poke(0xAB.U)
      dut.clock.step(1)
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // Read STATUS
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(ADDR_STATUS.U)
      dut.clock.step(1)

      val status = dut.io.wb.dat_i.peekInt()
      // tx_empty should be 0 (has data)
      ((status >> STATUS_TX_EMPTY) & 1) should be (0)
      // tx_full should be 0 (not full)
      ((status >> STATUS_TX_FULL) & 1) should be (0)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
    }
  }

  // ========== Wishbone Protocol Tests ==========

  it should "acknowledge transactions on next cycle" in {
    test(new Uart) { dut =>
      // Start transaction
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(ADDR_STATUS.U)

      // ACK should be low before clock
      dut.io.wb.ack.expect(false.B)

      dut.clock.step(1)

      // ACK should be high after clock
      dut.io.wb.ack.expect(true.B)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // ACK should be low after transaction
      dut.io.wb.ack.expect(false.B)
    }
  }

  it should "not acknowledge without strobe" in {
    test(new Uart) { dut =>
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(false.B)  // No strobe
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(ADDR_STATUS.U)
      dut.clock.step(1)

      // Should not acknowledge
      dut.io.wb.ack.expect(false.B)
    }
  }

  it should "handle back-to-back register accesses" in {
    test(new Uart) { dut =>
      // Set large baud divisor
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_BAUD.U)
      dut.io.wb.dat_o.poke(10000.U)
      dut.clock.step(1)
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // Back-to-back: Write TXDATA, Read STATUS, Write TXDATA, Read STATUS
      val operations = Seq(
        (true, ADDR_TXDATA, 0x11),
        (false, ADDR_STATUS, 0),
        (true, ADDR_TXDATA, 0x22),
        (false, ADDR_STATUS, 0)
      )

      for ((is_write, addr, data) <- operations) {
        dut.io.wb.cyc.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.we.poke(is_write.B)
        dut.io.wb.adr.poke(addr.U)
        if (is_write) {
          dut.io.wb.dat_o.poke(data.U)
        }
        dut.clock.step(1)
        dut.io.wb.ack.expect(true.B)

        dut.io.wb.cyc.poke(false.B)
        dut.io.wb.stb.poke(false.B)
        dut.clock.step(1)
      }
    }
  }

  // ========== TX State Machine Basic Test ==========

  it should "begin transmission when data is written" in {
    test(new Uart) { dut =>
      // Set small baud divisor for faster test
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_BAUD.U)
      dut.io.wb.dat_o.poke(10.U)  // Small divisor
      dut.clock.step(1)
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // TX should be idle (high) initially
      dut.io.tx.expect(true.B)

      // Write byte to transmit
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_TXDATA.U)
      dut.io.wb.dat_o.poke(0x55.U)  // 0b01010101
      dut.clock.step(1)
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // After a few cycles, TX should go low (start bit)
      // Wait for transmission to begin
      var started = false
      for (_ <- 0 until 20) {
        if (!dut.io.tx.peekBoolean()) {
          started = true
        }
        dut.clock.step(1)
      }

      started should be (true)
    }
  }

  // ========== Integration Test ==========

  it should "handle multiple TX writes with status checks" in {
    test(new Uart) { dut =>
      // Set large baud divisor to prevent transmission
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(ADDR_BAUD.U)
      dut.io.wb.dat_o.poke(50000.U)
      dut.clock.step(1)
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)

      // Write 3 bytes
      for (i <- 0 until 3) {
        dut.io.wb.cyc.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.we.poke(true.B)
        dut.io.wb.adr.poke(ADDR_TXDATA.U)
        dut.io.wb.dat_o.poke((0x41 + i).U)  // 'A', 'B', 'C'
        dut.clock.step(1)
        dut.io.wb.cyc.poke(false.B)
        dut.io.wb.stb.poke(false.B)
        dut.clock.step(1)
      }

      // Check STATUS: tx_empty should be 0
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(ADDR_STATUS.U)
      dut.clock.step(1)

      val status = dut.io.wb.dat_i.peekInt()
      ((status >> STATUS_TX_EMPTY) & 1) should be (0)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
    }
  }
}
