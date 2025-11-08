package core

import chisel3._
import chisel3.util._
import common.Config._

/**
 * IF 取指阶段
 *
 * 功能：
 * 1. PC 管理（递增、跳转）
 * 2. 从指令存储器读取指令
 * 3. 将 PC 和指令传递给 ID 阶段
 *
 * 输入：
 * - branch_taken: 分支是否跳转（来自 EX 阶段）
 * - branch_target: 分支目标地址（来自 EX 阶段）
 * - stall: 流水线暂停信号（来自 Hazard 单元）
 * - inst_data: 从指令存储器读取的指令
 *
 * 输出：
 * - inst_addr: 指令地址（发送给指令存储器）
 * - pc_out: 当前 PC
 * - inst_out: 读取的指令
 */
class IF extends Module {
  val io = IO(new Bundle {
    // 控制信号
    val stall         = Input(Bool())
    val flush         = Input(Bool())
    val branch_taken  = Input(Bool())
    val branch_target = Input(UInt(XLEN.W))

    // 指令存储器接口
    val inst_addr     = Output(UInt(XLEN.W))
    val inst_data     = Input(UInt(INST_WIDTH.W))

    // 输出到 ID 阶段
    val pc_out        = Output(UInt(XLEN.W))
    val inst_out      = Output(UInt(INST_WIDTH.W))
  })

  // PC 寄存器，复位后指向 SPI Flash 起始地址
  val pc = RegInit(PC_RESET.U(XLEN.W))

  // 下一个 PC 值
  val next_pc = WireDefault(pc + 4.U)

  // 分支跳转时更新 PC
  when(io.branch_taken) {
    next_pc := io.branch_target
  }

  // 更新 PC（除非流水线暂停）
  when(!io.stall) {
    pc := next_pc
  }

  // 输出指令地址（对齐到 4 字节）
  io.inst_addr := pc

  // 输出到 ID 阶段
  when(io.flush) {
    // Flush 时输出 NOP 指令
    io.pc_out   := 0.U
    io.inst_out := 0x00000013.U  // NOP (ADDI x0, x0, 0)
  }.otherwise {
    io.pc_out   := pc
    io.inst_out := io.inst_data
  }
}
