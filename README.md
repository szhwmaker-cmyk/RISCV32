# RV32E SoC - RISC-V Five-Stage Pipeline Processor System

## 项目概述

本项目实现了一个完整的基于 RISC-V RV32E 指令集的五级流水线处理器 SoC 系统。

### 核心特性

- **处理器核心**: RV32E 五级流水线（IF → ID → EX → MEM → WB）
- **指令集**: RV32E 基础整数指令集（16 个通用寄存器，37条指令）
- **总线协议**: Wishbone B4 Pipeline
- **外设系统**: UART, GPIO, SPI Master, I2C Master（完整协议实现）
- **存储器**: 256KB SRAM
- **启动方式**: SPI Flash Boot（支持应用程序和RT-Thread OS）

## 系统架构

```
┌─────────────────────────────────────────────────────┐
│           RV32E 5-Stage Pipeline Core               │
│  ┌────┐  ┌────┐  ┌────┐  ┌────┐  ┌────┐            │
│  │ IF │─→│ ID │─→│ EX │─→│MEM │─→│ WB │            │
│  └────┘  └────┘  └────┘  └────┘  └────┘            │
└────────────────────┬────────────────────────────────┘
                     │
         ┌───────────▼────────────┐
         │  Wishbone Interconnect │
         └──┬────────┬────────┬───┘
            │        │        │
      ┌─────▼──┐ ┌──▼───┐ ┌─▼────┐
      │ UART   │ │ GPIO │ │ SPI  │ ...
      └────────┘ └──────┘ └──────┘
```

## 目录结构

```
RV32E_SoC/
├── src/main/scala/
│   ├── common/       # 通用配置和定义
│   ├── core/         # 处理器核心
│   ├── bus/          # Wishbone总线
│   ├── peripherals/  # 外设模块
│   ├── boot/         # 启动控制器
│   ├── soc/          # SoC顶层
│   └── sim/          # 仿真模型
├── src/test/scala/   # 测试文件
├── sw/               # 软件
│   ├── bootloader/   # Boot Loader
│   ├── apps/         # 应用程序
│   └── rtos/         # RT-Thread OS
└── docs/             # 文档
```

## 构建和测试

### 编译硬件

```bash
mill rv32e_soc.compile
```

### 运行测试

```bash
# 运行所有测试
mill rv32e_soc.test

# 运行特定测试
mill rv32e_soc.test.testOnly core.RegFileSpec
mill rv32e_soc.test.testOnly peripherals.UartSpec
mill rv32e_soc.test.testOnly integration.AppBootSpec
```

### 生成 Verilog

```bash
mill rv32e_soc.runMain soc.SoCMain
```

## 功能特性

### 流水线冒险处理

- **数据冒险**: 完整的数据转发（EX→EX, MEM→EX）
- **LOAD-USE冒险**: 自动插入气泡（Stall）
- **控制冒险**: 分支预测失败时Flush流水线

### 外设功能

- **UART**: 完整的TX/RX状态机，支持8-N-1格式
- **GPIO**: 16位可配置方向GPIO
- **SPI Master**: 支持4种SPI模式（CPOL/CPHA）
- **I2C Master**: 完整I2C协议（START/STOP/ACK）
- **SPI Flash**: FAST_READ命令支持

### 启动模式

1. **应用程序启动**: 从SPI Flash加载程序到RAM执行
2. **OS启动**: 支持RT-Thread简单映像（无中断）

## 性能指标

- **系统时钟**: 50 MHz
- **理论CPI**: 1.0（无冒险）
- **实际CPI**: 约 1.3-1.5
- **SPI时钟**: 12.5 MHz
- **UART波特率**: 115200 bps

## 地址映射

| 设备 | 基地址 | 大小 | 描述 |
|------|--------|------|------|
| SPI Flash | 0x1000_0000 | 256MB | Flash访问空间 |
| UART | 0x2000_0000 | 64KB | UART寄存器 |
| GPIO | 0x2001_0000 | 64KB | GPIO寄存器 |
| SPI Master | 0x2002_0000 | 64KB | SPI寄存器 |
| I2C Master | 0x2003_0000 | 64KB | I2C寄存器 |
| RAM | 0x8000_0000 | 256MB | SRAM空间 |

## 开发工具

- **硬件描述语言**: Chisel 6.0
- **构建工具**: Mill
- **测试框架**: ChiselTest
- **Scala版本**: 2.13.12

## 文档

详细文档请参考 `docs/` 目录：

- [架构设计文档](docs/ARCHITECTURE.md)
- [测试报告](docs/TEST_REPORT.md)
- [使用手册](docs/USER_GUIDE.md)

## License

MIT License

## 作者

RV32E SoC Project Team
