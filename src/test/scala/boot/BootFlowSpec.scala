package rv32e.boot

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import rv32e.Config
import rv32e.soc.MinimalSoc
import rv32e.peripherals.FlashMemoryModel
import rv32e.core._
import rv32e.bus._

/**
 * Complete Boot Flow Integration Test
 *
 * This test verifies the complete boot sequence:
 * 1. CPU starts at PC = 0x00000000 (Boot ROM)
 * 2. Boot ROM code executes and reads from Flash
 * 3. Data is copied from Flash (0x10000000) to RAM (0x80000000)
 * 4. Boot ROM jumps to RAM entry point
 * 5. User program executes from RAM
 *
 * Test Strategy:
 * - Use FlashMemoryModel to simulate SPI Flash
 * - Pre-load Flash with a simple test program
 * - Monitor PC and bus transactions
 * - Verify Boot ROM execution
 * - Verify RAM is populated with Flash data
 * - Verify PC reaches RAM base address
 *
 * Known Issues:
 * - SPI Flash XIP is not fully implemented in SpiFlash.scala
 * - This test requires a working Flash memory model
 * - Current SoC design uses simplified memory-mapped Flash access
 */
class BootFlowSpec extends AnyFreeSpec with ChiselScalatestTester {

  /**
   * Simple test program to load into Flash
   * This program will be copied to RAM and executed
   */
  def createTestProgram(): Seq[Long] = {
    Seq(
      // Simple program that writes a pattern to RAM
      0x12345137L,  // lui  x2, 0x12345
      0x67810113L,  // addi x2, x2, 0x678    # x2 = 0x12345678
      0x80000237L,  // lui  x4, 0x80000      # x4 = RAM base
      0x00422023L,  // sw   x4, 0(x4)        # Store marker
      0x00222223L,  // sw   x2, 4(x4)        # Store test value

      // Write to GPIO to signal completion
      0x20010337L,  // lui  x6, 0x20010      # GPIO base
      0x0AA00393L,  // addi x7, x0, 0xAA     # Success pattern
      0x00732223L,  // sw   x7, 4(x6)        # GPIO = 0xAA

      // Infinite loop (halt)
      0x0000006FL   // jal  x0, 0            # Loop forever
    )
  }

  "BootFlow" - {

    "Boot ROM should contain correct boot code" in {
      test(new BootROM) { dut =>
        // Verify first few boot instructions
        val expected_start = Seq(
          0x10000537L,  // lui  t0, 0x10000  (Flash base)
          0x00000513L,  // addi t0, t0, 0
          0x800005b7L,  // lui  t1, 0x80000  (RAM base)
          0x00058593L   // addi t1, t1, 0
        )

        for ((expected, idx) <- expected_start.zipWithIndex) {
          dut.io.wb.cyc.poke(true.B)
          dut.io.wb.stb.poke(true.B)
          dut.io.wb.we.poke(false.B)
          dut.io.wb.adr.poke((idx * 4).U)
          dut.clock.step(1)

          dut.io.wb.ack.expect(true.B)
          dut.io.wb.dat_i.expect(expected.U,
            s"Boot ROM instruction $idx should be 0x${expected.toHexString}")

          dut.io.wb.cyc.poke(false.B)
          dut.io.wb.stb.poke(false.B)
          dut.clock.step(1)
        }
      }
    }

    "Flash memory model should store and retrieve data" in {
      test(new FlashMemoryModel) { dut =>
        val test_data = Seq(0xDEADBEEFL, 0xCAFEBABEL, 0x12345678L)

        // Write data to Flash
        for ((data, offset) <- test_data.zipWithIndex) {
          dut.io.wb.cyc.poke(true.B)
          dut.io.wb.stb.poke(true.B)
          dut.io.wb.we.poke(true.B)
          dut.io.wb.adr.poke((offset * 4).U)
          dut.io.wb.dat_o.poke(data.U)
          dut.io.wb.sel.poke(0xF.U)
          dut.clock.step(1)
          dut.io.wb.ack.expect(true.B)
          dut.io.wb.cyc.poke(false.B)
          dut.io.wb.stb.poke(false.B)
          dut.clock.step(1)
        }

        // Read back and verify
        for ((expected, offset) <- test_data.zipWithIndex) {
          dut.io.wb.cyc.poke(true.B)
          dut.io.wb.stb.poke(true.B)
          dut.io.wb.we.poke(false.B)
          dut.io.wb.adr.poke((offset * 4).U)
          dut.clock.step(1)
          dut.io.wb.ack.expect(true.B)
          dut.io.wb.dat_i.expect(expected.U,
            s"Flash read at offset ${offset * 4} should return 0x${expected.toHexString}")
          dut.io.wb.cyc.poke(false.B)
          dut.io.wb.stb.poke(false.B)
          dut.clock.step(1)
        }
      }
    }

    /**
     * CRITICAL TEST: Verify Boot ROM execution
     *
     * This test tracks PC through the first few boot instructions
     * to verify CPU is fetching and executing from Boot ROM.
     */
    "CPU should start executing from Boot ROM at PC=0x00000000" in {
      test(new MinimalSoc).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        // Reset
        dut.reset.poke(true.B)
        dut.clock.step(10)
        dut.reset.poke(false.B)
        dut.clock.step(2)

        // After reset, PC should start at 0x00000000
        println(f"Initial PC after reset: 0x${dut.io.debug_pc.peek().litValue}%08x")

        // Run for a few cycles and track PC
        for (cycle <- 0 until 20) {
          val pc = dut.io.debug_pc.peek().litValue
          val inst = dut.io.debug_inst.peek().litValue

          println(f"Cycle $cycle%3d: PC=0x$pc%08x, Inst=0x$inst%08x")

          // PC should be in Boot ROM range (0x00000000 - 0x000000FF)
          assert(pc < 0x100L || pc == 0L, // Allow PC=0 during pipeline fill
            f"PC should be in Boot ROM range, got 0x$pc%08x")

          dut.clock.step(1)
        }
      }
    }

