package core

import chisel3._
import chisel3.util._
import common.Config._

/**
 * 流水线寄存器定义
 * 定义各流水线阶段之间传递的数据结构
 */

/**
 * IF/ID 流水线寄存器
 */
class IF_ID_Reg extends Bundle {
  val pc   = UInt(XLEN.W)
  val inst = UInt(INST_WIDTH.W)
}

/**
 * ID/EX 流水线寄存器
 */
class ID_EX_Reg extends Bundle {
  val pc        = UInt(XLEN.W)
  val rs1_data  = UInt(XLEN.W)
  val rs2_data  = UInt(XLEN.W)
  val imm       = UInt(XLEN.W)
  val rs1_addr  = UInt(REG_ADDR_WIDTH.W)
  val rs2_addr  = UInt(REG_ADDR_WIDTH.W)
  val rd_addr   = UInt(REG_ADDR_WIDTH.W)

  // 控制信号
  val alu_op    = UInt(4.W)
  val alu_src   = Bool()         // 0: rs2_data, 1: imm
  val mem_read  = Bool()
  val mem_write = Bool()
  val mem_size  = UInt(2.W)      // 0: byte, 1: half, 2: word
  val mem_unsigned = Bool()      // 无符号扩展
  val reg_write = Bool()
  val wb_src    = UInt(2.W)      // 0: alu, 1: mem, 2: pc+4
  val branch    = Bool()
  val jump      = Bool()
}

/**
 * EX/MEM 流水线寄存器
 */
class EX_MEM_Reg extends Bundle {
  val pc           = UInt(XLEN.W)
  val alu_result   = UInt(XLEN.W)
  val rs2_data     = UInt(XLEN.W)
  val rd_addr      = UInt(REG_ADDR_WIDTH.W)
  val branch_taken = Bool()
  val branch_target = UInt(XLEN.W)

  // 控制信号
  val mem_read     = Bool()
  val mem_write    = Bool()
  val mem_size     = UInt(2.W)
  val mem_unsigned = Bool()
  val reg_write    = Bool()
  val wb_src       = UInt(2.W)
}

/**
 * MEM/WB 流水线寄存器
 */
class MEM_WB_Reg extends Bundle {
  val pc         = UInt(XLEN.W)
  val alu_result = UInt(XLEN.W)
  val mem_data   = UInt(XLEN.W)
  val rd_addr    = UInt(REG_ADDR_WIDTH.W)

  // 控制信号
  val reg_write  = Bool()
  val wb_src     = UInt(2.W)
}

/**
 * 指令类型定义
 */
object InstType {
  val R_TYPE = 0.U(3.W)
  val I_TYPE = 1.U(3.W)
  val S_TYPE = 2.U(3.W)
  val B_TYPE = 3.U(3.W)
  val U_TYPE = 4.U(3.W)
  val J_TYPE = 5.U(3.W)
}

/**
 * 写回数据源选择
 */
object WbSrc {
  val ALU  = 0.U(2.W)
  val MEM  = 1.U(2.W)
  val PC4  = 2.U(2.W)
}

/**
 * 内存访问大小
 */
object MemSize {
  val BYTE = 0.U(2.W)
  val HALF = 1.U(2.W)
  val WORD = 2.U(2.W)
}
