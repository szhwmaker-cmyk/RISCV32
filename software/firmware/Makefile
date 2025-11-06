# RV32E Firmware Makefile

# 工具链
CROSS_COMPILE ?= riscv32-unknown-elf-
CC = $(CROSS_COMPILE)gcc
AS = $(CROSS_COMPILE)as
LD = $(CROSS_COMPILE)ld
OBJCOPY = $(CROSS_COMPILE)objcopy
OBJDUMP = $(CROSS_COMPILE)objdump
SIZE = $(CROSS_COMPILE)size

# RV32E 编译选项
ARCH = rv32e
ABI = ilp32e

CFLAGS = -march=$(ARCH) -mabi=$(ABI) -nostdlib -nostartfiles \
         -fno-builtin -ffunction-sections -fdata-sections \
         -O2 -Wall -Wextra -g

ASFLAGS = -march=$(ARCH) -mabi=$(ABI)

LDFLAGS = -T firmware.ld -nostdlib -Wl,--gc-sections

# 源文件
SRCS_C = hello.c
SRCS_ASM = startup.S
OBJS = $(SRCS_C:.c=.o) $(SRCS_ASM:.S=.o)

# 目标文件
TARGET = firmware
ELF = $(TARGET).elf
BIN = $(TARGET).bin
HEX = $(TARGET).hex
LST = $(TARGET).lst
MAP = $(TARGET).map

.PHONY: all clean size

all: $(BIN) $(HEX) $(LST) size

# 编译 C 文件
%.o: %.c
	$(CC) $(CFLAGS) -c -o $@ $<

# 编译汇编文件
%.o: %.S
	$(CC) $(CFLAGS) -c -o $@ $<

# 链接
$(ELF): $(OBJS) firmware.ld
	$(CC) $(LDFLAGS) -Wl,-Map=$(MAP) -o $@ $(OBJS)

# 生成二进制文件
$(BIN): $(ELF)
	$(OBJCOPY) -O binary $< $@

# 生成 HEX 文件
$(HEX): $(ELF)
	$(OBJCOPY) -O ihex $< $@

# 生成反汇编列表
$(LST): $(ELF)
	$(OBJDUMP) -d -S $< > $@

# 显示大小
size: $(ELF)
	@echo "=== Firmware Size ==="
	$(SIZE) $<
	@echo ""

# 清理
clean:
	rm -f $(OBJS) $(ELF) $(BIN) $(HEX) $(LST) $(MAP)

# 帮助
help:
	@echo "RV32E Firmware Build System"
	@echo ""
	@echo "Targets:"
	@echo "  all      - Build firmware (default)"
	@echo "  clean    - Remove build artifacts"
	@echo "  size     - Show size information"
	@echo "  help     - Show this help"
	@echo ""
	@echo "Output files:"
	@echo "  $(ELF) - ELF executable"
	@echo "  $(BIN) - Binary image"
	@echo "  $(HEX) - Intel HEX format"
	@echo "  $(LST) - Disassembly listing"
	@echo "  $(MAP) - Linker map file"

# 生成内存初始化文件（用于仿真）
memfile: $(BIN)
	hexdump -v -e '1/4 "%08x\n"' $< > $(TARGET).mem

.PHONY: memfile
