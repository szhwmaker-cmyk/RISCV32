# Multi-Peripheral Integration Test
# Tests: GPIO + UART + SPI Flash coordination
# Scenario: Read from SPI Flash, process data, output via GPIO and UART

.section .text
.globl _start

# Peripheral base addresses
.equ GPIO_BASE,       0x20010000
.equ UART_BASE,       0x20000000
.equ SPI_CTRL_BASE,   0x20040000
.equ SPI_FLASH_BASE,  0x10000000

# GPIO register offsets
.equ GPIO_DATA_IN,    0x00
.equ GPIO_DATA_OUT,   0x04
.equ GPIO_DIR,        0x08
.equ GPIO_OE,         0x0C

# UART register offsets
.equ UART_TXDATA,     0x00
.equ UART_STATUS,     0x08
.equ UART_BAUD,       0x0C

# SPI Flash control register offsets
.equ SPI_CTRL,        0x00
.equ SPI_DIV,         0x04
.equ SPI_ADDR,        0x08
.equ SPI_DATA,        0x0C

_start:
    # ========== Stage 1: Initialize GPIO ==========
    lui  x1, 0x20010          # x1 = GPIO_BASE

    # Configure GPIO[7:0] as outputs
    addi x2, x0, 0xFF         # Lower 8 bits as output
    sw   x2, 8(x1)            # GPIO_DIR = 0xFF
    sw   x2, 12(x1)           # GPIO_OE = 0xFF

    # Set initial pattern on GPIO
    addi x3, x0, 0x01         # Initial pattern
    sw   x3, 4(x1)            # GPIO_DATA_OUT = 0x01

    # ========== Stage 2: Initialize UART ==========
    lui  x4, 0x20000          # x4 = UART_BASE

    # Set baud rate
    addi x5, x0, 217          # Divisor for 115200
    sw   x5, 12(x4)           # UART_BAUD = 217

    # Send start message "START\n"
    addi x6, x0, 'S'
    sw   x6, 0(x4)
    addi x6, x0, 'T'
    sw   x6, 0(x4)
    addi x6, x0, 'A'
    sw   x6, 0(x4)
    addi x6, x0, 'R'
    sw   x6, 0(x4)
    addi x6, x0, 'T'
    sw   x6, 0(x4)
    addi x6, x0, '\n'
    sw   x6, 0(x4)

    # ========== Stage 3: Configure SPI Flash Read ==========
    lui  x7, 0x20040          # x7 = SPI_CTRL_BASE

    # Set clock divider
    addi x8, x0, 4            # Divisor = 4
    sw   x8, 4(x7)            # SPI_DIV = 4

    # Set flash address to read from
    lui  x9, 0x10             # Address 0x100000
    sw   x9, 8(x7)            # SPI_ADDR = 0x100000

    # Start SPI transaction
    addi x10, x0, 1           # Start bit
    sw   x10, 0(x7)           # SPI_CTRL[0] = 1

    # ========== Stage 4: Wait for SPI completion ==========
spi_wait:
    lw   x11, 0(x7)           # Read SPI_CTRL
    andi x12, x11, 0x02       # Check BUSY bit
    bne  x12, x0, spi_wait    # If busy, keep waiting

    # Read SPI data
    lw   x13, 12(x7)          # x13 = SPI_DATA

    # ========== Stage 5: Process and output data ==========
    # Extract bytes from SPI data and output via GPIO
    andi x14, x13, 0xFF       # Get byte 0
    sw   x14, 4(x1)           # GPIO_DATA_OUT = byte 0

    # Short delay (busy loop)
    addi x15, x0, 100
delay1:
    addi x15, x15, -1
    bne  x15, x0, delay1

    # Output byte 1
    srli x14, x13, 8          # Shift right 8 bits
    andi x14, x14, 0xFF       # Mask to byte
    sw   x14, 4(x1)           # GPIO_DATA_OUT = byte 1

    # Delay
    addi x15, x0, 100
delay2:
    addi x15, x15, -1
    bne  x15, x0, delay2

    # Output byte 2
    srli x14, x13, 16         # Shift right 16 bits
    andi x14, x14, 0xFF
    sw   x14, 4(x1)           # GPIO_DATA_OUT = byte 2

    # Delay
    addi x15, x0, 100
delay3:
    addi x15, x15, -1
    bne  x15, x0, delay3

    # Output byte 3
    srli x14, x13, 24         # Shift right 24 bits
    andi x14, x14, 0xFF
    sw   x14, 4(x1)           # GPIO_DATA_OUT = byte 3

    # ========== Stage 6: Send data via UART ==========
    # Convert first byte to hex ASCII and send via UART
    andi x2, x13, 0xFF        # Get byte 0

    # Send high nibble
    srli x3, x2, 4            # High nibble
    jal  x15, nibble_to_hex   # Convert to ASCII
    sw   x3, 0(x4)            # Send via UART

    # Send low nibble
    andi x3, x2, 0x0F         # Low nibble
    jal  x15, nibble_to_hex
    sw   x3, 0(x4)

    # Send newline
    addi x3, x0, '\n'
    sw   x3, 0(x4)

    # ========== Stage 7: Read GPIO input ==========
    # Simulate external input on GPIO[15:8]
    lw   x5, 0(x1)            # Read GPIO_DATA_IN
    srli x5, x5, 8            # Get upper byte
    andi x5, x5, 0xFF

    # Output to lower GPIO bits (echo)
    sw   x5, 4(x1)            # GPIO_DATA_OUT = input

    # ========== Stage 8: Final status ==========
    # Send completion message
    addi x6, x0, 'D'
    sw   x6, 0(x4)
    addi x6, x0, 'O'
    sw   x6, 0(x4)
    addi x6, x0, 'N'
    sw   x6, 0(x4)
    addi x6, x0, 'E'
    sw   x6, 0(x4)
    addi x6, x0, '\n'
    sw   x6, 0(x4)

    # Set completion pattern on GPIO
    addi x2, x0, 0xAA
    sw   x2, 4(x1)            # GPIO_DATA_OUT = 0xAA

halt:
    beq  x0, x0, halt

# Subroutine: Convert nibble (0-15) to hex ASCII
# Input: x3 = nibble (0-15)
# Output: x3 = ASCII character ('0'-'9' or 'A'-'F')
nibble_to_hex:
    addi x10, x0, 10
    blt  x3, x10, is_digit    # If < 10, it's a digit

    # It's A-F
    addi x3, x3, -10          # Subtract 10
    addi x3, x3, 'A'          # Add 'A' (65)
    jalr x0, 0(x15)           # Return

is_digit:
    # It's 0-9
    addi x3, x3, '0'          # Add '0' (48)
    jalr x0, 0(x15)           # Return
