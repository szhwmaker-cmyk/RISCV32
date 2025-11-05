# RV32E Assembly Program Guide

This document explains how to compile and use the RV32E assembly test programs for integration testing.

---

## Test Programs

The following test programs are located in `src/test/resources/`:

1. **fibonacci.s** - Fibonacci calculation (arithmetic + branches)
2. **memcopy.s** - Memory copy operations (LOAD/STORE)
3. **uart_loopback.s** - UART communication test
4. **multi_peripheral.s** - Multi-peripheral coordination

---

## Compiling Assembly Programs

### Prerequisites

You need the RISC-V GNU toolchain configured for RV32E:

```bash
# Download and build RISC-V toolchain
git clone https://github.com/riscv/riscv-gnu-toolchain
cd riscv-gnu-toolchain

# Configure for RV32E (embedded, 16 registers)
./configure --prefix=/opt/riscv --with-arch=rv32e --with-abi=ilp32e
make

# Add to PATH
export PATH=/opt/riscv/bin:$PATH
```

### Compilation Steps

#### 1. Assemble to Object File

```bash
riscv32-unknown-elf-as -march=rv32e -mabi=ilp32e fibonacci.s -o fibonacci.o
```

#### 2. Link to ELF

```bash
riscv32-unknown-elf-ld -T linker.ld fibonacci.o -o fibonacci.elf
```

Linker script (`linker.ld`):

```ld
OUTPUT_ARCH("riscv")
ENTRY(_start)

MEMORY
{
    ROM (rx)  : ORIGIN = 0x00000000, LENGTH = 256
    RAM (rwx) : ORIGIN = 0x80000000, LENGTH = 64K
}

SECTIONS
{
    .text : {
        *(.text)
        *(.text.*)
    } > RAM

    .data : {
        *(.data)
        *(.data.*)
    } > RAM

    .bss : {
        *(.bss)
        *(.bss.*)
    } > RAM
}
```

#### 3. Extract Binary

```bash
riscv32-unknown-elf-objcopy -O binary fibonacci.elf fibonacci.bin
```

#### 4. Convert to Hex for Verilog Memory

```bash
od -An -tx4 -w4 -v fibonacci.bin > fibonacci.hex
```

Or use custom script to generate Chisel-compatible format:

```python
#!/usr/bin/env python3
import sys

with open(sys.argv[1], 'rb') as f:
    data = f.read()

print("val program = Seq(")
for i in range(0, len(data), 4):
    word = int.from_bytes(data[i:i+4], 'little')
    print(f'  "h{word:08x}".U,')
print(")")
```

Usage:
```bash
python3 bin2chisel.py fibonacci.bin > fibonacci.scala
```

---

## Integration with ChiselTest

### Method 1: Pre-load into Boot ROM

Modify `BootROM.scala` to load test program:

```scala
// In BootROM.scala
val program = if (useTestProgram) {
  // Load from compiled binary
  VecInit(loadTestProgram("fibonacci.hex"))
} else {
  // Use default boot code
  VecInit(defaultBootCode)
}
```

### Method 2: Load into RAM via Wishbone

In integration test:

```scala
def loadProgram(dut: MinimalSoc, program: Seq[UInt], baseAddr: Long): Unit = {
  for ((instr, offset) <- program.zipWithIndex) {
    val addr = baseAddr + (offset * 4)
    // Write via Wishbone bus
    writeMemory(dut, addr, instr)
  }
}
```

### Method 3: Direct Memory Initialization

Modify RAM module for testing:

```scala
// In Ram.scala
val mem = if (initFile.nonEmpty) {
  VecInit(loadMif(initFile))
} else {
  Mem(Config.RAM_SIZE / 4, UInt(32.W))
}
```

---

## Disassembly and Verification

### Disassemble ELF File

```bash
riscv32-unknown-elf-objdump -d fibonacci.elf
```

Example output:
```asm
80000000 <_start>:
80000000:   800000b7    lui     x1,0x80000
80000004:   00a00113    li      x2,10
80000008:   00000193    li      x3,0
```

### Verify Machine Code

```bash
# Dump hex values
riscv32-unknown-elf-objdump -s fibonacci.elf

# Compare with expected values
hexdump -C fibonacci.bin
```

---

## Manual Assembly Reference

For quick testing without toolchain, you can hand-assemble instructions:

### RV32E Instruction Encoding

#### R-Type (ADD, SUB, AND, OR, XOR, SLL, SRL, SRA, SLT, SLTU)
```
[31:25] funct7 | [24:20] rs2 | [19:15] rs1 | [14:12] funct3 | [11:7] rd | [6:0] opcode
```

Example: `ADD x1, x2, x3`
- opcode = 0110011 (R-type)
- rd = 00001 (x1)
- funct3 = 000 (ADD)
- rs1 = 00010 (x2)
- rs2 = 00011 (x3)
- funct7 = 0000000 (ADD)

