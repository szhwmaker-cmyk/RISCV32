# RV32E SoC 编程指南

**完整的RV32E编程参考手册**

本指南将教您如何为RV32E SoC编写程序，包括汇编语言、C语言编程、外设控制、内存管理等。

---

## 📋 目录

1. [RV32E指令集概述](#rv32e指令集概述)
2. [寄存器说明](#寄存器说明)
3. [内存映射](#内存映射)
4. [汇编编程](#汇编编程)
5. [C语言编程](#c语言编程)
6. [外设编程](#外设编程)
7. [中断和异常](#中断和异常)
8. [性能优化](#性能优化)
9. [调试技巧](#调试技巧)

---

## 🎯 RV32E指令集概述

### 什么是RV32E？

RV32E是RISC-V的嵌入式变种：
- **32位**地址和数据
- **16个寄存器**（x0-x15，而不是x0-x31）
- **精简指令集**（40条基础指令）
- **低资源消耗**（适合小型FPGA和ASIC）

### 支持的指令类型

| 类型 | 说明 | 指令数量 |
|------|------|---------|
| **R-Type** | 寄存器-寄存器运算 | 10条 |
| **I-Type** | 立即数运算和LOAD | 13条 |
| **S-Type** | STORE指令 | 3条 |
| **B-Type** | 分支指令 | 6条 |
| **U-Type** | 上位立即数 | 2条 |
| **J-Type** | 跳转指令 | 2条 |

---

## 📦 寄存器说明

### 通用寄存器（16个）

| 寄存器 | ABI名称 | 说明 | 保存者 |
|--------|---------|------|--------|
| x0 | zero | 硬连线为0 | - |
| x1 | ra | 返回地址 | 调用者 |
| x2 | sp | 栈指针 | 被调用者 |
| x3 | gp | 全局指针 | - |
| x4 | tp | 线程指针 | - |
| x5 | t0 | 临时寄存器0 | 调用者 |
| x6 | t1 | 临时寄存器1 | 调用者 |
| x7 | t2 | 临时寄存器2 | 调用者 |
| x8 | s0/fp | 保存寄存器0/帧指针 | 被调用者 |
| x9 | s1 | 保存寄存器1 | 被调用者 |
| x10 | a0 | 函数参数/返回值0 | 调用者 |
| x11 | a1 | 函数参数/返回值1 | 调用者 |
| x12 | a2 | 函数参数2 | 调用者 |
| x13 | a3 | 函数参数3 | 调用者 |
| x14 | a4 | 函数参数4 | 调用者 |
| x15 | a5 | 函数参数5 | 调用者 |

### 重要约定

**x0 (zero)**:
- 永远返回0
- 写入被忽略
- 用于实现各种技巧

```assembly
# 示例：NOP指令
addi x0, x0, 0      # 什么都不做

# 示例：mov伪指令
addi x1, x2, 0      # x1 = x2 (mov x1, x2)

# 示例：清零
addi x3, x0, 0      # x3 = 0 (li x3, 0)
```

**x1 (ra) - 返回地址**:
- JAL/JALR指令自动设置
- 函数返回时使用

```assembly
# 函数调用
jal  ra, my_function    # ra = PC + 4, 跳转到my_function

# 函数返回
jalr zero, 0(ra)        # 返回到调用者
```

**x2 (sp) - 栈指针**:
- 指向栈顶
- 向下增长（递减）
- 必须16字节对齐

```assembly
# 分配栈空间
addi sp, sp, -16        # 分配16字节

# 释放栈空间
addi sp, sp, 16         # 释放16字节
```

---

## 🗺️ 内存映射

### 完整地址映射

```
0x00000000 - 0x000000FF    Boot ROM (256 bytes)
             ↓
             启动代码，从Flash拷贝到RAM

0x10000000 - 0x10FFFFFF    SPI Flash (16 MB)
             ↓
             存储程序代码和数据

0x20000000 - 0x20000FFF    UART (4 KB)
  +0x000: TXDATA           发送数据寄存器
  +0x004: RXDATA           接收数据寄存器
  +0x008: STATUS           状态寄存器
  +0x00C: CTRL             控制寄存器

0x20001000 - 0x20001FFF    GPIO (4 KB)
  +0x000: DATA             数据寄存器
  +0x004: DIR              方向寄存器（0=输入,1=输出）
  +0x008: OE               输出使能

0x20002000 - 0x20002FFF    SPI Master (4 KB)
  +0x000: TXDATA           发送FIFO
  +0x004: RXDATA           接收FIFO
  +0x008: STATUS           状态寄存器
  +0x00C: CTRL             控制寄存器
  +0x010: SCKDIV           时钟分频

0x20003000 - 0x20003FFF    I2C Master (4 KB)
  +0x000: TXDATA           发送数据
  +0x004: RXDATA           接收数据
  +0x008: STATUS           状态寄存器
  +0x00C: CTRL             控制寄存器
  +0x010: CLKDIV           时钟分频

0x40000000 - 0x40000FFF    Boot Controller (4 KB)
  +0x000: CTRL             控制寄存器
  +0x004: STATUS           状态寄存器

0x80000000 - 0x8000FFFF    RAM (64 KB)
             ↓
             程序代码和数据区
             栈从顶部向下增长
```

### 内存区域属性

| 区域 | 大小 | 可读 | 可写 | 可执行 | 用途 |
|------|------|------|------|--------|------|
| Boot ROM | 256B | ✅ | ❌ | ✅ | 启动代码 |
| SPI Flash | 16MB | ✅ | ❌ | ❌ | 程序存储 |
| 外设 | 各4KB | ✅ | ✅ | ❌ | I/O控制 |
| RAM | 64KB | ✅ | ✅ | ✅ | 运行内存 |

---

## 💻 汇编编程

### 基本程序结构

```assembly
# 文件：hello.s
# 功能：简单的Hello程序

# =========== 数据段 ===========
.section .data
    message: .string "Hello!\n"
    value:   .word 42

# =========== BSS段 ===========
.section .bss
    buffer: .space 128          # 预留128字节未初始化内存

# =========== 代码段 ===========
.section .text
.globl _start                   # 程序入口点

_start:
    # 初始化栈指针
    lui  sp, 0x80010            # sp = 0x80010000 (64KB RAM顶部)

    # 调用main函数
    jal  ra, main

    # 程序结束（无限循环）
end:
    beq  x0, x0, end

# =========== 主函数 ===========
main:
    # 保存返回地址
    addi sp, sp, -4
    sw   ra, 0(sp)

    # 你的代码在这里...

    # 恢复返回地址并返回
    lw   ra, 0(sp)
    addi sp, sp, 4
    jalr zero, 0(ra)
```

### 算术运算

```assembly
# 加法
add  x1, x2, x3                 # x1 = x2 + x3
addi x1, x2, 100                # x1 = x2 + 100

# 减法
sub  x1, x2, x3                 # x1 = x2 - x3

# 逻辑运算
and  x1, x2, x3                 # x1 = x2 & x3
andi x1, x2, 0xFF               # x1 = x2 & 0xFF
or   x1, x2, x3                 # x1 = x2 | x3
ori  x1, x2, 0x100              # x1 = x2 | 0x100
xor  x1, x2, x3                 # x1 = x2 ^ x3
xori x1, x2, -1                 # x1 = ~x2

# 移位运算
sll  x1, x2, x3                 # x1 = x2 << x3
slli x1, x2, 4                  # x1 = x2 << 4
srl  x1, x2, x3                 # x1 = x2 >> x3 (逻辑右移)
srli x1, x2, 4                  # x1 = x2 >> 4
sra  x1, x2, x3                 # x1 = x2 >> x3 (算术右移)
srai x1, x2, 4                  # x1 = x2 >> 4 (保留符号)

# 比较
slt  x1, x2, x3                 # x1 = (x2 < x3) ? 1 : 0 (有符号)
slti x1, x2, 10                 # x1 = (x2 < 10) ? 1 : 0
sltu x1, x2, x3                 # x1 = (x2 < x3) ? 1 : 0 (无符号)
sltiu x1, x2, 10                # x1 = (x2 < 10) ? 1 : 0 (无符号)
```

### 内存访问

```assembly
# Load指令
lb   x1, 0(x2)                  # x1 = (int8_t)  mem[x2]     (符号扩展)
lh   x1, 0(x2)                  # x1 = (int16_t) mem[x2]     (符号扩展)
lw   x1, 0(x2)                  # x1 = (int32_t) mem[x2]
lbu  x1, 0(x2)                  # x1 = (uint8_t) mem[x2]     (零扩展)
lhu  x1, 0(x2)                  # x1 = (uint16_t) mem[x2]    (零扩展)

# Store指令
sb   x1, 0(x2)                  # mem[x2] = x1[7:0]          (字节)
sh   x1, 0(x2)                  # mem[x2] = x1[15:0]         (半字)
sw   x1, 0(x2)                  # mem[x2] = x1[31:0]         (字)

# 示例：访问数组
    la   x1, array              # x1 = 数组地址
    addi x2, x0, 5              # x2 = 索引5
    slli x2, x2, 2              # x2 = 5 * 4 (字节偏移)
    add  x3, x1, x2             # x3 = &array[5]
    lw   x4, 0(x3)              # x4 = array[5]
```

### 分支和跳转

```assembly
# 条件分支
beq  x1, x2, label              # if (x1 == x2) goto label
bne  x1, x2, label              # if (x1 != x2) goto label
blt  x1, x2, label              # if (x1 < x2)  goto label (有符号)
bge  x1, x2, label              # if (x1 >= x2) goto label (有符号)
bltu x1, x2, label              # if (x1 < x2)  goto label (无符号)
bgeu x1, x2, label              # if (x1 >= x2) goto label (无符号)

# 无条件跳转
jal  ra, function               # ra = PC + 4, goto function
jalr zero, 0(ra)                # goto *ra (函数返回)

# 示例：if-else结构
    addi x1, x0, 10
    addi x2, x0, 20
    blt  x1, x2, then_branch    # if (x1 < x2)
else_branch:
    # x1 >= x2的代码
    jal  x0, endif
then_branch:
    # x1 < x2的代码
endif:
    # 继续执行
```

### 循环结构

```assembly
# for循环示例：for(i=0; i<10; i++)
    addi x1, x0, 0              # i = 0
    addi x2, x0, 10             # limit = 10
for_loop:
    bge  x1, x2, for_end        # if (i >= 10) break

    # 循环体
    # ... 你的代码 ...

    addi x1, x1, 1              # i++
    jal  x0, for_loop
for_end:

# while循环示例：while(count > 0)
while_loop:
    beq  x1, x0, while_end      # if (count == 0) break

    # 循环体
    # ... 你的代码 ...

    addi x1, x1, -1             # count--
    jal  x0, while_loop
while_end:

# do-while循环示例：do { ... } while(x1 != 0)
do_while:
    # 循环体
    # ... 你的代码 ...

    bne  x1, x0, do_while       # if (x1 != 0) continue
```

### 函数调用约定

```assembly
# 函数定义：int add(int a, int b)
# 参数：a0 = a, a1 = b
# 返回值：a0
add:
    # 保存被调用者保存寄存器（如果使用）
    addi sp, sp, -8
    sw   s0, 0(sp)
    sw   s1, 4(sp)

    # 函数体
    add  a0, a0, a1             # result = a + b

    # 恢复寄存器
    lw   s0, 0(sp)
    lw   s1, 4(sp)
    addi sp, sp, 8

    # 返回
    jalr zero, 0(ra)

# 调用示例
caller:
    # 保存调用者保存寄存器
    addi sp, sp, -4
    sw   ra, 0(sp)

    # 设置参数
    addi a0, x0, 5              # a = 5
    addi a1, x0, 3              # b = 3

    # 调用函数
    jal  ra, add                # result in a0

    # 使用返回值
    add  x1, a0, x0             # x1 = result

    # 恢复ra并返回
    lw   ra, 0(sp)
    addi sp, sp, 4
    jalr zero, 0(ra)
```

---

## 🔧 C语言编程

### 工具链设置

```bash
# 安装RISC-V GCC工具链
# Ubuntu/Debian
sudo apt install gcc-riscv64-unknown-elf

# 或从源码编译
git clone https://github.com/riscv/riscv-gnu-toolchain
cd riscv-gnu-toolchain
./configure --prefix=/opt/riscv --with-arch=rv32e --with-abi=ilp32e
make

# 添加到PATH
export PATH=/opt/riscv/bin:$PATH
```

### Hello World in C

创建 `hello.c`:

```c
// 外设基地址定义
#define UART_BASE 0x20000000
#define GPIO_BASE 0x20001000

// UART寄存器
#define UART_TXDATA  (*(volatile unsigned int*)(UART_BASE + 0x00))
#define UART_RXDATA  (*(volatile unsigned int*)(UART_BASE + 0x04))
#define UART_STATUS  (*(volatile unsigned int*)(UART_BASE + 0x08))

// UART状态位
#define UART_TX_FULL  (1 << 1)
#define UART_RX_EMPTY (1 << 0)

// 发送一个字符
void uart_putc(char c) {
    // 等待发送FIFO不满
    while (UART_STATUS & UART_TX_FULL);
    UART_TXDATA = c;
}

// 发送字符串
void uart_puts(const char* s) {
    while (*s) {
        uart_putc(*s++);
    }
}

// 主函数
int main(void) {
    uart_puts("Hello from RV32E!\n");

    while (1) {
        // 主循环
    }

    return 0;
}

// 启动代码
void _start(void) {
    // 初始化栈指针
    asm volatile("lui sp, 0x80010");  // sp = 0x80010000

    // 调用main
    main();

    // 无限循环（防止返回）
    while (1);
}
```

创建链接脚本 `linker.ld`:

```ld
OUTPUT_ARCH("riscv")
ENTRY(_start)

MEMORY {
    RAM : ORIGIN = 0x80000000, LENGTH = 64K
}

SECTIONS {
    .text : {
        *(.text.start)
        *(.text*)
    } > RAM

    .rodata : {
        *(.rodata*)
    } > RAM

    .data : {
        *(.data*)
    } > RAM

    .bss : {
        *(.bss*)
        *(COMMON)
    } > RAM

    /* 栈在RAM顶部 */
    . = ORIGIN(RAM) + LENGTH(RAM);
    _stack_top = .;
}
```

编译和运行:

```bash
# 编译
riscv32-unknown-elf-gcc -march=rv32e -mabi=ilp32e -nostdlib \
    -T linker.ld hello.c -o hello.elf

# 生成二进制
riscv32-unknown-elf-objcopy -O binary hello.elf hello.bin

# 查看反汇编
riscv32-unknown-elf-objdump -d hello.elf

# 查看大小
riscv32-unknown-elf-size hello.elf
```

### 使用标准库

创建 `stdlib_example.c`:

```c
#include <stdint.h>
#include <string.h>

// 外设定义（同上）
#define UART_BASE 0x20000000
// ...

// 简单的printf实现
void mini_printf(const char* fmt, ...) {
    // 简化版本，只支持%d, %x, %s
    // 实现略...
}

// 字符串操作
void string_demo(void) {
    char buffer[32];

    strcpy(buffer, "Hello");
    strcat(buffer, " World");
    uart_puts(buffer);  // 输出: Hello World

    int len = strlen(buffer);
    mini_printf("Length: %d\n", len);
}

// 数学运算
int fibonacci(int n) {
    if (n <= 1) return n;
    return fibonacci(n-1) + fibonacci(n-2);
}

int main(void) {
    uart_puts("Standard Library Demo\n");

    string_demo();

    int result = fibonacci(10);
    mini_printf("Fib(10) = %d\n", result);  // 55

    return 0;
}
```

---

## 🔌 外设编程

### UART编程

```c
#define UART_BASE   0x20000000
#define UART_TXDATA (*(volatile uint32_t*)(UART_BASE + 0x00))
#define UART_RXDATA (*(volatile uint32_t*)(UART_BASE + 0x04))
#define UART_STATUS (*(volatile uint32_t*)(UART_BASE + 0x08))
#define UART_CTRL   (*(volatile uint32_t*)(UART_BASE + 0x0C))

#define UART_RX_EMPTY (1 << 0)
#define UART_TX_FULL  (1 << 1)

// 初始化UART
void uart_init(void) {
    // 波特率已由硬件配置为115200
    // 使能接收和发送
    UART_CTRL = 0x03;
}

// 发送字符
void uart_putc(char c) {
    while (UART_STATUS & UART_TX_FULL);
    UART_TXDATA = c;
}

// 接收字符
char uart_getc(void) {
    while (UART_STATUS & UART_RX_EMPTY);
    return (char)(UART_RXDATA & 0xFF);
}

// 发送字符串
void uart_puts(const char* s) {
    while (*s) {
        uart_putc(*s++);
    }
}

// 回显示例
void uart_echo(void) {
    char c = uart_getc();
    uart_putc(c);
}
```

### GPIO编程

```c
#define GPIO_BASE  0x20001000
#define GPIO_DATA  (*(volatile uint32_t*)(GPIO_BASE + 0x00))
#define GPIO_DIR   (*(volatile uint32_t*)(GPIO_BASE + 0x04))
#define GPIO_OE    (*(volatile uint32_t*)(GPIO_BASE + 0x08))

// 初始化GPIO
void gpio_init(void) {
    GPIO_DIR = 0x0F;    // GPIO[3:0]设为输出
    GPIO_OE  = 0x0F;    // 使能输出
}

// 设置GPIO值
void gpio_write(uint8_t value) {
    GPIO_DATA = value;
}

// 读取GPIO值
uint8_t gpio_read(void) {
    return (uint8_t)(GPIO_DATA & 0xFF);
}

// LED闪烁
void led_blink(void) {
    gpio_init();

    while (1) {
        gpio_write(0x01);    // LED ON
        delay_ms(500);

        gpio_write(0x00);    // LED OFF
        delay_ms(500);
    }
}

// 延时函数（软件延时）
void delay_ms(uint32_t ms) {
    // 假设25MHz时钟
    const uint32_t cycles_per_ms = 25000;
    uint32_t total_cycles = ms * cycles_per_ms;

    for (uint32_t i = 0; i < total_cycles / 10; i++) {
        asm volatile("nop");
    }
}
```

### SPI编程

```c
#define SPI_BASE   0x20002000
#define SPI_TXDATA (*(volatile uint32_t*)(SPI_BASE + 0x00))
#define SPI_RXDATA (*(volatile uint32_t*)(SPI_BASE + 0x04))
#define SPI_STATUS (*(volatile uint32_t*)(SPI_BASE + 0x08))
#define SPI_CTRL   (*(volatile uint32_t*)(SPI_BASE + 0x0C))
#define SPI_SCKDIV (*(volatile uint32_t*)(SPI_BASE + 0x10))

#define SPI_BUSY   (1 << 0)
#define SPI_RXNE   (1 << 1)  // RX not empty

// 初始化SPI
void spi_init(void) {
    SPI_SCKDIV = 2;     // SCK = 25MHz / (2+1) = 8.33MHz
    SPI_CTRL = 0x01;    // 使能SPI
}

// SPI传输一个字节
uint8_t spi_transfer(uint8_t data) {
    // 等待不忙
    while (SPI_STATUS & SPI_BUSY);

    // 发送数据
    SPI_TXDATA = data;

    // 等待接收完成
    while (!(SPI_STATUS & SPI_RXNE));

    // 读取接收到的数据
    return (uint8_t)(SPI_RXDATA & 0xFF);
}

// 读取SPI Flash示例
void spi_flash_read(uint32_t addr, uint8_t* buf, uint32_t len) {
    // 发送读命令 (0x03)
    spi_transfer(0x03);

    // 发送24位地址
    spi_transfer((addr >> 16) & 0xFF);
    spi_transfer((addr >> 8) & 0xFF);
    spi_transfer(addr & 0xFF);

    // 读取数据
    for (uint32_t i = 0; i < len; i++) {
        buf[i] = spi_transfer(0xFF);
    }
}
```

### I2C编程

```c
#define I2C_BASE   0x20003000
#define I2C_TXDATA (*(volatile uint32_t*)(I2C_BASE + 0x00))
#define I2C_RXDATA (*(volatile uint32_t*)(I2C_BASE + 0x04))
#define I2C_STATUS (*(volatile uint32_t*)(I2C_BASE + 0x08))
#define I2C_CTRL   (*(volatile uint32_t*)(I2C_BASE + 0x0C))
#define I2C_CLKDIV (*(volatile uint32_t*)(I2C_BASE + 0x10))

#define I2C_BUSY   (1 << 0)
#define I2C_ACK    (1 << 1)
#define I2C_NACK   (1 << 2)

// 初始化I2C
void i2c_init(void) {
    I2C_CLKDIV = 125;   // SCL = 25MHz / (125*4) = 50kHz
    I2C_CTRL = 0x01;    // 使能I2C
}

// 发送START条件
void i2c_start(void) {
    I2C_CTRL |= (1 << 4);
    while (I2C_STATUS & I2C_BUSY);
}

// 发送STOP条件
void i2c_stop(void) {
    I2C_CTRL |= (1 << 5);
    while (I2C_STATUS & I2C_BUSY);
}

// 写一个字节
int i2c_write_byte(uint8_t data) {
    I2C_TXDATA = data;
    while (I2C_STATUS & I2C_BUSY);

    // 检查ACK
    return (I2C_STATUS & I2C_ACK) ? 0 : -1;
}

// 读一个字节
uint8_t i2c_read_byte(int ack) {
    I2C_CTRL = (I2C_CTRL & ~(1 << 6)) | (ack << 6);
    while (I2C_STATUS & I2C_BUSY);

    return (uint8_t)(I2C_RXDATA & 0xFF);
}

// 写寄存器到I2C设备
int i2c_write_reg(uint8_t dev_addr, uint8_t reg_addr, uint8_t data) {
    i2c_start();

    if (i2c_write_byte(dev_addr << 1) < 0)  // 写地址
        goto error;

    if (i2c_write_byte(reg_addr) < 0)       // 寄存器地址
        goto error;

    if (i2c_write_byte(data) < 0)           // 数据
        goto error;

    i2c_stop();
    return 0;

error:
    i2c_stop();
    return -1;
}
```

---

## ⚡ 性能优化

### 1. 使用寄存器变量

```c
// 差的代码
void process_array(int* arr, int size) {
    for (int i = 0; i < size; i++) {
        arr[i] = arr[i] * 2;  // 每次循环都访问内存
    }
}

// 好的代码
void process_array_opt(int* arr, int size) {
    for (int i = 0; i < size; i++) {
        register int temp = arr[i];  // 读一次
        temp = temp * 2;             // 寄存器运算
        arr[i] = temp;               // 写一次
    }
}
```

### 2. 循环展开

```c
// 普通循环
for (int i = 0; i < 100; i++) {
    sum += array[i];
}

// 展开后（减少分支）
for (int i = 0; i < 100; i += 4) {
    sum += array[i];
    sum += array[i+1];
    sum += array[i+2];
    sum += array[i+3];
}
```

### 3. 避免除法和取模

```c
// 慢：使用除法
int index = value / 8;

// 快：使用移位
int index = value >> 3;  // 除以2^3 = 8

// 慢：取模
int remainder = value % 16;

// 快：按位与
int remainder = value & 0x0F;  // % 16 (如果16是2的幂)
```

### 4. 内联小函数

```c
// 使用inline避免函数调用开销
static inline uint32_t read_reg(uint32_t addr) {
    return *(volatile uint32_t*)addr;
}

static inline void write_reg(uint32_t addr, uint32_t value) {
    *(volatile uint32_t*)addr = value;
}
```

---

## 🐛 调试技巧

### 1. 串口调试

```c
// 调试打印宏
#define DEBUG_PRINT(fmt, ...) uart_puts(fmt)

void debug_hex(uint32_t value) {
    const char hex[] = "0123456789ABCDEF";
    uart_putc('0');
    uart_putc('x');
    for (int i = 28; i >= 0; i -= 4) {
        uart_putc(hex[(value >> i) & 0xF]);
    }
    uart_putc('\n');
}

// 使用示例
uint32_t pc;
asm volatile("auipc %0, 0" : "=r"(pc));
DEBUG_PRINT("PC = ");
debug_hex(pc);
```

### 2. 断言

```c
#define ASSERT(cond) do { \
    if (!(cond)) { \
        uart_puts("ASSERT FAILED: " #cond "\n"); \
        while(1); \
    } \
} while(0)

// 使用
void process(int* ptr) {
    ASSERT(ptr != NULL);
    ASSERT(*ptr >= 0);
    // ...
}
```

### 3. LED调试

```c
// 使用LED指示程序状态
#define LED_PATTERN(x) GPIO_DATA = (x)

void init_system(void) {
    LED_PATTERN(0x01);  // 阶段1
    uart_init();

    LED_PATTERN(0x03);  // 阶段2
    spi_init();

    LED_PATTERN(0x07);  // 阶段3
    i2c_init();

    LED_PATTERN(0x0F);  // 完成
}
```

---

## 📚 完整示例程序

### 示例：多任务调度器

```c
// 简单的协作式多任务调度器
#define MAX_TASKS 4

typedef struct {
    void (*function)(void);
    uint32_t period_ms;
    uint32_t last_run;
    int enabled;
} Task;

Task tasks[MAX_TASKS];
uint32_t system_ticks = 0;

// 添加任务
void task_add(int id, void (*func)(void), uint32_t period) {
    tasks[id].function = func;
    tasks[id].period_ms = period;
    tasks[id].last_run = 0;
    tasks[id].enabled = 1;
}

// 任务调度器
void task_scheduler(void) {
    for (int i = 0; i < MAX_TASKS; i++) {
        if (!tasks[i].enabled) continue;

        if ((system_ticks - tasks[i].last_run) >= tasks[i].period_ms) {
            tasks[i].function();
            tasks[i].last_run = system_ticks;
        }
    }
}

// 任务1：LED闪烁
void task_led(void) {
    static uint8_t state = 0;
    state = !state;
    gpio_write(state);
}

// 任务2：UART输出
void task_uart(void) {
    uart_puts("Tick\n");
}

// 任务3：读取传感器（示例）
void task_sensor(void) {
    // 读取I2C传感器
    // ...
}

// 主函数
int main(void) {
    // 初始化
    gpio_init();
    uart_init();

    // 添加任务
    task_add(0, task_led, 500);     // 每500ms闪烁
    task_add(1, task_uart, 1000);   // 每1000ms打印
    task_add(2, task_sensor, 2000); // 每2000ms读传感器

    // 主循环
    while (1) {
        task_scheduler();
        system_ticks++;
        delay_ms(1);
    }

    return 0;
}
```

---

## 🎓 最佳实践

### 1. 代码组织

```
project/
├── src/
│   ├── main.c
│   ├── uart.c
│   ├── gpio.c
│   └── utils.c
├── include/
│   ├── uart.h
│   ├── gpio.h
│   └── config.h
├── startup/
│   └── startup.s
├── linker.ld
└── Makefile
```

### 2. 头文件保护

```c
#ifndef _UART_H_
#define _UART_H_

// UART函数声明
void uart_init(void);
void uart_putc(char c);
// ...

#endif // _UART_H_
```

### 3. 使用Makefile

```makefile
CC = riscv32-unknown-elf-gcc
OBJCOPY = riscv32-unknown-elf-objcopy
CFLAGS = -march=rv32e -mabi=ilp32e -O2 -g
LDFLAGS = -T linker.ld -nostdlib

SRCS = main.c uart.c gpio.c
OBJS = $(SRCS:.c=.o)

all: firmware.bin

firmware.elf: $(OBJS)
	$(CC) $(LDFLAGS) $^ -o $@

firmware.bin: firmware.elf
	$(OBJCOPY) -O binary $< $@

%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -f *.o *.elf *.bin
```

---

**继续学习**:
- 📖 查看 [完整示例程序](examples/)
- 🔧 参考 [外设API文档](PERIPHERAL_API.md)
- 🏗️ 理解 [系统架构](ARCHITECTURE.md)

**文档版本**: 1.0
**最后更新**: 2025-11-05
