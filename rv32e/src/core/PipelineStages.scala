package core

import chisel3._
import chisel3.util._

/**
 * IF/ID Pipeline Register
 * 取指/译码级之间的流水线寄存器
 */
class IF_ID_Reg extends Bundle {
  val pc = UInt(32.W)           // 程序计数器
  val inst = UInt(32.W)         // 取到的指令
  val valid = Bool()            // 指令有效标志
}

/**
 * ID/EX Pipeline Register
 * 译码/执行级之间的流水线寄存器
 */
class ID_EX_Reg extends Bundle {
  val pc = UInt(32.W)
  val inst = UInt(32.W)

  // 控制信号
  val ctrl = new ControlSignals()

  // 寄存器数据
  val rs1_data = UInt(32.W)
  val rs2_data = UInt(32.W)
  val rs1_addr = UInt(4.W)
  val rs2_addr = UInt(4.W)
  val rd_addr = UInt(4.W)

  // 立即数
  val imm = SInt(32.W)

  val valid = Bool()
}

/**
 * EX/MEM Pipeline Register
 * 执行/访存级之间的流水线寄存器
 */
class EX_MEM_Reg extends Bundle {
  val pc = UInt(32.W)

  // 控制信号（传递到后续阶段）
  val mem_read = Bool()
  val mem_write = Bool()
  val mem_size = UInt(2.W)
  val mem_unsigned = Bool()
  val reg_write = Bool()
  val wb_src = UInt(2.W)

  // ALU结果
  val alu_result = UInt(32.W)
  val zero = Bool()

  // 分支信息
  val branch_taken = Bool()
  val branch_target = UInt(32.W)

  // 写回信息
  val rd_addr = UInt(4.W)
  val rs2_data = UInt(32.W)  // 用于STORE指令

  val valid = Bool()
}

/**
 * MEM/WB Pipeline Register
 * 访存/写回级之间的流水线寄存器
 */
class MEM_WB_Reg extends Bundle {
  val pc = UInt(32.W)

  // 控制信号
  val reg_write = Bool()
  val wb_src = UInt(2.W)

  // 数据
  val alu_result = UInt(32.W)
  val mem_data = UInt(32.W)
  val rd_addr = UInt(4.W)

  val valid = Bool()
}

/**
 * Hazard Detection and Forwarding Unit
 * 冒险检测和前递单元
 */
class HazardUnit extends Module {
  val io = IO(new Bundle {
    // ID stage inputs
    val id_rs1 = Input(UInt(4.W))
    val id_rs2 = Input(UInt(4.W))

    // EX stage inputs
    val ex_rd = Input(UInt(4.W))
    val ex_mem_read = Input(Bool())
    val ex_reg_write = Input(Bool())

    // MEM stage inputs
    val mem_rd = Input(UInt(4.W))
    val mem_reg_write = Input(Bool())

    // WB stage inputs
    val wb_rd = Input(UInt(4.W))
    val wb_reg_write = Input(Bool())

    // Branch control
    val branch_taken = Input(Bool())

    // Outputs
    val stall = Output(Bool())         // 流水线暂停
    val flush_if = Output(Bool())      // 冲刷IF阶段
    val flush_id = Output(Bool())      // 冲刷ID阶段
    val flush_ex = Output(Bool())      // 冲刷EX阶段

    // Forwarding controls
    val forward_a = Output(UInt(2.W))  // 0: no forward, 1: from EX/MEM, 2: from MEM/WB
    val forward_b = Output(UInt(2.W))
  })

  // Load-Use Hazard Detection
  // 当前一条指令是LOAD，且目标寄存器是当前指令的源寄存器时，需要暂停
  val load_use_hazard = io.ex_mem_read && (
    (io.ex_rd === io.id_rs1 && io.id_rs1 =/= 0.U) ||
    (io.ex_rd === io.id_rs2 && io.id_rs2 =/= 0.U)
  )

  io.stall := load_use_hazard

  // Control Hazard - Branch Taken
  // 分支跳转时，冲刷IF和ID阶段
  io.flush_if := io.branch_taken
  io.flush_id := io.branch_taken
  io.flush_ex := io.branch_taken

  // Forwarding Unit for ALU Source A (rs1)
  io.forward_a := MuxCase(0.U, Seq(
    // EX hazard: 前一条指令的结果
    (io.ex_reg_write && io.ex_rd =/= 0.U && io.ex_rd === io.id_rs1) -> 1.U,
    // MEM hazard: 前两条指令的结果
    (io.mem_reg_write && io.mem_rd =/= 0.U && io.mem_rd === io.id_rs1) -> 2.U
  ))

  // Forwarding Unit for ALU Source B (rs2)
  io.forward_b := MuxCase(0.U, Seq(
    // EX hazard
    (io.ex_reg_write && io.ex_rd =/= 0.U && io.ex_rd === io.id_rs2) -> 1.U,
    // MEM hazard
    (io.mem_reg_write && io.mem_rd =/= 0.U && io.mem_rd === io.id_rs2) -> 2.U
  ))
}

/**
 * PC (Program Counter) Module
 * 程序计数器模块
 */
class PC extends Module {
  val io = IO(new Bundle {
    val stall = Input(Bool())            // 暂停信号
    val branch_taken = Input(Bool())     // 分支跳转
    val branch_target = Input(UInt(32.W)) // 分支目标
    val pc_out = Output(UInt(32.W))      // 当前PC值
  })

  val pc_reg = RegInit(0x00000000.U(32.W))  // PC初始值为0

  io.pc_out := pc_reg

  when(!io.stall) {
    when(io.branch_taken) {
      pc_reg := io.branch_target
    }.otherwise {
      pc_reg := pc_reg + 4.U  // 正常情况下PC+4
    }
  }
}
