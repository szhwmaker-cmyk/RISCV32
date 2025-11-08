package core

import chisel3._
import chisel3.util._

/**
 * Register File for RV32E
 *
 * RV32E特性：
 * - 仅有16个通用寄存器 (x0-x15)
 * - x0 硬连线为0，写入无效
 * - 支持2个读端口和1个写端口
 *
 * 端口说明：
 * - rs1_addr/rs2_addr: 读端口地址
 * - rs1_data/rs2_data: 读端口数据（组合逻辑）
 * - rd_addr: 写端口地址
 * - rd_data: 写端口数据
 * - rd_wen: 写使能
 *
 * 注意：
 * - 读操作是组合逻辑，写操作在时钟上升沿
 * - 支持同时读写（forwarding在外部实现）
 */
class RegFile extends Module {
  val io = IO(new Bundle {
    // 读端口1 (rs1)
    val rs1_addr = Input(UInt(4.W))   // RV32E只需4位地址
    val rs1_data = Output(UInt(32.W))

    // 读端口2 (rs2)
    val rs2_addr = Input(UInt(4.W))
    val rs2_data = Output(UInt(32.W))

    // 写端口 (rd)
    val rd_addr  = Input(UInt(4.W))
    val rd_data  = Input(UInt(32.W))
    val rd_wen   = Input(Bool())      // 写使能
  })

  // 16个32位寄存器 (x0-x15)
  // x0在逻辑上恒为0，但为了简化设计，仍然分配存储空间
  val registers = Reg(Vec(16, UInt(32.W)))

  // 读端口1 - 组合逻辑
  io.rs1_data := Mux(io.rs1_addr === 0.U, 0.U, registers(io.rs1_addr))

  // 读端口2 - 组合逻辑
  io.rs2_data := Mux(io.rs2_addr === 0.U, 0.U, registers(io.rs2_addr))

  // 写端口 - 时序逻辑
  when(io.rd_wen && io.rd_addr =/= 0.U) {
    registers(io.rd_addr) := io.rd_data
  }
}

/**
 * Register File with Forwarding Support
 *
 * 增强版寄存器堆，内置前递逻辑
 * 当读地址与写地址相同且写使能有效时，直接返回写数据
 * 这样可以减少流水线中的数据冒险
 */
class RegFileWithForwarding extends Module {
  val io = IO(new Bundle {
    // 读端口1 (rs1)
    val rs1_addr = Input(UInt(4.W))
    val rs1_data = Output(UInt(32.W))

    // 读端口2 (rs2)
    val rs2_addr = Input(UInt(4.W))
    val rs2_data = Output(UInt(32.W))

    // 写端口 (rd)
    val rd_addr  = Input(UInt(4.W))
    val rd_data  = Input(UInt(32.W))
    val rd_wen   = Input(Bool())
  })

  // 实例化基础寄存器堆
  val regfile = Module(new RegFile())
  regfile.io.rs1_addr := io.rs1_addr
  regfile.io.rs2_addr := io.rs2_addr
  regfile.io.rd_addr := io.rd_addr
  regfile.io.rd_data := io.rd_data
  regfile.io.rd_wen := io.rd_wen

  // 读端口1 - 带前递
  io.rs1_data := MuxCase(regfile.io.rs1_data, Seq(
    (io.rs1_addr === 0.U) -> 0.U,  // x0恒为0
    (io.rd_wen && io.rs1_addr === io.rd_addr && io.rd_addr =/= 0.U) -> io.rd_data
  ))

  // 读端口2 - 带前递
  io.rs2_data := MuxCase(regfile.io.rs2_data, Seq(
    (io.rs2_addr === 0.U) -> 0.U,  // x0恒为0
    (io.rd_wen && io.rs2_addr === io.rd_addr && io.rd_addr =/= 0.U) -> io.rd_data
  ))
}
