package rv32e.core

import chisel3._
import chisel3.util._

/**
 * Execute (EX) Stage
 *
 * Responsibilities:
 * - Perform ALU operations
 * - Evaluate branch conditions
 * - Calculate branch/jump target addresses
 * - Forward results to later stages
 */
class EXIO extends Bundle {
  // Input from ID stage
  val id_ex = Input(new ID_EX_Reg)

  // Output to MEM stage
  val ex_mem = Output(new EX_MEM_Reg)

  // Branch/Jump control outputs
  val branch_taken = Output(Bool())
  val branch_target = Output(UInt(32.W))

  // Forwarding inputs (from later stages)
  val fwd_ex_data = Input(UInt(32.W))   // Forward from EX/MEM
  val fwd_mem_data = Input(UInt(32.W))  // Forward from MEM/WB
  val fwd_ex_sel = Input(UInt(2.W))     // Forwarding select for rs1
  val fwd_mem_sel = Input(UInt(2.W))    // Forwarding select for rs2

  // Control inputs
  val stall = Input(Bool())
  val flush = Input(Bool())
}

object ForwardSel {
  val NO_FWD = 0.U(2.W)   // No forwarding, use ID/EX data
  val FWD_EX = 1.U(2.W)   // Forward from EX/MEM
  val FWD_MEM = 2.U(2.W)  // Forward from MEM/WB
}

class EX extends Module {
  val io = IO(new EXIO)

  // ========== Forwarding Mux for rs1 and rs2 ==========
  // Select forwarded data for rs1
  val alu_src1 = MuxCase(io.id_ex.rs1_data, Seq(
    (io.fwd_ex_sel === ForwardSel.FWD_EX)  -> io.fwd_ex_data,
    (io.fwd_ex_sel === ForwardSel.FWD_MEM) -> io.fwd_mem_data
  ))

  // Select forwarded data for rs2
  val alu_src2_reg = MuxCase(io.id_ex.rs2_data, Seq(
    (io.fwd_mem_sel === ForwardSel.FWD_EX)  -> io.fwd_ex_data,
    (io.fwd_mem_sel === ForwardSel.FWD_MEM) -> io.fwd_mem_data
  ))

  // ========== ALU ==========
  val alu = Module(new ALU)
  alu.io.op := io.id_ex.ctrl.alu_op
  alu.io.src1 := alu_src1
  alu.io.src2 := Mux(io.id_ex.ctrl.alu_src, io.id_ex.imm, alu_src2_reg)

  val alu_result = alu.io.out

  // ========== Branch Condition Evaluation ==========
  val branch_taken = WireDefault(false.B)

  when(io.id_ex.ctrl.branch) {
    // Compare rs1 and rs2 for branch decision
    val eq = alu_src1 === alu_src2_reg
    val lt_signed = alu_src1.asSInt < alu_src2_reg.asSInt
    val lt_unsigned = alu_src1 < alu_src2_reg

    switch(io.id_ex.ctrl.branch_op) {
      is(BranchOp.BEQ)  { branch_taken := eq }
      is(BranchOp.BNE)  { branch_taken := !eq }
      is(BranchOp.BLT)  { branch_taken := lt_signed }
      is(BranchOp.BGE)  { branch_taken := !lt_signed }
      is(BranchOp.BLTU) { branch_taken := lt_unsigned }
      is(BranchOp.BGEU) { branch_taken := !lt_unsigned }
    }
  }

  // ========== Branch/Jump Target Calculation ==========
  val branch_target = WireDefault(0.U(32.W))

  when(io.id_ex.ctrl.branch) {
    // Branch target: PC + immediate
    branch_target := io.id_ex.pc + io.id_ex.imm
  }.elsewhen(io.id_ex.ctrl.jump) {
    // Check if JALR (uses ALU result) or JAL (uses PC + immediate)
    when(io.id_ex.ctrl.alu_src && io.id_ex.ctrl.alu_op === ALUOp.ADD) {
      // JALR: (rs1 + immediate) & ~1
      branch_target := Cat(alu_result(31, 1), 0.U(1.W))
    }.otherwise {
      // JAL: PC + immediate
      branch_target := io.id_ex.pc + io.id_ex.imm
    }
  }

  // Output branch decision
  io.branch_taken := (branch_taken && io.id_ex.ctrl.branch) || io.id_ex.ctrl.jump
  io.branch_target := branch_target

  // ========== EX/MEM Pipeline Register ==========
  val ex_mem_reg = RegInit(PipelineRegs.invalidEXMEM())

  when(io.flush) {
    ex_mem_reg := PipelineRegs.invalidEXMEM()
  }.elsewhen(!io.stall) {
    ex_mem_reg.pc := io.id_ex.pc
    ex_mem_reg.alu_result := alu_result
    ex_mem_reg.rs2_data := alu_src2_reg  // For store instructions
    ex_mem_reg.rd_addr := io.id_ex.rd_addr
    ex_mem_reg.mem_read := io.id_ex.ctrl.mem_read
    ex_mem_reg.mem_write := io.id_ex.ctrl.mem_write
    ex_mem_reg.mem_size := io.id_ex.ctrl.mem_size
    ex_mem_reg.mem_unsigned := io.id_ex.ctrl.mem_unsigned
    ex_mem_reg.reg_write := io.id_ex.ctrl.reg_write
    ex_mem_reg.wb_sel := io.id_ex.ctrl.wb_sel
    ex_mem_reg.valid := io.id_ex.valid
  }

  io.ex_mem := ex_mem_reg
}

object EX extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.core.EX"))
}
