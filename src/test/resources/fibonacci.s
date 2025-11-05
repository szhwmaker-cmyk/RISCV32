# Fibonacci Calculation Test Program
# Tests: Arithmetic instructions, branch instructions, loop control
# Calculates first 10 Fibonacci numbers and stores in memory
#
# Memory layout:
#   0x80000000: Result array (10 words)
#   0x80000100: Stack pointer initial value

.section .text
.globl _start

_start:
    # Initialize
    lui  x1, 0x80000          # x1 = 0x80000000 (base address)
    addi x2, x0, 10           # x2 = 10 (count)
    addi x3, x0, 0            # x3 = 0 (fib[0])
    addi x4, x0, 1            # x4 = 1 (fib[1])
    addi x5, x0, 0            # x5 = index counter

    # Store first two Fibonacci numbers
    sw   x3, 0(x1)            # mem[0] = 0
    addi x1, x1, 4            # increment pointer
    sw   x4, 0(x1)            # mem[1] = 1
    addi x1, x1, 4            # increment pointer
    addi x5, x5, 2            # index = 2

fib_loop:
    # Check if done (index >= count)
    bge  x5, x2, done         # if index >= 10, goto done

    # Calculate next Fibonacci number
    add  x6, x3, x4           # x6 = fib[n-2] + fib[n-1]

    # Store result
    sw   x6, 0(x1)            # mem[index] = x6

    # Update for next iteration
    addi x1, x1, 4            # increment pointer
    add  x3, x0, x4           # x3 = old x4 (shift window)
    add  x4, x0, x6           # x4 = new value
    addi x5, x5, 1            # index++

    # Loop back
    beq  x0, x0, fib_loop     # unconditional branch

done:
    # Signal completion by writing to GPIO
    lui  x7, 0x20010          # x7 = 0x20010000 (GPIO base)
    addi x8, x0, 0xFF         # x8 = 0xFF (completion signal)
    sw   x8, 4(x7)            # GPIO_DATA_OUT = 0xFF

    # Infinite loop
halt:
    beq  x0, x0, halt

# Expected results in memory (0x80000000):
# [0, 1, 1, 2, 3, 5, 8, 13, 21, 34]
