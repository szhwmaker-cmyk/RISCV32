# RV32E Bootloader Makefile

# 工具链
CROSS_COMPILE ?= riscv32-unknown-elf-
CC = $(CROSS_COMPILE)gcc
AS = $(CROSS_COMPILE)as
LD = $(CROSS_COMPILE)ld
OBJCOPY = $(CROSS_COMPILE)objcopy
OBJDUMP = $(CROSS_COMPILE)objdump

# RV32E 编译选项
ARCH = rv32e
ABI = ilp32e

CFLAGS = -march=$(ARCH) -mabi=$(ABI) -nostdlib -nostartfiles -fno-builtin -O2 -Wall
ASFLAGS = -march=$(ARCH) -mabi=$(ABI)
LDFLAGS = -T bootloader.ld -nostdlib

# 源文件
SRCS_ASM = bootloader.S
OBJS = $(SRCS_ASM:.S=.o)

# 目标文件
TARGET = bootloader
ELF = $(TARGET).elf
BIN = $(TARGET).bin
HEX = $(TARGET).hex
LST = $(TARGET).lst

.PHONY: all clean

all: $(BIN) $(HEX) $(LST)

# 编译汇编文件
%.o: %.S
	$(AS) $(ASFLAGS) -o $@ $<

# 链接
$(ELF): $(OBJS)
	$(LD) $(LDFLAGS) -o $@ $(OBJS)

# 生成二进制文件
$(BIN): $(ELF)
	$(OBJCOPY) -O binary $< $@

# 生成 HEX 文件
$(HEX): $(ELF)
	$(OBJCOPY) -O ihex $< $@

# 生成反汇编列表
$(LST): $(ELF)
	$(OBJDUMP) -d -S $< > $@

# 清理
clean:
	rm -f $(OBJS) $(ELF) $(BIN) $(HEX) $(LST)

# 显示大小
size: $(ELF)
	$(CROSS_COMPILE)size $<

# 帮助
help:
	@echo "RV32E Bootloader Build System"
	@echo ""
	@echo "Targets:"
	@echo "  all      - Build bootloader (default)"
	@echo "  clean    - Remove build artifacts"
	@echo "  size     - Show size information"
	@echo "  help     - Show this help"
	@echo ""
	@echo "Output files:"
	@echo "  $(ELF) - ELF executable"
	@echo "  $(BIN) - Binary image"
	@echo "  $(HEX) - Intel HEX format"
	@echo "  $(LST) - Disassembly listing"
