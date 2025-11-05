package rv32e.boot

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Test suite for Boot ROM
 */
class BootROMSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "BootROM"

  it should "respond with correct instruction at address 0" in {
    test(new BootROM) { dut =>
      // Request first instruction
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(0.U)
      dut.clock.step(1)

      // Should acknowledge
      dut.io.wb.ack.expect(true.B)

      // First instruction should be: lui t0, 0x10000 (0x10000537)
      dut.io.wb.dat_i.expect("h10000537".U)

      // De-assert transaction
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
    }
  }

  it should "read multiple sequential instructions" in {
    test(new BootROM) { dut =>
      val expected_instructions = Seq(
        "h10000537".U,  // lui  t0, 0x10000
        "h00000513".U,  // addi t0, t0, 0
        "h800005b7".U,  // lui  t1, 0x80000
        "h00058593".U,  // addi t1, t1, 0
        "h00004637".U,  // lui  t2, 0x4
        "h00060613".U   // addi t2, t2, 0
      )

      for ((expected, addr) <- expected_instructions.zipWithIndex) {
        dut.io.wb.cyc.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.we.poke(false.B)
        dut.io.wb.adr.poke((addr * 4).U)
        dut.clock.step(1)

        dut.io.wb.ack.expect(true.B)
        dut.io.wb.dat_i.expect(expected)

        dut.io.wb.cyc.poke(false.B)
        dut.io.wb.stb.poke(false.B)
        dut.clock.step(1)
      }
    }
  }

  it should "read the copy loop instructions" in {
    test(new BootROM) { dut =>
      val copy_loop_start = 6 // After initial setup (6 instructions)

      val loop_instructions = Seq(
        "h0002ae03".U,  // lw   t3, 0(t0)
        "h01c5a023".U,  // sw   t3, 0(t1)
        "h00450513".U,  // addi t0, t0, 4
        "h00458593".U,  // addi t1, t1, 4
        "hffc60613".U,  // addi t2, t2, -4
        "hfe0612e3".U   // bnez t2, copy_loop
      )

      for ((expected, offset) <- loop_instructions.zipWithIndex) {
        val addr = (copy_loop_start + offset) * 4
        dut.io.wb.cyc.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.we.poke(false.B)
        dut.io.wb.adr.poke(addr.U)
        dut.clock.step(1)

        dut.io.wb.ack.expect(true.B)
        dut.io.wb.dat_i.expect(expected)

        dut.io.wb.cyc.poke(false.B)
        dut.io.wb.stb.poke(false.B)
        dut.clock.step(1)
      }
    }
  }

  it should "read the final jump instructions" in {
    test(new BootROM) { dut =>
      val jump_start = 12 // After loop (12 instructions total before jump)

      val jump_instructions = Seq(
        "h800002b7".U,  // lui  t0, 0x80000
        "h00028293".U,  // addi t0, t0, 0
        "h00028067".U   // jalr x0, 0(t0)
      )

      for ((expected, offset) <- jump_instructions.zipWithIndex) {
        val addr = (jump_start + offset) * 4
        dut.io.wb.cyc.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.we.poke(false.B)
        dut.io.wb.adr.poke(addr.U)
        dut.clock.step(1)

        dut.io.wb.ack.expect(true.B)
        dut.io.wb.dat_i.expect(expected)

        dut.io.wb.cyc.poke(false.B)
        dut.io.wb.stb.poke(false.B)
        dut.clock.step(1)
      }
    }
  }

  it should "return NOP for unused ROM locations" in {
    test(new BootROM) { dut =>
      // Address beyond the 17 boot instructions (e.g., address 60)
      val unused_addr = 60 * 4

      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(unused_addr.U)
      dut.clock.step(1)

      dut.io.wb.ack.expect(true.B)
      // Should be NOP (addi x0, x0, 0 = 0x00000013)
      dut.io.wb.dat_i.expect("h00000013".U)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
    }
  }

  it should "ignore write attempts (read-only)" in {
    test(new BootROM) { dut =>
      // Try to write to Boot ROM
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(0.U)
      dut.io.wb.dat_o.poke("hDEADBEEF".U)
      dut.clock.step(1)

      // Should still acknowledge (but ignore write)
      dut.io.wb.ack.expect(true.B)

      // Read back - should be unchanged
      dut.io.wb.we.poke(false.B)
      dut.clock.step(1)

      dut.io.wb.ack.expect(true.B)
      dut.io.wb.dat_i.expect("h10000537".U) // Original instruction
    }
  }

  it should "handle rapid sequential reads" in {
    test(new BootROM) { dut =>
      // Simulate instruction fetch pattern
      for (i <- 0 until 10) {
        dut.io.wb.cyc.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.we.poke(false.B)
        dut.io.wb.adr.poke((i * 4).U)
        dut.clock.step(1)

        // Should always acknowledge within 1 cycle
        dut.io.wb.ack.expect(true.B)

        // Clear for next cycle
        dut.io.wb.cyc.poke(false.B)
        dut.io.wb.stb.poke(false.B)
        dut.clock.step(1)
      }
    }
  }

  it should "not acknowledge without strobe" in {
    test(new BootROM) { dut =>
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(false.B) // No strobe
      dut.io.wb.adr.poke(0.U)
      dut.clock.step(1)

      // Should not acknowledge without strobe
      dut.io.wb.ack.expect(false.B)
    }
  }

  it should "verify all 17 boot instructions are correct" in {
    test(new BootROM) { dut =>
      val all_boot_instructions = Seq(
        "h10000537".U,  // 0: lui  t0, 0x10000
        "h00000513".U,  // 1: addi t0, t0, 0
        "h800005b7".U,  // 2: lui  t1, 0x80000
        "h00058593".U,  // 3: addi t1, t1, 0
        "h00004637".U,  // 4: lui  t2, 0x4
        "h00060613".U,  // 5: addi t2, t2, 0
        "h0002ae03".U,  // 6: lw   t3, 0(t0)
        "h01c5a023".U,  // 7: sw   t3, 0(t1)
        "h00450513".U,  // 8: addi t0, t0, 4
        "h00458593".U,  // 9: addi t1, t1, 4
        "hffc60613".U,  // 10: addi t2, t2, -4
        "hfe0612e3".U,  // 11: bnez t2, copy_loop
        "h800002b7".U,  // 12: lui  t0, 0x80000
        "h00028293".U,  // 13: addi t0, t0, 0
        "h00028067".U   // 14: jalr x0, 0(t0)
      )

      // Verify each instruction
      for ((expected, addr) <- all_boot_instructions.zipWithIndex) {
        dut.io.wb.cyc.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.we.poke(false.B)
        dut.io.wb.adr.poke((addr * 4).U)
        dut.clock.step(1)

        dut.io.wb.ack.expect(true.B)
        dut.io.wb.dat_i.expect(expected,
          s"Instruction at address ${addr * 4} (0x${(addr * 4).toHexString})")

        dut.io.wb.cyc.poke(false.B)
        dut.io.wb.stb.poke(false.B)
        dut.clock.step(1)
      }
    }
  }
}
