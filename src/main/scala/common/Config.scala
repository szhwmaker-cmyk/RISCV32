package common

import chisel3._
import chisel3.util._

// ============================================================================
// RV32E SoC 系统配置参数
// ============================================================================

object Config {
  // 基础参数
  val XLEN = 32              // 数据宽度
  val REG_NUM = 16           // RV32E: 16个寄存器
  val REG_ADDR_WIDTH = 4     // log2(16) = 4

  // 地址空间映射
  val FLASH_BASE   = 0x10000000L  // SPI Flash 基地址
  val FLASH_SIZE   = 0x10000000L  // 256 MB

  val UART_BASE    = 0x20000000L  // UART 基地址
  val UART_SIZE    = 0x00010000L  // 64 KB

  val GPIO_BASE    = 0x20010000L  // GPIO 基地址
  val GPIO_SIZE    = 0x00010000L  // 64 KB

  val SPI_BASE     = 0x20020000L  // SPI Master 基地址
  val SPI_SIZE     = 0x00010000L  // 64 KB

  val I2C_BASE     = 0x20030000L  // I2C Master 基地址
  val I2C_SIZE     = 0x00010000L  // 64 KB

  val RAM_BASE     = 0x80000000L  // RAM 基地址
  val RAM_SIZE     = 0x10000000L  // 256 MB 地址空间
  val RAM_ACTUAL   = 0x00040000L  // 实际 256 KB

  // 外设寄存器偏移
  object UartReg {
    val TXDATA = 0x00
    val RXDATA = 0x04
    val STATUS = 0x08
    val BAUD   = 0x0C
    val CTRL   = 0x10
  }

  object GpioReg {
    val DATA_IN  = 0x00
    val DATA_OUT = 0x04
    val DIR      = 0x08
    val OE       = 0x0C
  }

  object SpiReg {
    val CTRL     = 0x00
    val DIV      = 0x04
    val DATA     = 0x08
    val STATUS   = 0x0C
  }

  object I2cReg {
    val CTRL     = 0x00
    val DIV      = 0x04
    val ADDR     = 0x08
    val DATA     = 0x0C
    val STATUS   = 0x10
  }

  // 时钟配置
  val CLOCK_FREQ = 50000000   // 50 MHz
  val UART_BAUD  = 115200     // 波特率
  val BAUD_DIV   = CLOCK_FREQ / UART_BAUD  // ≈ 434

  // Boot 配置
  val BOOT_ADDR  = FLASH_BASE // 启动地址
  val PROG_ADDR  = RAM_BASE   // 程序执行地址
}

// ============================================================================
// ALU 操作码定义
// ============================================================================

object AluOp {
  val ADD  = 0.U(4.W)
  val SUB  = 1.U(4.W)
  val SLT  = 2.U(4.W)  // Set Less Than
  val SLTU = 3.U(4.W)  // Set Less Than Unsigned
  val AND  = 4.U(4.W)
  val OR   = 5.U(4.W)
  val XOR  = 6.U(4.W)
  val SLL  = 7.U(4.W)  // Shift Left Logical
  val SRL  = 8.U(4.W)  // Shift Right Logical
  val SRA  = 9.U(4.W)  // Shift Right Arithmetic
  val COPY = 10.U(4.W) // Copy src1 (for LUI)
}

// ============================================================================
// 指令类型定义
// ============================================================================

object InstType {
  val R_TYPE = 0.U(3.W)
  val I_TYPE = 1.U(3.W)
  val S_TYPE = 2.U(3.W)
  val B_TYPE = 3.U(3.W)
  val U_TYPE = 4.U(3.W)
  val J_TYPE = 5.U(3.W)
}

// ============================================================================
// 分支类型定义
// ============================================================================

object BranchType {
  val NO_BR  = 0.U(3.W)
  val BEQ    = 1.U(3.W)
  val BNE    = 2.U(3.W)
  val BLT    = 3.U(3.W)
  val BGE    = 4.U(3.W)
  val BLTU   = 5.U(3.W)
  val BGEU   = 6.U(3.W)
}

// ============================================================================
// 内存访问类型
// ============================================================================

object MemType {
  val BYTE  = 0.U(2.W)  // LB/SB
  val HALF  = 1.U(2.W)  // LH/SH
  val WORD  = 2.U(2.W)  // LW/SW
  val BYTEU = 3.U(2.W)  // LBU
  val HALFU = 4.U(2.W)  // LHU
}

// ============================================================================
// 写回数据源选择
// ============================================================================

object WBSrc {
  val ALU_RESULT = 0.U(2.W)
  val MEM_DATA   = 1.U(2.W)
  val PC_PLUS_4  = 2.U(2.W)
}

// ============================================================================
// 控制信号 Bundle
// ============================================================================

class ControlSignals extends Bundle {
  val reg_write  = Bool()      // 是否写寄存器
  val mem_read   = Bool()      // 是否读内存
  val mem_write  = Bool()      // 是否写内存
  val alu_op     = UInt(4.W)   // ALU 操作码
  val alu_src1   = UInt(2.W)   // ALU src1 选择 (0=rs1, 1=PC)
  val alu_src2   = UInt(2.W)   // ALU src2 选择 (0=rs2, 1=imm)
  val branch     = UInt(3.W)   // 分支类型
  val jump       = Bool()      // 跳转指令 (JAL/JALR)
  val wb_src     = UInt(2.W)   // 写回数据源
  val mem_type   = UInt(3.W)   // 内存访问类型
}

// ============================================================================
// 流水线寄存器定义
// ============================================================================

class IF_ID_Reg extends Bundle {
  val pc = UInt(32.W)
  val inst = UInt(32.W)
  val valid = Bool()
}

class ID_EX_Reg extends Bundle {
  val pc = UInt(32.W)
  val rs1_data = UInt(32.W)
  val rs2_data = UInt(32.W)
  val imm = UInt(32.W)
  val rs1 = UInt(4.W)
  val rs2 = UInt(4.W)
  val rd = UInt(4.W)
  val ctrl = new ControlSignals
  val valid = Bool()
}

class EX_MEM_Reg extends Bundle {
  val alu_result = UInt(32.W)
  val rs2_data = UInt(32.W)
  val rd = UInt(4.W)
  val ctrl = new ControlSignals
  val valid = Bool()
  val branch_target = UInt(32.W)
  val branch_taken = Bool()
}

class MEM_WB_Reg extends Bundle {
  val alu_result = UInt(32.W)
  val mem_data = UInt(32.W)
  val rd = UInt(4.W)
  val ctrl = new ControlSignals
  val valid = Bool()
}

// ============================================================================
// 内存接口定义
// ============================================================================

class MemPortIO extends Bundle {
  val addr = Output(UInt(32.W))
  val wdata = Output(UInt(32.W))
  val rdata = Input(UInt(32.W))
  val wen = Output(Bool())
  val ren = Output(Bool())
  val mask = Output(UInt(4.W))  // 字节选择
  val valid = Output(Bool())
  val ready = Input(Bool())
}
