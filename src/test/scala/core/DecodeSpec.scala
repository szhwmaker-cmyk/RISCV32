package rv32e.core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Test suite for Instruction Decoder
 */
class DecodeSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "Decode"

  it should "decode R-type ADD instruction" in {
    test(new Decode) { dut =>
      // ADD x1, x2, x3 (0x003100B3)
      // 0000000 00011 00010 000 00001 0110011
      dut.io.inst.poke("b00000000001100010000000010110011".U)
      dut.clock.step(1)

      dut.io.rs1.expect(2.U)
      dut.io.rs2.expect(3.U)
      dut.io.rd.expect(1.U)
      dut.io.ctrl.alu_op.expect(ALUOp.ADD)
      dut.io.ctrl.alu_src.expect(false.B)
      dut.io.ctrl.reg_write.expect(true.B)
      dut.io.ctrl.mem_read.expect(false.B)
      dut.io.ctrl.mem_write.expect(false.B)
    }
  }

  it should "decode R-type SUB instruction" in {
    test(new Decode) { dut =>
      // SUB x4, x5, x6 (0x406282B3)
      // 0100000 00110 00101 000 00100 0110011
      dut.io.inst.poke("b01000000011000101000001000110011".U)
      dut.clock.step(1)

      dut.io.rs1.expect(5.U)
      dut.io.rs2.expect(6.U)
      dut.io.rd.expect(4.U)
      dut.io.ctrl.alu_op.expect(ALUOp.SUB)
      dut.io.ctrl.alu_src.expect(false.B)
      dut.io.ctrl.reg_write.expect(true.B)
    }
  }

  it should "decode I-type ADDI instruction" in {
    test(new Decode) { dut =>
      // ADDI x1, x2, 10 (0x00A10093)
      // 000000001010 00010 000 00001 0010011
      dut.io.inst.poke("b00000000101000010000000010010011".U)
      dut.clock.step(1)

      dut.io.rs1.expect(2.U)
      dut.io.rd.expect(1.U)
      dut.io.imm.expect(10.U)
      dut.io.ctrl.alu_op.expect(ALUOp.ADD)
      dut.io.ctrl.alu_src.expect(true.B)
      dut.io.ctrl.reg_write.expect(true.B)
    }
  }

  it should "decode Load Word (LW) instruction" in {
    test(new Decode) { dut =>
      // LW x3, 8(x2) (0x00812183)
      // 000000001000 00010 010 00011 0000011
      dut.io.inst.poke("b00000000100000010010000110000011".U)
      dut.clock.step(1)

      dut.io.rs1.expect(2.U)
      dut.io.rd.expect(3.U)
      dut.io.imm.expect(8.U)
      dut.io.ctrl.alu_op.expect(ALUOp.ADD)
      dut.io.ctrl.alu_src.expect(true.B)
      dut.io.ctrl.mem_read.expect(true.B)
      dut.io.ctrl.mem_write.expect(false.B)
      dut.io.ctrl.mem_size.expect(2.U) // Word
      dut.io.ctrl.reg_write.expect(true.B)
      dut.io.ctrl.wb_sel.expect(WBSel.MEM)
    }
  }

  it should "decode Store Word (SW) instruction" in {
    test(new Decode) { dut =>
      // SW x3, 4(x2) (0x00312223)
      // 0000000 00011 00010 010 00100 0100011
      dut.io.inst.poke("b00000000001100010010001000100011".U)
      dut.clock.step(1)

      dut.io.rs1.expect(2.U)
      dut.io.rs2.expect(3.U)
      dut.io.imm.expect(4.U)
      dut.io.ctrl.alu_op.expect(ALUOp.ADD)
      dut.io.ctrl.alu_src.expect(true.B)
      dut.io.ctrl.mem_write.expect(true.B)
      dut.io.ctrl.mem_read.expect(false.B)
      dut.io.ctrl.reg_write.expect(false.B)
    }
  }

  it should "decode BEQ instruction" in {
    test(new Decode) { dut =>
      // BEQ x1, x2, 8 (0x00208463)
      // 0 000010 00010 00001 000 0100 0 1100011
      dut.io.inst.poke("b00000000001000001000010001100011".U)
      dut.clock.step(1)

      dut.io.rs1.expect(1.U)
      dut.io.rs2.expect(2.U)
      dut.io.imm.expect(8.U)
      dut.io.ctrl.branch.expect(true.B)
      dut.io.ctrl.branch_op.expect(BranchOp.BEQ)
      dut.io.ctrl.reg_write.expect(false.B)
    }
  }

  it should "decode JAL instruction" in {
    test(new Decode) { dut =>
      // JAL x1, 20 (0x014000EF)
      // 0 0000001010 0 00000000 00001 1101111
      dut.io.inst.poke("b00000000010100000000000011101111".U)
      dut.clock.step(1)

      dut.io.rd.expect(1.U)
      dut.io.imm.expect(20.U)
      dut.io.ctrl.jump.expect(true.B)
      dut.io.ctrl.reg_write.expect(true.B)
      dut.io.ctrl.wb_sel.expect(WBSel.PC4)
    }
  }

  it should "decode JALR instruction" in {
    test(new Decode) { dut =>
      // JALR x1, 4(x2) (0x004100E7)
      // 000000000100 00010 000 00001 1100111
      dut.io.inst.poke("b00000000010000010000000011100111".U)
      dut.clock.step(1)

      dut.io.rs1.expect(2.U)
      dut.io.rd.expect(1.U)
      dut.io.imm.expect(4.U)
      dut.io.ctrl.jump.expect(true.B)
      dut.io.ctrl.alu_src.expect(true.B)
      dut.io.ctrl.reg_write.expect(true.B)
      dut.io.ctrl.wb_sel.expect(WBSel.PC4)
    }
  }

  it should "decode LUI instruction" in {
    test(new Decode) { dut =>
      // LUI x1, 0x12345 (0x123450B7)
      // 00010010001101000101 00001 0110111
      dut.io.inst.poke("b00010010001101000101000010110111".U)
      dut.clock.step(1)

      dut.io.rd.expect(1.U)
      dut.io.imm.expect(0x12345000L.U)
      dut.io.ctrl.alu_op.expect(ALUOp.COPY2)
      dut.io.ctrl.alu_src.expect(true.B)
      dut.io.ctrl.reg_write.expect(true.B)
      dut.io.ctrl.wb_sel.expect(WBSel.ALU)
    }
  }

  it should "decode AUIPC instruction" in {
    test(new Decode) { dut =>
      // AUIPC x2, 0x10000 (0x10000117)
      // 00010000000000000000 00010 0010111
      dut.io.inst.poke("b00010000000000000000000100010111".U)
      dut.clock.step(1)

      dut.io.rd.expect(2.U)
      dut.io.imm.expect(0x10000000L.U)
      dut.io.ctrl.alu_op.expect(ALUOp.ADD)
      dut.io.ctrl.alu_src.expect(true.B)
      dut.io.ctrl.reg_write.expect(true.B)
    }
  }

  it should "decode logical instructions (AND, OR, XOR)" in {
    test(new Decode) { dut =>
      // AND x1, x2, x3
      dut.io.inst.poke("b00000000001100010111000010110011".U)
      dut.clock.step(1)
      dut.io.ctrl.alu_op.expect(ALUOp.AND)

      // OR x1, x2, x3
      dut.io.inst.poke("b00000000001100010110000010110011".U)
      dut.clock.step(1)
      dut.io.ctrl.alu_op.expect(ALUOp.OR)

      // XOR x1, x2, x3
      dut.io.inst.poke("b00000000001100010100000010110011".U)
      dut.clock.step(1)
      dut.io.ctrl.alu_op.expect(ALUOp.XOR)
    }
  }

  it should "decode shift instructions" in {
    test(new Decode) { dut =>
      // SLL x1, x2, x3
      dut.io.inst.poke("b00000000001100010001000010110011".U)
      dut.clock.step(1)
      dut.io.ctrl.alu_op.expect(ALUOp.SLL)

      // SRL x1, x2, x3
      dut.io.inst.poke("b00000000001100010101000010110011".U)
      dut.clock.step(1)
      dut.io.ctrl.alu_op.expect(ALUOp.SRL)

      // SRA x1, x2, x3
      dut.io.inst.poke("b01000000001100010101000010110011".U)
      dut.clock.step(1)
      dut.io.ctrl.alu_op.expect(ALUOp.SRA)
    }
  }

  it should "decode comparison instructions" in {
    test(new Decode) { dut =>
      // SLT x1, x2, x3
      dut.io.inst.poke("b00000000001100010010000010110011".U)
      dut.clock.step(1)
      dut.io.ctrl.alu_op.expect(ALUOp.SLT)

      // SLTU x1, x2, x3
      dut.io.inst.poke("b00000000001100010011000010110011".U)
      dut.clock.step(1)
      dut.io.ctrl.alu_op.expect(ALUOp.SLTU)
    }
  }

  it should "handle negative immediate values" in {
    test(new Decode) { dut =>
      // ADDI x1, x2, -1 (0xFFF10093)
      dut.io.inst.poke("b11111111111100010000000010010011".U)
      dut.clock.step(1)

      dut.io.imm.expect(0xFFFFFFFF.U) // Sign-extended to -1
    }
  }
}
