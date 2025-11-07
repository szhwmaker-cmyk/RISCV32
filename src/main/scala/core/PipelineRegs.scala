package core

import chisel3._
import chisel3.util._
import common.Config._

/**
 * IF/ID 流水线寄存器
 */
class IFIDReg extends Bundle {
  val pc = UInt(XLEN.W)
  val inst = UInt(32.W)
  val valid = Bool()
}

/**
 * ID/EX 流水线寄存器
 */
class IDEXReg extends Bundle {
  val pc = UInt(XLEN.W)
  val inst = UInt(32.W)
  val rs1_data = UInt(XLEN.W)
  val rs2_data = UInt(XLEN.W)
  val imm = UInt(XLEN.W)
  val rs1_addr = UInt(REG_ADDR_WIDTH.W)
  val rs2_addr = UInt(REG_ADDR_WIDTH.W)
  val rd_addr = UInt(REG_ADDR_WIDTH.W)
  val ctrl = new ControlSignals
  val valid = Bool()
}

/**
 * EX/MEM 流水线寄存器
 */
class EXMEMReg extends Bundle {
  val pc = UInt(XLEN.W)
  val alu_out = UInt(XLEN.W)
  val rs2_data = UInt(XLEN.W)
  val rd_addr = UInt(REG_ADDR_WIDTH.W)
  val mem_read = Bool()
  val mem_write = Bool()
  val mem_width = UInt(2.W)
  val mem_unsigned = Bool()
  val reg_write = Bool()
  val wb_sel = UInt(2.W)
  val pc_plus_4 = UInt(XLEN.W)
  val valid = Bool()
}

/**
 * MEM/WB 流水线寄存器
 */
class MEMWBReg extends Bundle {
  val alu_out = UInt(XLEN.W)
  val mem_data = UInt(XLEN.W)
  val rd_addr = UInt(REG_ADDR_WIDTH.W)
  val reg_write = Bool()
  val wb_sel = UInt(2.W)
  val pc_plus_4 = UInt(XLEN.W)
  val valid = Bool()
}

/**
 * 分支目标和条件
 */
class BranchInfo extends Bundle {
  val taken = Bool()
  val target = UInt(XLEN.W)
}
