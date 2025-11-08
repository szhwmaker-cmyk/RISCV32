package core

import chisel3._
import chisel3.util._

/**
 * ALU Operation Codes for RV32E
 * 支持基本的算术和逻辑运算
 */
object ALUOp {
  val width = 4

  // 算术运算
  val ADD  = 0.U(width.W)  // 加法
  val SUB  = 1.U(width.W)  // 减法
  val SLT  = 2.U(width.W)  // 有符号比较 (set less than)
  val SLTU = 3.U(width.W)  // 无符号比较

  // 逻辑运算
  val AND  = 4.U(width.W)  // 按位与
  val OR   = 5.U(width.W)  // 按位或
  val XOR  = 6.U(width.W)  // 按位异或

  // 移位运算
  val SLL  = 7.U(width.W)  // 逻辑左移
  val SRL  = 8.U(width.W)  // 逻辑右移
  val SRA  = 9.U(width.W)  // 算术右移

  // 直通操作
  val COPY_A = 10.U(width.W)  // 输出 = A
  val COPY_B = 11.U(width.W)  // 输出 = B
}

/**
 * ALU (Arithmetic Logic Unit) 模块
 *
 * 功能：
 * - 32位算术运算（加、减）
 * - 32位逻辑运算（与、或、异或）
 * - 移位运算（逻辑左移、逻辑右移、算术右移）
 * - 比较运算（有符号/无符号）
 *
 * 参数：
 * - width: 数据宽度，默认32位
 */
class ALU(width: Int = 32) extends Module {
  val io = IO(new Bundle {
    val op  = Input(UInt(ALUOp.width.W))  // ALU操作码
    val a   = Input(UInt(width.W))         // 操作数A
    val b   = Input(UInt(width.W))         // 操作数B
    val out = Output(UInt(width.W))        // 运算结果
    val zero = Output(Bool())              // 结果为零标志
  })

  // 移位量取低5位（32位数据最多移位31位）
  val shamt = io.b(4, 0)

  // 根据操作码执行运算
  io.out := MuxLookup(io.op, 0.U)(Seq(
    // 算术运算
    ALUOp.ADD  -> (io.a + io.b),
    ALUOp.SUB  -> (io.a - io.b),

    // 比较运算
    ALUOp.SLT  -> Mux(io.a.asSInt < io.b.asSInt, 1.U, 0.U),
    ALUOp.SLTU -> Mux(io.a < io.b, 1.U, 0.U),

    // 逻辑运算
    ALUOp.AND  -> (io.a & io.b),
    ALUOp.OR   -> (io.a | io.b),
    ALUOp.XOR  -> (io.a ^ io.b),

    // 移位运算
    ALUOp.SLL  -> (io.a << shamt),
    ALUOp.SRL  -> (io.a >> shamt),
    ALUOp.SRA  -> (io.a.asSInt >> shamt).asUInt,

    // 直通操作
    ALUOp.COPY_A -> io.a,
    ALUOp.COPY_B -> io.b
  ))

  // 零标志：结果是否为0
  io.zero := io.out === 0.U
}
