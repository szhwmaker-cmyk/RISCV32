package rv32e.core

import chisel3._
import chisel3.util._
import rv32e.bus._

/**
 * RV32E Processor Core - Top Level
 *
 * A 5-stage pipelined processor implementing RV32E ISA
 * Stages: IF → ID → EX → MEM → WB
 *
 * Features:
 * - 16 general-purpose registers (x0-x15)
 * - Data forwarding (EX→EX, MEM→EX)
 * - Pipeline stalls for LOAD-USE hazards
 * - Branch prediction: static not-taken
 * - Wishbone bus interface for instruction and data memory
 *
 * ========== Branch Prediction Strategy (FIXED: Problem #8 - Documentation) ==========
 *
 * This processor uses a **static not-taken** branch prediction strategy:
 *
 * **How it works**:
 * 1. IF stage always fetches from PC+4 (assumes branch not taken)
 * 2. Branch condition evaluated in EX stage (2 cycles later)
 * 3. If branch actually taken, flush IF and ID stages and redirect PC
 *
 * **Misprediction penalty**: 2 cycles (flush IF and ID)
 *
 * **Why static not-taken**:
 * - Simple hardware implementation (no prediction table)
 * - No additional storage required
 * - Acceptable for embedded systems where branches are less frequent
 * - Forward branches (e.g., if-then-else) are typically not taken
 *
 * **Performance implications**:
 * - Taken branches: 2-cycle penalty
 * - Not-taken branches: 0-cycle penalty (predicted correctly)
 * - Average penalty depends on branch taken rate in workload
 *
 * **Alternative strategies** (not implemented):
 * - Static backward-taken (predict loops taken)
 * - 1-bit predictor (dynamic)
 * - 2-bit saturating counter (better than 1-bit)
 * - Branch Target Buffer (BTB) for target caching
 *
 * **Branch resolution timeline**:
 * ```
 * Cycle 0: IF fetches branch instruction at PC=X
 * Cycle 1: ID decodes branch, reads registers
 * Cycle 2: EX evaluates condition, computes target
 *          - If taken: flush IF (PC=X+4) and ID, redirect to target
 *          - If not taken: continue normally (prediction correct!)
 * ```
 */
class CoreIO extends Bundle {
  // Instruction memory interface
  val imem = new WishboneMasterIO

  // Data memory interface
  val dmem = new WishboneMasterIO

  // Debug/monitoring outputs (optional)
  val debug_pc = Output(UInt(32.W))
  val debug_inst = Output(UInt(32.W))
}

class Core extends Module {
  val io = IO(new CoreIO)

  // ========== Pipeline Stages ==========
  val if_stage = Module(new IF)
  val id_stage = Module(new ID)
  val ex_stage = Module(new EX)
  val mem_stage = Module(new MEM)
  val wb_stage = Module(new WB)

  // ========== Register File ==========
  val regfile = Module(new RegFile)

  // ========== Hazard Detection Unit ==========
  val hazard = Module(new Hazard)

  // ========== CSR Register File ==========
  val csr = Module(new CSR)

  // ========== Connect Memory Interfaces ==========
  io.imem <> if_stage.io.imem
  io.dmem <> mem_stage.io.dmem

  // ========== Connect Pipeline Stages ==========

  // IF → ID
  id_stage.io.if_id := if_stage.io.if_id

  // ID → EX
  ex_stage.io.id_ex := id_stage.io.id_ex

  // EX → MEM
  mem_stage.io.ex_mem := ex_stage.io.ex_mem

  // MEM → WB
  wb_stage.io.mem_wb := mem_stage.io.mem_wb

  // ========== Register File Connections ==========

  // Read ports (ID stage)
  regfile.io.rs1_addr := id_stage.io.rf_rs1_addr
  regfile.io.rs2_addr := id_stage.io.rf_rs2_addr
  id_stage.io.rf_rs1_data := regfile.io.rs1_data
  id_stage.io.rf_rs2_data := regfile.io.rs2_data

  // Write port (WB stage)
  regfile.io.rd_addr := wb_stage.io.rf_waddr
  regfile.io.rd_data := wb_stage.io.rf_wdata
  regfile.io.rd_wen := wb_stage.io.rf_wen

  // ========== Hazard Detection ==========

