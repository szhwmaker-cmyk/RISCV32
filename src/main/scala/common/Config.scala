package common

import chisel3._

/**
 * RV32E SoC 系统配置参数
 * 包含处理器核心参数、总线配置和地址空间映射
 */
object Config {

  // ============================================================================
  // 处理器核心参数
  // ============================================================================

  /** 数据宽度 (RV32) */
  val XLEN = 32

  /** 寄存器数量 (RV32E 使用 16 个寄存器: x0-x15) */
  val REG_NUM = 16

  /** 寄存器地址宽度 (4 位可表示 16 个寄存器) */
  val REG_ADDR_WIDTH = 4

  /** 复位后的 PC 值 (指向 SPI Flash 起始地址) */
  val PC_RESET = 0x10000000L

  /** 指令宽度 */
  val INST_WIDTH = 32


  // ============================================================================
  // 总线参数
  // ============================================================================

  /** 总线数据宽度 */
  val BUS_WIDTH = 32

  /** 地址总线宽度 */
  val ADDR_WIDTH = 32

  /** 字节选择信号宽度 (32位总线需要4个字节选择位) */
  val SEL_WIDTH = 4


  // ============================================================================
  // 存储器参数
  // ============================================================================

  /** RAM 大小 (64KB) */
  val RAM_SIZE = 64 * 1024

  /** RAM 地址宽度 (64KB = 2^16) */
  val RAM_ADDR_WIDTH = 16

  /** SPI Flash 大小 (假设 16MB) */
  val FLASH_SIZE = 16 * 1024 * 1024


  // ============================================================================
  // 地址空间映射
  // ============================================================================

  /** SPI Flash 基地址 (256MB 空间: 0x1000_0000 - 0x1FFF_FFFF) */
  val SPI_FLASH_BASE = 0x10000000L
  val SPI_FLASH_SIZE = 0x10000000L  // 256MB
  val SPI_FLASH_END  = SPI_FLASH_BASE + SPI_FLASH_SIZE - 1

  /** UART 基地址 (64KB 空间) */
  val UART_BASE = 0x20000000L
  val UART_SIZE = 0x10000L  // 64KB
  val UART_END  = UART_BASE + UART_SIZE - 1

  /** GPIO 基地址 (64KB 空间) */
  val GPIO_BASE = 0x20010000L
  val GPIO_SIZE = 0x10000L  // 64KB
  val GPIO_END  = GPIO_BASE + GPIO_SIZE - 1

  /** SPI Master 基地址 (64KB 空间) */
  val SPI_BASE = 0x20020000L
  val SPI_SIZE = 0x10000L  // 64KB
  val SPI_END  = SPI_BASE + SPI_SIZE - 1

  /** I2C Master 基地址 (64KB 空间) */
  val I2C_BASE = 0x20030000L
  val I2C_SIZE = 0x10000L  // 64KB
  val I2C_END  = I2C_BASE + I2C_SIZE - 1

  /** RAM 基地址 (256MB 空间: 0x8000_0000 - 0x8FFF_FFFF) */
  val RAM_BASE = 0x80000000L
  val RAM_SPACE_SIZE = 0x10000000L  // 256MB 地址空间
  val RAM_END  = RAM_BASE + RAM_SPACE_SIZE - 1


  // ============================================================================
  // 外设寄存器偏移 - UART
  // ============================================================================

  object UartRegs {
    val TXDATA = 0x00  // 发送数据寄存器
    val RXDATA = 0x04  // 接收数据寄存器
    val STATUS = 0x08  // 状态寄存器 [tx_full, tx_empty, rx_valid, ...]
    val BAUD   = 0x0C  // 波特率分频寄存器
    val CTRL   = 0x10  // 控制寄存器 [tx_en, rx_en, ...]
  }


  // ============================================================================
  // 外设寄存器偏移 - GPIO
  // ============================================================================

  object GpioRegs {
    val DATA_IN  = 0x00  // 输入数据寄存器
    val DATA_OUT = 0x04  // 输出数据寄存器
    val DIR      = 0x08  // 方向控制寄存器 (0=输入, 1=输出)
    val OE       = 0x0C  // 输出使能寄存器
  }


  // ============================================================================
  // 外设寄存器偏移 - SPI Flash Controller
  // ============================================================================

