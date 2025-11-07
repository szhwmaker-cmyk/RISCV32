package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import common.Config._

/**
 * Decoder 单元测试
 * 测试指令译码器的各类指令解码
 */
class DecoderSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Decoder"

  it should "decode ADDI instruction" in {
    test(new Decoder) { dut =>
      // addi x1, x2, 100
      val inst = "b00000110010000010000000010010011".U
      dut.io.inst.poke(inst)
      dut.clock.step(1)

      dut.io.rs1.expect(2.U)
      dut.io.rd.expect(1.U)
      dut.io.imm.expect(100.U)
      dut.io.ctrl.alu_op.expect(AluOp.ADD)
      dut.io.ctrl.alu_src2.expect(1.U, "Should use immediate")
      dut.io.ctrl.reg_write.expect(true.B)
    }
  }

  it should "decode ADD instruction" in {
    test(new Decoder) { dut =>
      // add x3, x1, x2
      val inst = "b00000000001000001000000110110011".U
      dut.io.inst.poke(inst)
      dut.clock.step(1)

      dut.io.rs1.expect(1.U)
      dut.io.rs2.expect(2.U)
      dut.io.rd.expect(3.U)
      dut.io.ctrl.alu_op.expect(AluOp.ADD)
      dut.io.ctrl.alu_src2.expect(0.U, "Should use rs2")
      dut.io.ctrl.reg_write.expect(true.B)
    }
  }

  it should "decode SUB instruction" in {
    test(new Decoder) { dut =>
      // sub x4, x3, x2
      val inst = "b01000000001000011000001000110011".U
      dut.io.inst.poke(inst)
      dut.clock.step(1)

      dut.io.rs1.expect(3.U)
      dut.io.rs2.expect(2.U)
      dut.io.rd.expect(4.U)
      dut.io.ctrl.alu_op.expect(AluOp.SUB)
      dut.io.ctrl.reg_write.expect(true.B)
    }
  }

  it should "decode LW instruction" in {
    test(new Decoder) { dut =>
      // lw x5, 8(x3)
      val inst = "b00000000100000011010001010000011".U
      dut.io.inst.poke(inst)
      dut.clock.step(1)

      dut.io.rs1.expect(3.U)
      dut.io.rd.expect(5.U)
      dut.io.imm.expect(8.U)
      dut.io.ctrl.mem_read.expect(true.B)
      dut.io.ctrl.reg_write.expect(true.B)
      dut.io.ctrl.wb_sel.expect(1.U, "Write back from memory")
      dut.io.ctrl.mem_width.expect(2.U, "Word access")
    }
  }

  it should "decode SW instruction" in {
    test(new Decoder) { dut =>
      // sw x5, 12(x3)
      val inst = "b00000000010100011010001100100011".U
      dut.io.inst.poke(inst)
      dut.clock.step(1)

      dut.io.rs1.expect(3.U)
      dut.io.rs2.expect(5.U)
      dut.io.imm.expect(12.U)
      dut.io.ctrl.mem_write.expect(true.B)
      dut.io.ctrl.mem_width.expect(2.U, "Word access")
    }
  }

  it should "decode BEQ instruction" in {
    test(new Decoder) { dut =>
      // beq x1, x2, 16
      val inst = "b00000000001000001000010001100011".U
      dut.io.inst.poke(inst)
      dut.clock.step(1)

      dut.io.rs1.expect(1.U)
      dut.io.rs2.expect(2.U)
      dut.io.imm.expect(16.U)
      dut.io.ctrl.branch.expect(true.B)
    }
  }

  it should "decode JAL instruction" in {
    test(new Decoder) { dut =>
      // jal x1, 100
      val inst = "b00000011001000000000000011101111".U
      dut.io.inst.poke(inst)
      dut.clock.step(1)

      dut.io.rd.expect(1.U)
      dut.io.ctrl.jump.expect(true.B)
      dut.io.ctrl.reg_write.expect(true.B)
      dut.io.ctrl.wb_sel.expect(2.U, "Write back PC+4")
    }
  }

  it should "decode JALR instruction" in {
    test(new Decoder) { dut =>
      // jalr x1, 8(x2)
      val inst = "b00000000100000010000000011100111".U
      dut.io.inst.poke(inst)
      dut.clock.step(1)

      dut.io.rs1.expect(2.U)
      dut.io.rd.expect(1.U)
      dut.io.imm.expect(8.U)
      dut.io.ctrl.jump.expect(true.B)
      dut.io.ctrl.reg_write.expect(true.B)
    }
  }

  it should "decode LUI instruction" in {
    test(new Decoder) { dut =>
      // lui x3, 0x12345
      val inst = "b00010010001101000101000110110111".U
      dut.io.inst.poke(inst)
      dut.clock.step(1)

      dut.io.rd.expect(3.U)
      dut.io.ctrl.reg_write.expect(true.B)
      dut.io.ctrl.alu_src2.expect(1.U, "Use immediate")
    }
  }

  it should "decode AUIPC instruction" in {
    test(new Decoder) { dut =>
      // auipc x4, 0x1000
      val inst = "b00000001000000000000001000010111".U
      dut.io.inst.poke(inst)
      dut.clock.step(1)

      dut.io.rd.expect(4.U)
      dut.io.ctrl.reg_write.expect(true.B)
      dut.io.ctrl.alu_src1.expect(1.U, "Use PC")
      dut.io.ctrl.alu_src2.expect(1.U, "Use immediate")
    }
  }

  it should "decode logical instructions (AND, OR, XOR)" in {
    test(new Decoder) { dut =>
      // and x5, x3, x4
      val inst_and = "b00000000010000011111001010110011".U
      dut.io.inst.poke(inst_and)
      dut.clock.step(1)
      dut.io.ctrl.alu_op.expect(AluOp.AND)

      // or x5, x3, x4
      val inst_or = "b00000000010000011110001010110011".U
      dut.io.inst.poke(inst_or)
      dut.clock.step(1)
      dut.io.ctrl.alu_op.expect(AluOp.OR)

      // xor x5, x3, x4
      val inst_xor = "b00000000010000011100001010110011".U
      dut.io.inst.poke(inst_xor)
      dut.clock.step(1)
      dut.io.ctrl.alu_op.expect(AluOp.XOR)
    }
  }

  it should "decode shift instructions" in {
    test(new Decoder) { dut =>
      // slli x5, x3, 4
      val inst_slli = "b00000000010000011001001010010011".U
      dut.io.inst.poke(inst_slli)
      dut.clock.step(1)
      dut.io.ctrl.alu_op.expect(AluOp.SLL)

      // srli x5, x3, 4
      val inst_srli = "b00000000010000011101001010010011".U
      dut.io.inst.poke(inst_srli)
      dut.clock.step(1)
      dut.io.ctrl.alu_op.expect(AluOp.SRL)

      // srai x5, x3, 4
      val inst_srai = "b01000000010000011101001010010011".U
      dut.io.inst.poke(inst_srai)
      dut.clock.step(1)
      dut.io.ctrl.alu_op.expect(AluOp.SRA)
    }
  }

  it should "decode byte and halfword load instructions" in {
    test(new Decoder) { dut =>
      // lb x5, 0(x3) - load byte
      val inst_lb = "b00000000000000011000001010000011".U
      dut.io.inst.poke(inst_lb)
      dut.clock.step(1)
      dut.io.ctrl.mem_read.expect(true.B)
      dut.io.ctrl.mem_width.expect(0.U)
      dut.io.ctrl.mem_unsigned.expect(false.B)

      // lh x5, 0(x3) - load halfword
      val inst_lh = "b00000000000000011001001010000011".U
      dut.io.inst.poke(inst_lh)
      dut.clock.step(1)
      dut.io.ctrl.mem_read.expect(true.B)
      dut.io.ctrl.mem_width.expect(1.U)
      dut.io.ctrl.mem_unsigned.expect(false.B)

      // lbu x5, 0(x3) - load byte unsigned
      val inst_lbu = "b00000000000000011100001010000011".U
      dut.io.inst.poke(inst_lbu)
      dut.clock.step(1)
      dut.io.ctrl.mem_unsigned.expect(true.B)
    }
  }
}
