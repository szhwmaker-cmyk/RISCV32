# BootROM Makefile
# 编译启动代码为二进制文件

CROSS_COMPILE ?= riscv32-unknown-elf-
CC = $(CROSS_COMPILE)gcc
OBJCOPY = $(CROSS_COMPILE)objcopy
OBJDUMP = $(CROSS_COMPILE)objdump

CFLAGS = -march=rv32e -mabi=ilp32e -nostdlib -nostartfiles -T bootrom.ld
LDFLAGS = -static

all: bootrom.bin bootrom.hex bootrom.dump

bootrom.elf: bootrom.S bootrom.ld
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ bootrom.S

bootrom.bin: bootrom.elf
	$(OBJCOPY) -O binary $< $@

bootrom.hex: bootrom.elf
	$(OBJCOPY) -O verilog $< $@

bootrom.dump: bootrom.elf
	$(OBJDUMP) -d $< > $@

clean:
	rm -f *.elf *.bin *.hex *.dump

.PHONY: all clean
