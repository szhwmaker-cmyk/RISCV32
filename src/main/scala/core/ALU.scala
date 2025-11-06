package core

import chisel3._
import chisel3.util._
import common.Config._

/**
 * ALU 操作码定义
 */
object AluOp {
  val ADD = 0.U(4.W)
  val SUB = 1.U(4.W)
  val SLT = 2.U(4.W) // Set Less Than (signed)
  val SLTU = 3.U(4.W) // Set Less Than Unsigned
  val AND = 4.U(4.W)
  val OR = 5.U(4.W)
  val XOR = 6.U(4.W)
  val SLL = 7.U(4.W) // Shift Left Logical
  val SRL = 8.U(4.W) // Shift Right Logical
  val SRA = 9.U(4.W) // Shift Right Arithmetic
  val COPY1 = 10.U(4.W) // 直接输出 src1 (用于 LUI)
  val COPY2 = 11.U(4.W) // 直接输出 src2
  val NOP = 15.U(4.W)
}

/**
 * 算术逻辑单元 (ALU)
 *
 * 支持 RV32E 所需的所有算术和逻辑操作
 * - 加减法
 * - 逻辑运算 (AND, OR, XOR)
 * - 移位操作 (SLL, SRL, SRA)
 * - 比较操作 (SLT, SLTU)
 */
class ALU extends Module {
  val io = IO(new Bundle {
    val op = Input(UInt(4.W)) // ALU 操作码
    val src1 = Input(UInt(XLEN.W)) // 源操作数 1
    val src2 = Input(UInt(XLEN.W)) // 源操作数 2
    val out = Output(UInt(XLEN.W)) // 运算结果
    val zero = Output(Bool()) // 结果是否为 0
    val negative = Output(Bool()) // 结果是否为负
  })

  // 移位量（取 src2 的低 5 位）
  val shamt = io.src2(4, 0)

  // 有符号比较
  val lt_signed = io.src1.asSInt < io.src2.asSInt

  // 无符号比较
  val lt_unsigned = io.src1 < io.src2

  // ALU 运算
  val result = MuxLookup(io.op, 0.U)(
    Seq(
      AluOp.ADD -> (io.src1 + io.src2),
      AluOp.SUB -> (io.src1 - io.src2),
      AluOp.SLT -> Mux(lt_signed, 1.U, 0.U),
      AluOp.SLTU -> Mux(lt_unsigned, 1.U, 0.U),
      AluOp.AND -> (io.src1 & io.src2),
      AluOp.OR -> (io.src1 | io.src2),
      AluOp.XOR -> (io.src1 ^ io.src2),
      AluOp.SLL -> (io.src1 << shamt),
      AluOp.SRL -> (io.src1 >> shamt),
      AluOp.SRA -> (io.src1.asSInt >> shamt).asUInt,
      AluOp.COPY1 -> io.src1,
      AluOp.COPY2 -> io.src2,
      AluOp.NOP -> 0.U
    )
  )

  io.out := result
  io.zero := (result === 0.U)
  io.negative := result(XLEN - 1)
}

/**
 * 分支比较单元
 *
 * 专门用于分支指令的条件判断
 */
class BranchComp extends Module {
  val io = IO(new Bundle {
    val src1 = Input(UInt(XLEN.W))
    val src2 = Input(UInt(XLEN.W))
    val br_type = Input(UInt(3.W)) // 分支类型

    val br_taken = Output(Bool()) // 是否跳转
  })

  // 分支类型
  val BEQ = 0.U
  val BNE = 1.U
  val BLT = 4.U
  val BGE = 5.U
  val BLTU = 6.U
  val BGEU = 7.U

  val eq = io.src1 === io.src2
  val lt_signed = io.src1.asSInt < io.src2.asSInt
  val lt_unsigned = io.src1 < io.src2

  io.br_taken := MuxLookup(io.br_type, false.B)(
    Seq(
      BEQ -> eq,
      BNE -> !eq,
      BLT -> lt_signed,
      BGE -> !lt_signed,
      BLTU -> lt_unsigned,
      BGEU -> !lt_unsigned
    )
  )
}
