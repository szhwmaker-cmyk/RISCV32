package core

import chisel3._
import chisel3.util._
import common._

// ============================================================================
// IF Stage - Instruction Fetch
// 取指阶段：PC管理和指令获取
// ============================================================================

class IF extends Module {
  val io = IO(new Bundle {
    // PC控制
    val stall = Input(Bool())          // 流水线暂停
    val flush = Input(Bool())          // 流水线冲刷
    val branch_taken = Input(Bool())   // 分支跳转
    val branch_target = Input(UInt(32.W))  // 跳转目标地址

    // 指令内存接口
    val imem = new MemPortIO

    // 输出到IF/ID寄存器
    val pc_out = Output(UInt(32.W))
    val inst_out = Output(UInt(32.W))
  })

  // PC 寄存器
  val pc_reg = RegInit(Config.BOOT_ADDR.U(32.W))

  // 下一个PC值
  val pc_next = Wire(UInt(32.W))
  val pc_plus_4 = pc_reg + 4.U

  // PC更新逻辑
  when(io.flush || io.branch_taken) {
    // 分支跳转或冲刷
    pc_next := io.branch_target
  }.elsewhen(io.stall) {
    // 流水线暂停，保持当前PC
    pc_next := pc_reg
  }.otherwise {
    // 正常递增
    pc_next := pc_plus_4
  }

  // 更新PC
  pc_reg := pc_next

  // 指令内存访问
  io.imem.addr := pc_reg
  io.imem.wdata := 0.U
  io.imem.wen := false.B
  io.imem.ren := true.B
  io.imem.mask := "b1111".U
  io.imem.valid := !io.stall

  // 输出
  io.pc_out := pc_reg
  io.inst_out := Mux(io.stall || io.flush, 0x00000013.U, io.imem.rdata) // NOP = ADDI x0, x0, 0
}