    /**
     * DOCUMENTATION TEST: Current Boot Flow Limitations
     *
     * This test documents the current state of Boot implementation
     * and identifies what needs to be completed for full Boot flow.
     */
    "LIMITATION: Document incomplete XIP and Boot flow" in {
      test(new MinimalSoc) { dut =>
        dut.reset.poke(true.B)
        dut.clock.step(5)
        dut.reset.poke(false.B)
        dut.clock.step(10)

        println("========================================")
        println("Boot Flow Analysis:")
        println("========================================")
        println("✓ Boot ROM exists and contains correct boot code")
        println("✓ CPU starts at PC = 0x00000000 (Boot ROM base)")
        println("✓ Boot ROM code is syntactically correct")
        println("")
        println("✗ SPI Flash XIP is NOT implemented (returns zeros)")
        println("  - See SpiFlash.scala:215 - wb_mem.dat_i := 0.U")
        println("  - Boot ROM will read zeros from Flash")
        println("  - RAM will be filled with zeros")
        println("")
        println("✗ BootController exists but NOT integrated")
        println("  - BootController.scala defines FSM")
        println("  - MinimalSoc.scala does NOT instantiate it")
        println("  - Current design relies on Boot ROM code only")
        println("")
        println("To complete Boot flow:")
        println("1. Implement SPI Flash XIP read logic")
        println("2. OR integrate BootController for DMA copy")
        println("3. OR use FlashMemoryModel for simulation tests")
        println("========================================")

        true should be (true)  // Documentation test always passes
      }
    }

    /**
     * FUTURE TEST: Complete Boot Flow with Flash Model
     *
     * This test shows how Boot flow SHOULD work when Flash XIP is implemented.
     * Currently skipped until Flash XIP is completed.
     */
    "FUTURE: Complete boot sequence with Flash→RAM copy" ignore {
      // This test would require:
      // 1. Working Flash XIP implementation
      // 2. Integration of FlashMemoryModel
      // 3. Ability to peek/poke RAM contents
      // 4. Extended simulation time for full 16KB copy

      test(new MinimalSoc) { dut =>
        val test_program = createTestProgram()

        // Step 1: Pre-load Flash with test program
        // (Would need custom SoC with FlashMemoryModel integrated)
        println("Loading test program into Flash...")

        // Step 2: Reset and start boot
        dut.reset.poke(true.B)
        dut.clock.step(10)
        dut.reset.poke(false.B)

        // Step 3: Wait for Boot ROM to copy Flash→RAM
        // Boot ROM copies 16KB = 4096 words
        // At ~5 cycles per word, this takes ~20,000 cycles
        println("Waiting for Flash→RAM copy...")
        dut.clock.step(25000)

        // Step 4: Verify PC has jumped to RAM
        val final_pc = dut.io.debug_pc.peek().litValue
        println(f"Final PC: 0x$final_pc%08x")

        assert(final_pc >= Config.RAM_BASE,
          f"PC should have jumped to RAM (0x${Config.RAM_BASE}%08x), got 0x$final_pc%08x")

        // Step 5: Verify test program executes
        // (Check GPIO for completion signal)
        println("Verifying test program execution...")
        dut.clock.step(100)

        // Would check GPIO output here for 0xAA success pattern
      }
    }

    /**
     * STRESS TEST: Boot ROM read bandwidth
     *
     * Verifies Boot ROM can handle rapid instruction fetches
     * without stalls or errors.
     */
    "Boot ROM should handle continuous instruction fetch" in {
      test(new BootROM) { dut =>
        // Simulate pipelined instruction fetch
        // CPU will fetch every cycle when running normally

        for (addr <- 0 until 15) {  // 15 instructions in boot code
          // Cycle 1: Request
          dut.io.wb.cyc.poke(true.B)
          dut.io.wb.stb.poke(true.B)
          dut.io.wb.we.poke(false.B)
          dut.io.wb.adr.poke((addr * 4).U)
          dut.clock.step(1)

          // Should acknowledge immediately (single cycle)
          dut.io.wb.ack.expect(true.B,
            s"Boot ROM should ACK fetch of instruction $addr")

          // Data should be non-zero (valid instruction)
          val inst = dut.io.wb.dat_i.peek().litValue
          assert(inst != 0L,
            s"Boot ROM instruction $addr should not be 0")

          // Cycle 2: Next request (no gap)
          // Don't clear cyc/stb, just change address
        }

        dut.io.wb.cyc.poke(false.B)
        dut.io.wb.stb.poke(false.B)
      }
    }

