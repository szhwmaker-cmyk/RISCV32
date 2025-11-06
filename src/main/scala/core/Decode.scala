package rv32e.core

import chisel3._
import chisel3.util._

/**
 * Instruction Decoder
 *
 * Decodes RISC-V instructions and generates control signals
 * Supports RV32I base instruction set
 */
object InstructionType {
  val R_TYPE = 0.U(3.W)
  val I_TYPE = 1.U(3.W)
  val S_TYPE = 2.U(3.W)
  val B_TYPE = 3.U(3.W)
  val U_TYPE = 4.U(3.W)
  val J_TYPE = 5.U(3.W)
}

object Opcode {
  val LOAD     = "b0000011".U(7.W)
  val STORE    = "b0100011".U(7.W)
  val BRANCH   = "b1100011".U(7.W)
  val JALR     = "b1100111".U(7.W)
  val JAL      = "b1101111".U(7.W)
  val OP_IMM   = "b0010011".U(7.W)
  val OP       = "b0110011".U(7.W)
  val AUIPC    = "b0010111".U(7.W)
  val LUI      = "b0110111".U(7.W)
  val MISC_MEM = "b0001111".U(7.W)  // FENCE, FENCE.I
  val SYSTEM   = "b1110011".U(7.W)  // ECALL, EBREAK, CSR*
}

class DecodeIO extends Bundle {
  val inst = Input(UInt(32.W))
  val ctrl = Output(new ControlSignals)
  val rs1  = Output(UInt(4.W))
  val rs2  = Output(UInt(4.W))
  val rd   = Output(UInt(4.W))
  val imm  = Output(UInt(32.W))
}

class Decode extends Module {
  val io = IO(new DecodeIO)

  // Extract instruction fields
  val opcode = io.inst(6, 0)
  val rd     = io.inst(11, 7)
  val funct3 = io.inst(14, 12)
  val rs1    = io.inst(19, 15)
  val rs2    = io.inst(24, 20)
  val funct7 = io.inst(31, 25)

  // Output register addresses (4-bit for RV32E)
  io.rs1 := rs1(3, 0)
  io.rs2 := rs2(3, 0)
  io.rd  := rd(3, 0)

  // RV32E Compliance Assertions: Verify high bit is 0 (registers x0-x15 only)
  // These assertions catch illegal register encodings (x16-x31)
  // Note: Disabled in synthesis, active in simulation for debugging
  when(io.inst =/= 0.U) {  // Only check valid instructions
    assert(rs1(4) === 0.U(1.W), cf"Invalid RS1: instruction uses x${rs1}, RV32E only supports x0-x15")
    assert(rs2(4) === 0.U(1.W), cf"Invalid RS2: instruction uses x${rs2}, RV32E only supports x0-x15")
    assert(rd(4) === 0.U(1.W), cf"Invalid RD: instruction uses x${rd}, RV32E only supports x0-x15")
  }

  // ========== Immediate Generation ==========
  val imm_i = Cat(Fill(20, io.inst(31)), io.inst(31, 20))
  val imm_s = Cat(Fill(20, io.inst(31)), io.inst(31, 25), io.inst(11, 7))
  val imm_b = Cat(Fill(19, io.inst(31)), io.inst(31), io.inst(7), io.inst(30, 25), io.inst(11, 8), 0.U(1.W))
  val imm_u = Cat(io.inst(31, 12), Fill(12, 0.U))
  val imm_j = Cat(Fill(11, io.inst(31)), io.inst(31), io.inst(19, 12), io.inst(20), io.inst(30, 21), 0.U(1.W))

  // Default immediate (I-type)
  io.imm := imm_i

  // ========== Control Signal Generation ==========
  val ctrl = Wire(new ControlSignals)

  // Default values (NOP)
  ctrl.alu_op := ALUOp.ADD
  ctrl.alu_src := false.B
  ctrl.mem_read := false.B
  ctrl.mem_write := false.B
  ctrl.mem_size := 2.U  // Word
  ctrl.mem_unsigned := false.B
  ctrl.branch := false.B
  ctrl.jump := false.B
  ctrl.branch_op := 0.U
  ctrl.reg_write := false.B
  ctrl.wb_sel := WBSel.ALU
  ctrl.is_ecall := false.B
  ctrl.is_ebreak := false.B
  ctrl.is_fence := false.B

