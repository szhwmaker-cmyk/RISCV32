#!/bin/bash
#
# Build Minimal RTOS Simulation for RV32E SoC
#

set -e

# Toolchain
CROSS_COMPILE=${CROSS_COMPILE:-"riscv32-unknown-elf-"}
AS="${CROSS_COMPILE}as"
LD="${CROSS_COMPILE}ld"
OBJCOPY="${CROSS_COMPILE}objcopy"
OBJDUMP="${CROSS_COMPILE}objdump"

OUTPUT_DIR="build"
mkdir -p $OUTPUT_DIR

echo "Building Minimal RTOS for RV32E SoC..."

# Assemble
echo "Assembling minimal_rtos.s..."
$AS -march=rv32e -mabi=ilp32e minimal_rtos.s -o $OUTPUT_DIR/minimal_rtos.o

# Create simple linker script
cat > $OUTPUT_DIR/minimal.lds << 'EOF'
OUTPUT_ARCH("riscv")
ENTRY(_start)

MEMORY
{
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
EOF

# Link
echo "Linking..."
$LD -T $OUTPUT_DIR/minimal.lds $OUTPUT_DIR/minimal_rtos.o -o $OUTPUT_DIR/minimal_rtos.elf

# Generate binary
echo "Generating binary..."
$OBJCOPY -O binary $OUTPUT_DIR/minimal_rtos.elf $OUTPUT_DIR/minimal_rtos.bin

# Generate hex file for simulation
echo "Generating hex file..."
od -An -tx4 -w4 -v $OUTPUT_DIR/minimal_rtos.bin > $OUTPUT_DIR/minimal_rtos.hex

# Disassemble for verification
echo "Disassembling..."
$OBJDUMP -d $OUTPUT_DIR/minimal_rtos.elf > $OUTPUT_DIR/minimal_rtos.dis

# Convert to Scala/Chisel format
echo "Converting to Chisel format..."
python3 << 'PYTHON_EOF' > $OUTPUT_DIR/minimal_rtos.scala
import sys

with open('build/minimal_rtos.bin', 'rb') as f:
    data = f.read()

print('  // Minimal RTOS image for RT-Thread simulation')
print('  val rtthread_image = Seq(')

for i in range(0, len(data), 4):
    if i + 4 <= len(data):
        word = int.from_bytes(data[i:i+4], 'little')
        print(f'    "h{word:08x}".U,  // 0x{0x80000000 + i:08x}')
    else:
        # Handle partial word at end
        remaining = data[i:]
        word = int.from_bytes(remaining + b'\x00' * (4 - len(remaining)), 'little')
        print(f'    "h{word:08x}".U   // 0x{0x80000000 + i:08x} (partial)')

print('  )')
PYTHON_EOF

echo ""
echo "Build completed successfully!"
echo "Output files:"
echo "  Binary:     $OUTPUT_DIR/minimal_rtos.bin"
echo "  ELF:        $OUTPUT_DIR/minimal_rtos.elf"
echo "  Hex:        $OUTPUT_DIR/minimal_rtos.hex"
echo "  Disassembly:$OUTPUT_DIR/minimal_rtos.dis"
echo "  Chisel:     $OUTPUT_DIR/minimal_rtos.scala"
echo ""

# Print binary size
SIZE=$(wc -c < $OUTPUT_DIR/minimal_rtos.bin)
echo "Binary size: $SIZE bytes"

# Show first few instructions
echo ""
echo "First few instructions:"
head -20 $OUTPUT_DIR/minimal_rtos.dis | tail -15
