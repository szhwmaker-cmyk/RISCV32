package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import common.AluOp

class ALUSpec extends AnyFlatSpec with ChiselScalatestTester {
  "ALU" should "perform ADD correctly" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.ADD)
      dut.io.src1.poke(10.U)
      dut.io.src2.poke(20.U)
      dut.clock.step()
      dut.io.out.expect(30.U)
      dut.io.zero.expect(false.B)
    }
  }

  it should "perform SUB correctly" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.SUB)
      dut.io.src1.poke(50.U)
      dut.io.src2.poke(30.U)
      dut.clock.step()
      dut.io.out.expect(20.U)
    }
  }

  it should "perform AND correctly" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.AND)
      dut.io.src1.poke(0xFF.U)
      dut.io.src2.poke(0x0F.U)
      dut.clock.step()
      dut.io.out.expect(0x0F.U)
    }
  }

  it should "perform SLL correctly" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.SLL)
      dut.io.src1.poke(1.U)
      dut.io.src2.poke(4.U)
      dut.clock.step()
      dut.io.out.expect(16.U)
    }
  }

  it should "set zero flag correctly" in {
    test(new ALU) { dut =>
      dut.io.op.poke(AluOp.SUB)
      dut.io.src1.poke(100.U)
      dut.io.src2.poke(100.U)
      dut.clock.step()
      dut.io.out.expect(0.U)
      dut.io.zero.expect(true.B)
    }
  }
}
