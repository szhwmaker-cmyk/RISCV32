package core

import chisel3._
import chisel3.util._

/**
 * Immediate Generation Types
 * RISC-V 指令格式的立即数类型
 */
object ImmType {
  val width = 3

  val I_TYPE = 0.U(width.W)  // I-type: imm[11:0]
  val S_TYPE = 1.U(width.W)  // S-type: imm[11:5] | imm[4:0]
  val B_TYPE = 2.U(width.W)  // B-type: imm[12|10:5] | imm[4:1|11]
  val U_TYPE = 3.U(width.W)  // U-type: imm[31:12]
  val J_TYPE = 4.U(width.W)  // J-type: imm[20|10:1|11|19:12]
  val ZERO   = 5.U(width.W)  // 输出0（用于寄存器-寄存器指令）
}

/**
 * Immediate Generator (立即数生成器)
 *
 * 功能：
 * - 从32位RISC-V指令中提取立即数
 * - 支持所有RISC-V指令格式（I, S, B, U, J）
 * - 自动进行符号扩展
 *
 * RISC-V立即数编码格式：
 *
 * I-type (12-bit): 用于 ADDI, LOAD 等
 *   31          20 19    15 14    12 11    7 6      0
 *   [   imm[11:0]  ][ rs1  ][ funct3 ][ rd  ][ opcode ]
 *
 * S-type (12-bit): 用于 STORE
 *   31          25 24    20 19    15 14    12 11    7 6      0
 *   [ imm[11:5] ][ rs2  ][ rs1  ][ funct3 ][imm[4:0]][ opcode ]
 *
 * B-type (13-bit): 用于 BRANCH
 *   31 30      25 24    20 19    15 14    12 11    8 7 6      0
 *   [i][imm[10:5]][ rs2  ][ rs1  ][ funct3 ][imm[4:1]][i][opcode]
 *   12                                                 11
 *
 * U-type (20-bit): 用于 LUI, AUIPC
 *   31                                    12 11    7 6      0
 *   [          imm[31:12]                  ][ rd  ][ opcode ]
 *
 * J-type (21-bit): 用于 JAL
 *   31 30                21 20 19        12 11    7 6      0
 *   [i][   imm[10:1]    ][i][imm[19:12] ][ rd  ][ opcode ]
 *   20                    11
 */
class ImmGen extends Module {
  val io = IO(new Bundle {
    val inst = Input(UInt(32.W))         // 输入指令
    val imm_type = Input(UInt(ImmType.width.W))  // 立即数类型
    val imm = Output(SInt(32.W))         // 输出立即数（符号扩展到32位）
  })

  // 从指令中提取各种格式的立即数
  val i_imm = io.inst(31, 20)  // I-type: [31:20]
  val s_imm = Cat(io.inst(31, 25), io.inst(11, 7))  // S-type: [31:25]|[11:7]
  val b_imm = Cat(
    io.inst(31),      // imm[12]
    io.inst(7),       // imm[11]
    io.inst(30, 25),  // imm[10:5]
    io.inst(11, 8),   // imm[4:1]
    0.U(1.W)          // imm[0] = 0 (分支地址必须对齐到2字节)
  )
  val u_imm = Cat(io.inst(31, 12), 0.U(12.W))  // U-type: [31:12] << 12
  val j_imm = Cat(
    io.inst(31),      // imm[20]
    io.inst(19, 12),  // imm[19:12]
    io.inst(20),      // imm[11]
    io.inst(30, 21),  // imm[10:1]
    0.U(1.W)          // imm[0] = 0 (跳转地址必须对齐到2字节)
  )

  // 根据类型选择立即数并进行符号扩展
  io.imm := MuxLookup(io.imm_type, 0.S)(Seq(
    ImmType.I_TYPE -> i_imm.asSInt,
    ImmType.S_TYPE -> s_imm.asSInt,
    ImmType.B_TYPE -> b_imm.asSInt,
    ImmType.U_TYPE -> u_imm.asSInt,  // U-type不需要符号扩展，已经是完整的32位
    ImmType.J_TYPE -> j_imm.asSInt,
    ImmType.ZERO   -> 0.S
  ))
}
