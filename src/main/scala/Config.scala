package rv32e

import chisel3._

/**
 * System-wide configuration parameters for RV32E SoC
 */
object Config {
  // ========== Processor Parameters ==========
  val XLEN = 32                    // Register width
  val REG_NUM = 16                 // RV32E uses 16 registers (x0-x15)
  val PC_RESET = 0x10000000L       // Reset PC: SPI Flash base address

  // ========== Pipeline Parameters ==========
  val PIPELINE_STAGES = 5          // IF, ID, EX, MEM, WB

  // ========== Bus Parameters ==========
  val BUS_WIDTH = 32               // Data bus width
  val ADDR_WIDTH = 32              // Address bus width
  val BYTE_SELECT_WIDTH = 4        // Byte select width (32-bit / 8)

  // ========== Memory Map ==========
  // SPI Flash (256MB, XIP region)
  val SPI_FLASH_BASE = 0x10000000L
  val SPI_FLASH_SIZE = 0x10000000L // 256MB
  val SPI_FLASH_END  = SPI_FLASH_BASE + SPI_FLASH_SIZE - 1

  // Peripheral Base (512MB region)
  val PERIPHERAL_BASE = 0x20000000L

  // UART (4KB)
  val UART_BASE = 0x20000000L
  val UART_SIZE = 0x1000L
  val UART_END  = UART_BASE + UART_SIZE - 1

  // GPIO (4KB)
  val GPIO_BASE = 0x20010000L
  val GPIO_SIZE = 0x1000L
  val GPIO_END  = GPIO_BASE + GPIO_SIZE - 1

  // SPI Master (4KB)
  val SPI_BASE = 0x20020000L
  val SPI_SIZE = 0x1000L
  val SPI_END  = SPI_BASE + SPI_SIZE - 1

  // I2C Master (4KB)
  val I2C_BASE = 0x20030000L
  val I2C_SIZE = 0x1000L
  val I2C_END  = I2C_BASE + I2C_SIZE - 1

  // SPI Flash Controller registers (4KB)
  val SPI_FLASH_CTRL_BASE = 0x20040000L
  val SPI_FLASH_CTRL_SIZE = 0x1000L
  val SPI_FLASH_CTRL_END  = SPI_FLASH_CTRL_BASE + SPI_FLASH_CTRL_SIZE - 1

  // RAM (256MB, main memory)
  val RAM_BASE = 0x80000000L
  val RAM_SIZE = 0x10000L          // 64KB for now
  val RAM_END  = RAM_BASE + RAM_SIZE - 1

  // ========== Peripheral Parameters ==========
  // UART
  val UART_FIFO_DEPTH = 16
  val UART_DEFAULT_BAUD = 115200
  val UART_CLOCK_FREQ = 50000000   // 50MHz

  // GPIO
  val GPIO_WIDTH = 16

  // SPI
  val SPI_FIFO_DEPTH = 8

  // I2C
  val I2C_FIFO_DEPTH = 8

  // ========== Boot Parameters ==========
  val BOOT_FROM_FLASH = true
  val BOOT_COPY_SIZE = 0x4000      // Copy 16KB from flash to RAM
  val BOOT_ENTRY_POINT = RAM_BASE  // Jump to RAM after boot

  // ========== Helper Functions ==========
  def inRange(addr: UInt, base: Long, end: Long): Bool = {
    (addr >= base.U) && (addr <= end.U)
  }
}
