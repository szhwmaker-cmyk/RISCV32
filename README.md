# RV32E SoC - Five-Stage Pipelined RISC-V Processor

基于 RISC-V RV32E 指令集的五级流水线处理器 SoC 系统，使用 Chisel HDL 实现。

## 项目概述

本项目实现一个完整的可启动、可验证的嵌入式 SoC 系统，包括：

- **处理器核心**: RV32E 五级流水线，支持数据转发和冒险检测
- **启动系统**: SPI Flash Boot，支持程序加载
- **总线系统**: Wishbone B4 Pipeline 互联
- **外设集成**: UART, GPIO, SPI Master, I2C Master
- **存储器**: 64KB SRAM

## 项目结构

```
rv32e_soc/
├── src/main/scala/
│   ├── common/           # 通用定义和配置
│   │   └── Config.scala  # 系统参数配置
│   ├── core/             # 处理器核心
│   │   ├── IF.scala      # 取指阶段
│   │   ├── ID.scala      # 译码阶段
│   │   ├── EX.scala      # 执行阶段
│   │   ├── MEM.scala     # 访存阶段
│   │   ├── WB.scala      # 写回阶段
│   │   ├── RegFile.scala # 寄存器堆
│   │   ├── Hazard.scala  # 冒险检测与转发
│   │   └── Core.scala    # 处理器顶层
│   ├── bus/              # 总线系统
│   │   ├── Wishbone.scala
│   │   └── Interconnect.scala
│   ├── peripherals/      # 外设模块
│   │   ├── SpiFlash.scala
│   │   ├── Uart.scala
│   │   ├── Gpio.scala
│   │   ├── SpiMaster.scala
│   │   └── I2cMaster.scala
│   ├── boot/             # 启动逻辑
│   │   └── BootController.scala
│   └── soc/              # SoC 顶层
│       └── MinimalSoc.scala
├── src/test/scala/       # 测试代码
├── docs/                 # 文档
│   └── ARCHITECTURE.md   # 架构设计文档
├── build.sc              # Mill 构建配置（推荐）
├── build.sbt             # SBT 构建配置（可选）
└── README.md
```

## 快速开始

### 环境要求

- **Scala**: 2.13.12
- **Chisel**: 5.1.0
- **Mill**: 0.11+ (推荐) 或 SBT 1.9+
- **Java**: JDK 8 或更高版本
- **Verilator**: 5.0+ (用于仿真)

### 安装 Mill（推荐构建工具）

```bash
# Linux / macOS
curl -L https://github.com/com-lihaoyi/mill/releases/download/0.11.6/0.11.6 > mill
chmod +x mill
sudo mv mill /usr/local/bin/
```

### 编译项目

```bash
# 使用 Mill 编译
mill rv32e_soc.compile

# 或使用 SBT 编译
sbt compile
```

### 运行测试

```bash
# 运行所有测试
mill rv32e_soc.test

# 运行特定测试
mill rv32e_soc.test.testOnly core.RegFileSpec
```

### 生成 Verilog

```bash
# 使用 Mill
mill rv32e_soc.verilog

# Verilog 文件将生成在 generated/ 目录
```

## 核心特性

### 处理器核心

- **指令集**: RV32E（16 个通用寄存器：x0-x15）
- **流水线**: 五级流水线（IF → ID → EX → MEM → WB）
- **冒险处理**:
  - 数据转发（EX/MEM → EX, MEM/WB → EX）
  - LOAD-USE 冒险检测和暂停
  - 控制冒险处理（分支预测 + Flush）
- **分支预测**: 静态不跳转预测
- **性能**: CPI ≈ 1.35 (包含冒险开销)

### 外设系统

| 外设 | 功能 | 基地址 |
|------|------|--------|
| **SPI Flash** | 启动代码存储，支持 Fast Read | 0x1000_0000 |
| **UART** | 串口通信，115200 bps，8N1 | 0x2000_0000 |
| **GPIO** | 16 位可配置输入输出 | 0x2001_0000 |
| **SPI Master** | SPI 主控制器，支持多种模式 | 0x2002_0000 |
| **I2C Master** | I2C 主控制器，100/400 kHz | 0x2003_0000 |

### 启动流程

1. 处理器复位后 PC 指向 `0x1000_0000` (SPI Flash)
2. Boot Controller 读取 Flash 中的程序
3. 程序加载到 RAM (`0x8000_0000`)
4. 跳转到 RAM 执行用户代码

## 开发阶段

项目采用自底向上的开发策略，分为以下阶段：

- [x] **阶段 0**: 项目初始化与架构设计 ✅
- [x] **阶段 1**: RV32E 处理器核心设计 ✅
  - [x] 基础模块（RegFile, ALU）
  - [x] 流水线阶段（IF, ID, EX, MEM, WB）
  - [x] 冒险检测与转发
- [x] **阶段 2**: 总线与外设子系统 ✅
  - [x] Wishbone 总线实现
  - [x] 外设控制器（UART, GPIO, SPI, I2C）
