package core

import chisel3._
import chisel3.util._
import common.Config._

/**
 * ALU 操作码定义
 */
object AluOp {
  val ADD  = 0.U(4.W)
  val SUB  = 1.U(4.W)
  val SLT  = 2.U(4.W)   // Set Less Than (signed)
  val SLTU = 3.U(4.W)   // Set Less Than Unsigned
  val AND  = 4.U(4.W)
  val OR   = 5.U(4.W)
  val XOR  = 6.U(4.W)
  val SLL  = 7.U(4.W)   // Shift Left Logical
  val SRL  = 8.U(4.W)   // Shift Right Logical
  val SRA  = 9.U(4.W)   // Shift Right Arithmetic
  val NOP  = 15.U(4.W)
}

/**
 * ALU 算术逻辑单元
 *
 * 支持 RV32E 所有算术和逻辑运算
 * - 加减法
 * - 逻辑运算（AND, OR, XOR）
 * - 移位运算（逻辑左移、逻辑右移、算术右移）
 * - 比较运算（有符号、无符号）
 */
class ALU extends Module {
  val io = IO(new Bundle {
    val op   = Input(UInt(4.W))       // ALU 操作码
    val src1 = Input(UInt(XLEN.W))    // 操作数1
    val src2 = Input(UInt(XLEN.W))    // 操作数2
    val out  = Output(UInt(XLEN.W))   // 运算结果
    val zero = Output(Bool())         // 结果是否为0（用于分支判断）
  })

  // 移位量：取低5位（对于32位数据，移位量范围是0-31）
  val shamt = io.src2(4, 0)

  // ALU 运算
  val result = MuxLookup(io.op, 0.U)(Seq(
    AluOp.ADD  -> (io.src1 + io.src2),
    AluOp.SUB  -> (io.src1 - io.src2),
    AluOp.SLT  -> Mux(io.src1.asSInt < io.src2.asSInt, 1.U, 0.U),
    AluOp.SLTU -> Mux(io.src1 < io.src2, 1.U, 0.U),
    AluOp.AND  -> (io.src1 & io.src2),
    AluOp.OR   -> (io.src1 | io.src2),
    AluOp.XOR  -> (io.src1 ^ io.src2),
    AluOp.SLL  -> (io.src1 << shamt),
    AluOp.SRL  -> (io.src1 >> shamt),
    AluOp.SRA  -> (io.src1.asSInt >> shamt).asUInt,
    AluOp.NOP  -> 0.U
  ))

  io.out  := result
  io.zero := result === 0.U
}
