package rv32e.core

import chisel3._
import chisel3.util._

/**
 * Write Back (WB) Stage
 *
 * Responsibilities:
 * - Select write-back data from ALU result, memory data, or PC+4
 * - Write result to register file
 * - This is the final stage of the pipeline
 */
class WBIO extends Bundle {
  // Input from MEM stage
  val mem_wb = Input(new MEM_WB_Reg)

  // Register file write interface
  val rf_wen = Output(Bool())
  val rf_waddr = Output(UInt(4.W))
  val rf_wdata = Output(UInt(32.W))

  // For forwarding (export write-back data)
  val wb_data = Output(UInt(32.W))
  val wb_addr = Output(UInt(4.W))
  val wb_en = Output(Bool())
}

class WB extends Module {
  val io = IO(new WBIO)

  // ========== Write-Back Data Selection ==========
  val wb_data = WireDefault(0.U(32.W))

  switch(io.mem_wb.wb_sel) {
    is(WBSel.ALU) {
      wb_data := io.mem_wb.alu_result
    }
    is(WBSel.MEM) {
      wb_data := io.mem_wb.mem_data
    }
    is(WBSel.PC4) {
      wb_data := io.mem_wb.pc + 4.U
    }
  }

  // ========== Register File Write ==========
  io.rf_wen := io.mem_wb.reg_write && io.mem_wb.valid
  io.rf_waddr := io.mem_wb.rd_addr
  io.rf_wdata := wb_data

  // ========== Forwarding Interface ==========
  io.wb_data := wb_data
  io.wb_addr := io.mem_wb.rd_addr
  io.wb_en := io.mem_wb.reg_write && io.mem_wb.valid
}

object WB extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.core.WB"))
}