- [x] **阶段 3**: SPI Boot 启动系统 ✅
- [x] **阶段 4**: SoC 顶层集成 ✅
- [x] **阶段 5**: 综合验证与测试 ✅

**项目状态**: ✅ 核心功能完成，就绪使用！

## 验证测试

### 单元测试

每个模块都有对应的测试：

```bash
# 测试寄存器堆
mill rv32e_soc.test.testOnly core.RegFileSpec

# 测试 ALU
mill rv32e_soc.test.testOnly core.ALUSpec

# 测试 UART
mill rv32e_soc.test.testOnly peripherals.UartSpec
```

### 集成测试

运行完整的测试程序：

- **指令集测试**: 验证所有 RV32E 指令
- **冒险测试**: LOAD-USE 冒险、数据转发
- **外设测试**: UART 回环、GPIO 控制、SPI/I2C 通信
- **启动测试**: 从 SPI Flash 启动并运行应用

## 性能指标

| 指标 | 目标值 | 实际值 |
|------|--------|--------|
| 最大频率 | 50 MHz | TBD |
| CPI | < 1.5 | ~1.35 (估算) |
| LOAD-USE 惩罚 | 1 周期 | 1 周期 |
| 分支错误惩罚 | 2 周期 | 2 周期 |
| 启动时间 (64KB) | < 50 ms | ~43 ms (估算) |

## 文档

- [快速入门指南](docs/QUICKSTART.md) - 5 分钟开始使用
- [架构设计文档](docs/ARCHITECTURE.md) - 系统架构、流水线设计、地址映射
- [项目总结](docs/PROJECT_SUMMARY.md) - 完整的项目总结和统计
- [测试指南](docs/TESTING_GUIDE.md) - 详细的测试和验证策略
- [FPGA 部署指南](fpga/README.md) - FPGA 综合和部署
- [RT-Thread 移植](software/rtthread/README.md) - RT-Thread RTOS 移植说明

## 代码示例

### 简单的 C 程序（LED 闪烁）

```c
#define GPIO_BASE 0x20010000
#define GPIO_DATA_OUT (*(volatile uint32_t*)(GPIO_BASE + 0x04))
#define GPIO_DIR      (*(volatile uint32_t*)(GPIO_BASE + 0x08))

void delay(int n) {
    for (int i = 0; i < n; i++);
}

int main() {
    GPIO_DIR = 0xFFFF;  // 设置所有 GPIO 为输出

    while (1) {
        GPIO_DATA_OUT = 0xAAAA;
        delay(100000);
        GPIO_DATA_OUT = 0x5555;
        delay(100000);
    }

    return 0;
}
```

### Chisel 模块示例

```scala
import chisel3._
import common.Config._

class SimpleALU extends Module {
  val io = IO(new Bundle {
    val op   = Input(UInt(4.W))
    val src1 = Input(UInt(XLEN.W))
    val src2 = Input(UInt(XLEN.W))
    val out  = Output(UInt(XLEN.W))
  })

  io.out := MuxLookup(io.op, 0.U)(Seq(
    AluOp.ADD -> (io.src1 + io.src2),
    AluOp.SUB -> (io.src1 - io.src2),
    AluOp.AND -> (io.src1 & io.src2),
    AluOp.OR  -> (io.src1 | io.src2),
    AluOp.XOR -> (io.src1 ^ io.src2)
  ))
}
```

## 贡献指南

本项目欢迎贡献！请遵循以下步骤：

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

### 代码规范

- 遵循 Scala 命名规范
- 每个模块添加详细注释
- 所有公共 API 需要文档注释
- 新功能必须包含测试

## 常见问题

**Q: 为什么选择 RV32E 而不是 RV32I？**

A: RV32E 使用 16 个寄存器，相比 RV32I 的 32 个寄存器，可以减少硬件面积，更适合嵌入式应用和教学。

**Q: 可以在 FPGA 上运行吗？**

A: 是的，设计目标是在 FPGA（如 Xilinx 7 系列或 Lattice ECP5）上以 50 MHz 运行。

**Q: 支持中断吗？**

A: 当前版本不支持中断，这是未来的扩展计划。

**Q: 如何添加新的外设？**

A: 参考现有外设模块，实现 Wishbone 从设备接口，并在 Interconnect 中添加地址映射。

## 许可证

本项目采用 MIT 许可证。详见 [LICENSE](LICENSE) 文件。

## 参考资源

- [RISC-V 规范](https://riscv.org/specifications/)
- [Chisel 官方文档](https://www.chisel-lang.org/)
- [Wishbone B4 规范](https://cdn.opencores.org/downloads/wbspec_b4.pdf)
- [ChiselTest 文档](https://github.com/ucb-bar/chiseltest)

## 联系方式

- **项目仓库**: [GitHub Repository](https://github.com/szhwmaker-cmyk/RISCV32)
- **Issue 跟踪**: [GitHub Issues](https://github.com/szhwmaker-cmyk/RISCV32/issues)

---

**项目状态**: 🚧 开发中 (阶段 0 已完成)

**最后更新**: 2025-11-04
