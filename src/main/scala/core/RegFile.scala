package core

import chisel3._
import chisel3.util._
import common.Config._

/**
 * RV32E 寄存器堆
 * 包含 16 个通用寄存器 (x0-x15)
 * x0 固定为 0
 *
 * 特性:
 * - 双读端口
 * - 单写端口
 * - 同步写，异步读
 * - 写优先（写后立即可读）
 */
class RegFile extends Module {
  val io = IO(new Bundle {
    // 读端口 1
    val rs1_addr = Input(UInt(REG_ADDR_WIDTH.W))
    val rs1_data = Output(UInt(XLEN.W))

    // 读端口 2
    val rs2_addr = Input(UInt(REG_ADDR_WIDTH.W))
    val rs2_data = Output(UInt(XLEN.W))

    // 写端口
    val rd_addr = Input(UInt(REG_ADDR_WIDTH.W))
    val rd_data = Input(UInt(XLEN.W))
    val rd_wen = Input(Bool())
  })

  // 寄存器数组 (x1-x15，x0不需要存储)
  val regs = Reg(Vec(REG_NUM, UInt(XLEN.W)))

  // ============================================================================
  // 读操作（异步读，支持写后立即读）
  // ============================================================================

  // 读端口 1
  val rs1_data_reg = Mux(io.rs1_addr === 0.U, 0.U, regs(io.rs1_addr))
  val rs1_forward = io.rd_wen && (io.rd_addr === io.rs1_addr) && (io.rd_addr =/= 0.U)
  io.rs1_data := Mux(rs1_forward, io.rd_data, rs1_data_reg)

  // 读端口 2
  val rs2_data_reg = Mux(io.rs2_addr === 0.U, 0.U, regs(io.rs2_addr))
  val rs2_forward = io.rd_wen && (io.rd_addr === io.rs2_addr) && (io.rd_addr =/= 0.U)
  io.rs2_data := Mux(rs2_forward, io.rd_data, rs2_data_reg)

  // ============================================================================
  // 写操作（同步写）
  // ============================================================================

  when(io.rd_wen && io.rd_addr =/= 0.U) {
    regs(io.rd_addr) := io.rd_data
  }

  // ============================================================================
  // 调试：初始化寄存器为 0（可选）
  // ============================================================================

  when(reset.asBool) {
    for (i <- 0 until REG_NUM) {
      regs(i) := 0.U
    }
  }
}

/**
 * 寄存器堆测试模块
 */
object RegFileMain extends App {
  println("Generating RegFile Verilog...")
  val verilog = circt.stage.ChiselStage.emitSystemVerilog(
    new RegFile,
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info")
  )
  println(verilog)
}
