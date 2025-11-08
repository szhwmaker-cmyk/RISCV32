package core

import chisel3._
import chisel3.util._
import common.Config._

/**
 * WB 写回阶段
 *
 * 功能：
 * 1. 选择写回数据源（ALU / Memory / PC+4）
 * 2. 输出寄存器写使能和写数据
 */
class WB extends Module {
  val io = IO(new Bundle {
    // 来自 MEM 阶段
    val mem_wb_reg  = Input(new MEM_WB_Reg)

    // 输出到寄存器文件
    val reg_wen     = Output(Bool())
    val reg_waddr   = Output(UInt(REG_ADDR_WIDTH.W))
    val reg_wdata   = Output(UInt(XLEN.W))
  })

  // 选择写回数据源
  val write_data = MuxLookup(io.mem_wb_reg.wb_src, io.mem_wb_reg.alu_result)(Seq(
    WbSrc.ALU -> io.mem_wb_reg.alu_result,
    WbSrc.MEM -> io.mem_wb_reg.mem_data,
    WbSrc.PC4 -> (io.mem_wb_reg.pc + 4.U)
  ))

  // 输出到寄存器文件
  io.reg_wen   := io.mem_wb_reg.reg_write
  io.reg_waddr := io.mem_wb_reg.rd_addr
  io.reg_wdata := write_data
}
