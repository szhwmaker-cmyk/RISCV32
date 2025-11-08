package core

import chisel3._
import chisel3.util._
import common._

// ============================================================================
// MEM Stage - Memory Access
// 访存阶段：数据内存访问
// ============================================================================

class MEM extends Module {
  val io = IO(new Bundle {
    // 来自EX/MEM寄存器
    val alu_result = Input(UInt(32.W))
    val rs2_data = Input(UInt(32.W))
    val ctrl = Input(new ControlSignals)

    // 数据内存接口
    val dmem = new MemPortIO

    // 输出到MEM/WB
    val mem_data = Output(UInt(32.W))
  })

  // 地址
  val addr = io.alu_result
  val byte_offset = addr(1, 0)

  // Store 数据处理
  val store_data = Wire(UInt(32.W))
  val write_mask = Wire(UInt(4.W))

  // 根据访问类型生成 store 数据和写掩码
  switch(io.ctrl.mem_type) {
    is(MemType.BYTE) {
      store_data := io.rs2_data(7, 0) << (byte_offset << 3)
      write_mask := (1.U << byte_offset)
    }
    is(MemType.HALF) {
      store_data := io.rs2_data(15, 0) << (byte_offset << 3)
      write_mask := Mux(byte_offset(1), "b1100".U, "b0011".U)
    }
    is(MemType.WORD) {
      store_data := io.rs2_data
      write_mask := "b1111".U
    }
  }

  // 数据内存接口
  io.dmem.addr := addr
  io.dmem.wdata := store_data
  io.dmem.wen := io.ctrl.mem_write
  io.dmem.ren := io.ctrl.mem_read
  io.dmem.mask := write_mask
  io.dmem.valid := io.ctrl.mem_read || io.ctrl.mem_write

  // Load 数据处理
  val load_data = Wire(UInt(32.W))
  val mem_read_data = io.dmem.rdata

  // 根据字节偏移选择数据
  val shifted_data = mem_read_data >> (byte_offset << 3)

  switch(io.ctrl.mem_type) {
    is(MemType.BYTE) {
      load_data := Cat(Fill(24, shifted_data(7)), shifted_data(7, 0))  // 符号扩展
    }
    is(MemType.BYTEU) {
      load_data := Cat(Fill(24, 0.U), shifted_data(7, 0))  // 零扩展
    }
    is(MemType.HALF) {
      load_data := Cat(Fill(16, shifted_data(15)), shifted_data(15, 0))  // 符号扩展
    }
    is(MemType.HALFU) {
      load_data := Cat(Fill(16, 0.U), shifted_data(15, 0))  // 零扩展
    }
    is(MemType.WORD) {
      load_data := mem_read_data
    }
  }

  io.mem_data := load_data
}
