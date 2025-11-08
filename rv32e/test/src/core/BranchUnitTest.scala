package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/**
 * Branch Unit 测试
 */
class BranchUnitTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "BranchUnit"

  it should "handle BEQ (branch if equal) correctly" in {
    test(new BranchUnit()) { dut =>
      dut.io.branch_type.poke(BranchType.BEQ)
      dut.io.pc.poke(0x1000.U)
      dut.io.imm.poke(8.S)

      // Equal values - should branch
      dut.io.rs1_data.poke(10.U)
      dut.io.rs2_data.poke(10.U)
      dut.io.taken.expect(true.B)
      dut.io.target.expect(0x1008.U)  // PC + 8

      // Not equal - should not branch
      dut.io.rs1_data.poke(10.U)
      dut.io.rs2_data.poke(20.U)
      dut.io.taken.expect(false.B)
    }
  }

  it should "handle BNE (branch if not equal) correctly" in {
    test(new BranchUnit()) { dut =>
      dut.io.branch_type.poke(BranchType.BNE)
      dut.io.pc.poke(0x2000.U)
      dut.io.imm.poke(-4.S)

      // Not equal - should branch
      dut.io.rs1_data.poke(10.U)
      dut.io.rs2_data.poke(20.U)
      dut.io.taken.expect(true.B)
      dut.io.target.expect(0x1FFC.U)  // PC - 4

      // Equal - should not branch
      dut.io.rs1_data.poke(15.U)
      dut.io.rs2_data.poke(15.U)
      dut.io.taken.expect(false.B)
    }
  }

  it should "handle BLT (branch if less than, signed) correctly" in {
    test(new BranchUnit()) { dut =>
      dut.io.branch_type.poke(BranchType.BLT)
      dut.io.pc.poke(0x3000.U)
      dut.io.imm.poke(16.S)

      // rs1 < rs2 (signed) - should branch
      dut.io.rs1_data.poke(5.U)
      dut.io.rs2_data.poke(10.U)
      dut.io.taken.expect(true.B)
      dut.io.target.expect(0x3010.U)

      // rs1 >= rs2 - should not branch
      dut.io.rs1_data.poke(10.U)
      dut.io.rs2_data.poke(5.U)
      dut.io.taken.expect(false.B)

      // Test with negative numbers
      dut.io.rs1_data.poke("hFFFFFFFF".U)  // -1 (signed)
      dut.io.rs2_data.poke(1.U)
      dut.io.taken.expect(true.B)  // -1 < 1

      dut.io.rs1_data.poke(1.U)
      dut.io.rs2_data.poke("hFFFFFFFF".U)  // -1 (signed)
      dut.io.taken.expect(false.B)  // 1 >= -1
    }
  }

  it should "handle BGE (branch if greater or equal, signed) correctly" in {
    test(new BranchUnit()) { dut =>
      dut.io.branch_type.poke(BranchType.BGE)
      dut.io.pc.poke(0x4000.U)
      dut.io.imm.poke(12.S)

      // rs1 >= rs2 - should branch
      dut.io.rs1_data.poke(10.U)
      dut.io.rs2_data.poke(5.U)
      dut.io.taken.expect(true.B)

      dut.io.rs1_data.poke(10.U)
      dut.io.rs2_data.poke(10.U)
      dut.io.taken.expect(true.B)  // Equal case

      // rs1 < rs2 - should not branch
      dut.io.rs1_data.poke(5.U)
      dut.io.rs2_data.poke(10.U)
      dut.io.taken.expect(false.B)

      // Test with negative numbers
      dut.io.rs1_data.poke(1.U)
      dut.io.rs2_data.poke("hFFFFFFFF".U)  // -1
      dut.io.taken.expect(true.B)  // 1 >= -1
    }
  }

  it should "handle BLTU (branch if less than, unsigned) correctly" in {
    test(new BranchUnit()) { dut =>
      dut.io.branch_type.poke(BranchType.BLTU)
      dut.io.pc.poke(0x5000.U)
      dut.io.imm.poke(20.S)

      // rs1 < rs2 (unsigned) - should branch
      dut.io.rs1_data.poke(5.U)
      dut.io.rs2_data.poke(10.U)
      dut.io.taken.expect(true.B)

      // rs1 >= rs2 - should not branch
      dut.io.rs1_data.poke(10.U)
      dut.io.rs2_data.poke(5.U)
      dut.io.taken.expect(false.B)

      // Test unsigned comparison
      dut.io.rs1_data.poke(1.U)
      dut.io.rs2_data.poke("hFFFFFFFF".U)  // Maximum unsigned
      dut.io.taken.expect(true.B)  // 1 < 0xFFFFFFFF (unsigned)

      dut.io.rs1_data.poke("hFFFFFFFF".U)
      dut.io.rs2_data.poke(1.U)
      dut.io.taken.expect(false.B)  // 0xFFFFFFFF >= 1
    }
  }

  it should "handle BGEU (branch if greater or equal, unsigned) correctly" in {
    test(new BranchUnit()) { dut =>
      dut.io.branch_type.poke(BranchType.BGEU)
      dut.io.pc.poke(0x6000.U)
      dut.io.imm.poke(24.S)

      // rs1 >= rs2 (unsigned) - should branch
      dut.io.rs1_data.poke(10.U)
      dut.io.rs2_data.poke(5.U)
      dut.io.taken.expect(true.B)

      dut.io.rs1_data.poke(10.U)
      dut.io.rs2_data.poke(10.U)
      dut.io.taken.expect(true.B)

      // rs1 < rs2 - should not branch
      dut.io.rs1_data.poke(5.U)
      dut.io.rs2_data.poke(10.U)
      dut.io.taken.expect(false.B)

      // Test unsigned comparison
      dut.io.rs1_data.poke("hFFFFFFFF".U)
      dut.io.rs2_data.poke(1.U)
      dut.io.taken.expect(true.B)  // 0xFFFFFFFF >= 1
    }
  }

  it should "handle JAL (jump and link) correctly" in {
    test(new BranchUnit()) { dut =>
      dut.io.branch_type.poke(BranchType.JAL)
      dut.io.pc.poke(0x1000.U)
      dut.io.imm.poke(100.S)

      // JAL always jumps
      dut.io.rs1_data.poke(0.U)  // Don't care
      dut.io.rs2_data.poke(0.U)  // Don't care
      dut.io.taken.expect(true.B)
      dut.io.target.expect(0x1064.U)  // PC + 100

      // Test negative offset
      dut.io.pc.poke(0x2000.U)
      dut.io.imm.poke(-8.S)
      dut.io.taken.expect(true.B)
      dut.io.target.expect(0x1FF8.U)  // PC - 8
    }
  }

  it should "handle JALR (jump and link register) correctly" in {
    test(new BranchUnit()) { dut =>
      dut.io.branch_type.poke(BranchType.JALR)

      // JALR: target = (rs1 + imm) & ~1
      dut.io.rs1_data.poke(0x1000.U)
      dut.io.imm.poke(100.S)
      dut.io.pc.poke(0x5000.U)  // PC不影响JALR

      dut.io.taken.expect(true.B)
      dut.io.target.expect(0x1064.U)  // rs1 + imm

      // Test LSB clearing (alignment)
      dut.io.rs1_data.poke(0x1000.U)
      dut.io.imm.poke(101.S)  // Odd offset
      dut.io.target.expect(0x1064.U)  // (0x1000 + 101) & ~1 = 0x1064

      // Test with negative offset
      dut.io.rs1_data.poke(0x2000.U)
      dut.io.imm.poke(-4.S)
      dut.io.target.expect(0x1FFC.U)  // 0x2000 - 4
    }
  }

  it should "calculate branch targets correctly for various offsets" in {
    test(new BranchUnit()) { dut =>
      dut.io.branch_type.poke(BranchType.BEQ)
      dut.io.rs1_data.poke(5.U)
      dut.io.rs2_data.poke(5.U)  // Make branch taken

      // Test various PC and offset combinations
      val testCases = Seq(
        (0x0000.U, 4.S, 0x0004.U),
        (0x1000.U, 8.S, 0x1008.U),
        (0x2000.U, -4.S, 0x1FFC.U),
        (0xFFFF.U, 1.S, 0x10000.U),
        (0x8000.U, -0x1000.S, 0x7000.U)
      )

      for ((pc, imm, expected) <- testCases) {
        dut.io.pc.poke(pc)
        dut.io.imm.poke(imm)
        dut.io.target.expect(expected)
      }
    }
  }

  it should "ensure JALR target is always aligned (LSB = 0)" in {
    test(new BranchUnit()) { dut =>
      dut.io.branch_type.poke(BranchType.JALR)
      dut.io.pc.poke(0.U)

      // Test various odd addresses
      val testCases = Seq(
        (0x1000.U, 1.S, 0x1000.U),    // 0x1001 -> 0x1000
        (0x1000.U, 3.S, 0x1002.U),    // 0x1003 -> 0x1002
        (0x2000.U, 5.S, 0x2004.U),    // 0x2005 -> 0x2004
        (0x3001.U, 0.S, 0x3000.U)     // 0x3001 -> 0x3000
      )

      for ((rs1, imm, expected) <- testCases) {
        dut.io.rs1_data.poke(rs1)
        dut.io.imm.poke(imm)
        dut.io.target.expect(expected)
        // Verify LSB is 0
        assert((dut.io.target.peek().litValue & 1) == 0)
      }
    }
  }
}

