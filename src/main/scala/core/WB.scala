package core

import chisel3._
import chisel3.util._
import common._

// ============================================================================
// WB Stage - Write Back
// 写回阶段：选择写回数据源
// ============================================================================

class WB extends Module {
  val io = IO(new Bundle {
    // 来自MEM/WB寄存器
    val alu_result = Input(UInt(32.W))
    val mem_data = Input(UInt(32.W))
    val pc_plus_4 = Input(UInt(32.W))
    val ctrl = Input(new ControlSignals)

    // 输出到寄存器文件
    val write_data = Output(UInt(32.W))
  })

  // 写回数据选择
  io.write_data := MuxLookup(io.ctrl.wb_src, io.alu_result)(Seq(
    WBSrc.ALU_RESULT -> io.alu_result,
    WBSrc.MEM_DATA   -> io.mem_data,
    WBSrc.PC_PLUS_4  -> io.pc_plus_4
  ))
}
