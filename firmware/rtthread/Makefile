# RT-Thread 测试程序 Makefile

CROSS_COMPILE ?= riscv32-unknown-elf-
CC = $(CROSS_COMPILE)gcc
OBJCOPY = $(CROSS_COMPILE)objcopy
OBJDUMP = $(CROSS_COMPILE)objdump

CFLAGS = -march=rv32em -mabi=ilp32e -O2 -Wall -nostdlib -nostartfiles
LDFLAGS = -T rtthread.ld -static

SRCS = main.c start.S
OBJS = $(SRCS:.c=.o)
OBJS := $(OBJS:.S=.o)

all: rtthread.elf rtthread.bin rtthread.hex rtthread.dump

rtthread.elf: $(OBJS) rtthread.ld
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $(OBJS)

%.o: %.c
	$(CC) $(CFLAGS) -c -o $@ $<

%.o: %.S
	$(CC) $(CFLAGS) -c -o $@ $<

rtthread.bin: rtthread.elf
	$(OBJCOPY) -O binary $< $@

rtthread.hex: rtthread.elf
	$(OBJCOPY) -O verilog $< $@

rtthread.dump: rtthread.elf
	$(OBJDUMP) -d $< > $@

clean:
	rm -f *.o *.elf *.bin *.hex *.dump

.PHONY: all clean
