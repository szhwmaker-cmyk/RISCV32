package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import common.Config._

/**
 * ALU 单元测试
 * 测试算术逻辑单元的所有操作
 */
class ALUSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "ALU"

  it should "perform ADD operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.ADD)
      dut.io.src1.poke(10.U)
      dut.io.src2.poke(20.U)
      dut.clock.step(1)
      dut.io.out.expect(30.U, "10 + 20 should equal 30")
    }
  }

  it should "perform SUB operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.SUB)
      dut.io.src1.poke(50.U)
      dut.io.src2.poke(30.U)
      dut.clock.step(1)
      dut.io.out.expect(20.U, "50 - 30 should equal 20")
    }
  }

  it should "perform AND operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.AND)
      dut.io.src1.poke("hFF00FF00".U)
      dut.io.src2.poke("h0F0F0F0F".U)
      dut.clock.step(1)
      dut.io.out.expect("h0F000F00".U)
    }
  }

  it should "perform OR operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.OR)
      dut.io.src1.poke("hF0F0F0F0".U)
      dut.io.src2.poke("h0F0F0F0F".U)
      dut.clock.step(1)
      dut.io.out.expect("hFFFFFFFF".U)
    }
  }

  it should "perform XOR operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.XOR)
      dut.io.src1.poke("hAAAAAAAA".U)
      dut.io.src2.poke("h55555555".U)
      dut.clock.step(1)
      dut.io.out.expect("hFFFFFFFF".U)
    }
  }

  it should "perform SLL (shift left logical)" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.SLL)
      dut.io.src1.poke("h00000001".U)
      dut.io.src2.poke(4.U)
      dut.clock.step(1)
      dut.io.out.expect("h00000010".U, "1 << 4 = 16")
    }
  }

  it should "perform SRL (shift right logical)" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.SRL)
      dut.io.src1.poke("h80000000".U)
      dut.io.src2.poke(4.U)
      dut.clock.step(1)
      dut.io.out.expect("h08000000".U)
    }
  }

  it should "perform SRA (shift right arithmetic)" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.SRA)
      dut.io.src1.poke("h80000000".U)
      dut.io.src2.poke(4.U)
      dut.clock.step(1)
      dut.io.out.expect("hF8000000".U, "Should sign-extend")
    }
  }

  it should "perform SLT (set less than, signed)" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.SLT)

      // -1 < 1 (signed)
      dut.io.src1.poke("hFFFFFFFF".U)
      dut.io.src2.poke(1.U)
      dut.clock.step(1)
      dut.io.out.expect(1.U, "-1 < 1 should be true")

      // 1 < -1 (signed)
      dut.io.src1.poke(1.U)
      dut.io.src2.poke("hFFFFFFFF".U)
      dut.clock.step(1)
      dut.io.out.expect(0.U, "1 < -1 should be false")
    }
  }

  it should "perform SLTU (set less than, unsigned)" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.SLTU)

      // 0xFFFFFFFF > 1 (unsigned)
      dut.io.src1.poke("hFFFFFFFF".U)
      dut.io.src2.poke(1.U)
      dut.clock.step(1)
      dut.io.out.expect(0.U, "0xFFFFFFFF < 1 should be false (unsigned)")

      // 1 < 0xFFFFFFFF (unsigned)
      dut.io.src1.poke(1.U)
      dut.io.src2.poke("hFFFFFFFF".U)
      dut.clock.step(1)
      dut.io.out.expect(1.U, "1 < 0xFFFFFFFF should be true (unsigned)")
    }
  }

  it should "perform MUL (multiply)" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.MUL)
      dut.io.src1.poke(7.U)
      dut.io.src2.poke(8.U)
      dut.clock.step(1)
      dut.io.out.expect(56.U, "7 * 8 = 56")
    }
  }

  it should "perform DIV (divide)" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.DIV)
      dut.io.src1.poke(100.U)
      dut.io.src2.poke(10.U)
      dut.clock.step(1)
      dut.io.out.expect(10.U, "100 / 10 = 10")
    }
  }

  it should "handle divide by zero correctly" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.DIV)
      dut.io.src1.poke(100.U)
      dut.io.src2.poke(0.U)
      dut.clock.step(1)
      dut.io.out.expect("hFFFFFFFF".U, "Division by zero should return all 1s")
    }
  }

  it should "perform REM (remainder)" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.REM)
      dut.io.src1.poke(100.U)
      dut.io.src2.poke(30.U)
      dut.clock.step(1)
      dut.io.out.expect(10.U, "100 % 30 = 10")
    }
  }

  it should "handle overflow correctly" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.ADD)
      dut.io.src1.poke("hFFFFFFFF".U)
      dut.io.src2.poke(1.U)
      dut.clock.step(1)
      dut.io.out.expect(0.U, "Should wrap around on overflow")
    }
  }
}
