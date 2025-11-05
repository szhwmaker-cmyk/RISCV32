# Minimal RTOS Simulation for RV32E SoC
# Simulates RT-Thread-like behavior for testing
# This is a standalone program that demonstrates multi-tasking concepts

.section .text
.globl _start

# Constants
.equ RAM_BASE,      0x80000000
.equ UART_BASE,     0x20000000
.equ GPIO_BASE,     0x20010000
.equ STACK_SIZE,    1024

_start:
    # Initialize stack pointer
    lui  sp, 0x80010          # sp = 0x80010000 (top of RAM)

    # Initialize BSS (if any)
    # Not needed for this simple demo

    # Print banner
    jal  print_banner

    # Initialize GPIO for LED
    jal  init_gpio

    # Initialize task system
    jal  init_tasks

    # Main scheduler loop
main_loop:
    # Task 1: Blink LED
    jal  task_led

    # Task 2: Print message
    jal  task_uart

    # Task 3: Update counter
    jal  task_counter

    # Delay
    addi x1, x0, 100
delay_loop:
    addi x1, x1, -1
    bne  x1, x0, delay_loop

    # Repeat
    beq  x0, x0, main_loop

#=========================================
# Task Functions
#=========================================

task_led:
    # Toggle GPIO LED (bit 0)
    lui  x5, 0x20010          # GPIO base
    lw   x6, 4(x5)            # Read current GPIO_DATA_OUT
    xori x6, x6, 0x01         # Toggle bit 0
    sw   x6, 4(x5)            # Write back
    jalr x0, 0(x1)            # Return

task_uart:
    # Print "TICK" message every N calls
    # Use counter to throttle output
    lui  x7, 0x80000
    addi x7, x7, 0x100        # Counter at 0x80000100
    lw   x8, 0(x7)            # Load counter
    addi x8, x8, 1            # Increment
    sw   x8, 0(x7)            # Store

    # Check if counter reached threshold (e.g., 10)
    addi x9, x0, 10
    bne  x8, x9, task_uart_skip

    # Reset counter
    sw   x0, 0(x7)

    # Print message
    lui  x1, 0x20000          # UART base

    # "TICK\n"
    addi x2, x0, 'T'
    sw   x2, 0(x1)
    addi x2, x0, 'I'
    sw   x2, 0(x1)
    addi x2, x0, 'C'
    sw   x2, 0(x1)
    addi x2, x0, 'K'
    sw   x2, 0(x1)
    addi x2, x0, '\n'
    sw   x2, 0(x1)

task_uart_skip:
    jalr x0, 0(x1)            # Return

task_counter:
    # Increment global counter
    lui  x10, 0x80000
    addi x10, x10, 0x104      # Counter at 0x80000104
    lw   x11, 0(x10)
    addi x11, x11, 1
    sw   x11, 0(x10)
    jalr x0, 0(x1)            # Return

#=========================================
# Initialization Functions
#=========================================

init_gpio:
    lui  x2, 0x20010          # GPIO base
    addi x3, x0, 0xFF         # All outputs
    sw   x3, 8(x2)            # GPIO_DIR = 0xFF
    sw   x3, 12(x2)           # GPIO_OE = 0xFF
    addi x3, x0, 0            # Initial state
    sw   x3, 4(x2)            # GPIO_DATA_OUT = 0
    jalr x0, 0(x1)            # Return

init_tasks:
    # Initialize task-related memory
    lui  x4, 0x80000
    addi x4, x4, 0x100
    sw   x0, 0(x4)            # UART tick counter = 0
    sw   x0, 4(x4)            # Global counter = 0
    jalr x0, 0(x1)            # Return

print_banner:
    # Save return address
    addi sp, sp, -4
    sw   x1, 0(sp)

    lui  x5, 0x20000          # UART base

    # Print "RT-Thread Sim\n"
    addi x6, x0, 'R'
    sw   x6, 0(x5)
    addi x6, x0, 'T'
    sw   x6, 0(x5)
    addi x6, x0, '-'
    sw   x6, 0(x5)
    addi x6, x0, 'T'
    sw   x6, 0(x5)
    addi x6, x0, 'h'
    sw   x6, 0(x5)
    addi x6, x0, 'r'
    sw   x6, 0(x5)
    addi x6, x0, 'e'
    sw   x6, 0(x5)
    addi x6, x0, 'a'
    sw   x6, 0(x5)
    addi x6, x0, 'd'
    sw   x6, 0(x5)
    addi x6, x0, ' '
    sw   x6, 0(x5)
    addi x6, x0, 'S'
    sw   x6, 0(x5)
    addi x6, x0, 'i'
    sw   x6, 0(x5)
    addi x6, x0, 'm'
    sw   x6, 0(x5)
    addi x6, x0, '\n'
    sw   x6, 0(x5)

    # Restore and return
    lw   x1, 0(sp)
    addi sp, sp, 4
    jalr x0, 0(x1)

halt:
    beq  x0, x0, halt
