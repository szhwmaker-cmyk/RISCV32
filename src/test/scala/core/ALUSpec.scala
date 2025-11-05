package rv32e.core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Test suite for ALU
 */
class ALUSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "ALU"

  it should "perform ADD operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(ALUOp.ADD)
      dut.io.src1.poke(10.U)
      dut.io.src2.poke(20.U)
      dut.clock.step(1)
      dut.io.out.expect(30.U)
      dut.io.zero.expect(false.B)

      // Test overflow (unsigned wrapping)
      dut.io.src1.poke(0xFFFFFFFF.U)
      dut.io.src2.poke(1.U)
      dut.clock.step(1)
      dut.io.out.expect(0.U)
      dut.io.zero.expect(true.B)
    }
  }

  it should "perform SUB operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(ALUOp.SUB)
      dut.io.src1.poke(50.U)
      dut.io.src2.poke(30.U)
      dut.clock.step(1)
      dut.io.out.expect(20.U)

      // Test underflow (unsigned wrapping)
      dut.io.src1.poke(10.U)
      dut.io.src2.poke(20.U)
      dut.clock.step(1)
      dut.io.out.expect(0xFFFFFFF6.U) // -10 in 2's complement
    }
  }

  it should "perform AND operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(ALUOp.AND)
      dut.io.src1.poke(0xFF00FF00.U)
      dut.io.src2.poke(0xF0F0F0F0.U)
      dut.clock.step(1)
      dut.io.out.expect(0xF000F000.U)
    }
  }

  it should "perform OR operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(ALUOp.OR)
      dut.io.src1.poke(0x0F0F0F0F.U)
      dut.io.src2.poke(0xF0F0F0F0.U)
      dut.clock.step(1)
      dut.io.out.expect(0xFFFFFFFF.U)
    }
  }

  it should "perform XOR operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(ALUOp.XOR)
      dut.io.src1.poke(0xAAAAAAAA.U)
      dut.io.src2.poke(0xFFFFFFFF.U)
      dut.clock.step(1)
      dut.io.out.expect(0x55555555.U)
    }
  }

  it should "perform SLL (shift left logical)" in {
    test(new ALU) { dut =>
      dut.io.op.poke(ALUOp.SLL)
      dut.io.src1.poke(0x00000001.U)
      dut.io.src2.poke(4.U)
      dut.clock.step(1)
      dut.io.out.expect(0x00000010.U)

      // Shift by 31
      dut.io.src1.poke(0x00000001.U)
      dut.io.src2.poke(31.U)
      dut.clock.step(1)
      dut.io.out.expect(0x80000000.U)

      // Shift amount uses only lower 5 bits
      dut.io.src1.poke(0x00000001.U)
      dut.io.src2.poke(36.U) // 36 % 32 = 4
      dut.clock.step(1)
      dut.io.out.expect(0x00000010.U)
    }
  }

  it should "perform SRL (shift right logical)" in {
    test(new ALU) { dut =>
      dut.io.op.poke(ALUOp.SRL)
      dut.io.src1.poke(0x80000000.U)
      dut.io.src2.poke(4.U)
      dut.clock.step(1)
      dut.io.out.expect(0x08000000.U)

      // Shift by 31
      dut.io.src1.poke(0x80000000.U)
      dut.io.src2.poke(31.U)
      dut.clock.step(1)
      dut.io.out.expect(0x00000001.U)
    }
  }

  it should "perform SRA (shift right arithmetic)" in {
    test(new ALU) { dut =>
      dut.io.op.poke(ALUOp.SRA)

      // Positive number (sign bit = 0)
      dut.io.src1.poke(0x40000000.U)
      dut.io.src2.poke(4.U)
      dut.clock.step(1)
      dut.io.out.expect(0x04000000.U)

      // Negative number (sign bit = 1, should extend with 1s)
      dut.io.src1.poke(0x80000000.U)
      dut.io.src2.poke(4.U)
      dut.clock.step(1)
      dut.io.out.expect(0xF8000000.U)

      // Shift negative by 31
      dut.io.src1.poke(0x80000000.U)
      dut.io.src2.poke(31.U)
      dut.clock.step(1)
      dut.io.out.expect(0xFFFFFFFF.U)
    }
  }

  it should "perform SLT (set less than signed)" in {
    test(new ALU) { dut =>
      dut.io.op.poke(ALUOp.SLT)

      // 10 < 20 (true)
      dut.io.src1.poke(10.U)
      dut.io.src2.poke(20.U)
      dut.clock.step(1)
      dut.io.out.expect(1.U)

      // 20 < 10 (false)
      dut.io.src1.poke(20.U)
      dut.io.src2.poke(10.U)
      dut.clock.step(1)
      dut.io.out.expect(0.U)

      // -10 < 10 (true in signed comparison)
      dut.io.src1.poke(0xFFFFFFF6.U) // -10
      dut.io.src2.poke(10.U)
      dut.clock.step(1)
      dut.io.out.expect(1.U)

      // 10 < -10 (false)
      dut.io.src1.poke(10.U)
      dut.io.src2.poke(0xFFFFFFF6.U) // -10
      dut.clock.step(1)
      dut.io.out.expect(0.U)
    }
  }

  it should "perform SLTU (set less than unsigned)" in {
    test(new ALU) { dut =>
      dut.io.op.poke(ALUOp.SLTU)

      // 10 < 20 (true)
      dut.io.src1.poke(10.U)
      dut.io.src2.poke(20.U)
      dut.clock.step(1)
      dut.io.out.expect(1.U)

      // 0xFFFFFFF6 < 10 (false in unsigned comparison)
      dut.io.src1.poke(0xFFFFFFF6.U)
      dut.io.src2.poke(10.U)
      dut.clock.step(1)
      dut.io.out.expect(0.U)

      // 10 < 0xFFFFFFF6 (true in unsigned)
      dut.io.src1.poke(10.U)
      dut.io.src2.poke(0xFFFFFFF6.U)
      dut.clock.step(1)
      dut.io.out.expect(1.U)
    }
  }

  it should "perform COPY1 operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(ALUOp.COPY1)
      dut.io.src1.poke(0x12345678.U)
      dut.io.src2.poke(0xABCDEF00.U)
      dut.clock.step(1)
      dut.io.out.expect(0x12345678.U)
    }
  }

  it should "perform COPY2 operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(ALUOp.COPY2)
      dut.io.src1.poke(0x12345678.U)
      dut.io.src2.poke(0xABCDEF00.U)
      dut.clock.step(1)
      dut.io.out.expect(0xABCDEF00.U)
    }
  }

  it should "correctly set zero flag" in {
    test(new ALU) { dut =>
      dut.io.op.poke(ALUOp.ADD)

      // Non-zero result
      dut.io.src1.poke(10.U)
      dut.io.src2.poke(20.U)
      dut.clock.step(1)
      dut.io.zero.expect(false.B)

      // Zero result
      dut.io.src1.poke(0.U)
      dut.io.src2.poke(0.U)
      dut.clock.step(1)
      dut.io.zero.expect(true.B)

      // SUB resulting in zero
      dut.io.op.poke(ALUOp.SUB)
      dut.io.src1.poke(50.U)
      dut.io.src2.poke(50.U)
      dut.clock.step(1)
      dut.io.zero.expect(true.B)
    }
  }
}
