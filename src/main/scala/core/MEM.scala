package core

import chisel3._
import chisel3.util._
import common.Config._

/**
 * MEM 访存阶段
 *
 * 功能：
 * 1. 通过 D-Memory Port 访问数据存储器
 * 2. 处理 LOAD/STORE 指令
 * 3. 支持字节、半字、字访问
 * 4. 处理符号/无符号扩展
 */
class MEM extends Module {
  val io = IO(new Bundle {
    // 来自 EX 阶段
    val ex_mem_reg    = Input(new EX_MEM_Reg)
    val flush         = Input(Bool())

    // 数据存储器接口
    val mem_addr      = Output(UInt(XLEN.W))
    val mem_wdata     = Output(UInt(XLEN.W))
    val mem_wen       = Output(Bool())
    val mem_ren       = Output(Bool())
    val mem_size      = Output(UInt(2.W))
    val mem_rdata     = Input(UInt(XLEN.W))

    // 输出到 WB 阶段
    val mem_wb_reg    = Output(new MEM_WB_Reg)
  })

  // 存储器访问地址
  io.mem_addr := io.ex_mem_reg.alu_result

  // 存储器写数据
  io.mem_wdata := io.ex_mem_reg.rs2_data

  // 存储器读写使能
  io.mem_wen  := io.ex_mem_reg.mem_write
  io.mem_ren  := io.ex_mem_reg.mem_read
  io.mem_size := io.ex_mem_reg.mem_size

  // 处理读取数据的符号扩展
  val addr_offset = io.ex_mem_reg.alu_result(1, 0)

  // 根据访问大小和地址偏移选择正确的数据
  val loaded_data = WireDefault(io.mem_rdata)

  when(io.ex_mem_reg.mem_size === MemSize.BYTE) {
    // 字节访问
    val byte_data = MuxLookup(addr_offset, 0.U)(Seq(
      0.U -> io.mem_rdata(7, 0),
      1.U -> io.mem_rdata(15, 8),
      2.U -> io.mem_rdata(23, 16),
      3.U -> io.mem_rdata(31, 24)
    ))
    loaded_data := Mux(
      io.ex_mem_reg.mem_unsigned,
      byte_data,                              // 无符号扩展
      Cat(Fill(24, byte_data(7)), byte_data)  // 符号扩展
    )
  }.elsewhen(io.ex_mem_reg.mem_size === MemSize.HALF) {
    // 半字访问
    val half_data = Mux(
      addr_offset(1) === 0.U,
      io.mem_rdata(15, 0),
      io.mem_rdata(31, 16)
    )
    loaded_data := Mux(
      io.ex_mem_reg.mem_unsigned,
      half_data,                               // 无符号扩展
      Cat(Fill(16, half_data(15)), half_data)  // 符号扩展
    )
  }.otherwise {
    // 字访问
    loaded_data := io.mem_rdata
  }

  // 输出到 WB 阶段
  when(io.flush) {
    io.mem_wb_reg.pc         := 0.U
    io.mem_wb_reg.alu_result := 0.U
    io.mem_wb_reg.mem_data   := 0.U
    io.mem_wb_reg.rd_addr    := 0.U
    io.mem_wb_reg.reg_write  := false.B
    io.mem_wb_reg.wb_src     := WbSrc.ALU
  }.otherwise {
    io.mem_wb_reg.pc         := io.ex_mem_reg.pc
    io.mem_wb_reg.alu_result := io.ex_mem_reg.alu_result
    io.mem_wb_reg.mem_data   := loaded_data
    io.mem_wb_reg.rd_addr    := io.ex_mem_reg.rd_addr
    io.mem_wb_reg.reg_write  := io.ex_mem_reg.reg_write
    io.mem_wb_reg.wb_src     := io.ex_mem_reg.wb_src
  }
}
