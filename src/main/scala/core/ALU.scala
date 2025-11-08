package core

import chisel3._
import chisel3.util._
import common.AluOp

// ============================================================================
// 算术逻辑单元 (ALU)
// 支持所有 RV32E 基础整数运算
// ============================================================================

class ALU extends Module {
  val io = IO(new Bundle {
    val op   = Input(UInt(4.W))      // 操作码
    val src1 = Input(UInt(32.W))     // 操作数1
    val src2 = Input(UInt(32.W))     // 操作数2
    val out  = Output(UInt(32.W))    // 运算结果
    val zero = Output(Bool())        // 零标志 (用于分支判断)
  })

  // 运算结果
  val result = WireDefault(0.U(32.W))

  // Shift amount (低5位)
  val shamt = io.src2(4, 0)

  // 根据操作码选择运算
  result := MuxLookup(io.op, 0.U)(Seq(
    AluOp.ADD  -> (io.src1 + io.src2),
    AluOp.SUB  -> (io.src1 - io.src2),
    AluOp.SLT  -> Mux(io.src1.asSInt < io.src2.asSInt, 1.U, 0.U),  // 有符号比较
    AluOp.SLTU -> Mux(io.src1 < io.src2, 1.U, 0.U),                 // 无符号比较
    AluOp.AND  -> (io.src1 & io.src2),
    AluOp.OR   -> (io.src1 | io.src2),
    AluOp.XOR  -> (io.src1 ^ io.src2),
    AluOp.SLL  -> (io.src1 << shamt),                               // 逻辑左移
    AluOp.SRL  -> (io.src1 >> shamt),                               // 逻辑右移
    AluOp.SRA  -> (io.src1.asSInt >> shamt).asUInt,                 // 算术右移
    AluOp.COPY -> io.src1                                           // 直接复制 (用于LUI)
  ))

  io.out := result
  io.zero := (result === 0.U)
}
