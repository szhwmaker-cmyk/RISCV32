# Memory Copy Test Program
# Tests: LOAD/STORE instructions (LW, LH, LB, SW, SH, SB)
# Copies data blocks with different access sizes

.section .text
.globl _start

_start:
    # Initialize source and destination addresses
    lui  x1, 0x80000          # x1 = 0x80000000 (source)
    addi x1, x1, 0x100        # x1 = 0x80000100
    lui  x2, 0x80000          # x2 = 0x80000000 (destination)
    addi x2, x2, 0x200        # x2 = 0x80000200

    # Prepare test data in source memory
    addi x3, x0, 0x12         # x3 = 0x12
    addi x4, x3, 0x34         # x4 = 0x46
    addi x5, x4, 0x56         # x5 = 0x9C
    addi x6, x5, 0x78         # x6 = 0x114 (but only lower byte matters)

    # Store test pattern: 0x12345678 (as 4 bytes)
    sb   x3, 0(x1)            # byte 0 = 0x12
    sb   x4, 1(x1)            # byte 1 = 0x46
    sb   x5, 2(x1)            # byte 2 = 0x9C
    sb   x6, 3(x1)            # byte 3 = 0x14

    # Test 1: Word copy (LW + SW)
    lw   x7, 0(x1)            # Load word from source
    sw   x7, 0(x2)            # Store word to destination

    # Test 2: Halfword copy (LH + SH)
    lh   x8, 0(x1)            # Load halfword
    sh   x8, 4(x2)            # Store halfword

    # Test 3: Byte copy (LB + SB)
    lb   x9, 0(x1)            # Load byte
    sb   x9, 8(x2)            # Store byte

    # Test 4: Unsigned loads (LHU, LBU)
    lbu  x10, 3(x1)           # Load unsigned byte
    sb   x10, 12(x2)          # Store byte

    lhu  x11, 2(x1)           # Load unsigned halfword
    sh   x11, 16(x2)          # Store halfword

    # Test 5: Block copy (8 words)
    lui  x1, 0x80000          # Reset source
    addi x1, x1, 0x300        # x1 = 0x80000300
    lui  x2, 0x80000          # Reset dest
    addi x2, x2, 0x400        # x2 = 0x80000400
    addi x12, x0, 8           # x12 = 8 (count)

block_copy_loop:
    beq  x12, x0, copy_done   # if count == 0, done
    lw   x13, 0(x1)           # Load word
    sw   x13, 0(x2)           # Store word
    addi x1, x1, 4            # Increment source
    addi x2, x2, 4            # Increment dest
    addi x12, x12, -1         # Decrement count
    beq  x0, x0, block_copy_loop

copy_done:
    # Signal completion via GPIO
    lui  x14, 0x20010         # GPIO base
    addi x15, x0, 0xAA        # Completion signal
    sw   x15, 4(x14)          # GPIO_DATA_OUT = 0xAA

halt:
    beq  x0, x0, halt
