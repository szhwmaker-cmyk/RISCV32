package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/**
 * ALU 模块测试
 * 测试覆盖所有ALU操作
 */
class ALUTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "ALU"

  it should "perform ADD operation correctly" in {
    test(new ALU()) { dut =>
      dut.io.op.poke(ALUOp.ADD)
      dut.io.a.poke(10.U)
      dut.io.b.poke(20.U)
      dut.io.out.expect(30.U)
      dut.io.zero.expect(false.B)
    }
  }

  it should "perform SUB operation correctly" in {
    test(new ALU()) { dut =>
      dut.io.op.poke(ALUOp.SUB)
      dut.io.a.poke(30.U)
      dut.io.b.poke(10.U)
      dut.io.out.expect(20.U)
      dut.io.zero.expect(false.B)

      // Test zero flag
      dut.io.a.poke(10.U)
      dut.io.b.poke(10.U)
      dut.io.out.expect(0.U)
      dut.io.zero.expect(true.B)
    }
  }

  it should "perform SLT (signed comparison) correctly" in {
    test(new ALU()) { dut =>
      dut.io.op.poke(ALUOp.SLT)

      // Positive numbers
      dut.io.a.poke(5.U)
      dut.io.b.poke(10.U)
      dut.io.out.expect(1.U)

      dut.io.a.poke(10.U)
      dut.io.b.poke(5.U)
      dut.io.out.expect(0.U)

      // Negative vs positive
      dut.io.a.poke("hFFFFFFFF".U)  // -1
      dut.io.b.poke(1.U)
      dut.io.out.expect(1.U)  // -1 < 1
    }
  }

  it should "perform SLTU (unsigned comparison) correctly" in {
    test(new ALU()) { dut =>
      dut.io.op.poke(ALUOp.SLTU)

      dut.io.a.poke(5.U)
      dut.io.b.poke(10.U)
      dut.io.out.expect(1.U)

      // Unsigned comparison
      dut.io.a.poke("hFFFFFFFF".U)  // Maximum unsigned value
      dut.io.b.poke(1.U)
      dut.io.out.expect(0.U)  // 0xFFFFFFFF > 1 (unsigned)
    }
  }

  it should "perform AND operation correctly" in {
    test(new ALU()) { dut =>
      dut.io.op.poke(ALUOp.AND)
      dut.io.a.poke("hAAAAAAAA".U)
      dut.io.b.poke("h55555555".U)
      dut.io.out.expect(0.U)

      dut.io.a.poke("hFF00FF00".U)
      dut.io.b.poke("h0F0F0F0F".U)
      dut.io.out.expect("h0F000F00".U)
    }
  }

  it should "perform OR operation correctly" in {
    test(new ALU()) { dut =>
      dut.io.op.poke(ALUOp.OR)
      dut.io.a.poke("hAAAAAAAA".U)
      dut.io.b.poke("h55555555".U)
      dut.io.out.expect("hFFFFFFFF".U)
    }
  }

  it should "perform XOR operation correctly" in {
    test(new ALU()) { dut =>
      dut.io.op.poke(ALUOp.XOR)
      dut.io.a.poke("hAAAAAAAA".U)
      dut.io.b.poke("hAAAAAAAA".U)
      dut.io.out.expect(0.U)

      dut.io.a.poke("hAAAAAAAA".U)
      dut.io.b.poke("h55555555".U)
      dut.io.out.expect("hFFFFFFFF".U)
    }
  }

  it should "perform SLL (shift left logical) correctly" in {
    test(new ALU()) { dut =>
      dut.io.op.poke(ALUOp.SLL)
      dut.io.a.poke(1.U)
      dut.io.b.poke(4.U)  // Shift by 4
      dut.io.out.expect(16.U)

      dut.io.a.poke("h80000000".U)
      dut.io.b.poke(1.U)  // Shift left by 1
      dut.io.out.expect(0.U)  // Overflow
    }
  }

  it should "perform SRL (shift right logical) correctly" in {
    test(new ALU()) { dut =>
      dut.io.op.poke(ALUOp.SRL)
      dut.io.a.poke(16.U)
      dut.io.b.poke(4.U)  // Shift by 4
      dut.io.out.expect(1.U)

      // Test with MSB set
      dut.io.a.poke("h80000000".U)
      dut.io.b.poke(1.U)
      dut.io.out.expect("h40000000".U)  // Logical shift (zero fill)
    }
  }

  it should "perform SRA (shift right arithmetic) correctly" in {
    test(new ALU()) { dut =>
      dut.io.op.poke(ALUOp.SRA)

      // Positive number
      dut.io.a.poke(16.U)
      dut.io.b.poke(4.U)
      dut.io.out.expect(1.U)

      // Negative number (sign extension)
      dut.io.a.poke("h80000000".U)
      dut.io.b.poke(1.U)
      dut.io.out.expect("hC0000000".U)  // Arithmetic shift (sign fill)

      dut.io.a.poke("hFFFFFFFF".U)  // -1
      dut.io.b.poke(4.U)
      dut.io.out.expect("hFFFFFFFF".U)  // Still -1
    }
  }

  it should "perform COPY_A operation correctly" in {
    test(new ALU()) { dut =>
      dut.io.op.poke(ALUOp.COPY_A)
      dut.io.a.poke("h12345678".U)
      dut.io.b.poke("hABCDEF00".U)
      dut.io.out.expect("h12345678".U)
    }
  }

  it should "perform COPY_B operation correctly" in {
    test(new ALU()) { dut =>
      dut.io.op.poke(ALUOp.COPY_B)
      dut.io.a.poke("h12345678".U)
      dut.io.b.poke("hABCDEF00".U)
      dut.io.out.expect("hABCDEF00".U)
    }
  }

  it should "handle edge cases" in {
    test(new ALU()) { dut =>
      // Maximum values
      dut.io.op.poke(ALUOp.ADD)
      dut.io.a.poke("hFFFFFFFF".U)
      dut.io.b.poke(1.U)
      dut.io.out.expect(0.U)  // Overflow wraps around
      dut.io.zero.expect(true.B)

      // Zero operands
      dut.io.op.poke(ALUOp.ADD)
      dut.io.a.poke(0.U)
      dut.io.b.poke(0.U)
      dut.io.out.expect(0.U)
      dut.io.zero.expect(true.B)

      // Large shift amounts (should use only lower 5 bits)
      dut.io.op.poke(ALUOp.SLL)
      dut.io.a.poke(1.U)
      dut.io.b.poke(32.U)  // Same as shift by 0 (32 & 0x1F = 0)
      dut.io.out.expect(1.U)

      dut.io.b.poke(33.U)  // Same as shift by 1 (33 & 0x1F = 1)
      dut.io.out.expect(2.U)
    }
  }
}
