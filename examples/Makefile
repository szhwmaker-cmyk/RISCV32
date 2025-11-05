# =====================================================
# Makefile for RV32E SoC Examples
# =====================================================
# 支持编译汇编和C语言程序
# =====================================================

# ========== 工具链配置 ==========
PREFIX = riscv32-unknown-elf-
AS = $(PREFIX)as
CC = $(PREFIX)gcc
LD = $(PREFIX)ld
OBJCOPY = $(PREFIX)objcopy
OBJDUMP = $(PREFIX)objdump
SIZE = $(PREFIX)size

# ========== 编译选项 ==========
ARCH = rv32e
ABI = ilp32e
ASFLAGS = -march=$(ARCH) -mabi=$(ABI)
CFLAGS = -march=$(ARCH) -mabi=$(ABI) -O2 -g -nostdlib -ffreestanding -Wall
LDFLAGS = -T linker.ld -nostdlib

# ========== 源文件 ==========
ASM_SOURCES = $(wildcard *.s)
C_SOURCES = $(wildcard *.c)

# ========== 目标文件 ==========
ASM_TARGETS = $(ASM_SOURCES:.s=.elf)
C_TARGETS = $(C_SOURCES:.c=.elf)
ALL_TARGETS = $(ASM_TARGETS) $(C_TARGETS)

# ========== 二进制文件 ==========
BIN_FILES = $(ALL_TARGETS:.elf=.bin)
HEX_FILES = $(ALL_TARGETS:.elf=.hex)

# ========== 默认目标 ==========
.PHONY: all clean help list

all: $(ALL_TARGETS) $(BIN_FILES)

# ========== 汇编程序编译规则 ==========
%.o: %.s
	@echo "Assembling $<..."
	$(AS) $(ASFLAGS) $< -o $@

# ========== C程序编译规则 ==========
%.o: %.c
	@echo "Compiling $<..."
	$(CC) $(CFLAGS) -c $< -o $@

# ========== 链接规则 ==========
%.elf: %.o
	@echo "Linking $@..."
	$(LD) $(LDFLAGS) $< -o $@
	@echo "Size of $@:"
	@$(SIZE) $@
	@echo

# ========== 生成二进制文件 ==========
%.bin: %.elf
	@echo "Creating binary $@..."
	$(OBJCOPY) -O binary $< $@

# ========== 生成十六进制文件 ==========
%.hex: %.elf
	@echo "Creating hex $@..."
	$(OBJCOPY) -O ihex $< $@

# ========== 反汇编 ==========
%.dis: %.elf
	@echo "Disassembling $<..."
	$(OBJDUMP) -d $< > $@

# ========== 清理 ==========
clean:
	@echo "Cleaning up..."
	rm -f *.o *.elf *.bin *.hex *.dis
	@echo "Done."

# ========== 帮助 ==========
help:
	@echo "RV32E SoC Examples Makefile"
	@echo ""
	@echo "Targets:"
	@echo "  all          - Build all examples"
	@echo "  clean        - Remove all generated files"
	@echo "  list         - List all examples"
	@echo "  <name>.elf   - Build specific example"
	@echo "  <name>.bin   - Build binary for specific example"
	@echo "  <name>.dis   - Generate disassembly for specific example"
	@echo ""
	@echo "Examples:"
	@echo "  make                    # Build all"
	@echo "  make 01_led_blink.elf   # Build LED blink example"
	@echo "  make 05_hello_c.dis     # Disassemble C hello example"
	@echo "  make clean              # Clean all"

# ========== 列出所有示例 ==========
list:
	@echo "Available Examples:"
	@echo ""
	@echo "Assembly Examples:"
	@for file in $(ASM_SOURCES); do \
		echo "  $$file"; \
	done
	@echo ""
	@echo "C Examples:"
	@for file in $(C_SOURCES); do \
		echo "  $$file"; \
	done

# ========== 测试目标 ==========
.PHONY: test-sim test-fpga

test-sim: all
	@echo "Running simulation tests..."
	@echo "Note: This requires Mill and ChiselTest"
	@cd .. && mill rv32e.test.testOnly rv32e.integration.IntegrationSpec

test-fpga: all
	@echo "Programming FPGA..."
	@echo "Note: This requires Vivado and connected FPGA board"
	@cd ../fpga && ./build_and_program.sh

# ========== 依赖关系 ==========
$(C_TARGETS): linker.ld

# ========== 特殊目标 ==========
# 生成Flash映像（用于FPGA部署）
flash_image.bin: 01_led_blink.bin
	@echo "Creating Flash image..."
	dd if=$< of=$@ bs=4096 count=4 2>/dev/null
	@echo "Flash image created: $@"

.PHONY: flash_image.bin
