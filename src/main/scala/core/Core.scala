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
  hazard.io.id_ex_mem_read := id_stage.io.id_ex.ctrl.mem_read

  hazard.io.ex_mem_rd := ex_stage.io.ex_mem.rd_addr
  hazard.io.ex_mem_reg_write := ex_stage.io.ex_mem.reg_write
  hazard.io.ex_mem_mem_read := ex_stage.io.ex_mem.mem_read

  hazard.io.mem_wb_rd := mem_stage.io.mem_wb.rd_addr
  hazard.io.mem_wb_reg_write := mem_stage.io.mem_wb.reg_write

  hazard.io.branch_taken := ex_stage.io.branch_taken

  // ========== Stall and Flush Control ==========

  // IF stage
  if_stage.io.stall := hazard.io.stall_if
  if_stage.io.flush := hazard.io.flush_if
  if_stage.io.branch_taken := ex_stage.io.branch_taken
  if_stage.io.branch_target := ex_stage.io.branch_target

  // ID stage
  id_stage.io.stall := hazard.io.stall_id
  id_stage.io.flush := hazard.io.flush_id
  id_stage.io.pc := if_stage.io.if_id.pc

  // EX stage
  ex_stage.io.stall := hazard.io.stall_ex
  ex_stage.io.flush := hazard.io.flush_ex

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
  ex_stage.io.fwd_ex_sel := hazard.io.fwd_rs1_sel   // Forwarding control for rs1
  ex_stage.io.fwd_mem_sel := hazard.io.fwd_rs2_sel  // Forwarding control for rs2

  // ========== Debug Outputs ==========
  io.debug_pc := if_stage.io.if_id.pc
  io.debug_inst := if_stage.io.if_id.inst
}

object Core extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.core.Core"))
}
