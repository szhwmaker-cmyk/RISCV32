package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/**
 * ALU 单元测试
 *
 * 测试所有 ALU 操作
 */
class ALUTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "ALU"

  it should "perform ADD operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.ADD)
      dut.io.src1.poke(100.U)
      dut.io.src2.poke(50.U)
      dut.io.out.expect(150.U)
      dut.io.zero.expect(false.B)
    }
  }

  it should "perform SUB operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.SUB)
      dut.io.src1.poke(100.U)
      dut.io.src2.poke(50.U)
      dut.io.out.expect(50.U)

      // Test zero flag
      dut.io.src1.poke(50.U)
      dut.io.src2.poke(50.U)
      dut.io.out.expect(0.U)
      dut.io.zero.expect(true.B)
    }
  }

  it should "perform AND operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.AND)
      dut.io.src1.poke(0xFF00FF00L.U)
      dut.io.src2.poke(0xF0F0F0F0L.U)
      dut.io.out.expect(0xF000F000L.U)
    }
  }

  it should "perform OR operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.OR)
      dut.io.src1.poke(0xFF00FF00L.U)
      dut.io.src2.poke(0x00FF00FFL.U)
      dut.io.out.expect(0xFFFFFFFFL.U)
    }
  }

  it should "perform XOR operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.XOR)
      dut.io.src1.poke(0xAAAAAAAAL.U)
      dut.io.src2.poke(0x55555555L.U)
      dut.io.out.expect(0xFFFFFFFFL.U)
    }
  }

  it should "perform SLL (shift left logical)" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.SLL)
      dut.io.src1.poke(1.U)
      dut.io.src2.poke(4.U)
      dut.io.out.expect(16.U)
    }
  }

  it should "perform SRL (shift right logical)" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.SRL)
      dut.io.src1.poke(0x80000000L.U)
      dut.io.src2.poke(4.U)
      dut.io.out.expect(0x08000000L.U)
    }
  }

  it should "perform SRA (shift right arithmetic)" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.SRA)
      dut.io.src1.poke(0x80000000L.U) // Negative number
      dut.io.src2.poke(4.U)
      dut.io.out.expect(0xF8000000L.U) // Sign extended
    }
  }

  it should "perform SLT (set less than signed)" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.SLT)

      // -1 < 1 (signed)
      dut.io.src1.poke(0xFFFFFFFFL.U)
      dut.io.src2.poke(1.U)
      dut.io.out.expect(1.U)

      // 1 < -1 (signed) = false
      dut.io.src1.poke(1.U)
      dut.io.src2.poke(0xFFFFFFFFL.U)
      dut.io.out.expect(0.U)
    }
  }

  it should "perform SLTU (set less than unsigned)" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.SLTU)

      // 0xFFFFFFFF > 1 (unsigned)
      dut.io.src1.poke(0xFFFFFFFFL.U)
      dut.io.src2.poke(1.U)
      dut.io.out.expect(0.U)

      // 1 < 0xFFFFFFFF (unsigned)
      dut.io.src1.poke(1.U)
      dut.io.src2.poke(0xFFFFFFFFL.U)
      dut.io.out.expect(1.U)
    }
  }
}
