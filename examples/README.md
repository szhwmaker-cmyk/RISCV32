# RV32E SoC 示例程序

本目录包含RV32E SoC的各种示例程序，从简单的LED闪烁到复杂的多任务应用。

---

## 📚 示例列表

| 编号 | 文件名 | 语言 | 难度 | 功能描述 |
|------|--------|------|------|----------|
| 01 | `01_led_blink.s` | 汇编 | ⭐ | LED闪烁，最基础的GPIO示例 |
| 02 | `02_uart_hello.s` | 汇编 | ⭐⭐ | UART发送Hello消息 |
| 03 | `03_uart_echo.s` | 汇编 | ⭐⭐ | UART回显服务器 |
| 04 | `04_button_led.s` | 汇编 | ⭐⭐ | 按钮控制LED |
| 05 | `05_hello_c.c` | C | ⭐⭐ | C语言版Hello World |

---

## 🚀 快速开始

### 前置条件

1. **安装RISC-V工具链**:
```bash
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

2. **验证工具链**:
```bash
riscv32-unknown-elf-gcc --version
riscv32-unknown-elf-as --version
```

### 编译所有示例

```bash
cd examples/
make
```

**输出**:
```
Assembling 01_led_blink.s...
Linking 01_led_blink.elf...
Size of 01_led_blink.elf:
   text    data     bss     dec     hex filename
    112       0       0     112      70 01_led_blink.elf

Creating binary 01_led_blink.bin...
...
```

### 编译单个示例

```bash
# 编译LED闪烁示例
make 01_led_blink.elf

# 生成二进制文件
make 01_led_blink.bin

# 查看反汇编
make 01_led_blink.dis
```

### 清理生成文件

```bash
make clean
```

---

## 🔬 示例详解

### 01_led_blink.s - LED闪烁 ⭐

**学习要点**:
- GPIO基地址访问
- 寄存器配置（DIR, OE, DATA）
- 软件延时循环
- 无条件跳转

**硬件需求**:
- GPIO LED[0]

**FPGA测试**:
```bash
# 编译
make 01_led_blink.bin

# 部署到FPGA（需要修改fpga/scripts）
# 或使用仿真测试
mill rv32e.test.testOnly rv32e.integration.IntegrationSpec
```

**预期行为**:
- LED[0]以1Hz频率闪烁（ON 0.5s, OFF 0.5s）

**代码片段**:
```assembly
# LED ON
addi x2, x0, 0x01      # x2 = 1
sw   x2, 0(x1)         # GPIO_DATA = 1

# 延时
lui  x3, 0x00BEB
addi x3, x3, 0x640     # 约500ms @ 25MHz
delay_on:
    addi x3, x3, -1
    bne  x3, x0, delay_on

# LED OFF
addi x2, x0, 0x00
sw   x2, 0(x1)
```

---

### 02_uart_hello.s - UART Hello ⭐⭐

**学习要点**:
- UART寄存器访问（TXDATA, STATUS）
- 字符串存储（.data段）
- 轮询发送（等待TX FIFO不满）
- 字符串结束符处理

**硬件需求**:
- UART TX

**UART配置**:
- 波特率: 115200
- 数据位: 8
- 停止位: 1
- 校验: 无

**测试**:
```bash
# 编译
make 02_uart_hello.elf

# 连接串口
screen /dev/ttyUSB1 115200

# 或
minicom -D /dev/ttyUSB1 -b 115200
```

**预期输出**:
```
Hello RV32E!
```

---

### 03_uart_echo.s - UART回显 ⭐⭐

**学习要点**:
- UART接收（RXDATA, RX_EMPTY）
- 双向通信（发送和接收）
- 子程序调用（JAL/JALR）
- 栈操作（保存/恢复寄存器）

**测试**:
```bash
# 连接串口
screen /dev/ttyUSB1 115200

# 输入任何字符，都会被回显
```

**预期输出**:
```
UART Echo Server Ready
Type something...
[用户输入] test
[回显] test
```

**子程序示例**:
```assembly
uart_puts:
    addi sp, sp, -4       # 分配栈空间
    sw   ra, 0(sp)        # 保存返回地址

    # ... 函数体 ...

    lw   ra, 0(sp)        # 恢复返回地址
    addi sp, sp, 4        # 释放栈空间
    jalr zero, 0(ra)      # 返回
```

---

### 04_button_led.s - 按钮LED ⭐⭐

**学习要点**:
- GPIO输入读取
- 位操作（移位、掩码）
- 输入输出混合配置
- 消抖处理

**硬件需求**:
- GPIO LED[3:0]（输出）
- GPIO BTN[3:0]（输入）

**预期行为**:
- 按下BTN[n] → LED[n]亮
- 松开BTN[n] → LED[n]灭
- 支持多个按钮同时按下

---

### 05_hello_c.c - C语言Hello ⭐⭐

**学习要点**:
- C语言访问硬件寄存器
- volatile关键字使用
- 函数封装（uart_putc, uart_puts等）
- 启动代码编写

**编译**:
```bash
make 05_hello_c.elf

# 查看编译结果
riscv32-unknown-elf-objdump -d 05_hello_c.elf

# 查看大小
riscv32-unknown-elf-size 05_hello_c.elf
```

**预期UART输出**:
```
========================================
  RV32E SoC - Hello World (C)
========================================
System initializing...
System ready!

LED ON  - Count: 0
LED OFF - Count: 0
LED ON  - Count: 1
LED OFF - Count: 1
...
```

**C语言寄存器访问**:
```c
#define UART_BASE   0x20000000
#define UART_TXDATA (*(volatile uint32_t*)(UART_BASE + 0x00))