Machine code: `0000000_00011_00010_000_00001_0110011` = `0x003100B3`

#### I-Type (ADDI, ANDI, ORI, XORI, SLTI, SLTIU, LW, LH, LB)
```
[31:20] imm[11:0] | [19:15] rs1 | [14:12] funct3 | [11:7] rd | [6:0] opcode
```

Example: `ADDI x1, x0, 10`
- opcode = 0010011 (I-type ALU)
- rd = 00001 (x1)
- funct3 = 000 (ADDI)
- rs1 = 00000 (x0)
- imm = 000000001010 (10)

Machine code: `000000001010_00000_000_00001_0010011` = `0x00A00093`

#### S-Type (SW, SH, SB)
```
[31:25] imm[11:5] | [24:20] rs2 | [19:15] rs1 | [14:12] funct3 | [11:7] imm[4:0] | [6:0] opcode
```

#### B-Type (BEQ, BNE, BLT, BGE, BLTU, BGEU)
```
[31] imm[12] | [30:25] imm[10:5] | [24:20] rs2 | [19:15] rs1 | [14:12] funct3 |
[11:8] imm[4:1] | [7] imm[11] | [6:0] opcode
```

#### U-Type (LUI, AUIPC)
```
[31:12] imm[31:12] | [11:7] rd | [6:0] opcode
```

Example: `LUI x1, 0x80000`
- opcode = 0110111 (LUI)
- rd = 00001 (x1)
- imm = 0x80000 (upper 20 bits)

Machine code: `10000000000000000000_00001_0110111` = `0x800000B7`

#### J-Type (JAL)
```
[31] imm[20] | [30:21] imm[10:1] | [20] imm[11] | [19:12] imm[19:12] |
[11:7] rd | [6:0] opcode
```

---

## Test Program Examples

### Fibonacci (Simplified)

```asm
_start:
    addi x1, x0, 0        # x1 = 0 (fib[0])
    addi x2, x0, 1        # x2 = 1 (fib[1])
    addi x3, x0, 10       # x3 = 10 (count)

loop:
    add  x4, x1, x2       # x4 = x1 + x2
    add  x1, x0, x2       # x1 = x2
    add  x2, x0, x4       # x2 = x4
    addi x3, x3, -1       # x3--
    bnez x3, loop         # if x3 != 0, loop

done:
    # Store result
    lui  x5, 0x80000
    sw   x2, 0(x5)
```

Machine code:
```
0x00000093  # addi x1, x0, 0
0x00100113  # addi x2, x0, 1
0x00a00193  # addi x3, x0, 10
0x00208233  # add  x4, x1, x2
0x000100b3  # add  x1, x0, x2
0x00020133  # add  x2, x0, x4
0xfff18193  # addi x3, x3, -1
0xfe0198e3  # bnez x3, loop
0x800002b7  # lui  x5, 0x80000
0x0022a023  # sw   x2, 0(x5)
```

---

## Debugging Tips

### 1. Check Instruction Encoding

Use online RISC-V encoder:
- https://luplab.gitlab.io/rvcodecjs/

### 2. Verify with QEMU

```bash
qemu-riscv32 -cpu rv32 fibonacci.elf
```

### 3. Use Spike Simulator

```bash
spike --isa=rv32e pk fibonacci.elf
```

### 4. Waveform Analysis

Generate VCD in ChiselTest:
```scala
test(new MinimalSoc).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
  // Test code
}
```

View with GTKWave:
```bash
gtkwave test_run_dir/MinimalSoc/MinimalSoc.vcd
```

---

## Memory Map Reference

| Address Range   | Device       | Purpose                    |
|----------------|--------------|----------------------------|
| 0x0000_0000    | Boot ROM     | Boot code (256 bytes)      |
| 0x1000_0000    | SPI Flash    | Program storage (XIP)      |
| 0x2000_0000    | UART         | Serial communication       |
| 0x2001_0000    | GPIO         | General I/O                |
| 0x2002_0000    | SPI Master   | SPI peripherals            |
| 0x2003_0000    | I2C Master   | I2C peripherals            |
| 0x2004_0000    | Flash Ctrl   | Flash controller registers |
| 0x8000_0000    | RAM          | Main memory (64KB)         |

---

## Further Reading

- **RISC-V ISA Spec**: https://riscv.org/specifications/
- **RV32E Extension**: RISC-V Spec Chapter 2.2
- **GNU Toolchain**: https://github.com/riscv/riscv-gnu-toolchain
- **Chisel Testing**: https://github.com/ucb-bar/chiseltest

---

**Note**: The test programs in `src/test/resources/` are written in assembly and require compilation before use. The integration test framework in `IntegrationSpec.scala` provides a structure for running these programs but currently uses simplified inline machine code for demonstration.
