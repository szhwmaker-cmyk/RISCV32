package core

import chisel3._
import chisel3.util._

/**
 * MEM (Memory Access) Stage
 * 访存阶段
 *
 * 功能：
 * - LOAD指令：从数据存储器读取数据
 * - STORE指令：向数据存储器写入数据
 * - 处理不同大小的访存（byte, half word, word）
 * - 处理有符号/无符号扩展
 * - 将结果传递到MEM/WB流水线寄存器
 */
class MEMStage extends Module {
  val io = IO(new Bundle {
    // 来自EX阶段
    val ex_mem = Input(new EX_MEM_Reg())

    // 数据存储器接口
    val dmem_addr = Output(UInt(32.W))
    val dmem_wdata = Output(UInt(32.W))
    val dmem_rdata = Input(UInt(32.W))
    val dmem_wen = Output(Bool())
    val dmem_ren = Output(Bool())
    val dmem_size = Output(UInt(2.W))  // 0: byte, 1: half, 2: word

    // 输出到WB阶段
    val mem_wb = Output(new MEM_WB_Reg())
  })

  // 数据存储器访问
  io.dmem_addr := io.ex_mem.alu_result
  io.dmem_wen := io.ex_mem.mem_write && io.ex_mem.valid
  io.dmem_ren := io.ex_mem.mem_read && io.ex_mem.valid
  io.dmem_size := io.ex_mem.mem_size

  // STORE数据准备
  // 根据size调整写入数据
  val store_data = Wire(UInt(32.W))
  val byte_offset = io.ex_mem.alu_result(1, 0)

  store_data := MuxCase(io.ex_mem.rs2_data, Seq(
    (io.ex_mem.mem_size === 0.U) -> {  // SB (byte)
      val byte_data = io.ex_mem.rs2_data(7, 0)
      MuxLookup(byte_offset, 0.U)(Seq(
        0.U -> Cat(0.U(24.W), byte_data),
        1.U -> Cat(0.U(16.W), byte_data, 0.U(8.W)),
        2.U -> Cat(0.U(8.W), byte_data, 0.U(16.W)),
        3.U -> Cat(byte_data, 0.U(24.W))
      ))
    },
    (io.ex_mem.mem_size === 1.U) -> {  // SH (half word)
      val half_data = io.ex_mem.rs2_data(15, 0)
      Mux(byte_offset(1),
        Cat(half_data, 0.U(16.W)),
        Cat(0.U(16.W), half_data)
      )
    }
    // word时直接使用rs2_data
  ))

  io.dmem_wdata := store_data

  // LOAD数据处理
  // 根据size和是否有符号扩展处理读取的数据
  val load_data = Wire(UInt(32.W))

  load_data := MuxCase(io.dmem_rdata, Seq(
    (io.ex_mem.mem_size === 0.U) -> {  // LB/LBU (byte)
      val byte_data = MuxLookup(byte_offset, 0.U)(Seq(
        0.U -> io.dmem_rdata(7, 0),
        1.U -> io.dmem_rdata(15, 8),
        2.U -> io.dmem_rdata(23, 16),
        3.U -> io.dmem_rdata(31, 24)
      ))
      Mux(io.ex_mem.mem_unsigned,
        Cat(0.U(24.W), byte_data),  // 无符号扩展
        Cat(Fill(24, byte_data(7)), byte_data)  // 符号扩展
      )
    },
    (io.ex_mem.mem_size === 1.U) -> {  // LH/LHU (half word)
      val half_data = Mux(byte_offset(1),
        io.dmem_rdata(31, 16),
        io.dmem_rdata(15, 0)
      )
      Mux(io.ex_mem.mem_unsigned,
        Cat(0.U(16.W), half_data),  // 无符号扩展
        Cat(Fill(16, half_data(15)), half_data)  // 符号扩展
      )
    }
    // word时直接使用dmem_rdata
  ))

  // MEM/WB Pipeline Register
  val mem_wb_reg = RegInit(0.U.asTypeOf(new MEM_WB_Reg()))

  mem_wb_reg.pc := io.ex_mem.pc
  mem_wb_reg.reg_write := io.ex_mem.reg_write
  mem_wb_reg.wb_src := io.ex_mem.wb_src
  mem_wb_reg.alu_result := io.ex_mem.alu_result
  mem_wb_reg.mem_data := load_data
  mem_wb_reg.rd_addr := io.ex_mem.rd_addr
  mem_wb_reg.valid := io.ex_mem.valid

  io.mem_wb := mem_wb_reg
}

/**
 * Simple Data Memory (用于仿真测试)
 * 简单的数据存储器模型
 */
class DataMemory(size: Int = 1024) extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(32.W))
    val wdata = Input(UInt(32.W))
    val rdata = Output(UInt(32.W))
    val wen = Input(Bool())
    val ren = Input(Bool())
    val size = Input(UInt(2.W))  // 0: byte, 1: half, 2: word
  })

  // 数据存储器（字节可寻址）
  val mem = Mem(size, UInt(8.W))

  // 读取逻辑
  val word_addr = io.addr(31, 2)  // 字地址
  val byte_offset = io.addr(1, 0)  // 字节偏移

  // 读取一个字（4字节）
  val read_word = Cat(
    mem((word_addr << 2.U) + 3.U),
    mem((word_addr << 2.U) + 2.U),
    mem((word_addr << 2.U) + 1.U),
    mem(word_addr << 2.U)
  )

  io.rdata := read_word

  // 写入逻辑
  when(io.wen) {
    switch(io.size) {
      is(0.U) {  // Byte
        mem(io.addr) := io.wdata(7, 0)
      }
      is(1.U) {  // Half word
        val half_addr = io.addr(31, 1) << 1.U
        mem(half_addr) := io.wdata(7, 0)
        mem(half_addr + 1.U) := io.wdata(15, 8)
      }
      is(2.U) {  // Word
        val w_addr = word_addr << 2.U
        mem(w_addr) := io.wdata(7, 0)
        mem(w_addr + 1.U) := io.wdata(15, 8)
        mem(w_addr + 2.U) := io.wdata(23, 16)
        mem(w_addr + 3.U) := io.wdata(31, 24)
      }
    }
  }
}