  // Inputs to hazard unit
  hazard.io.id_ex_rs1 := id_stage.io.id_ex.rs1_addr
  hazard.io.id_ex_rs2 := id_stage.io.id_ex.rs2_addr
  hazard.io.id_ex_rd := id_stage.io.id_ex.rd_addr           // FIXED: ID/EX destination for LOAD-USE detection
  hazard.io.id_ex_rd_valid := id_stage.io.id_ex.ctrl.reg_write  // FIXED: Will write to register
  hazard.io.id_ex_mem_read := id_stage.io.id_ex.ctrl.mem_read

  hazard.io.ex_mem_rd := ex_stage.io.ex_mem.rd_addr
  hazard.io.ex_mem_reg_write := ex_stage.io.ex_mem.reg_write
  hazard.io.ex_mem_mem_read := ex_stage.io.ex_mem.mem_read

  hazard.io.mem_wb_rd := mem_stage.io.mem_wb.rd_addr
  hazard.io.mem_wb_reg_write := mem_stage.io.mem_wb.reg_write

  hazard.io.branch_taken := ex_stage.io.branch_taken

  // ========== Exception Handling ==========

  // Detect exceptions in EX stage (now using consistent EX/MEM signals)
  // FIXED: Use ex_stage.io.ex_mem signals instead of mixing with id_stage.io.id_ex
  val exception_detected = ex_stage.io.ex_mem.valid &&
    (ex_stage.io.ex_mem.is_ecall || ex_stage.io.ex_mem.is_ebreak)

  // Determine exception cause
  val exception_cause = Mux(ex_stage.io.ex_mem.is_ecall,
    ExceptionCause.ECALL_M,
    ExceptionCause.BREAKPOINT
  )

  // Connect CSR exception interface
  csr.io.exception := exception_detected
  csr.io.exception_pc := ex_stage.io.ex_mem.pc  // FIXED: Use EX/MEM PC
  csr.io.exception_cause := exception_cause

  // CSR read/write (not used yet, defaults)
  csr.io.addr := 0.U
  csr.io.wdata := 0.U
  csr.io.wen := false.B

  // Exception causes pipeline flush and PC redirect
  val exception_redirect = exception_detected
  val exception_target = csr.io.trap_vector

  // ========== Stall and Flush Control ==========

  // IF stage
  if_stage.io.stall := hazard.io.stall_if
  if_stage.io.flush := hazard.io.flush_if || exception_redirect
  // Branch or exception redirect
  if_stage.io.branch_taken := ex_stage.io.branch_taken || exception_redirect
  if_stage.io.branch_target := Mux(exception_redirect, exception_target, ex_stage.io.branch_target)

  // ID stage
  id_stage.io.stall := hazard.io.stall_id
  id_stage.io.flush := hazard.io.flush_id || exception_redirect
  id_stage.io.pc := if_stage.io.if_id.pc

  // EX stage
  ex_stage.io.stall := hazard.io.stall_ex
  ex_stage.io.flush := hazard.io.flush_ex || exception_redirect

  // MEM stage
  mem_stage.io.stall := hazard.io.stall_mem
  mem_stage.io.flush := false.B  // MEM stage doesn't flush

  // ========== Data Forwarding ==========

  // Forward data sources
  val fwd_ex_data = ex_stage.io.ex_mem.alu_result
  val fwd_mem_data = wb_stage.io.wb_data

  // Forwarding muxes for rs1
  val fwd_rs1_data = MuxCase(id_stage.io.id_ex.rs1_data, Seq(
    (hazard.io.fwd_rs1_sel === ForwardSel.FWD_EX)  -> fwd_ex_data,
    (hazard.io.fwd_rs1_sel === ForwardSel.FWD_MEM) -> fwd_mem_data
  ))

  // Forwarding muxes for rs2
  val fwd_rs2_data = MuxCase(id_stage.io.id_ex.rs2_data, Seq(
    (hazard.io.fwd_rs2_sel === ForwardSel.FWD_EX)  -> fwd_ex_data,
    (hazard.io.fwd_rs2_sel === ForwardSel.FWD_MEM) -> fwd_mem_data
  ))

  // Connect forwarding to EX stage
  ex_stage.io.fwd_ex_data := fwd_ex_data
  ex_stage.io.fwd_mem_data := fwd_mem_data
  ex_stage.io.fwd_rs1_sel := hazard.io.fwd_rs1_sel  // FIXED: Clear naming (was fwd_ex_sel)
  ex_stage.io.fwd_rs2_sel := hazard.io.fwd_rs2_sel  // FIXED: Clear naming (was fwd_mem_sel)

  // ========== Debug Outputs ==========
  io.debug_pc := if_stage.io.if_id.pc
  io.debug_inst := if_stage.io.if_id.inst
}

object Core extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.core.Core"))
}
