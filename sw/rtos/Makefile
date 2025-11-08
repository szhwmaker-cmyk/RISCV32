# RT-Thread Makefile for RV32E SoC

# RISC-V Toolchain
PREFIX = riscv32-unknown-elf-
CC = $(PREFIX)gcc
AS = $(PREFIX)gcc
AR = $(PREFIX)ar
OBJCOPY = $(PREFIX)objcopy
OBJDUMP = $(PREFIX)objdump
SIZE = $(PREFIX)size

# Architecture
ARCH = rv32e
ABI = ilp32e

# RT-Thread Source (需要下载RT-Thread源码)
# 这里假设RT-Thread源码在 ../rt-thread 目录
RT_THREAD_DIR = ../rt-thread

# Compilation flags
CFLAGS = -march=$(ARCH) -mabi=$(ABI) -O2 -g -Wall
CFLAGS += -ffreestanding -nostdlib
CFLAGS += -ffunction-sections -fdata-sections
CFLAGS += -I. -I$(RT_THREAD_DIR)/include -I$(RT_THREAD_DIR)/libcpu/risc-v/common

ASFLAGS = -march=$(ARCH) -mabi=$(ABI) -x assembler-with-cpp

LDFLAGS = -march=$(ARCH) -mabi=$(ABI)
LDFLAGS += -T linker_rtt.ld
LDFLAGS += -nostartfiles -Wl,--gc-sections
LDFLAGS += -Wl,-Map=rtthread.map

# Source files
BSP_SRC = board.c main.c
ASM_SRC = context_gcc.S start_rtt.S

# RT-Thread kernel sources (minimal set)
RTT_KERNEL_SRC = \
	$(RT_THREAD_DIR)/src/clock.c \
	$(RT_THREAD_DIR)/src/idle.c \
	$(RT_THREAD_DIR)/src/ipc.c \
	$(RT_THREAD_DIR)/src/kservice.c \
	$(RT_THREAD_DIR)/src/mem.c \
	$(RT_THREAD_DIR)/src/mempool.c \
	$(RT_THREAD_DIR)/src/object.c \
	$(RT_THREAD_DIR)/src/scheduler.c \
	$(RT_THREAD_DIR)/src/thread.c \
	$(RT_THREAD_DIR)/src/timer.c

# Object files
BSP_OBJS = $(BSP_SRC:.c=.o)
ASM_OBJS = $(ASM_SRC:.S=.o)
RTT_OBJS = $(notdir $(RTT_KERNEL_SRC:.c=.o))

ALL_OBJS = $(ASM_OBJS) $(BSP_OBJS) $(RTT_OBJS)

# Target
TARGET = rtthread

.PHONY: all clean

all: $(TARGET).elf $(TARGET).bin $(TARGET).dump
	@echo "Build completed!"
	@$(SIZE) $(TARGET).elf

$(TARGET).elf: $(ALL_OBJS)
	$(CC) $(LDFLAGS) -o $@ $(ALL_OBJS)

$(TARGET).bin: $(TARGET).elf
	$(OBJCOPY) -O binary $< $@

$(TARGET).dump: $(TARGET).elf
	$(OBJDUMP) -D $< > $@

# Compile BSP sources
%.o: %.c
	$(CC) $(CFLAGS) -c -o $@ $<

# Compile assembly
%.o: %.S
	$(AS) $(ASFLAGS) -c -o $@ $<

# Compile RT-Thread kernel sources
%.o: $(RT_THREAD_DIR)/src/%.c
	$(CC) $(CFLAGS) -c -o $@ $<

clean:
	rm -f *.o *.elf *.bin *.map *.dump

# Note: This Makefile assumes RT-Thread source is available
# For a complete build, you need to:
# 1. Download RT-Thread: git clone https://github.com/RT-Thread/rt-thread.git
# 2. Place it in the parent directory or adjust RT_THREAD_DIR
# 3. Run: make
