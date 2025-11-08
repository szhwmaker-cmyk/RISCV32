package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/**
 * ALU 单元测试
 */
class ALUSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "ALU"

  it should "perform ADD operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.ADD)
      dut.io.src1.poke(10.U)
      dut.io.src2.poke(20.U)
      dut.io.out.expect(30.U)
      dut.io.zero.expect(false.B)
    }
  }

  it should "perform SUB operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.SUB)
      dut.io.src1.poke(50.U)
      dut.io.src2.poke(30.U)
      dut.io.out.expect(20.U)
      dut.io.zero.expect(false.B)
    }
  }

  it should "perform AND operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.AND)
      dut.io.src1.poke(0xFF.U)
      dut.io.src2.poke(0x0F.U)
      dut.io.out.expect(0x0F.U)
    }
  }

  it should "perform OR operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.OR)
      dut.io.src1.poke(0xF0.U)
      dut.io.src2.poke(0x0F.U)
      dut.io.out.expect(0xFF.U)
    }
  }

  it should "perform XOR operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.XOR)
      dut.io.src1.poke(0xFF.U)
      dut.io.src2.poke(0xFF.U)
      dut.io.out.expect(0.U)
      dut.io.zero.expect(true.B)
    }
  }

  it should "perform SLL operation" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.SLL)
      dut.io.src1.poke(1.U)
      dut.io.src2.poke(4.U)  // shift amount
      dut.io.out.expect(16.U)
    }
  }
}