  object SpiFlashRegs {
    val CTRL  = 0x00  // 控制寄存器 [start, busy, done]
    val DIV   = 0x04  // 时钟分频寄存器
    val ADDR  = 0x08  // Flash 地址寄存器
    val DATA  = 0x0C  // 读取数据寄存器
    val CMD   = 0x10  // SPI 命令寄存器
  }


  // ============================================================================
  // 外设寄存器偏移 - SPI Master
  // ============================================================================

  object SpiRegs {
    val TXDATA = 0x00  // 发送数据寄存器
    val RXDATA = 0x04  // 接收数据寄存器
    val CTRL   = 0x08  // 控制寄存器 [CPOL, CPHA, data_width, ...]
    val DIV    = 0x0C  // 时钟分频寄存器
    val STATUS = 0x10  // 状态寄存器 [busy, done, ...]
    val SS     = 0x14  // 片选寄存器
  }


  // ============================================================================
  // 外设寄存器偏移 - I2C Master
  // ============================================================================

  object I2cRegs {
    val DATA   = 0x00  // 数据寄存器
    val ADDR   = 0x04  // 从设备地址寄存器
    val CTRL   = 0x08  // 控制寄存器 [start, stop, read, write, ack, ...]
    val DIV    = 0x0C  // 时钟分频寄存器
    val STATUS = 0x10  // 状态寄存器 [busy, ack_received, ...]
  }


  // ============================================================================
  // UART 默认配置
  // ============================================================================

  /** 系统时钟频率 (50 MHz) */
  val SYS_CLK_FREQ = 50000000

  /** 默认波特率 (115200) */
  val UART_DEFAULT_BAUD = 115200

  /** UART 波特率分频值 = SYS_CLK_FREQ / BAUD_RATE */
  val UART_DEFAULT_DIV = SYS_CLK_FREQ / UART_DEFAULT_BAUD


  // ============================================================================
  // 流水线参数
  // ============================================================================

  /** 流水线阶段数 */
  val PIPELINE_STAGES = 5  // IF, ID, EX, MEM, WB

  /** 分支预测策略 (0: 不跳转, 1: 总是跳转) */
  val BRANCH_PREDICTION = 0  // 静态预测不跳转


  // ============================================================================
  // ALU 操作码
  // ============================================================================

  object AluOp {
    val ADD  = 0.U(4.W)
    val SUB  = 1.U(4.W)
    val SLT  = 2.U(4.W)  // Set Less Than (signed)
    val SLTU = 3.U(4.W)  // Set Less Than Unsigned
    val AND  = 4.U(4.W)
    val OR   = 5.U(4.W)
    val XOR  = 6.U(4.W)
    val SLL  = 7.U(4.W)  // Shift Left Logical
    val SRL  = 8.U(4.W)  // Shift Right Logical
    val SRA  = 9.U(4.W)  // Shift Right Arithmetic
    val NOP  = 15.U(4.W)
  }


  // ============================================================================
  // 辅助函数：地址匹配
  // ============================================================================

  /**
   * 检查地址是否在指定范围内
   */
  def inRange(addr: UInt, base: Long, size: Long): Bool = {
    val end = base + size - 1
    addr >= base.U && addr <= end.U
  }

  /**
   * 地址解码：判断地址属于哪个外设
   */
  def decodeAddr(addr: UInt): (Bool, Bool, Bool, Bool, Bool, Bool) = {
    val sel_flash = inRange(addr, SPI_FLASH_BASE, SPI_FLASH_SIZE)
    val sel_uart  = inRange(addr, UART_BASE, UART_SIZE)
    val sel_gpio  = inRange(addr, GPIO_BASE, GPIO_SIZE)
    val sel_spi   = inRange(addr, SPI_BASE, SPI_SIZE)
    val sel_i2c   = inRange(addr, I2C_BASE, I2C_SIZE)
    val sel_ram   = inRange(addr, RAM_BASE, RAM_SPACE_SIZE)

    (sel_flash, sel_uart, sel_gpio, sel_spi, sel_i2c, sel_ram)
  }


  // ============================================================================
  // 调试配置
  // ============================================================================

  /** 是否启用调试输出 */
  val DEBUG_ENABLE = false

  /** 是否启用性能计数器 */
  val PERF_COUNTER_ENABLE = true
}
