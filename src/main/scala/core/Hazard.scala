package rv32e.core

import chisel3._
import chisel3.util._

/**
 * Hazard Detection and Forwarding Unit
 *
 * Handles three types of hazards:
 * 1. Data Hazards (RAW - Read After Write)
 *    - Forwarding from EX/MEM and MEM/WB stages
 *    - Stall on LOAD-USE hazard
 * 2. Control Hazards (Branches/Jumps)
 *    - Flush pipeline on branch taken
 * 3. Structural Hazards (avoided by design)
 */
class HazardIO extends Bundle {
  // Pipeline register inputs for hazard detection
  val id_ex_rs1 = Input(UInt(4.W))
  val id_ex_rs2 = Input(UInt(4.W))
  val id_ex_mem_read = Input(Bool())

  val ex_mem_rd = Input(UInt(4.W))
  val ex_mem_reg_write = Input(Bool())
  val ex_mem_mem_read = Input(Bool())

  val mem_wb_rd = Input(UInt(4.W))
  val mem_wb_reg_write = Input(Bool())

  // Branch control
  val branch_taken = Input(Bool())

  // Hazard control outputs
  val stall_if = Output(Bool())     // Stall IF stage
  val stall_id = Output(Bool())     // Stall ID stage
  val stall_ex = Output(Bool())     // Stall EX stage
  val stall_mem = Output(Bool())    // Stall MEM stage

  val flush_if = Output(Bool())     // Flush IF stage
  val flush_id = Output(Bool())     // Flush ID stage
  val flush_ex = Output(Bool())     // Flush EX stage

  // Forwarding control
  val fwd_rs1_sel = Output(UInt(2.W))  // Forward select for rs1
  val fwd_rs2_sel = Output(UInt(2.W))  // Forward select for rs2
}

class Hazard extends Module {
  val io = IO(new HazardIO)

  // ========== Data Hazard Detection ==========

  /**
   * EX Hazard (EX/MEM → EX forwarding)
   * Forward if:
   * - EX/MEM stage will write to register file
   * - EX/MEM.rd matches ID/EX.rs1 or ID/EX.rs2
   * - EX/MEM.rd is not x0
   */
  val ex_hazard_rs1 = io.ex_mem_reg_write &&
                      (io.ex_mem_rd =/= 0.U) &&
                      (io.ex_mem_rd === io.id_ex_rs1)

  val ex_hazard_rs2 = io.ex_mem_reg_write &&
                      (io.ex_mem_rd =/= 0.U) &&
                      (io.ex_mem_rd === io.id_ex_rs2)

  /**
   * MEM Hazard (MEM/WB → EX forwarding)
   * Forward if:
   * - MEM/WB stage will write to register file
   * - MEM/WB.rd matches ID/EX.rs1 or ID/EX.rs2
   * - MEM/WB.rd is not x0
   * - NOT already forwarding from EX/MEM (EX has priority)
   */
  val mem_hazard_rs1 = io.mem_wb_reg_write &&
                       (io.mem_wb_rd =/= 0.U) &&
                       (io.mem_wb_rd === io.id_ex_rs1) &&
                       !ex_hazard_rs1

  val mem_hazard_rs2 = io.mem_wb_reg_write &&
                       (io.mem_wb_rd =/= 0.U) &&
                       (io.mem_wb_rd === io.id_ex_rs2) &&
                       !ex_hazard_rs2

  // ========== Forwarding Control ==========
  io.fwd_rs1_sel := MuxCase(ForwardSel.NO_FWD, Seq(
    ex_hazard_rs1  -> ForwardSel.FWD_EX,
    mem_hazard_rs1 -> ForwardSel.FWD_MEM
  ))

  io.fwd_rs2_sel := MuxCase(ForwardSel.NO_FWD, Seq(
    ex_hazard_rs2  -> ForwardSel.FWD_EX,
    mem_hazard_rs2 -> ForwardSel.FWD_MEM
  ))

  // ========== LOAD-USE Hazard Detection ==========
  /**
   * LOAD-USE Hazard:
   * If EX stage is executing a LOAD and the destination register
   * will be used by the instruction in ID stage, we must stall.
   *
   * Stall condition:
   * - ID/EX is a LOAD (mem_read = true)
   * - ID/EX.rd matches current rs1 or rs2 in ID stage
   *
   * Note: We need to access ID stage's rs1/rs2, which means
   * this logic should be in the pipeline controller.
   * For now, we detect when EX/MEM has a load.
   */
  val load_use_hazard = io.id_ex_mem_read &&
                        ((io.ex_mem_rd === io.id_ex_rs1) ||
                         (io.ex_mem_rd === io.id_ex_rs2)) &&
                        (io.ex_mem_rd =/= 0.U)

  // ========== Stall Control ==========
  val stall = load_use_hazard

  io.stall_if := stall
  io.stall_id := stall
  io.stall_ex := false.B  // EX stage doesn't stall
  io.stall_mem := false.B // MEM stage doesn't stall

  // ========== Flush Control (Branch/Jump) ==========
  /**
   * When a branch is taken in EX stage:
   * - Flush IF stage (instruction being fetched is wrong)
   * - Flush ID stage (instruction being decoded is wrong)
   * - EX stage continues (it has the correct branch instruction)
   */
  io.flush_if := io.branch_taken
  io.flush_id := io.branch_taken
  io.flush_ex := false.B

  // When stalling due to load-use, also flush EX to insert a bubble
  when(stall) {
    io.flush_ex := true.B
  }
}

object Hazard extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.core.Hazard"))
}
