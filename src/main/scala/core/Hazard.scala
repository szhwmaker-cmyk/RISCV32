package core

import chisel3._
import chisel3.util._
import common.Config._

/**
 * 冒险检测和转发单元
 *
 * 负责：
 * 1. 数据转发 (Forwarding/Bypassing)
 * 2. LOAD-USE 冒险检测和暂停
 * 3. 控制冒险处理
 */
class HazardUnit extends Module {
  val io = IO(new Bundle {
    // ID 阶段信息
    val id_rs1 = Input(UInt(REG_ADDR_WIDTH.W))
    val id_rs2 = Input(UInt(REG_ADDR_WIDTH.W))

    // EX 阶段信息
    val ex_rd = Input(UInt(REG_ADDR_WIDTH.W))
    val ex_reg_write = Input(Bool())
    val ex_mem_read = Input(Bool())

    // MEM 阶段信息
    val mem_rd = Input(UInt(REG_ADDR_WIDTH.W))
    val mem_reg_write = Input(Bool())

    // WB 阶段信息
    val wb_rd = Input(UInt(REG_ADDR_WIDTH.W))
    val wb_reg_write = Input(Bool())

    // 分支控制
    val branch_taken = Input(Bool())

    // 控制输出
    val stall_if = Output(Bool())
    val stall_id = Output(Bool())
    val flush_if = Output(Bool())
    val flush_id = Output(Bool())
    val flush_ex = Output(Bool())

    // 转发控制
    val forward_a = Output(UInt(2.W)) // 0: no forward, 1: from EX/MEM, 2: from MEM/WB
    val forward_b = Output(UInt(2.W))
  })

  // ============================================================================
  // 数据转发逻辑
  // ============================================================================

  // 转发条件检查
  def needForward(rs: UInt, rd: UInt, reg_write: Bool): Bool = {
    reg_write && (rd =/= 0.U) && (rd === rs)
  }

  // Forward A (rs1)
  val forward_a_from_ex_mem = needForward(io.id_rs1, io.ex_rd, io.ex_reg_write)
  val forward_a_from_mem_wb = needForward(io.id_rs1, io.mem_rd, io.mem_reg_write)

  io.forward_a := MuxCase(
    0.U,
    Seq(
      forward_a_from_ex_mem -> 1.U, // 优先从 EX/MEM 转发
      forward_a_from_mem_wb -> 2.U // 其次从 MEM/WB 转发
    )
  )

  // Forward B (rs2)
  val forward_b_from_ex_mem = needForward(io.id_rs2, io.ex_rd, io.ex_reg_write)
  val forward_b_from_mem_wb = needForward(io.id_rs2, io.mem_rd, io.mem_reg_write)

  io.forward_b := MuxCase(
    0.U,
    Seq(
      forward_b_from_ex_mem -> 1.U,
      forward_b_from_mem_wb -> 2.U
    )
  )

  // ============================================================================
  // LOAD-USE 冒险检测
  // ============================================================================

  // 当 EX 阶段是 LOAD 指令，且 ID 阶段指令需要使用该 LOAD 的结果时，需要暂停
  val load_use_hazard_rs1 = io.ex_mem_read && (io.ex_rd =/= 0.U) && (io.ex_rd === io.id_rs1)
  val load_use_hazard_rs2 = io.ex_mem_read && (io.ex_rd =/= 0.U) && (io.ex_rd === io.id_rs2)
  val load_use_hazard = load_use_hazard_rs1 || load_use_hazard_rs2

  // ============================================================================
  // 控制冒险处理
  // ============================================================================

  // 分支跳转时需要 flush IF 和 ID 阶段
  val control_hazard = io.branch_taken

  // ============================================================================
  // 暂停和清空控制信号
  // ============================================================================

  io.stall_if := load_use_hazard
  io.stall_id := load_use_hazard
  io.flush_if := control_hazard
  io.flush_id := control_hazard
  io.flush_ex := load_use_hazard || control_hazard
}

/**
 * 转发逻辑辅助模块（用于 EX 阶段）
 *
 * 提供更细粒度的转发控制
 */
class ForwardingUnit extends Module {
  val io = IO(new Bundle {
    // ID/EX 阶段的寄存器地址
    val ex_rs1 = Input(UInt(REG_ADDR_WIDTH.W))
    val ex_rs2 = Input(UInt(REG_ADDR_WIDTH.W))

    // EX/MEM 阶段信息
    val mem_rd = Input(UInt(REG_ADDR_WIDTH.W))
    val mem_reg_write = Input(Bool())

    // MEM/WB 阶段信息
    val wb_rd = Input(UInt(REG_ADDR_WIDTH.W))
    val wb_reg_write = Input(Bool())

    // 转发控制输出
    val forward_a = Output(UInt(2.W))
    val forward_b = Output(UInt(2.W))
  })

  // Forward A
  when(io.mem_reg_write && (io.mem_rd =/= 0.U) && (io.mem_rd === io.ex_rs1)) {
    io.forward_a := 1.U // 从 EX/MEM 转发
  }.elsewhen(io.wb_reg_write && (io.wb_rd =/= 0.U) && (io.wb_rd === io.ex_rs1)) {
    io.forward_a := 2.U // 从 MEM/WB 转发
  }.otherwise {
    io.forward_a := 0.U // 不转发
  }

  // Forward B
  when(io.mem_reg_write && (io.mem_rd =/= 0.U) && (io.mem_rd === io.ex_rs2)) {
    io.forward_b := 1.U
  }.elsewhen(io.wb_reg_write && (io.wb_rd =/= 0.U) && (io.wb_rd === io.ex_rs2)) {
    io.forward_b := 2.U
  }.otherwise {
    io.forward_b := 0.U
  }
}
