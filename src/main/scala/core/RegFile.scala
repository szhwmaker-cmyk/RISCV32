package core

import chisel3._
import chisel3.util._
import common.Config._

// ============================================================================
// 寄存器文件 (Register File)
// RV32E: 16个通用寄存器 (x0-x15)
// x0 硬连线为 0
// 支持: 2读1写
// ============================================================================

class RegFile extends Module {
  val io = IO(new Bundle {
    // 读端口1
    val rs1_addr = Input(UInt(REG_ADDR_WIDTH.W))
    val rs1_data = Output(UInt(XLEN.W))

    // 读端口2
    val rs2_addr = Input(UInt(REG_ADDR_WIDTH.W))
    val rs2_data = Output(UInt(XLEN.W))

    // 写端口
    val wen      = Input(Bool())
    val rd_addr  = Input(UInt(REG_ADDR_WIDTH.W))
    val rd_data  = Input(UInt(XLEN.W))
  })

  // 16个寄存器
  val regs = RegInit(VecInit(Seq.fill(REG_NUM)(0.U(XLEN.W))))

  // 读操作 (组合逻辑)
  // x0 始终返回 0
  io.rs1_data := Mux(io.rs1_addr === 0.U, 0.U, regs(io.rs1_addr))
  io.rs2_data := Mux(io.rs2_addr === 0.U, 0.U, regs(io.rs2_addr))

  // 写操作 (同步)
  when(io.wen && io.rd_addr =/= 0.U) {
    regs(io.rd_addr) := io.rd_data
  }

  // 写后读转发优化 (可选)
  when(io.wen && io.rd_addr =/= 0.U) {
    when(io.rd_addr === io.rs1_addr) {
      io.rs1_data := io.rd_data
    }
    when(io.rd_addr === io.rs2_addr) {
      io.rs2_data := io.rd_data
    }
  }
}
