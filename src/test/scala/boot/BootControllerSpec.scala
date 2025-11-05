package rv32e.boot

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import rv32e.Config

/**
 * Test suite for Boot Controller
 *
 * Tests the boot state machine that copies code from SPI Flash to RAM
 * and then releases the CPU to execute.
 *
 * Boot Sequence:
 *   1. RESET → wait for boot_start
 *   2. INIT_SPI → initialize SPI Flash controller
 *   3. READ_FLASH → read 32-bit word from Flash
 *   4. LOAD_MEM → write word to RAM
 *   5. Repeat steps 3-4 until BOOT_COPY_SIZE bytes copied
 *   6. BOOT_DONE → release CPU reset
 *   7. RUN → normal execution
 */
class BootControllerSpec extends AnyFreeSpec with ChiselScalatestTester {

  "BootController" - {

    "should start in RESET state with CPU held in reset" in {
      test(new BootController) { dut =>
        // Initially in reset state
        dut.io.cpu_reset.expect(true.B, "CPU should be held in reset")
        dut.io.boot_done.expect(false.B, "Boot should not be done")
        dut.io.flash_read.expect(false.B, "Flash should not be reading")
        dut.io.ram_write.expect(false.B, "RAM should not be writing")
      }
    }

    "should transition from RESET to INIT_SPI on boot_start" in {
      test(new BootController) { dut =>
        // Start boot sequence
        dut.io.boot_start.poke(true.B)
        dut.clock.step(1)

        // Should transition to INIT_SPI state
        dut.io.cpu_reset.expect(true.B, "CPU should still be in reset during init")
        dut.io.boot_done.expect(false.B)

        // One more cycle should transition to READ_FLASH
        dut.clock.step(1)

        // Should now be in READ_FLASH state
        dut.io.flash_read.expect(true.B, "Should be reading from Flash")
        dut.io.flash_addr.expect(0.U, "Should start reading from address 0")
      }
    }

    "should read from Flash and wait for valid data" in {
      test(new BootController) { dut =>
        // Start boot
        dut.io.boot_start.poke(true.B)
        dut.clock.step(2)  // RESET → INIT_SPI → READ_FLASH

        // Now in READ_FLASH state
        dut.io.flash_read.expect(true.B)
        dut.io.flash_addr.expect(0.U)
        dut.io.cpu_reset.expect(true.B)

        // Flash data not valid yet
        dut.io.flash_data.poke(0x12345678.U)
        dut.io.flash_valid.poke(false.B)
        dut.clock.step(1)

        // Should stay in READ_FLASH state
        dut.io.flash_read.expect(true.B)

        // Flash data now valid
        dut.io.flash_valid.poke(true.B)
        dut.clock.step(1)

        // Should transition to LOAD_MEM state
        dut.io.ram_write.expect(true.B, "Should be writing to RAM")
        dut.io.ram_addr.expect(Config.RAM_BASE.U, "Should write to RAM base address")
        dut.io.ram_data.expect(0x12345678.U, "Should write Flash data")
      }
    }

    "should write data to RAM and wait for ACK" in {
      test(new BootController) { dut =>
        // Get to LOAD_MEM state
        dut.io.boot_start.poke(true.B)
        dut.clock.step(2)  // → READ_FLASH

        dut.io.flash_data.poke(0xAABBCCDD.U)
        dut.io.flash_valid.poke(true.B)
        dut.clock.step(1)  // → LOAD_MEM

        // Now in LOAD_MEM state
        dut.io.ram_write.expect(true.B)
        dut.io.ram_addr.expect(Config.RAM_BASE.U)
        dut.io.ram_data.expect(0xAABBCCDD.U)

        // RAM not ready yet
        dut.io.ram_ack.poke(false.B)
        dut.clock.step(1)

        // Should stay in LOAD_MEM state
        dut.io.ram_write.expect(true.B)

        // RAM acknowledges write
        dut.io.ram_ack.poke(true.B)
        dut.clock.step(1)

        // Should transition back to READ_FLASH for next word
        dut.io.flash_read.expect(true.B)
        dut.io.flash_addr.expect(4.U, "Should read next address (0 + 4)")
      }
    }

    "should increment addresses correctly during copy" in {
      test(new BootController) { dut =>
        // Start boot
        dut.io.boot_start.poke(true.B)
        dut.clock.step(2)  // → READ_FLASH

        // Copy first word (address 0 → RAM_BASE + 0)
        dut.io.flash_addr.expect(0.U)
        dut.io.flash_data.poke(0x11111111.U)
        dut.io.flash_valid.poke(true.B)
        dut.clock.step(1)  // → LOAD_MEM

        dut.io.ram_addr.expect((Config.RAM_BASE + 0).U)
        dut.io.ram_data.expect(0x11111111.U)
        dut.io.ram_ack.poke(true.B)
        dut.clock.step(1)  // → READ_FLASH

        // Copy second word (address 4 → RAM_BASE + 4)
        dut.io.flash_addr.expect(4.U)
        dut.io.flash_data.poke(0x22222222.U)
        dut.io.flash_valid.poke(true.B)
        dut.clock.step(1)  // → LOAD_MEM

        dut.io.ram_addr.expect((Config.RAM_BASE + 4).U)
        dut.io.ram_data.expect(0x22222222.U)
        dut.io.ram_ack.poke(true.B)
        dut.clock.step(1)  // → READ_FLASH

        // Copy third word (address 8 → RAM_BASE + 8)
        dut.io.flash_addr.expect(8.U)
        dut.io.flash_data.poke(0x33333333.U)
        dut.io.flash_valid.poke(true.B)
        dut.clock.step(1)  // → LOAD_MEM

        dut.io.ram_addr.expect((Config.RAM_BASE + 8).U)
        dut.io.ram_data.expect(0x33333333.U)
      }
    }

    "should complete boot after copying BOOT_COPY_SIZE bytes" in {
      test(new BootController).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val boot_size = Config.BOOT_COPY_SIZE
        val words_to_copy = boot_size / 4  // 16384 / 4 = 4096 words

        println(s"Testing boot copy: $boot_size bytes = $words_to_copy words")

        // Start boot
        dut.io.boot_start.poke(true.B)
        dut.clock.step(2)  // → READ_FLASH

        // Copy all words
        for (i <- 0 until words_to_copy) {
          // READ_FLASH state
          dut.io.flash_addr.expect((i * 4).U)
          dut.io.flash_data.poke((0x10000000 + i).U)
          dut.io.flash_valid.poke(true.B)
          dut.io.cpu_reset.expect(true.B, s"CPU should be in reset during copy (word $i)")
          dut.clock.step(1)  // → LOAD_MEM

          // LOAD_MEM state
          dut.io.ram_addr.expect((Config.RAM_BASE + i * 4).U)
          dut.io.ram_data.expect((0x10000000 + i).U)
          dut.io.ram_write.expect(true.B)
          dut.io.ram_ack.poke(true.B)
          dut.clock.step(1)  // → READ_FLASH (or BOOT_DONE if last word)

          if (i % 1000 == 0 && i > 0) {
            println(s"  Copied $i / $words_to_copy words...")
          }
        }

        // Should now be in BOOT_DONE state
        dut.io.cpu_reset.expect(false.B, "CPU should be released from reset")
        dut.io.boot_done.expect(true.B, "Boot should be complete")
        dut.io.cpu_boot_pc.expect(Config.RAM_BASE.U, "Boot PC should be RAM base")

        dut.clock.step(1)  // → RUN

        // Should now be in RUN state
        dut.io.cpu_reset.expect(false.B, "CPU should remain released")
        dut.io.boot_done.expect(true.B, "Boot should remain complete")
        dut.io.flash_read.expect(false.B, "Flash should not be reading")
        dut.io.ram_write.expect(false.B, "RAM should not be writing")
      }
    }

