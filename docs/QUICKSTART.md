# RV32E SoC 快速入门指南

## 简介

本指南帮助你快速开始使用 RV32E SoC 项目，包括编译、测试、仿真和 FPGA 部署。

## 系统要求

### 硬件要求
- **RAM**: 至少 4 GB
- **存储**: 至少 10 GB 可用空间
- **FPGA**: Xilinx Artix-7 或兼容板卡（可选）

### 软件要求

#### 必需工具
1. **Java JDK** (8 或更高)
```bash
sudo apt install openjdk-11-jdk
```

2. **Mill** (Scala 构建工具)
```bash
curl -L https://github.com/com-lihaoyi/mill/releases/download/0.11.6/0.11.6 > mill
chmod +x mill
sudo mv mill /usr/local/bin/
```

3. **RISC-V 工具链**
```bash
# 下载预编译工具链
wget https://github.com/sifive/freedom-tools/releases/download/v2020.12.0/riscv64-unknown-elf-toolchain-10.2.0-2020.12.8-x86_64-linux-ubuntu14.tar.gz
tar xzf riscv64-unknown-elf-toolchain-10.2.0-2020.12.8-x86_64-linux-ubuntu14.tar.gz
sudo mv riscv64-unknown-elf-toolchain-10.2.0-2020.12.8-x86_64-linux-ubuntu14 /opt/riscv
export PATH=/opt/riscv/bin:$PATH
```

#### 可选工具
- **Verilator** (用于仿真)
```bash
sudo apt install verilator
```

- **Vivado** (用于 FPGA 综合)
  - 从 Xilinx 官网下载

- **GTKWave** (波形查看)
```bash
sudo apt install gtkwave
```

## 5 分钟快速开始

### 1. 克隆项目

```bash
git clone https://github.com/szhwmaker-cmyk/RISCV32.git
cd RISCV32
```

### 2. 编译 Chisel 代码

```bash
make compile
```

期望输出：
```
=== Compiling Chisel code ===
[info] compiling ...
[success] Total time: 30 s
```

### 3. 运行测试

```bash
make test
```

这将运行所有单元测试，验证各个模块的功能。

### 4. 生成 Verilog

```bash
make verilog
```

生成的 Verilog 文件位于 `generated/` 目录。

### 5. 构建固件

```bash
make firmware
```

这将编译示例固件程序。

## 详细教程

### 运行特定测试

```bash
# 测试寄存器堆
make test-core.RegFileTest

# 测试 ALU
make test-core.ALUTest

# 测试 UART
make test-peripherals.UartTest

# 测试 GPIO
make test-peripherals.GpioTest
```

### 查看测试波形

测试生成的 VCD 波形文件在 `test_run_dir/` 目录：

```bash
gtkwave test_run_dir/*/RegFileTest.vcd
```

### 构建软件

#### Bootloader

```bash
cd software/bootloader
make
ls -lh bootloader.bin
```

#### Firmware

```bash
cd software/firmware
make
ls -lh firmware.bin firmware.lst
```

查看反汇编：
```bash
less firmware.lst
```

### Verilog 仿真

```bash
make sim
```

（注：需要实现 Verilator 仿真脚本）

### FPGA 综合

完整的 FPGA 构建流程：

```bash
# 1. 生成 Verilog
make verilog

# 2. 运行 Vivado 综合
make fpga
```

综合报告位于 `fpga/scripts/vivado_project/`。

## 项目结构详解

