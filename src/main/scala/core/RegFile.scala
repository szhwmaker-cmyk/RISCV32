package core

import chisel3._
import chisel3.util._
import common.Config._

/**
 * RV32E 寄存器堆
 *
 * RV32E 使用 16 个通用寄存器 (x0-x15)
 * - x0 硬连线为 0
 * - x1-x15 为通用寄存器
 *
 * 支持双读单写：
 * - 两个读端口 (rs1, rs2)
 * - 一个写端口 (rd)
 * - 写操作在时钟上升沿进行
 */
class RegFile extends Module {
  val io = IO(new Bundle {
    // 读端口
    val rs1_addr = Input(UInt(REG_ADDR_WIDTH.W))
    val rs1_data = Output(UInt(XLEN.W))
    val rs2_addr = Input(UInt(REG_ADDR_WIDTH.W))
    val rs2_data = Output(UInt(XLEN.W))

    // 写端口
    val wen = Input(Bool())
    val waddr = Input(UInt(REG_ADDR_WIDTH.W))
    val wdata = Input(UInt(XLEN.W))

    // 调试接口（可选）
    val debug_reg = if (DEBUG_ENABLE) Some(Output(Vec(REG_NUM, UInt(XLEN.W)))) else None
  })

  // 寄存器存储（x1-x15，x0 不占用存储空间）
  val regs = Reg(Vec(REG_NUM - 1, UInt(XLEN.W)))

  // 读操作（组合逻辑）
  // x0 始终读出 0
  io.rs1_data := Mux(io.rs1_addr === 0.U, 0.U, regs(io.rs1_addr - 1.U))
  io.rs2_data := Mux(io.rs2_addr === 0.U, 0.U, regs(io.rs2_addr - 1.U))

  // 写操作（时序逻辑）
  // x0 不可写
  when(io.wen && io.waddr =/= 0.U) {
    regs(io.waddr - 1.U) := io.wdata
  }

  // 调试输出
  if (DEBUG_ENABLE) {
    val debug_vec = Wire(Vec(REG_NUM, UInt(XLEN.W)))
    debug_vec(0) := 0.U
    for (i <- 1 until REG_NUM) {
      debug_vec(i) := regs(i - 1)
    }
    io.debug_reg.get := debug_vec
  }
}

object RegFile {
  /**
   * 寄存器名称映射（用于调试）
   */
  val regNames = Seq(
    "zero", // x0
    "ra", // x1 - return address
    "sp", // x2 - stack pointer
    "gp", // x3 - global pointer
    "tp", // x4 - thread pointer
    "t0", // x5 - temporary
    "t1", // x6 - temporary
    "t2", // x7 - temporary
    "s0", // x8 - saved register / frame pointer
    "s1", // x9 - saved register
    "a0", // x10 - argument / return value
    "a1", // x11 - argument / return value
    "a2", // x12 - argument
    "a3", // x13 - argument
    "a4", // x14 - argument
    "a5" // x15 - argument
  )

  def getRegName(idx: Int): String = {
    if (idx >= 0 && idx < REG_NUM) regNames(idx) else "inv"
  }
}