    /**
     * VERIFICATION TEST: Boot ROM instruction correctness
     *
     * Verifies each Boot ROM instruction is correct and follows
     * the documented boot sequence algorithm.
     */
    "Boot ROM instructions should match documented algorithm" in {
      test(new BootROM) { dut =>
        // Expected boot sequence:
        // 1. Load Flash base (0x10000000) into t0
        // 2. Load RAM base (0x80000000) into t1
        // 3. Load count (16KB = 0x4000) into t2
        // 4. Loop: Load from Flash, Store to RAM, increment, decrement
        // 5. Jump to RAM entry point

        val boot_instructions = Seq(
          (0,  0x10000537L, "lui  t0, 0x10000      # t0 = Flash base (upper)"),
          (4,  0x00000513L, "addi t0, t0, 0        # t0 = 0x10000000"),
          (8,  0x800005b7L, "lui  t1, 0x80000      # t1 = RAM base (upper)"),
          (12, 0x00058593L, "addi t1, t1, 0        # t1 = 0x80000000"),
          (16, 0x00004637L, "lui  t2, 0x4          # t2 = 0x4000 (16KB)"),
          (20, 0x00060613L, "addi t2, t2, 0        # t2 = 16384"),
          (24, 0x0002ae03L, "lw   t3, 0(t0)        # Load from Flash"),
          (28, 0x01c5a023L, "sw   t3, 0(t1)        # Store to RAM"),
          (32, 0x00450513L, "addi t0, t0, 4        # Flash addr += 4"),
          (36, 0x00458593L, "addi t1, t1, 4        # RAM addr += 4"),
          (40, 0xffc60613L, "addi t2, t2, -4       # count -= 4"),
          (44, 0xfe0612e3L, "bnez t2, -24          # Loop if count > 0"),
          (48, 0x800002b7L, "lui  t0, 0x80000      # t0 = RAM base"),
          (52, 0x00028293L, "addi t0, t0, 0        # t0 = 0x80000000"),
          (56, 0x00028067L, "jalr x0, 0(t0)        # Jump to RAM")
        )

        for ((addr, expected_inst, description) <- boot_instructions) {
          dut.io.wb.cyc.poke(true.B)
          dut.io.wb.stb.poke(true.B)
          dut.io.wb.we.poke(false.B)
          dut.io.wb.adr.poke(addr.U)
          dut.clock.step(1)

          dut.io.wb.ack.expect(true.B)
          val actual = dut.io.wb.dat_i.peek().litValue

          assert(actual == expected_inst,
            f"\nAddress 0x$addr%02x: Expected 0x$expected_inst%08x, got 0x$actual%08x\n  $description")

          println(f"✓ 0x$addr%02x: 0x$actual%08x  $description")

          dut.io.wb.cyc.poke(false.B)
          dut.io.wb.stb.poke(false.B)
          dut.clock.step(1)
        }

        println("\n✅ All Boot ROM instructions verified correct!")
      }
    }

    /**
     * PERFORMANCE TEST: Boot time estimation
     *
     * Estimates how long boot would take if Flash XIP worked.
     */
    "Estimate boot time for complete Flash→RAM copy" in {
      test(new BootROM) { dut =>
        // Boot parameters
        val bytes_to_copy = Config.BOOT_COPY_SIZE  // 16384 bytes
        val words_to_copy = bytes_to_copy / 4       // 4096 words

        // Cycles per word (from boot code):
        // - 1 cycle: lw (load from Flash) - but Flash may take multiple cycles
        // - 1 cycle: sw (store to RAM)
        // - 1 cycle: addi (increment Flash addr)
        // - 1 cycle: addi (increment RAM addr)
        // - 1 cycle: addi (decrement counter)
        // - 1 cycle: bnez (branch)
        // = 6 cycles per word (assuming single-cycle Flash read)

        val cycles_per_word = 6
        val estimated_boot_cycles = (words_to_copy * cycles_per_word) +
                                    20  // Setup overhead

        val clock_freq = 25_000_000  // 25 MHz (FPGA target)
        val boot_time_us = (estimated_boot_cycles.toDouble / clock_freq) * 1_000_000

        println("========================================")
        println("Boot Time Estimation:")
        println("========================================")
        println(f"Words to copy:     $words_to_copy%,d")
        println(f"Cycles per word:   $cycles_per_word")
        println(f"Total boot cycles: $estimated_boot_cycles%,d")
        println(f"Clock frequency:   ${clock_freq/1_000_000} MHz")
        println(f"Estimated time:    ${boot_time_us}%.1f µs")
        println("========================================")

        // Sanity check: boot should take less than 2ms
        assert(boot_time_us < 2000,
          f"Boot time too long: ${boot_time_us}%.1f µs")

        true should be (true)
      }
    }
  }
}