    "should hold CPU in reset until boot complete" in {
      test(new BootController) { dut =>
        // Start boot
        dut.io.boot_start.poke(true.B)
        dut.clock.step(2)  // → READ_FLASH

        // Copy a few words and verify CPU stays in reset
        for (i <- 0 until 10) {
          // READ_FLASH
          dut.io.cpu_reset.expect(true.B, s"CPU should be in reset (word $i, READ)")
          dut.io.flash_data.poke((0x1000 + i).U)
          dut.io.flash_valid.poke(true.B)
          dut.clock.step(1)  // → LOAD_MEM

          // LOAD_MEM
          dut.io.cpu_reset.expect(true.B, s"CPU should be in reset (word $i, LOAD)")
          dut.io.ram_ack.poke(true.B)
          dut.clock.step(1)  // → READ_FLASH
        }

        // CPU should still be in reset
        dut.io.cpu_reset.expect(true.B, "CPU should remain in reset during copy")
      }
    }

    "should assert boot_done only after entering BOOT_DONE state" in {
      test(new BootController) { dut =>
        // Initially not done
        dut.io.boot_done.expect(false.B)

        // Start boot
        dut.io.boot_start.poke(true.B)
        dut.clock.step(2)  // → READ_FLASH

        // During copy, should not be done
        for (i <- 0 until 5) {
          dut.io.boot_done.expect(false.B, s"Boot should not be done during copy (word $i)")
          dut.io.flash_data.poke(0.U)
          dut.io.flash_valid.poke(true.B)
          dut.clock.step(1)  // → LOAD_MEM

          dut.io.boot_done.expect(false.B)
          dut.io.ram_ack.poke(true.B)
          dut.clock.step(1)  // → READ_FLASH
        }

        dut.io.boot_done.expect(false.B, "Boot should not be done yet")
      }
    }

