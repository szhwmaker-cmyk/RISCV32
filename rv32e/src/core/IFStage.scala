package core

import chisel3._
import chisel3.util._

/**
 * IF (Instruction Fetch) Stage
 * 取指阶段
 *
 * 功能：
 * - 维护程序计数器(PC)
 * - 从指令存储器读取指令
 * - 处理分支预测和跳转
 * - 将PC和指令传递到IF/ID流水线寄存器
 *
 * 输入：
 * - stall: 流水线暂停信号
 * - flush: 流水线冲刷信号
 * - branch_taken: 分支跳转信号
 * - branch_target: 分支目标地址
 * - imem: 指令存储器接口
 *
 * 输出：
 * - if_id: IF/ID流水线寄存器
 */
class IFStage extends Module {
  val io = IO(new Bundle {
    // 控制信号
    val stall = Input(Bool())
    val flush = Input(Bool())

    // 分支控制
    val branch_taken = Input(Bool())
    val branch_target = Input(UInt(32.W))

    // 指令存储器接口
    val imem_addr = Output(UInt(32.W))
    val imem_data = Input(UInt(32.W))
    val imem_valid = Input(Bool())

    // 输出到ID阶段
    val if_id = Output(new IF_ID_Reg())
  })

  // 程序计数器
  val pc_module = Module(new PC())
  pc_module.io.stall := io.stall
  pc_module.io.branch_taken := io.branch_taken
  pc_module.io.branch_target := io.branch_target

  val pc = pc_module.io.pc_out

  // 指令存储器地址
  io.imem_addr := pc

  // IF/ID Pipeline Register
  val if_id_reg = RegInit(0.U.asTypeOf(new IF_ID_Reg()))

  when(io.flush) {
    // 冲刷：插入NOP指令
    if_id_reg.pc := 0.U
    if_id_reg.inst := 0x00000013.U  // NOP (ADDI x0, x0, 0)
    if_id_reg.valid := false.B
  }.elsewhen(!io.stall) {
    // 正常更新
    if_id_reg.pc := pc
    if_id_reg.inst := io.imem_data
    if_id_reg.valid := io.imem_valid
  }
  // stall时保持不变

  io.if_id := if_id_reg
}

/**
 * Simple Instruction Memory (用于仿真测试)
 * 简单的指令存储器模型
 */
class InstructionMemory(size: Int = 1024) extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(32.W))
    val data = Output(UInt(32.W))
    val valid = Output(Bool())
  })

  // 指令存储器（字对齐，每个地址存储一个32位指令）
  val mem = Mem(size, UInt(32.W))

  // 地址右移2位（除以4）得到字地址
  val word_addr = io.addr >> 2.U

  // 检查地址是否有效
  val addr_valid = word_addr < size.U

  io.data := Mux(addr_valid, mem(word_addr), 0.U)
  io.valid := addr_valid
}

/**
 * Instruction Memory with Initialization
 * 可初始化的指令存储器
 */
class InstructionMemoryWithInit(size: Int = 1024, initFile: String = "") extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(32.W))
    val data = Output(UInt(32.W))
    val valid = Output(Bool())

    // 初始化接口（用于测试）
    val init_wen = Input(Bool())
    val init_addr = Input(UInt(32.W))
    val init_data = Input(UInt(32.W))
  })

  val mem = SyncReadMem(size, UInt(32.W))

  // 初始化逻辑
  when(io.init_wen) {
    mem.write(io.init_addr, io.init_data)
  }

  // 读取逻辑
  val word_addr = io.addr >> 2.U
  val addr_valid = word_addr < size.U

  io.data := mem.read(word_addr, !io.init_wen)
  io.valid := addr_valid
}
