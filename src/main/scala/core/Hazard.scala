package core

import chisel3._
import chisel3.util._
import common.Config._

/**
 * 冒险检测与转发单元
 *
 * 功能：
 * 1. 数据冒险检测
 * 2. 数据转发控制（EX/MEM → EX, MEM/WB → EX）
 * 3. LOAD-USE 冒险检测和流水线暂停
 * 4. 控制冒险检测（分支跳转时的 Flush）
 */
class HazardUnit extends Module {
  val io = IO(new Bundle {
    // 来自 ID/EX 流水线寄存器
    val id_ex_rs1       = Input(UInt(REG_ADDR_WIDTH.W))
    val id_ex_rs2       = Input(UInt(REG_ADDR_WIDTH.W))
    val id_ex_mem_read  = Input(Bool())
    val id_ex_rd        = Input(UInt(REG_ADDR_WIDTH.W))

    // 来自 EX/MEM 流水线寄存器
    val ex_mem_rd       = Input(UInt(REG_ADDR_WIDTH.W))
    val ex_mem_reg_write = Input(Bool())
    val ex_mem_alu_result = Input(UInt(XLEN.W))

    // 来自 MEM/WB 流水线寄存器
    val mem_wb_rd       = Input(UInt(REG_ADDR_WIDTH.W))
    val mem_wb_reg_write = Input(Bool())
    val mem_wb_wdata    = Input(UInt(XLEN.W))

    // 来自 IF/ID 流水线寄存器（用于 LOAD-USE 检测）
    val if_id_rs1       = Input(UInt(REG_ADDR_WIDTH.W))
    val if_id_rs2       = Input(UInt(REG_ADDR_WIDTH.W))

    // 分支跳转信号
    val branch_taken    = Input(Bool())

    // 输出控制信号
    val forward_a       = Output(UInt(2.W))  // 00: no forward, 01: from MEM, 10: from WB
    val forward_b       = Output(UInt(2.W))
    val stall_if        = Output(Bool())
    val stall_id        = Output(Bool())
    val flush_if        = Output(Bool())
    val flush_id        = Output(Bool())
    val flush_ex        = Output(Bool())
  })

  // ============================================================================
  // 数据转发逻辑
  // ============================================================================

  // Forward A (for rs1)
  io.forward_a := MuxCase(0.U(2.W), Seq(
    // EX/MEM 转发优先级更高（更新的数据）
    (io.ex_mem_reg_write && io.ex_mem_rd =/= 0.U && io.ex_mem_rd === io.id_ex_rs1) -> 1.U(2.W),
    // MEM/WB 转发
    (io.mem_wb_reg_write && io.mem_wb_rd =/= 0.U && io.mem_wb_rd === io.id_ex_rs1) -> 2.U(2.W)
  ))

  // Forward B (for rs2)
  io.forward_b := MuxCase(0.U(2.W), Seq(
    // EX/MEM 转发优先级更高
    (io.ex_mem_reg_write && io.ex_mem_rd =/= 0.U && io.ex_mem_rd === io.id_ex_rs2) -> 1.U(2.W),
    // MEM/WB 转发
    (io.mem_wb_reg_write && io.mem_wb_rd =/= 0.U && io.mem_wb_rd === io.id_ex_rs2) -> 2.U(2.W)
  ))

  // ============================================================================
  // LOAD-USE 冒险检测
  // ============================================================================

  // 检测 ID/EX 阶段是否为 LOAD 指令，且目标寄存器被 IF/ID 阶段的指令使用
  val load_use_hazard = io.id_ex_mem_read && (
    (io.id_ex_rd =/= 0.U && io.id_ex_rd === io.if_id_rs1) ||
    (io.id_ex_rd =/= 0.U && io.id_ex_rd === io.if_id_rs2)
  )

  // ============================================================================
  // 流水线控制信号
  // ============================================================================

  // LOAD-USE 冒险：暂停 IF 和 ID，插入 NOP 到 EX
  when(load_use_hazard) {
    io.stall_if := true.B
    io.stall_id := true.B
    io.flush_ex := true.B
  }.otherwise {
    io.stall_if := false.B
    io.stall_id := false.B
    io.flush_ex := false.B
  }

  // 分支跳转：Flush IF 和 ID 阶段
  when(io.branch_taken) {
    io.flush_if := true.B
    io.flush_id := true.B
  }.otherwise {
    io.flush_if := false.B
    io.flush_id := false.B
  }

  // 如果检测到 LOAD-USE 冒险，同时分支跳转，优先处理分支跳转
  when(io.branch_taken) {
    io.stall_if := false.B
    io.stall_id := false.B
    io.flush_if := true.B
    io.flush_id := true.B
    io.flush_ex := true.B
  }
}
