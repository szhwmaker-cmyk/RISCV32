package common

import chisel3._

/**
 * RV32E SoC 系统配置参数
 * 包含处理器核心参数、总线配置和地址空间映射
 * 支持 RT-Thread 和 Wishbone B4 总线
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

  /** 复位后的 PC 值 (指向 BootROM 起始地址) */
  val PC_RESET = 0x00000000L

  /** 指令宽度 */
  val INST_WIDTH = 32


  // ============================================================================
  // 总线参数 (Wishbone B4)
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

  /** BootROM 大小 (64KB) */
  val BOOTROM_SIZE = 64 * 1024

  /** BootROM 地址宽度 (64KB = 2^16) */
  val BOOTROM_ADDR_WIDTH = 16

  /** SRAM 大小 (128KB) */
  val SRAM_SIZE = 128 * 1024

  /** SRAM 地址宽度 (128KB = 2^17) */
  val SRAM_ADDR_WIDTH = 17


  // ============================================================================
  // 地址空间映射 (符合 RT-Thread 需求)
  // ============================================================================

  /** BootROM 基地址 (64KB: 0x0000_0000 - 0x0000_FFFF) */
  val BOOTROM_BASE = 0x00000000L
  val BOOTROM_END  = BOOTROM_BASE + BOOTROM_SIZE - 1

  /** UART0 基地址 (4KB: 0x1000_0000 - 0x1000_0FFF) */
  val UART_BASE = 0x10000000L
  val UART_SIZE = 0x1000L
  val UART_END  = UART_BASE + UART_SIZE - 1

  /** SPI0 基地址 (4KB: 0x1000_1000 - 0x1000_1FFF) */
  val SPI_BASE = 0x10001000L
  val SPI_SIZE = 0x1000L
  val SPI_END  = SPI_BASE + SPI_SIZE - 1

  /** I2C0 基地址 (4KB: 0x1000_2000 - 0x1000_2FFF) */
  val I2C_BASE = 0x10002000L
  val I2C_SIZE = 0x1000L
  val I2C_END  = I2C_BASE + I2C_SIZE - 1

  /** GPIO 基地址 (4KB: 0x1000_3000 - 0x1000_3FFF) */
  val GPIO_BASE = 0x10003000L
  val GPIO_SIZE = 0x1000L
  val GPIO_END  = GPIO_BASE + GPIO_SIZE - 1

  /** Timer 基地址 (4KB: 0x1000_4000 - 0x1000_4FFF) */
  val TIMER_BASE = 0x10004000L
  val TIMER_SIZE = 0x1000L
  val TIMER_END  = TIMER_BASE + TIMER_SIZE - 1

  /** SRAM 基地址 (128KB: 0x2000_0000 - 0x2001_FFFF) */
  val SRAM_BASE = 0x20000000L
  val SRAM_END  = SRAM_BASE + SRAM_SIZE - 1


  // ============================================================================
  // 外设寄存器偏移 - UART 16550 兼容
  // ============================================================================

  object UartRegs {
    val RBR_THR = 0x00  // 接收缓冲/发送保持寄存器
    val IER     = 0x04  // 中断使能寄存器
    val IIR_FCR = 0x08  // 中断标识/FIFO控制寄存器
    val LCR     = 0x0C  // 线路控制寄存器
    val MCR     = 0x10  // Modem控制寄存器
    val LSR     = 0x14  // 线路状态寄存器
    val MSR     = 0x18  // Modem状态寄存器
    val SCR     = 0x1C  // 暂存寄存器
    val DLL     = 0x00  // 波特率除数低字节 (DLAB=1)
    val DLH     = 0x04  // 波特率除数高字节 (DLAB=1)
  }


  // ============================================================================
  // 外设寄存器偏移 - GPIO (32位)
  // ============================================================================

  object GpioRegs {
    val DATA_IN  = 0x00  // 输入数据寄存器
    val DATA_OUT = 0x04  // 输出数据寄存器
    val DIR      = 0x08  // 方向控制寄存器 (0=输入, 1=输出)
    val OE       = 0x0C  // 输出使能寄存器
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
  // 外设寄存器偏移 - Timer (64位)
  // ============================================================================

  object TimerRegs {
    val MTIME_LO   = 0x00  // 时间寄存器低32位
    val MTIME_HI   = 0x04  // 时间寄存器高32位
    val MTIMECMP_LO = 0x08  // 时间比较寄存器低32位
    val MTIMECMP_HI = 0x0C  // 时间比较寄存器高32位
    val CTRL       = 0x10  // 控制寄存器
  }


  // ============================================================================
  // 系统配置
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
    val MUL  = 10.U(4.W) // Multiply
    val DIV  = 11.U(4.W) // Divide
    val REM  = 12.U(4.W) // Remainder
    val NOP  = 15.U(4.W)
  }


  // ============================================================================
  // CSR 地址 (Machine Mode)
  // ============================================================================

  object CSRAddr {
    // Machine Information
    val MVENDORID  = 0xF11
    val MARCHID    = 0xF12
    val MIMPID     = 0xF13
    val MHARTID    = 0xF14

    // Machine Trap Setup
    val MSTATUS    = 0x300
    val MISA       = 0x301
    val MIE        = 0x304
    val MTVEC      = 0x305

    // Machine Trap Handling
    val MSCRATCH   = 0x340
    val MEPC       = 0x341
    val MCAUSE     = 0x342
    val MTVAL      = 0x343
    val MIP        = 0x344

    // Machine Counter/Timers
    val MCYCLE     = 0xB00
    val MINSTRET   = 0xB02
    val MCYCLEH    = 0xB80
    val MINSTRETH  = 0xB82
  }


  // ============================================================================
  // 异常和中断编码
  // ============================================================================

  object Exception {
    val INST_ADDR_MISALIGNED  = 0
    val INST_ACCESS_FAULT     = 1
    val ILLEGAL_INST          = 2
    val BREAKPOINT            = 3
    val LOAD_ADDR_MISALIGNED  = 4
    val LOAD_ACCESS_FAULT     = 5
    val STORE_ADDR_MISALIGNED = 6
    val STORE_ACCESS_FAULT    = 7
    val ECALL_M               = 11
  }

  object Interrupt {
    val M_SOFTWARE = 3
    val M_TIMER    = 7
    val M_EXTERNAL = 11
  }


  // ============================================================================
  // 指令类型
  // ============================================================================

  object InstType {
    val R = 0.U(3.W)  // R-type
    val I = 1.U(3.W)  // I-type
    val S = 2.U(3.W)  // S-type
    val B = 3.U(3.W)  // B-type
    val U = 4.U(3.W)  // U-type
    val J = 5.U(3.W)  // J-type
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
   * 地址解码：判断地址属于哪个外设/存储器
   */
  def decodeAddr(addr: UInt): (Bool, Bool, Bool, Bool, Bool, Bool, Bool) = {
    val sel_bootrom = inRange(addr, BOOTROM_BASE, BOOTROM_SIZE)
    val sel_uart    = inRange(addr, UART_BASE, UART_SIZE)
    val sel_gpio    = inRange(addr, GPIO_BASE, GPIO_SIZE)
    val sel_spi     = inRange(addr, SPI_BASE, SPI_SIZE)
    val sel_i2c     = inRange(addr, I2C_BASE, I2C_SIZE)
    val sel_timer   = inRange(addr, TIMER_BASE, TIMER_SIZE)
    val sel_sram    = inRange(addr, SRAM_BASE, SRAM_SIZE)

    (sel_bootrom, sel_uart, sel_gpio, sel_spi, sel_i2c, sel_timer, sel_sram)
  }


  // ============================================================================
  // 调试配置
  // ============================================================================

  /** 是否启用调试输出 */
  val DEBUG_ENABLE = false

  /** 是否启用性能计数器 */
  val PERF_COUNTER_ENABLE = true
}