  // Decode based on opcode
  switch(opcode) {
    // ========== R-Type (OP) ==========
    is(Opcode.OP) {
      ctrl.alu_src := false.B  // Use rs2
      ctrl.reg_write := true.B
      ctrl.wb_sel := WBSel.ALU

      // Determine ALU operation from funct3 and funct7
      switch(funct3) {
        is(0.U) { ctrl.alu_op := Mux(funct7(5), ALUOp.SUB, ALUOp.ADD) } // ADD/SUB
        is(1.U) { ctrl.alu_op := ALUOp.SLL }  // SLL
        is(2.U) { ctrl.alu_op := ALUOp.SLT }  // SLT
        is(3.U) { ctrl.alu_op := ALUOp.SLTU } // SLTU
        is(4.U) { ctrl.alu_op := ALUOp.XOR }  // XOR
        is(5.U) { ctrl.alu_op := Mux(funct7(5), ALUOp.SRA, ALUOp.SRL) } // SRL/SRA
        is(6.U) { ctrl.alu_op := ALUOp.OR }   // OR
        is(7.U) { ctrl.alu_op := ALUOp.AND }  // AND
      }
    }

    // ========== I-Type (OP-IMM) ==========
    is(Opcode.OP_IMM) {
      ctrl.alu_src := true.B  // Use immediate
      ctrl.reg_write := true.B
      ctrl.wb_sel := WBSel.ALU
      io.imm := imm_i

      switch(funct3) {
        is(0.U) { ctrl.alu_op := ALUOp.ADD }  // ADDI
        is(1.U) { ctrl.alu_op := ALUOp.SLL }  // SLLI
        is(2.U) { ctrl.alu_op := ALUOp.SLT }  // SLTI
        is(3.U) { ctrl.alu_op := ALUOp.SLTU } // SLTIU
        is(4.U) { ctrl.alu_op := ALUOp.XOR }  // XORI
        is(5.U) { ctrl.alu_op := Mux(funct7(5), ALUOp.SRA, ALUOp.SRL) } // SRLI/SRAI
        is(6.U) { ctrl.alu_op := ALUOp.OR }   // ORI
        is(7.U) { ctrl.alu_op := ALUOp.AND }  // ANDI
      }
    }

    // ========== Load Instructions ==========
    is(Opcode.LOAD) {
      ctrl.alu_op := ALUOp.ADD
      ctrl.alu_src := true.B  // rs1 + offset
      ctrl.mem_read := true.B
      ctrl.reg_write := true.B
      ctrl.wb_sel := WBSel.MEM
      io.imm := imm_i

      switch(funct3) {
        is(0.U) { // LB
          ctrl.mem_size := 0.U
          ctrl.mem_unsigned := false.B
        }
        is(1.U) { // LH
          ctrl.mem_size := 1.U
          ctrl.mem_unsigned := false.B
        }
        is(2.U) { // LW
          ctrl.mem_size := 2.U
          ctrl.mem_unsigned := false.B
        }
        is(4.U) { // LBU
          ctrl.mem_size := 0.U
          ctrl.mem_unsigned := true.B
        }
        is(5.U) { // LHU
          ctrl.mem_size := 1.U
          ctrl.mem_unsigned := true.B
        }
      }
    }

    // ========== Store Instructions ==========
    is(Opcode.STORE) {
      ctrl.alu_op := ALUOp.ADD
      ctrl.alu_src := true.B  // rs1 + offset
      ctrl.mem_write := true.B
      io.imm := imm_s

      switch(funct3) {
        is(0.U) { ctrl.mem_size := 0.U } // SB
        is(1.U) { ctrl.mem_size := 1.U } // SH
        is(2.U) { ctrl.mem_size := 2.U } // SW
      }
    }

    // ========== Branch Instructions ==========
    is(Opcode.BRANCH) {
      ctrl.alu_op := ALUOp.ADD  // For target calculation
      ctrl.alu_src := true.B
      ctrl.branch := true.B
      io.imm := imm_b

      switch(funct3) {
        is(0.U) { ctrl.branch_op := BranchOp.BEQ }
        is(1.U) { ctrl.branch_op := BranchOp.BNE }
        is(4.U) { ctrl.branch_op := BranchOp.BLT }
        is(5.U) { ctrl.branch_op := BranchOp.BGE }
        is(6.U) { ctrl.branch_op := BranchOp.BLTU }
        is(7.U) { ctrl.branch_op := BranchOp.BGEU }
      }
    }

    // ========== JAL ==========
    is(Opcode.JAL) {
      ctrl.jump := true.B
      ctrl.reg_write := true.B
      ctrl.wb_sel := WBSel.PC4  // Save PC+4 to rd
      io.imm := imm_j
    }

    // ========== JALR ==========
    is(Opcode.JALR) {
      ctrl.jump := true.B
      ctrl.alu_src := true.B
      ctrl.alu_op := ALUOp.ADD  // rs1 + offset
      ctrl.reg_write := true.B
      ctrl.wb_sel := WBSel.PC4
      io.imm := imm_i
    }

    // ========== LUI ==========
    is(Opcode.LUI) {
      ctrl.alu_op := ALUOp.COPY2  // Copy immediate
      ctrl.alu_src := true.B
      ctrl.reg_write := true.B
      ctrl.wb_sel := WBSel.ALU
      io.imm := imm_u
    }

    // ========== AUIPC ==========
    is(Opcode.AUIPC) {
      ctrl.alu_op := ALUOp.ADD  // PC + immediate
      ctrl.alu_src := true.B
      ctrl.reg_write := true.B
      ctrl.wb_sel := WBSel.ALU
      io.imm := imm_u
    }

    // ========== FENCE ==========
    is(Opcode.MISC_MEM) {
      // FENCE instruction (funct3 = 000)
      // In single-core, no-cache system, FENCE is effectively a NOP
      // Just mark it as a fence instruction for tracking purposes
      ctrl.is_fence := true.B
      // All other signals remain at default (NOP behavior)
    }

    // ========== SYSTEM (ECALL, EBREAK) ==========
    is(Opcode.SYSTEM) {
      // Check funct3 = 000 for ECALL/EBREAK
      when(funct3 === 0.U) {
        // Distinguish ECALL (imm12=0) from EBREAK (imm12=1)
        val imm12 = io.inst(31, 20)
        when(imm12 === 0.U) {
          // ECALL
          ctrl.is_ecall := true.B
        }.elsewhen(imm12 === 1.U) {
          // EBREAK
          ctrl.is_ebreak := true.B
        }
      }
      // Note: CSR instructions (funct3 != 000) not implemented yet
      // They would be added here for full RV32I support
    }
  }

  io.ctrl := ctrl
}

object Decode extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.core.Decode"))
}