```
RISCV32/
├── src/
│   ├── main/scala/
│   │   ├── common/         # 通用配置和定义
│   │   ├── core/           # 处理器核心
│   │   │   ├── RegFile.scala
│   │   │   ├── ALU.scala
│   │   │   ├── IF.scala, ID.scala, EX.scala, MEM.scala
│   │   │   ├── Hazard.scala
│   │   │   └── Core.scala
│   │   ├── bus/            # 总线互连
│   │   │   └── Wishbone.scala
│   │   ├── peripherals/    # 外设控制器
│   │   │   ├── SpiFlash.scala
│   │   │   ├── Uart.scala
│   │   │   ├── Gpio.scala
│   │   │   ├── I2c.scala
│   │   │   ├── SpiMaster.scala
│   │   │   └── Ram.scala
│   │   └── soc/            # SoC 顶层
│   │       └── RV32ESoC.scala
│   └── test/scala/         # 测试代码
│       ├── core/
│       ├── peripherals/
│       └── soc/
├── software/
│   ├── bootloader/         # SPI Flash 启动加载器
│   ├── firmware/           # 示例固件程序
│   └── rtthread/           # RT-Thread 移植
├── fpga/
│   ├── constraints/        # FPGA 约束文件
│   └── scripts/            # 综合脚本
├── docs/                   # 文档
├── build.sc               # Mill 构建配置
├── Makefile               # 主 Makefile
└── README.md              # 项目说明
```

## 常见任务

### 修改系统配置

编辑 `src/main/scala/common/Config.scala`：

```scala
object Config {
  val XLEN = 32              // 数据宽度
  val REG_NUM = 16           // 寄存器数量 (RV32E)
  val PC_RESET = 0x10000000L // 复位 PC 地址
  val SYS_CLK_FREQ = 50000000 // 系统时钟频率

  // 修改波特率
  val UART_DEFAULT_BAUD = 115200

  // 修改 RAM 大小
  val RAM_SIZE = 64 * 1024   // 64 KB
}
```

### 添加新的外设

1. 在 `src/main/scala/peripherals/` 创建新文件
2. 实现 Wishbone 从设备接口
3. 在 `Config.scala` 中添加地址映射
4. 在 `RV32ESoC.scala` 中实例化并连接
5. 编写单元测试

### 添加新的指令

1. 修改 `Decoder.scala` 添加指令译码
2. 修改 `ALU.scala` 添加 ALU 操作（如需要）
3. 更新 `ControlSignals` Bundle
4. 编写测试验证

### 调试技巧

#### 启用调试输出

在 `Config.scala` 中：
```scala
val DEBUG_ENABLE = true
```

#### 查看寄存器值

在测试中访问调试接口：
```scala
if (dut.io.debug.isDefined) {
  val regs = dut.io.debug.get.regs
  println(s"x1 = ${regs(1).peek()}")
}
```

#### 使用 ChiselTest printf

```scala
printf("PC = 0x%x, inst = 0x%x\n", io.pc, io.inst)
```

#### 生成 VCD 波形

```scala
test(new MyModule).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
  // 测试代码
}
```

## 性能优化

### 编译优化

```bash
# 使用优化选项
JAVA_OPTS="-Xmx4G -Xss2M" make compile
```

### 并行测试

```bash
mill rv32e_soc.test -j 4
```

### Verilog 生成优化

在 `build.sc` 中调整 Chisel 选项：
```scala
def chiselOpts = Seq(
  "--target-dir", "generated",
  "--emission-options", "disableMemRandomization,disableRegisterRandomization"
)
```

## 下一步

- 阅读 [架构文档](ARCHITECTURE.md) 了解设计细节
- 查看 [FPGA 部署指南](../fpga/README.md)
- 尝试 [RT-Thread 移植](../software/rtthread/README.md)
- 参与 [贡献指南](../CONTRIBUTING.md)

## 故障排除

### 问题：编译失败 "symbol not found"

**解决**：清理并重新编译
```bash
make clean
make compile
```

### 问题：测试超时

**解决**：增加超时时间，在测试中：
```scala
dut.clock.setTimeout(10000)
```

### 问题：Verilog 生成失败

**解决**：检查 Chisel 版本和语法
```bash
mill clean
mill rv32e_soc.compile
mill rv32e_soc.verilog
```

### 问题：FPGA 综合时序不收敛

**解决**：
1. 降低目标频率
2. 检查关键路径
3. 添加流水线寄存器

## 获取帮助

- **文档**: `docs/` 目录
- **示例**: `software/firmware/` 目录
- **Issue**: https://github.com/szhwmaker-cmyk/RISCV32/issues
- **讨论**: https://github.com/szhwmaker-cmyk/RISCV32/discussions

## 许可证

本项目采用 MIT 许可证。详见 [LICENSE](../LICENSE) 文件。
