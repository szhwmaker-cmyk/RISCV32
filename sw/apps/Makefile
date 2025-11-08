# Makefile for RV32E applications

# RISC-V GCC工具链
PREFIX = riscv32-unknown-elf-
CC = $(PREFIX)gcc
OBJCOPY = $(PREFIX)objcopy
OBJDUMP = $(PREFIX)objdump

# 编译选项
ARCH = rv32e
ABI = ilp32e
CFLAGS = -march=$(ARCH) -mabi=$(ABI) -O2 -g -Wall
CFLAGS += -ffreestanding -nostdlib -nostartfiles
CFLAGS += -T linker.ld

# 目标文件
APPS = hello.elf

all: $(APPS)

%.elf: %.c start.S
	$(CC) $(CFLAGS) start.S $< -o $@
	$(OBJCOPY) -O binary $@ $(@:.elf=.bin)
	$(OBJDUMP) -d $@ > $(@:.elf=.dump)
	@echo "Built $@"

clean:
	rm -f *.elf *.bin *.dump *.o

.PHONY: all clean