void uart_putc(char c) {
    while (UART_STATUS & UART_TX_FULL);
    UART_TXDATA = c;
}
```

---

## 🛠️ 编译流程详解

### 汇编程序编译流程

```bash
# 1. 汇编：.s → .o
riscv32-unknown-elf-as -march=rv32e -mabi=ilp32e 01_led_blink.s -o 01_led_blink.o

# 2. 链接：.o → .elf
riscv32-unknown-elf-ld -T linker.ld 01_led_blink.o -o 01_led_blink.elf

# 3. 生成二进制：.elf → .bin
riscv32-unknown-elf-objcopy -O binary 01_led_blink.elf 01_led_blink.bin

# 4. 查看反汇编
riscv32-unknown-elf-objdump -d 01_led_blink.elf > 01_led_blink.dis
```

### C程序编译流程

```bash
# 1. 编译：.c → .o
riscv32-unknown-elf-gcc -march=rv32e -mabi=ilp32e -O2 -nostdlib -c 05_hello_c.c -o 05_hello_c.o

# 2-4. 与汇编程序相同
```

---

## 📦 文件结构

```
examples/
├── 01_led_blink.s        # LED闪烁（汇编）
├── 02_uart_hello.s       # UART发送（汇编）
├── 03_uart_echo.s        # UART回显（汇编）
├── 04_button_led.s       # 按钮LED（汇编）
├── 05_hello_c.c          # Hello World（C）
├── linker.ld             # 链接器脚本
├── Makefile              # 编译脚本
└── README.md             # 本文档
```

---

## 🎯 学习路径建议

### 初学者路径

1. **01_led_blink.s** - 理解GPIO基本操作
2. **02_uart_hello.s** - 学习UART发送
3. **05_hello_c.c** - 转到C语言编程

### 进阶路径

4. **03_uart_echo.s** - 学习UART双向通信和子程序
5. **04_button_led.s** - 学习输入处理

### 高级任务（自己实现）

- 📝 **PWM LED调光** - 使用软件PWM控制LED亮度
- 🎵 **蜂鸣器音乐** - 通过GPIO生成不同频率
- 🌡️ **I2C温度传感器** - 读取温度并显示
- 💾 **SPI Flash读写** - 存储和读取数据
- 🎮 **简单游戏** - 按钮输入，LED输出

---

## 🔧 调试技巧

### 1. 使用反汇编查看代码

```bash
make 01_led_blink.dis
cat 01_led_blink.dis
```

输出示例:
```
01_led_blink.elf:     file format elf32-littleriscv

Disassembly of section .text:

80000000 <_start>:
80000000:   200010b7                lui     ra,0x20001
80000004:   00f00113                li      sp,15
80000008:   0020a223                sw      sp,4(ra)
...
```

### 2. 使用仿真测试

```bash
# 在ChiselTest中测试
cd ..
mill rv32e.test.testOnly rv32e.integration.IntegrationSpec

# 查看VCD波形
gtkwave test_run_dir/*/IntegrationSpec.vcd
```

### 3. UART调试输出

在程序中添加调试输出：
```assembly
# 调试：打印寄存器值
debug_print:
    # 将x1的值以十六进制输出
    # (实现略，参考uart_hello示例)
```

### 4. LED状态指示

使用LED表示程序状态：
```assembly
# 阶段1完成
addi x2, x0, 0x01
sw   x2, 0(x1)

# 阶段2完成
addi x2, x0, 0x03
sw   x2, 0(x1)

# 阶段3完成
addi x2, x0, 0x07
sw   x2, 0(x1)
```

---

## 📖 相关文档

- 📘 [快速入门教程](../QUICK_START.md) - 从零到LED闪烁
- 💻 [编程指南](../PROGRAMMING_GUIDE.md) - 完整的编程参考
- 🏗️ [架构文档](../ARCHITECTURE.md) - 理解硬件结构
- 🔌 [外设API](../PERIPHERAL_API.md) - 详细的外设文档（待创建）

---

## ❓ 常见问题

### Q: 编译时报错 "cannot find -lc"

**A**: 使用 `-nostdlib` 选项，不链接标准库：
```bash
riscv32-unknown-elf-gcc -march=rv32e -mabi=ilp32e -nostdlib ...
```

### Q: 程序运行后没有反应？

**A**: 检查清单：
1. 确认FPGA已正确编程（DONE LED亮）
2. 检查时钟配置（PLL locked）
3. 确认栈指针正确设置
4. 使用LED调试，确认程序执行到哪里

### Q: UART没有输出？

**A**:
1. 确认波特率：115200
2. 确认设备：`/dev/ttyUSB1`（不是USB0）
3. 检查权限：`sudo chmod 666 /dev/ttyUSB1`
4. 测试回环：`echo "test" > /dev/ttyUSB1`

### Q: 如何修改延时时间？

**A**: 调整延时循环计数：
```assembly
# 500ms @ 25MHz ≈ 12,500,000 cycles
lui  x3, 0x00BEB        # 高20位
addi x3, x3, 0x640      # 低12位

# 自定义延时公式：
# cycles = delay_ms * 25000
# 然后转换为十六进制，拆分为lui和addi
```

---

## 🤝 贡献

欢迎贡献更多示例！

**建议的新示例**:
- [ ] SPI Flash读写示例
- [ ] I2C传感器示例
- [ ] PWM LED调光
- [ ] 简单shell命令行
- [ ] 多任务调度器

**提交PR**:
1. Fork本项目
2. 创建新示例文件
3. 添加到本README
4. 提交Pull Request

---

**文档版本**: 1.0
**最后更新**: 2025-11-05
**维护者**: RV32E SoC Team

---

**祝您学习愉快！** 🚀
如有问题，请查看 [完整文档](../) 或提交 Issue。
