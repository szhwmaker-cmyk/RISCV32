package core

import chisel3._
import chisel3.util._

/**
 * Branch Function Codes
 * RISC-V分支指令类型
 */
object BranchType {
  val width = 3

  val BEQ  = 0.U(width.W)  // Branch if Equal
  val BNE  = 1.U(width.W)  // Branch if Not Equal
  val BLT  = 2.U(width.W)  // Branch if Less Than (signed)
  val BGE  = 3.U(width.W)  // Branch if Greater or Equal (signed)
  val BLTU = 4.U(width.W)  // Branch if Less Than (unsigned)
  val BGEU = 5.U(width.W)  // Branch if Greater or Equal (unsigned)
  val JAL  = 6.U(width.W)  // Jump and Link (always taken)
  val JALR = 7.U(width.W)  // Jump and Link Register (always taken)
}

/**
 * Branch Unit (分支判断单元)
 *
 * 功能：
 * - 执行分支条件判断
 * - 计算分支目标地址
 * - 支持所有RISC-V分支指令（BEQ, BNE, BLT, BGE, BLTU, BGEU）
 * - 支持跳转指令（JAL, JALR）
 *
 * 输入：
 * - rs1_data: 源寄存器1的值
 * - rs2_data: 源寄存器2的值
 * - pc: 当前程序计数器
 * - imm: 立即数（分支偏移量）
 * - branch_type: 分支类型
 *
 * 输出：
 * - taken: 分支是否执行（跳转）
 * - target: 分支目标地址
 */
class BranchUnit extends Module {
  val io = IO(new Bundle {
    val rs1_data = Input(UInt(32.W))     // 源寄存器1
    val rs2_data = Input(UInt(32.W))     // 源寄存器2（JALR不使用）
    val pc = Input(UInt(32.W))           // 当前PC
    val imm = Input(SInt(32.W))          // 立即数（分支偏移量）
    val branch_type = Input(UInt(BranchType.width.W))

    val taken = Output(Bool())           // 分支是否执行
    val target = Output(UInt(32.W))      // 分支目标地址
  })

  // 将输入转换为有符号数进行比较
  val rs1_signed = io.rs1_data.asSInt
  val rs2_signed = io.rs2_data.asSInt

  // 分支条件判断
  val branch_taken = MuxLookup(io.branch_type, false.B)(Seq(
    BranchType.BEQ  -> (io.rs1_data === io.rs2_data),
    BranchType.BNE  -> (io.rs1_data =/= io.rs2_data),
    BranchType.BLT  -> (rs1_signed < rs2_signed),
    BranchType.BGE  -> (rs1_signed >= rs2_signed),
    BranchType.BLTU -> (io.rs1_data < io.rs2_data),
    BranchType.BGEU -> (io.rs1_data >= io.rs2_data),
    BranchType.JAL  -> true.B,   // JAL总是跳转
    BranchType.JALR -> true.B    // JALR总是跳转
  ))

  // 分支目标地址计算
  // - 条件分支（BEQ, BNE, BLT, etc.）和JAL: target = PC + imm
  // - JALR: target = (rs1 + imm) & ~1 (最低位清零，确保对齐)
  val pc_offset_target = (io.pc.asSInt + io.imm).asUInt
  val jalr_target = ((io.rs1_data.asSInt + io.imm).asUInt & "hFFFFFFFE".U)

  io.target := Mux(io.branch_type === BranchType.JALR, jalr_target, pc_offset_target)
  io.taken := branch_taken
}

/**
 * Branch Target Buffer Entry
 * 简化的分支预测器条目（用于流水线阶段2）
 */
class BTBEntry extends Bundle {
  val valid = Bool()        // 条目是否有效
  val pc = UInt(32.W)       // 分支指令PC
  val target = UInt(32.W)   // 分支目标地址
  val taken = Bool()        // 上次是否跳转
}

/**
 * Simple Branch Predictor
 * 简单的静态分支预测器
 *
 * 预测策略：
 * - 向后跳转（negative offset）：预测跳转（用于循环）
 * - 向前跳转（positive offset）：预测不跳转（用于if语句）
 * - JAL/JALR: 总是预测跳转
 */
class SimpleBranchPredictor extends Module {
  val io = IO(new Bundle {
    val pc = Input(UInt(32.W))
    val imm = Input(SInt(32.W))
    val branch_type = Input(UInt(BranchType.width.W))
    val is_branch = Input(Bool())       // 是否是分支指令

    val predict_taken = Output(Bool())   // 预测结果
    val predict_target = Output(UInt(32.W))
  })

  // 判断是否是无条件跳转
  val is_jump = (io.branch_type === BranchType.JAL) || (io.branch_type === BranchType.JALR)

  // 静态预测：
  // - 无条件跳转：总是跳转
  // - 条件分支：向后跳转（imm < 0）预测跳转，向前跳转预测不跳转
  val backward_branch = io.imm < 0.S
  io.predict_taken := io.is_branch && (is_jump || backward_branch)

  // 预测目标地址（简化版本，仅用于JAL）
  // JALR需要寄存器值，在译码阶段才能计算
  io.predict_target := (io.pc.asSInt + io.imm).asUInt
}