/**
 * Simple Branch Predictor 测试
 */
class SimpleBranchPredictorTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "SimpleBranchPredictor"

  it should "predict JAL and JALR as always taken" in {
    test(new SimpleBranchPredictor()) { dut =>
      dut.io.is_branch.poke(true.B)
      dut.io.pc.poke(0x1000.U)

      // JAL should be predicted as taken
      dut.io.branch_type.poke(BranchType.JAL)
      dut.io.imm.poke(100.S)
      dut.io.predict_taken.expect(true.B)
      dut.io.predict_target.expect(0x1064.U)

      // JALR should be predicted as taken
      dut.io.branch_type.poke(BranchType.JALR)
      dut.io.imm.poke(-50.S)
      dut.io.predict_taken.expect(true.B)
    }
  }

  it should "predict backward branches as taken" in {
    test(new SimpleBranchPredictor()) { dut =>
      dut.io.is_branch.poke(true.B)
      dut.io.pc.poke(0x2000.U)
      dut.io.branch_type.poke(BranchType.BEQ)

      // Negative offset (backward branch) - predict taken
      dut.io.imm.poke(-8.S)
      dut.io.predict_taken.expect(true.B)
      dut.io.predict_target.expect(0x1FF8.U)

      dut.io.imm.poke(-100.S)
      dut.io.predict_taken.expect(true.B)
    }
  }

  it should "predict forward branches as not taken" in {
    test(new SimpleBranchPredictor()) { dut =>
      dut.io.is_branch.poke(true.B)
      dut.io.pc.poke(0x3000.U)
      dut.io.branch_type.poke(BranchType.BNE)

      // Positive offset (forward branch) - predict not taken
      dut.io.imm.poke(8.S)
      dut.io.predict_taken.expect(false.B)

      dut.io.imm.poke(100.S)
      dut.io.predict_taken.expect(false.B)
    }
  }

  it should "not predict when is_branch is false" in {
    test(new SimpleBranchPredictor()) { dut =>
      dut.io.is_branch.poke(false.B)
      dut.io.pc.poke(0x1000.U)
      dut.io.branch_type.poke(BranchType.JAL)
      dut.io.imm.poke(-100.S)

      // Even for JAL and backward branch, should not predict taken
      dut.io.predict_taken.expect(false.B)
    }
  }
}
