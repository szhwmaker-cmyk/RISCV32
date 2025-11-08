package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/**
 * ImmGen 模块测试
 * 测试所有RISC-V立即数格式
 */
class ImmGenTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "ImmGen"

  it should "extract I-type immediate correctly" in {
    test(new ImmGen()) { dut =>
      dut.io.imm_type.poke(ImmType.I_TYPE)

      // ADDI x1, x2, 100
      // imm = 100 = 0x064
      val inst1 = "b0000_0110_0100_00010_000_00001_0010011".U
      dut.io.inst.poke(inst1)
      dut.io.imm.expect(100.S)

      // Test negative immediate
      // imm = -1 = 0xFFF (12-bit) = 0xFFFFFFFF (sign-extended 32-bit)
      val inst2 = "b1111_1111_1111_00010_000_00001_0010011".U
      dut.io.inst.poke(inst2)
      dut.io.imm.expect(-1.S)

      // Test maximum positive I-type immediate (2047)
      val inst3 = "b0111_1111_1111_00010_000_00001_0010011".U
      dut.io.inst.poke(inst3)
      dut.io.imm.expect(2047.S)

      // Test minimum negative I-type immediate (-2048)
      val inst4 = "b1000_0000_0000_00010_000_00001_0010011".U
      dut.io.inst.poke(inst4)
      dut.io.imm.expect(-2048.S)
    }
  }

  it should "extract S-type immediate correctly" in {
    test(new ImmGen()) { dut =>
      dut.io.imm_type.poke(ImmType.S_TYPE)

      // SW x2, 4(x3)
      // imm = 4 = imm[11:5]=0, imm[4:0]=4
      val inst1 = "b0000000_00010_00011_010_00100_0100011".U
      dut.io.inst.poke(inst1)
      dut.io.imm.expect(4.S)

      // Test negative S-type immediate
      // imm = -4 (0xFFC in 12-bit)
      val inst2 = "b1111111_00010_00011_010_11100_0100011".U
      dut.io.inst.poke(inst2)
      dut.io.imm.expect(-4.S)

      // Test maximum positive S-type (2047)
      val inst3 = "b0111111_00010_00011_010_11111_0100011".U
      dut.io.inst.poke(inst3)
      dut.io.imm.expect(2047.S)
    }
  }

  it should "extract B-type immediate correctly" in {
    test(new ImmGen()) { dut =>
      dut.io.imm_type.poke(ImmType.B_TYPE)

      // BEQ x1, x2, 8
      // imm = 8 = 0b0_000000_0100
      // Format: imm[12|10:5] rs2 rs1 funct3 imm[4:1|11]
      val inst1 = "b0_000000_00010_00001_000_0100_0_1100011".U
      dut.io.inst.poke(inst1)
      dut.io.imm.expect(8.S)

      // BEQ with negative offset (-4)
      // imm = -4 = 0b1_111111_1110 (13-bit, imm[0] always 0)
      val inst2 = "b1_111111_00010_00001_000_1110_1_1100011".U
      dut.io.inst.poke(inst2)
      dut.io.imm.expect(-4.S)

      // Test that LSB is always 0
      dut.io.imm.expect((dut.io.imm.peek().litValue & 1) == 0)
    }
  }

  it should "extract U-type immediate correctly" in {
    test(new ImmGen()) { dut =>
      dut.io.imm_type.poke(ImmType.U_TYPE)

      // LUI x1, 0x12345
      // imm[31:12] = 0x12345
      val inst1 = "b00010010001101000101_00001_0110111".U
      dut.io.inst.poke(inst1)
      dut.io.imm.expect(0x12345000L.S)

      // AUIPC with different immediate
      val inst2 = "b10000000000000000000_00010_0010111".U
      dut.io.inst.poke(inst2)
      dut.io.imm.expect(0x80000000L.S)

      // Test that lower 12 bits are always 0
      dut.io.imm.expect((dut.io.imm.peek().litValue & 0xFFF) == 0)
    }
  }

  it should "extract J-type immediate correctly" in {
    test(new ImmGen()) { dut =>
      dut.io.imm_type.poke(ImmType.J_TYPE)

      // JAL x1, offset
      // imm = 8 = 0b0_0000000000_0_00000100
      // Format: imm[20|10:1|11|19:12] rd opcode
      val inst1 = "b0_0000000100_0_00000000_00001_1101111".U
      dut.io.inst.poke(inst1)
      dut.io.imm.expect(8.S)

      // JAL with larger offset
      // imm = 0x800 = 2048
      val inst2 = "b0_0100000000_0_00000000_00001_1101111".U
      dut.io.inst.poke(inst2)
      dut.io.imm.expect(2048.S)

      // JAL with negative offset
      // imm = -4
      val inst3 = "b1_1111111110_1_11111111_00001_1101111".U
      dut.io.inst.poke(inst3)
      dut.io.imm.expect(-4.S)

      // Test that LSB is always 0
      dut.io.imm.expect((dut.io.imm.peek().litValue & 1) == 0)
    }
  }

  it should "output zero for ZERO type" in {
    test(new ImmGen()) { dut =>
      dut.io.imm_type.poke(ImmType.ZERO)

      // Any instruction should produce 0
      dut.io.inst.poke("hFFFFFFFF".U)
      dut.io.imm.expect(0.S)

      dut.io.inst.poke("h12345678".U)
      dut.io.imm.expect(0.S)
    }
  }

  it should "handle sign extension correctly" in {
    test(new ImmGen()) { dut =>
      // Test I-type sign extension
      dut.io.imm_type.poke(ImmType.I_TYPE)

      // imm[11] = 1 (negative), should sign-extend to 0xFFFFFFFF
      val inst1 = "b1000_0000_0001_00000_000_00000_0010011".U
      dut.io.inst.poke(inst1)
      val result1 = dut.io.imm.peek().litValue
      // Upper 20 bits should all be 1
      assert((result1 & 0xFFFFF000L) == 0xFFFFF000L)

      // imm[11] = 0 (positive), should sign-extend to 0x000007FF
      val inst2 = "b0111_1111_1111_00000_000_00000_0010011".U
      dut.io.inst.poke(inst2)
      val result2 = dut.io.imm.peek().litValue
      // Upper 20 bits should all be 0
      assert((result2 & 0xFFFFF000L) == 0)
    }
  }

  it should "handle all instruction formats in sequence" in {
    test(new ImmGen()) { dut =>
      // Test switching between different types
      val testCases = Seq(
        (ImmType.I_TYPE, "b0000_0000_0001_00000_000_00000_0010011".U, 1.S),
        (ImmType.S_TYPE, "b0000000_00000_00000_000_00010_0100011".U, 2.S),
        (ImmType.B_TYPE, "b0_000000_00000_00000_000_0100_0_1100011".U, 4.S),
        (ImmType.U_TYPE, "b00000000000000001000_00000_0110111".U, 0x1000.S),
        (ImmType.J_TYPE, "b0_0000001000_0_00000000_00000_1101111".U, 16.S),
        (ImmType.ZERO, "hFFFFFFFF".U, 0.S)
      )

      for ((immType, inst, expected) <- testCases) {
        dut.io.imm_type.poke(immType)
        dut.io.inst.poke(inst)
        dut.io.imm.expect(expected)
      }
    }
  }
}
