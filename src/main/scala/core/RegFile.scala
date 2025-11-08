package core

import chisel3._
import chisel3.util._
import common.Config._

/**
 * RV32E 寄存器堆
 *
 * RV32E 使用 16 个通用寄存器 (x0-x15)
 * - x0 永远为 0
 * - 支持同周期读写（写优先）
 * - 双读端口，单写端口
 *
 * @note 采用同步读，异步写的设计
 */
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

  // 寄存器堆：16个寄存器
  val regs = RegInit(VecInit(Seq.fill(REG_NUM)(0.U(XLEN.W))))

  // 写操作：仅当写使能有效且目标寄存器不是x0时写入
  when(io.wen && io.rd_addr =/= 0.U) {
    regs(io.rd_addr) := io.rd_data
  }

  // 读端口1：支持写后读转发
  io.rs1_data := Mux(
    io.rs1_addr === 0.U,
    0.U,
    Mux(
      io.wen && (io.rs1_addr === io.rd_addr),
      io.rd_data,  // 转发写入的数据
      regs(io.rs1_addr)
    )
  )

  // 读端口2：支持写后读转发
  io.rs2_data := Mux(
    io.rs2_addr === 0.U,
    0.U,
    Mux(
      io.wen && (io.rs2_addr === io.rd_addr),
      io.rd_data,  // 转发写入的数据
      regs(io.rs2_addr)
    )
  )
}
