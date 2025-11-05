# UART Loopback Test Program
# Tests: UART peripheral communication
# Sends "HELLO" via UART and reads it back

.section .text
.globl _start

# UART register offsets
.equ UART_BASE,    0x20000000
.equ UART_TXDATA,  0x00
.equ UART_RXDATA,  0x04
.equ UART_STATUS,  0x08
.equ UART_BAUD,    0x0C

# Status bits
.equ STATUS_TX_EMPTY, 0x01
.equ STATUS_TX_FULL,  0x02
.equ STATUS_RX_VALID, 0x04
.equ STATUS_RX_EMPTY, 0x08

_start:
    # Initialize UART base address
    lui  x1, 0x20000          # x1 = 0x20000000 (UART base)

    # Configure UART baud rate
    addi x2, x0, 217          # Divisor for 115200 @ 25MHz
    sw   x2, 12(x1)           # UART_BAUD = 217

    # Prepare message "HELLO" in registers
    addi x3, x0, 'H'          # x3 = 'H' (72)
    addi x4, x0, 'E'          # x4 = 'E' (69)
    addi x5, x0, 'L'          # x5 = 'L' (76)
    addi x6, x0, 'L'          # x6 = 'L' (76)
    addi x7, x0, 'O'          # x7 = 'O' (79)

    # Send 'H'
    jal  x15, uart_send_char  # Call with x3 = char
    add  x3, x0, x4           # Load 'E'

    # Send 'E'
    jal  x15, uart_send_char
    add  x3, x0, x5           # Load 'L'

    # Send first 'L'
    jal  x15, uart_send_char
    add  x3, x0, x6           # Load 'L'

    # Send second 'L'
    jal  x15, uart_send_char
    add  x3, x0, x7           # Load 'O'

    # Send 'O'
    jal  x15, uart_send_char

    # Now read back the characters
    # Store received data starting at 0x80000500
    lui  x8, 0x80000          # x8 = base for RX buffer
    addi x8, x8, 0x500        # x8 = 0x80000500
    addi x9, x0, 5            # x9 = 5 (count)

receive_loop:
    beq  x9, x0, rx_done      # If count == 0, done

    # Wait for RX data
wait_rx:
    lw   x10, 8(x1)           # Read STATUS
    andi x11, x10, 0x04       # Check RX_VALID bit
    beq  x11, x0, wait_rx     # If not valid, wait

    # Read character
    lw   x12, 4(x1)           # Read RXDATA
    sb   x12, 0(x8)           # Store in buffer

    # Next character
    addi x8, x8, 1            # Increment pointer
    addi x9, x9, -1           # Decrement count
    beq  x0, x0, receive_loop

rx_done:
    # Verify received data matches sent data
    # Compare "HELLO" at 0x80000500
    lui  x13, 0x80000
    addi x13, x13, 0x500

    lb   x14, 0(x13)          # Should be 'H'
    addi x2, x0, 'H'
    bne  x14, x2, test_fail

    lb   x14, 1(x13)          # Should be 'E'
    addi x2, x0, 'E'
    bne  x14, x2, test_fail

    lb   x14, 2(x13)          # Should be 'L'
    addi x2, x0, 'L'
    bne  x14, x2, test_fail

    lb   x14, 3(x13)          # Should be 'L'
    bne  x14, x2, test_fail

    lb   x14, 4(x13)          # Should be 'O'
    addi x2, x0, 'O'
    bne  x14, x2, test_fail

test_pass:
    # Signal success via GPIO
    lui  x15, 0x20010
    addi x14, x0, 0xFF
    sw   x14, 4(x15)          # GPIO_DATA_OUT = 0xFF
    beq  x0, x0, halt

test_fail:
    # Signal failure via GPIO
    lui  x15, 0x20010
    addi x14, x0, 0x00
    sw   x14, 4(x15)          # GPIO_DATA_OUT = 0x00
    beq  x0, x0, halt

halt:
    beq  x0, x0, halt

# Subroutine: Send character via UART
# Input: x3 = character to send
# Uses: x10, x11, x1 (UART base)
uart_send_char:
    # Wait for TX FIFO not full
wait_tx:
    lw   x10, 8(x1)           # Read STATUS
    andi x11, x10, 0x02       # Check TX_FULL bit
    bne  x11, x0, wait_tx     # If full, wait

    # Send character
    sw   x3, 0(x1)            # Write to TXDATA

    # Return
    jalr x0, 0(x15)           # Return to caller
