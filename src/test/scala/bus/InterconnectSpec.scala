package rv32e.bus

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import rv32e.Config._

/**
 * Test suite for Wishbone Interconnect (Crossbar Switch)
 *
 * Critical component that routes ALL traffic between CPU and peripherals.
 * Tests address decoding, routing logic, and bus protocol compliance.
 *
 * Address Map (9 slaves):
 *   0x00000000 - Boot ROM (256 bytes)
 *   0x10000000 - SPI Flash (16 MB)
 *   0x20000000 - UART (4 KB)
 *   0x20001000 - GPIO (4 KB)
 *   0x20002000 - SPI Master (4 KB)
 *   0x20003000 - I2C Master (4 KB)
 *   0x40000000 - Boot Controller (4 KB)
 *   0x80000000 - RAM (64 KB)
 *   Other      - Invalid (should not ACK)
 */
class InterconnectSpec extends AnyFreeSpec with ChiselScalatestTester {

  "Interconnect" - {

    "should route read requests to Boot ROM (0x00000000)" in {
      test(new Interconnect) { dut =>
        // Master initiates read from Boot ROM
        dut.io.master.adr.poke(BOOT_ROM_BASE.U)
        dut.io.master.dat_w.poke(0.U)
        dut.io.master.sel.poke("b1111".U)
        dut.io.master.we.poke(false.B)
        dut.io.master.stb.poke(true.B)
        dut.io.master.cyc.poke(true.B)

        dut.clock.step(1)

        // Check that Boot ROM slave is selected (slave 0)
        dut.io.slaves(0).stb.expect(true.B, "Boot ROM should be selected")
        dut.io.slaves(0).cyc.expect(true.B)
        dut.io.slaves(0).adr.expect(BOOT_ROM_BASE.U)

        // Other slaves should not be selected
        for (i <- 1 until 9) {
          dut.io.slaves(i).stb.expect(false.B, s"Slave $i should not be selected")
        }

        // Simulate Boot ROM responding
        dut.io.slaves(0).dat_r.poke(0x12345678.U)
        dut.io.slaves(0).ack.poke(true.B)

        dut.clock.step(1)

        // Master should receive data
        dut.io.master.dat_r.expect(0x12345678.U)
        dut.io.master.ack.expect(true.B)
      }
    }

    "should route read requests to SPI Flash (0x10000000)" in {
      test(new Interconnect) { dut =>
        dut.io.master.adr.poke(SPI_FLASH_BASE.U)
        dut.io.master.dat_w.poke(0.U)
        dut.io.master.sel.poke("b1111".U)
        dut.io.master.we.poke(false.B)
        dut.io.master.stb.poke(true.B)
        dut.io.master.cyc.poke(true.B)

        dut.clock.step(1)

        // Check that SPI Flash slave is selected (slave 1)
        dut.io.slaves(1).stb.expect(true.B, "SPI Flash should be selected")
        dut.io.slaves(1).cyc.expect(true.B)
        dut.io.slaves(1).adr.expect(SPI_FLASH_BASE.U)

        // Simulate Flash responding
        dut.io.slaves(1).dat_r.poke(0xAABBCCDD.U)
        dut.io.slaves(1).ack.poke(true.B)

        dut.clock.step(1)

        dut.io.master.dat_r.expect(0xAABBCCDD.U)
        dut.io.master.ack.expect(true.B)
      }
    }

    "should route write requests to UART (0x20000000)" in {
      test(new Interconnect) { dut =>
        val uart_data = 0x00000041.U  // 'A' in TXDATA register

        dut.io.master.adr.poke(UART_BASE.U)
        dut.io.master.dat_w.poke(uart_data)
        dut.io.master.sel.poke("b1111".U)
        dut.io.master.we.poke(true.B)
        dut.io.master.stb.poke(true.B)
        dut.io.master.cyc.poke(true.B)

        dut.clock.step(1)

        // Check that UART slave is selected (slave 2)
        dut.io.slaves(2).stb.expect(true.B, "UART should be selected")
        dut.io.slaves(2).cyc.expect(true.B)
        dut.io.slaves(2).we.expect(true.B)
        dut.io.slaves(2).dat_w.expect(uart_data)

        // Simulate UART ACK
        dut.io.slaves(2).ack.poke(true.B)

        dut.clock.step(1)

        dut.io.master.ack.expect(true.B)
      }
    }

    "should route requests to GPIO (0x20001000)" in {
      test(new Interconnect) { dut =>
        dut.io.master.adr.poke(GPIO_BASE.U)
        dut.io.master.dat_w.poke(0x0000000F.U)  // Set GPIO[3:0]
        dut.io.master.sel.poke("b1111".U)
        dut.io.master.we.poke(true.B)
        dut.io.master.stb.poke(true.B)
        dut.io.master.cyc.poke(true.B)

        dut.clock.step(1)

        // Check that GPIO slave is selected (slave 3)
        dut.io.slaves(3).stb.expect(true.B, "GPIO should be selected")
        dut.io.slaves(3).adr.expect(GPIO_BASE.U)
        dut.io.slaves(3).we.expect(true.B)

        dut.io.slaves(3).ack.poke(true.B)
        dut.clock.step(1)

        dut.io.master.ack.expect(true.B)
      }
    }

    "should route requests to SPI Master (0x20002000)" in {
      test(new Interconnect) { dut =>
        dut.io.master.adr.poke(SPI_BASE.U)
        dut.io.master.dat_w.poke(0x000000A5.U)
        dut.io.master.sel.poke("b1111".U)
        dut.io.master.we.poke(true.B)
        dut.io.master.stb.poke(true.B)
        dut.io.master.cyc.poke(true.B)

        dut.clock.step(1)

        // Check that SPI Master slave is selected (slave 4)
        dut.io.slaves(4).stb.expect(true.B, "SPI Master should be selected")
        dut.io.slaves(4).adr.expect(SPI_BASE.U)

        dut.io.slaves(4).ack.poke(true.B)
        dut.clock.step(1)

        dut.io.master.ack.expect(true.B)
      }
    }

    "should route requests to I2C Master (0x20003000)" in {
      test(new Interconnect) { dut =>
        dut.io.master.adr.poke(I2C_BASE.U)
        dut.io.master.dat_w.poke(0x00000050.U)  // I2C address
        dut.io.master.sel.poke("b1111".U)
        dut.io.master.we.poke(true.B)
        dut.io.master.stb.poke(true.B)
        dut.io.master.cyc.poke(true.B)

        dut.clock.step(1)

        // Check that I2C Master slave is selected (slave 5)
        dut.io.slaves(5).stb.expect(true.B, "I2C Master should be selected")
        dut.io.slaves(5).adr.expect(I2C_BASE.U)

        dut.io.slaves(5).ack.poke(true.B)
        dut.clock.step(1)

        dut.io.master.ack.expect(true.B)
      }
    }

    "should route requests to Boot Controller (0x40000000)" in {
      test(new Interconnect) { dut =>
        dut.io.master.adr.poke(BOOT_CTRL_BASE.U)
        dut.io.master.dat_w.poke(0x00000001.U)  // Start boot
        dut.io.master.sel.poke("b1111".U)
        dut.io.master.we.poke(true.B)
        dut.io.master.stb.poke(true.B)
        dut.io.master.cyc.poke(true.B)

        dut.clock.step(1)

        // Check that Boot Controller slave is selected (slave 6)
        dut.io.slaves(6).stb.expect(true.B, "Boot Controller should be selected")
        dut.io.slaves(6).adr.expect(BOOT_CTRL_BASE.U)

        dut.io.slaves(6).ack.poke(true.B)
        dut.clock.step(1)

        dut.io.master.ack.expect(true.B)
      }
    }

    "should route requests to RAM (0x80000000)" in {
      test(new Interconnect) { dut =>
        val ram_addr = 0x80000100.U  // RAM offset 0x100

        dut.io.master.adr.poke(ram_addr)
        dut.io.master.dat_w.poke(0xDEADBEEF.U)
        dut.io.master.sel.poke("b1111".U)
        dut.io.master.we.poke(true.B)
        dut.io.master.stb.poke(true.B)
        dut.io.master.cyc.poke(true.B)

        dut.clock.step(1)

        // Check that RAM slave is selected (slave 7)
        dut.io.slaves(7).stb.expect(true.B, "RAM should be selected")
        dut.io.slaves(7).adr.expect(ram_addr)
        dut.io.slaves(7).dat_w.expect(0xDEADBEEF.U)
        dut.io.slaves(7).we.expect(true.B)

        dut.io.slaves(7).ack.poke(true.B)
        dut.clock.step(1)

        dut.io.master.ack.expect(true.B)
      }
    }

    "should handle invalid addresses gracefully" in {
      test(new Interconnect) { dut =>
        val invalid_addr = 0x90000000.U  // Not in any slave range

        dut.io.master.adr.poke(invalid_addr)
        dut.io.master.dat_w.poke(0.U)
        dut.io.master.sel.poke("b1111".U)
        dut.io.master.we.poke(false.B)
        dut.io.master.stb.poke(true.B)
        dut.io.master.cyc.poke(true.B)

        dut.clock.step(1)

        // No slave should be selected
        for (i <- 0 until 9) {
          dut.io.slaves(i).stb.expect(false.B, s"No slave $i should be selected for invalid address")
        }

        // Master should receive no ACK (or timeout)
        // In real implementation, should return error or timeout
        dut.io.master.ack.expect(false.B, "Invalid address should not ACK")
      }
    }

    "should propagate byte select signals correctly" in {
      test(new Interconnect) { dut =>
        // Test byte write to UART (sel = 0001, only lowest byte)
        dut.io.master.adr.poke(UART_BASE.U)
        dut.io.master.dat_w.poke(0x12345678.U)
        dut.io.master.sel.poke("b0001".U)  // Only byte 0
        dut.io.master.we.poke(true.B)
        dut.io.master.stb.poke(true.B)
        dut.io.master.cyc.poke(true.B)

        dut.clock.step(1)

        // UART should receive correct sel
        dut.io.slaves(2).sel.expect("b0001".U, "Byte select should propagate")
        dut.io.slaves(2).dat_w.expect(0x12345678.U)

        dut.io.slaves(2).ack.poke(true.B)
        dut.clock.step(1)
      }
    }

    "should handle back-to-back requests to different slaves" in {
      test(new Interconnect) { dut =>
        // Request 1: Read from Boot ROM
        dut.io.master.adr.poke(BOOT_ROM_BASE.U)
        dut.io.master.we.poke(false.B)
        dut.io.master.stb.poke(true.B)
        dut.io.master.cyc.poke(true.B)

        dut.clock.step(1)
        dut.io.slaves(0).stb.expect(true.B, "Boot ROM should be selected")

        dut.io.slaves(0).dat_r.poke(0x11111111.U)
        dut.io.slaves(0).ack.poke(true.B)
        dut.clock.step(1)

        // Request 2: Write to GPIO (immediately after)
        dut.io.master.adr.poke(GPIO_BASE.U)
        dut.io.master.dat_w.poke(0xFF.U)
        dut.io.master.we.poke(true.B)
        dut.io.master.stb.poke(true.B)
        dut.io.master.cyc.poke(true.B)

        dut.clock.step(1)

        // Boot ROM should no longer be selected
        dut.io.slaves(0).stb.expect(false.B, "Boot ROM should not be selected")

        // GPIO should now be selected
        dut.io.slaves(3).stb.expect(true.B, "GPIO should be selected")
        dut.io.slaves(3).we.expect(true.B)

        dut.io.slaves(3).ack.poke(true.B)
        dut.clock.step(1)
      }
    }

    "should respect cyc signal (no routing when cyc=0)" in {
      test(new Interconnect) { dut =>
        // Set up valid address and stb, but cyc = 0
        dut.io.master.adr.poke(UART_BASE.U)
        dut.io.master.stb.poke(true.B)
        dut.io.master.cyc.poke(false.B)  // Bus cycle not active
        dut.io.master.we.poke(false.B)

        dut.clock.step(1)

        // No slave should be selected
        for (i <- 0 until 9) {
          dut.io.slaves(i).stb.expect(false.B, s"Slave $i should not respond when cyc=0")
          dut.io.slaves(i).cyc.expect(false.B)
        }
      }
    }

    "should respect stb signal (no routing when stb=0)" in {
      test(new Interconnect) { dut =>
        // Set up valid address and cyc, but stb = 0
        dut.io.master.adr.poke(GPIO_BASE.U)
        dut.io.master.stb.poke(false.B)  // Strobe not active
        dut.io.master.cyc.poke(true.B)
        dut.io.master.we.poke(false.B)

        dut.clock.step(1)

        // No slave should receive strobe
        for (i <- 0 until 9) {
          dut.io.slaves(i).stb.expect(false.B, s"Slave $i should not receive stb when master stb=0")
        }
      }
    }

    "should propagate we (write enable) signal correctly" in {
      test(new Interconnect) { dut =>
        // Write request to RAM
        dut.io.master.adr.poke(RAM_BASE.U)
        dut.io.master.dat_w.poke(0xCAFEBABE.U)
        dut.io.master.we.poke(true.B)
        dut.io.master.sel.poke("b1111".U)
        dut.io.master.stb.poke(true.B)
        dut.io.master.cyc.poke(true.B)

        dut.clock.step(1)

        // RAM should receive we=1
        dut.io.slaves(7).we.expect(true.B, "RAM should receive write enable")

        dut.io.slaves(7).ack.poke(true.B)
        dut.clock.step(1)

        // Read request to RAM
        dut.io.master.we.poke(false.B)
        dut.clock.step(1)

        // RAM should receive we=0
        dut.io.slaves(7).we.expect(false.B, "RAM should receive write disable")
      }
    }

    "should handle maximum address in each region" in {
      test(new Interconnect) { dut =>
        // Test Boot ROM max address (0x000000FF)
        dut.io.master.adr.poke((BOOT_ROM_BASE + BOOT_ROM_SIZE - 4).U)
        dut.io.master.stb.poke(true.B)
        dut.io.master.cyc.poke(true.B)
        dut.io.master.we.poke(false.B)

        dut.clock.step(1)
        dut.io.slaves(0).stb.expect(true.B, "Boot ROM should handle max address")

        dut.io.slaves(0).ack.poke(true.B)
        dut.clock.step(1)

        // Test RAM max address (0x8000FFFF)
        dut.io.master.adr.poke((RAM_BASE + RAM_SIZE - 4).U)
        dut.clock.step(1)

        dut.io.slaves(7).stb.expect(true.B, "RAM should handle max address")
      }
    }

    "should correctly decode adjacent peripheral regions" in {
      test(new Interconnect) { dut =>
        // Test boundary between UART (0x20000000) and GPIO (0x20001000)

        // Last address in UART region
        dut.io.master.adr.poke(0x20000FFC.U)
        dut.io.master.stb.poke(true.B)
        dut.io.master.cyc.poke(true.B)
        dut.io.master.we.poke(false.B)

        dut.clock.step(1)
        dut.io.slaves(2).stb.expect(true.B, "UART should be selected at 0x20000FFC")
        dut.io.slaves(3).stb.expect(false.B, "GPIO should not be selected")

        dut.io.slaves(2).ack.poke(true.B)
        dut.clock.step(1)

        // First address in GPIO region
        dut.io.master.adr.poke(0x20001000.U)
        dut.clock.step(1)

        dut.io.slaves(2).stb.expect(false.B, "UART should not be selected")
        dut.io.slaves(3).stb.expect(true.B, "GPIO should be selected at 0x20001000")
      }
    }

    "should handle address wraparound edge cases" in {
      test(new Interconnect) { dut =>
        // Test address 0xFFFFFFFF (max 32-bit address)
        dut.io.master.adr.poke(0xFFFFFFFF.U)
        dut.io.master.stb.poke(true.B)
        dut.io.master.cyc.poke(true.B)
        dut.io.master.we.poke(false.B)

        dut.clock.step(1)

        // Should not select any slave (invalid address)
        for (i <- 0 until 9) {
          dut.io.slaves(i).stb.expect(false.B, s"Slave $i should not respond to 0xFFFFFFFF")
        }
      }
    }
  }
}
