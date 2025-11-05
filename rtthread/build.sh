#!/bin/bash
#
# RT-Thread Build Script for RV32E SoC
#

set -e

# Configuration
RT_THREAD_ROOT=${RT_THREAD_ROOT:-"../../rt-thread"}
BSP_ROOT=$(pwd)
OUTPUT_DIR="build"
IMAGE_NAME="rtthread.bin"

# Toolchain
CROSS_COMPILE=${CROSS_COMPILE:-"riscv32-unknown-elf-"}
CC="${CROSS_COMPILE}gcc"
AS="${CROSS_COMPILE}as"
LD="${CROSS_COMPILE}ld"
OBJCOPY="${CROSS_COMPILE}objcopy"
SIZE="${CROSS_COMPILE}size"

# Compiler flags for RV32E
CFLAGS="-march=rv32e -mabi=ilp32e -O2 -g -Wall"
CFLAGS="$CFLAGS -ffunction-sections -fdata-sections"
CFLAGS="$CFLAGS -I${BSP_ROOT}"
CFLAGS="$CFLAGS -I${RT_THREAD_ROOT}/include"
CFLAGS="$CFLAGS -I${RT_THREAD_ROOT}/libcpu/risc-v/common"
CFLAGS="$CFLAGS -DRT_USING_NEWLIB"

# Linker flags
LDFLAGS="-march=rv32e -mabi=ilp32e -nostartfiles -Wl,--gc-sections"
LDFLAGS="$LDFLAGS -T ${BSP_ROOT}/link.lds"

echo "================================================"
echo "RT-Thread Build for RV32E SoC"
echo "================================================"
echo "RT-Thread Root: $RT_THREAD_ROOT"
echo "BSP Root:       $BSP_ROOT"
echo "Toolchain:      $CROSS_COMPILE"
echo "================================================"

# Check RT-Thread source
if [ ! -d "$RT_THREAD_ROOT" ]; then
    echo "Error: RT-Thread source not found at $RT_THREAD_ROOT"
    echo "Please set RT_THREAD_ROOT environment variable or clone RT-Thread:"
    echo "  git clone https://github.com/RT-Thread/rt-thread.git"
    exit 1
fi

# Create output directory
mkdir -p $OUTPUT_DIR

echo "Compiling RT-Thread kernel..."

# Compile kernel source files (simplified - would use actual RT-Thread build system)
KERNEL_SRCS="
    $RT_THREAD_ROOT/src/clock.c
    $RT_THREAD_ROOT/src/components.c
    $RT_THREAD_ROOT/src/idle.c
    $RT_THREAD_ROOT/src/ipc.c
    $RT_THREAD_ROOT/src/irq.c
    $RT_THREAD_ROOT/src/kservice.c
    $RT_THREAD_ROOT/src/mem.c
    $RT_THREAD_ROOT/src/mempool.c
    $RT_THREAD_ROOT/src/object.c
    $RT_THREAD_ROOT/src/scheduler.c
    $RT_THREAD_ROOT/src/thread.c
    $RT_THREAD_ROOT/src/timer.c
"

# Compile BSP source files
BSP_SRCS="
    start.S
    board.c
    drv_uart.c
    application.c
"

echo "Building BSP files..."
for src in $BSP_SRCS; do
    obj="$OUTPUT_DIR/$(basename $src .c).o"
    obj="$(basename $obj .S).o"
    echo "  Compiling $src..."

    if [[ $src == *.S ]]; then
        $CC $CFLAGS -c $src -o $OUTPUT_DIR/$obj
    else
        $CC $CFLAGS -c $src -o $OUTPUT_DIR/$obj
    fi
done

echo "Linking..."
# Collect all object files
OBJ_FILES=$(find $OUTPUT_DIR -name "*.o")

$CC $LDFLAGS $OBJ_FILES -o $OUTPUT_DIR/rtthread.elf

echo "Generating binary..."
$OBJCOPY -O binary $OUTPUT_DIR/rtthread.elf $OUTPUT_DIR/$IMAGE_NAME

echo "Generating hex file..."
od -An -tx4 -w4 -v $OUTPUT_DIR/$IMAGE_NAME > $OUTPUT_DIR/rtthread.hex

echo "Build information:"
$SIZE $OUTPUT_DIR/rtthread.elf

echo ""
echo "================================================"
echo "Build completed successfully!"
echo "Output files:"
echo "  Binary: $OUTPUT_DIR/$IMAGE_NAME"
echo "  ELF:    $OUTPUT_DIR/rtthread.elf"
echo "  Hex:    $OUTPUT_DIR/rtthread.hex"
echo "================================================"