    "should not start boot without boot_start signal" in {
      test(new BootController) { dut =>
        // Don't assert boot_start
        dut.io.boot_start.poke(false.B)

        // Wait several cycles
        for (_ <- 0 until 10) {
          dut.clock.step(1)
          dut.io.cpu_reset.expect(true.B, "CPU should remain in reset")
          dut.io.boot_done.expect(false.B, "Boot should not start")
          dut.io.flash_read.expect(false.B, "Flash should not be accessed")
        }
      }
    }

    "should handle Flash latency (multi-cycle valid delay)" in {
      test(new BootController) { dut =>
        // Start boot
        dut.io.boot_start.poke(true.B)
        dut.clock.step(2)  // → READ_FLASH

        // Flash takes 5 cycles to respond
        dut.io.flash_data.poke(0xDEADBEEF.U)
        dut.io.flash_valid.poke(false.B)

        for (i <- 0 until 5) {
          dut.clock.step(1)
          // Should stay in READ_FLASH state
          dut.io.flash_read.expect(true.B, s"Should wait for Flash valid (cycle $i)")
          dut.io.ram_write.expect(false.B)
        }

        // Flash now valid
        dut.io.flash_valid.poke(true.B)
        dut.clock.step(1)  // → LOAD_MEM

        // Should now be writing to RAM
        dut.io.ram_write.expect(true.B)
        dut.io.ram_data.expect(0xDEADBEEF.U)
      }
    }

    "should handle RAM latency (multi-cycle ACK delay)" in {
      test(new BootController) { dut =>
        // Get to LOAD_MEM state
        dut.io.boot_start.poke(true.B)
        dut.clock.step(2)  // → READ_FLASH
        dut.io.flash_data.poke(0xCAFEBABE.U)
        dut.io.flash_valid.poke(true.B)
        dut.clock.step(1)  // → LOAD_MEM

        // RAM takes 3 cycles to acknowledge
        dut.io.ram_ack.poke(false.B)

        for (i <- 0 until 3) {
          dut.clock.step(1)
          // Should stay in LOAD_MEM state
          dut.io.ram_write.expect(true.B, s"Should wait for RAM ACK (cycle $i)")
          dut.io.flash_read.expect(false.B)
        }

        // RAM acknowledges
        dut.io.ram_ack.poke(true.B)
        dut.clock.step(1)  // → READ_FLASH

        // Should now be reading next word
        dut.io.flash_read.expect(true.B)
        dut.io.flash_addr.expect(4.U)
      }
    }

