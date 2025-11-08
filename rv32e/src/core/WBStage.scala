package core

import chisel3._
import chisel3.util._

/**
 * WB (Write Back) Stage
 * 写回阶段
 *
 * 功能：
 * - 选择写回数据源（ALU结果、内存数据、PC+4）
 * - 将数据写回寄存器堆
 * - 这是流水线的最后一个阶段
 */
class WBStage extends Module {
  val io = IO(new Bundle {
    // 来自MEM阶段
    val mem_wb = Input(new MEM_WB_Reg())

    // 写回寄存器堆
    val wb_rd_addr = Output(UInt(4.W))
    val wb_rd_data = Output(UInt(32.W))
    val wb_reg_write = Output(Bool())

    // 前递数据（给EX阶段使用）
    val wb_forward_data = Output(UInt(32.W))
  })

  // 选择写回数据源
  val wb_data = MuxLookup(io.mem_wb.wb_src, io.mem_wb.alu_result)(Seq(
    0.U -> io.mem_wb.alu_result,           // ALU结果
    1.U -> io.mem_wb.mem_data,             // 内存数据
    2.U -> (io.mem_wb.pc + 4.U)            // PC+4 (用于JAL/JALR)
  ))

  // 输出到寄存器堆
  io.wb_rd_addr := io.mem_wb.rd_addr
  io.wb_rd_data := wb_data
  io.wb_reg_write := io.mem_wb.reg_write && io.mem_wb.valid

  // 前递数据
  io.wb_forward_data := wb_data
}
