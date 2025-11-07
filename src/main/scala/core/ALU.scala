package core

import chisel3._
import chisel3.util._
import common.Config._

/**
 * RV32E 算术逻辑单元 (ALU)
 * 支持 RV32EM 指令集的算术和逻辑运算
 *
 * 支持的操作:
 * - 算术: ADD, SUB
 * - 逻辑: AND, OR, XOR
 * - 移位: SLL, SRL, SRA
 * - 比较: SLT, SLTU
 * - 乘除: MUL, DIV, REM (M扩展)
 */
class ALU extends Module {
  val io = IO(new Bundle {
    val op = Input(UInt(4.W))        // ALU 操作码
    val src1 = Input(UInt(XLEN.W))   // 源操作数 1
    val src2 = Input(UInt(XLEN.W))   // 源操作数 2
    val out = Output(UInt(XLEN.W))   // 结果
  })

  // 移位量（只使用低 5 位）
  val shamt = io.src2(4, 0)

  // ============================================================================
  // 算术运算
  // ============================================================================

  val add_result = io.src1 + io.src2
  val sub_result = io.src1 - io.src2

  // ============================================================================
  // 逻辑运算
  // ============================================================================

  val and_result = io.src1 & io.src2
  val or_result = io.src1 | io.src2
  val xor_result = io.src1 ^ io.src2

  // ============================================================================
  // 移位运算
  // ============================================================================

  val sll_result = io.src1 << shamt
  val srl_result = io.src1 >> shamt
  val sra_result = (io.src1.asSInt >> shamt).asUInt

  // ============================================================================
  // 比较运算
  // ============================================================================

  // 有符号比较
  val slt_result = Mux(io.src1.asSInt < io.src2.asSInt, 1.U, 0.U)

  // 无符号比较
  val sltu_result = Mux(io.src1 < io.src2, 1.U, 0.U)

  // ============================================================================
  // 乘除运算 (M 扩展)
  // ============================================================================

  // 乘法 (取低 32 位)
  val mul_result = (io.src1 * io.src2)(XLEN - 1, 0)

  // 除法 (有符号)
  val div_result = WireDefault(0.U(XLEN.W))
  val rem_result = WireDefault(0.U(XLEN.W))

  when(io.src2 =/= 0.U) {
    div_result := (io.src1.asSInt / io.src2.asSInt).asUInt
    rem_result := (io.src1.asSInt % io.src2.asSInt).asUInt
  }.otherwise {
    // 除零情况：根据 RISC-V 规范
    div_result := "hFFFFFFFF".U  // 全1
    rem_result := io.src1         // 返回被除数
  }

  // ============================================================================
  // 输出多路选择
  // ============================================================================

  io.out := MuxLookup(io.op, 0.U)(Seq(
    AluOp.ADD -> add_result,
    AluOp.SUB -> sub_result,
    AluOp.SLT -> slt_result,
    AluOp.SLTU -> sltu_result,
    AluOp.AND -> and_result,
    AluOp.OR -> or_result,
    AluOp.XOR -> xor_result,
    AluOp.SLL -> sll_result,
    AluOp.SRL -> srl_result,
    AluOp.SRA -> sra_result,
    AluOp.MUL -> mul_result,
    AluOp.DIV -> div_result,
    AluOp.REM -> rem_result
  ))
}

/**
 * ALU 测试模块
 */
object ALUMain extends App {
  println("Generating ALU Verilog...")
  val verilog = circt.stage.ChiselStage.emitSystemVerilog(
    new ALU,
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info")
  )
  println(verilog)
}
