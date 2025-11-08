# RV32E SoC 用户指南

## 快速开始

### 1. 环境要求

- **Scala**: 2.13.12
- **Mill**: 最新版本
- **Java**: JDK 11 或更高
- **RISC-V工具链** (可选，用于编译应用程序): riscv32-unknown-elf-gcc

### 2. 克隆项目

```bash
git clone <repository-url>
cd RV32E_SoC
```

### 3. 编译硬件

```bash
mill rv32e_soc.compile
```

### 4. 运行测试

```bash
# 运行所有测试
mill rv32e_soc.test

# 运行特定测试
mill rv32e_soc.test.testOnly core.RegFileSpec
mill rv32e_soc.test.testOnly peripherals.UartSpec
mill rv32e_soc.test.testOnly soc.SoCIntegrationSpec
```

### 5. 生成Verilog

```bash
mill rv32e_soc.runMain soc.SoCMain
```

生成的Verilog文件位于 `generated/` 目录。

## 硬件架构

### 处理器核心

- **架构**: RV32E 五级流水线
- **寄存器**: 16个通用寄存器 (x0-x15)
- **指令集**: RV32E基础整数指令 (37条)

### 存储器映射

| 设备 | 基地址 | 大小 | 说明 |
|------|--------|------|------|
| SPI Flash | 0x1000_0000 | 256 MB | 程序存储 |
| UART | 0x2000_0000 | 64 KB | 串口通信 |
| GPIO | 0x2001_0000 | 64 KB | 通用IO |
| SPI Master | 0x2002_0000 | 64 KB | SPI主控 |
| I2C Master | 0x2003_0000 | 64 KB | I2C主控 |
| RAM | 0x8000_0000 | 256 KB | 数据存储 |

### 外设寄存器

#### UART (Base: 0x2000_0000)

| 偏移 | 名称 | 读/写 | 说明 |
|------|------|-------|------|
| 0x00 | TXDATA | W | 发送数据 |
| 0x04 | RXDATA | R | 接收数据 |
| 0x08 | STATUS | R | 状态 (bit0: tx_busy, bit1: tx_empty, bit4: rx_valid) |
| 0x0C | BAUD | RW | 波特率分频 |
| 0x10 | CTRL | RW | 控制 (bit0: tx_en, bit1: rx_en) |

#### GPIO (Base: 0x2001_0000)

| 偏移 | 名称 | 读/写 | 说明 |
|------|------|-------|------|
| 0x00 | DATA_IN | R | 输入数据 |
| 0x04 | DATA_OUT | RW | 输出数据 |
| 0x08 | DIR | RW | 方向 (0=输入, 1=输出) |
| 0x0C | OE | RW | 输出使能 |

#### SPI Master (Base: 0x2002_0000)

| 偏移 | 名称 | 读/写 | 说明 |
|------|------|-------|------|
| 0x00 | CTRL | RW | 控制 (bit[7:4]: data_width-1, bit[3:2]: cs_sel, bit1: cpha, bit0: cpol) |
| 0x04 | DIV | RW | 时钟分频 |
| 0x08 | DATA | RW | 数据寄存器 |
| 0x0C | STATUS | R | 状态 (bit0: busy, bit1: done) |

#### I2C Master (Base: 0x2003_0000)

| 偏移 | 名称 | 读/写 | 说明 |
|------|------|-------|------|
| 0x00 | CTRL | RW | 控制 (bit0: start, bit1: stop, bit2: write, bit3: read) |
| 0x04 | DIV | RW | 时钟分频 |
| 0x08 | ADDR | RW | 从设备地址 |
| 0x0C | DATA | RW | 数据 |
| 0x10 | STATUS | R | 状态 (bit0: busy, bit1: done, bit2: nack) |

## 应用程序开发

### 编写程序

参考 `sw/apps/hello.c` 示例：

```c
#include <stdint.h>

#define UART_BASE 0x20000000
#define UART_TXDATA (*(volatile uint32_t*)(UART_BASE + 0x00))
#define UART_STATUS (*(volatile uint32_t*)(UART_BASE + 0x08))

void uart_putc(char c) {
    while (UART_STATUS & 0x01);  // 等待TX空闲
    UART_TXDATA = c;
}

void uart_puts(const char* s) {
    while (*s) uart_putc(*s++);
}

int main() {
    uart_puts("Hello from RV32E!\\r\\n");
    while (1);
    return 0;
}
```

### 编译程序

```bash
cd sw/apps
make hello.elf
```

需要 `riscv32-unknown-elf-gcc` 工具链，配置为 `-march=rv32e -mabi=ilp32e`。

### 加载到Flash

编译生成的 `hello.bin` 可以：
1. 通过SPI编程器写入物理Flash芯片
2. 在仿真中加载到Flash模型

## FPGA部署

### 1. 生成Verilog

```bash
mill rv32e_soc.runMain soc.SoCMain
```

### 2. 约束文件

根据目标FPGA创建约束文件，映射引脚：

```
# 时钟
set_property PACKAGE_PIN xxx [get_ports clk]

# UART
set_property PACKAGE_PIN xxx [get_ports uart_tx]
set_property PACKAGE_PIN xxx [get_ports uart_rx]

# GPIO
set_property PACKAGE_PIN xxx [get_ports gpio_out[0]]
...
```

### 3. 综合和实现

使用Vivado、Quartus或其他FPGA工具：
1. 创建项目
2. 添加生成的Verilog文件
3. 添加约束文件
4. 综合、实现、生成比特流
5. 下载到FPGA

## 调试技巧

### 仿真调试

```scala
// 在测试中启用VCD波形
test(new RV32E_SoC).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
  // 测试代码
}
```

### UART监控

使用串口工具 (如 minicom, screen, putty) 连接UART：
- 波特率: 115200
- 数据位: 8
- 停止位: 1
- 校验: 无

### GPIO指示

使用LED观察GPIO输出状态，帮助调试程序执行流程。

## 常见问题

### Q: 如何修改系统时钟频率？

A: 在 `common/Config.scala` 中修改 `CLOCK_FREQ`，并重新计算波特率分频值。

### Q: 如何添加新的外设？

A:
1. 在 `peripherals/` 创建新的外设模块
2. 在 `bus/Interconnect.scala` 添加新的从设备端口
3. 在 `soc/RV32E_SoC.scala` 实例化并连接
4. 在 `common/Config.scala` 添加地址映射

### Q: RAM大小可以调整吗？

A: 可以，修改 `common/Config.scala` 中的 `RAM_ACTUAL` 和 `peripherals/Ram.scala` 中的 `RAM_WORDS`。

### Q: 支持哪些RISC-V指令？

A: 支持RV32E基础整数指令集的所有37条指令。不支持M扩展(乘除法)、A扩展(原子操作)、F/D扩展(浮点)。

## 性能优化建议

1. **减少分支**: 分支跳转会导致2周期损失
2. **避免LOAD-USE**: 在LOAD后立即使用会导致1周期暂停
3. **使用寄存器**: RV32E只有16个寄存器，合理分配使用
4. **循环展开**: 减少分支次数

## 更多资源

- RISC-V规范: https://riscv.org/specifications/
- Chisel文档: https://www.chisel-lang.org/
- Wishbone规范: OpenCores Wishbone Spec

## 技术支持

如有问题或建议，请提交Issue或Pull Request。