    "should set correct boot PC (RAM base address)" in {
      test(new BootController) { dut =>
        val boot_size = Config.BOOT_COPY_SIZE
        val words_to_copy = boot_size / 4

        // Start and complete boot
        dut.io.boot_start.poke(true.B)
        dut.clock.step(2)  // → READ_FLASH

        // Fast copy
        for (_ <- 0 until words_to_copy) {
          dut.io.flash_data.poke(0.U)
          dut.io.flash_valid.poke(true.B)
          dut.clock.step(1)  // → LOAD_MEM
          dut.io.ram_ack.poke(true.B)
          dut.clock.step(1)  // → READ_FLASH or BOOT_DONE
        }

        // Check boot PC
        dut.io.cpu_boot_pc.expect(Config.RAM_BASE.U, "Boot PC should be RAM base")
      }
    }

    "should stay in RUN state after boot complete" in {
      test(new BootController) { dut =>
        val boot_size = Config.BOOT_COPY_SIZE
        val words_to_copy = boot_size / 4

        // Complete boot
        dut.io.boot_start.poke(true.B)
        dut.clock.step(2)  // → READ_FLASH

        for (_ <- 0 until words_to_copy) {
          dut.io.flash_data.poke(0.U)
          dut.io.flash_valid.poke(true.B)
          dut.clock.step(1)
          dut.io.ram_ack.poke(true.B)
          dut.clock.step(1)
        }

        // Should be in BOOT_DONE
        dut.io.boot_done.expect(true.B)
        dut.clock.step(1)  // → RUN

        // Stay in RUN state for many cycles
        for (i <- 0 until 100) {
          dut.clock.step(1)
          dut.io.cpu_reset.expect(false.B, s"CPU should stay released (cycle $i)")
          dut.io.boot_done.expect(true.B, s"Boot should stay done (cycle $i)")
          dut.io.flash_read.expect(false.B, s"Flash should not read in RUN state (cycle $i)")
          dut.io.ram_write.expect(false.B, s"RAM should not write in RUN state (cycle $i)")
        }
      }
    }

    "should copy exactly BOOT_COPY_SIZE bytes (16 KB)" in {
      test(new BootController) { dut =>
        val boot_size = Config.BOOT_COPY_SIZE
        assert(boot_size == 16384, "BOOT_COPY_SIZE should be 16 KB")

        val words_to_copy = boot_size / 4  // 4096 words
        var words_copied = 0

        dut.io.boot_start.poke(true.B)
        dut.clock.step(2)  // → READ_FLASH

        // Count how many words get copied
        while (words_copied < words_to_copy + 10) {  // +10 safety margin
          if (dut.io.flash_read.peek().litToBoolean) {
            // In READ_FLASH state
            dut.io.flash_data.poke(0.U)
            dut.io.flash_valid.poke(true.B)
            dut.clock.step(1)  // → LOAD_MEM
          } else if (dut.io.ram_write.peek().litToBoolean) {
            // In LOAD_MEM state
            dut.io.ram_ack.poke(true.B)
            words_copied += 1
            dut.clock.step(1)  // → READ_FLASH or BOOT_DONE
          } else if (dut.io.boot_done.peek().litToBoolean) {
            // Boot complete
            break
          } else {
            dut.clock.step(1)
          }
        }

        assert(words_copied == words_to_copy,
               s"Should copy exactly $words_to_copy words, copied $words_copied")
      }
    }
  }
}
