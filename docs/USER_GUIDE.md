# RV32E SoC User Guide
# 用户指南

## 目录

1. [快速开始](#快速开始)
2. [构建项目](#构建项目)
3. [仿真测试](#仿真测试)
4. [编写固件](#编写固件)
5. [FPGA部署](#fpga部署)
6. [调试技巧](#调试技巧)
7. [常见问题](#常见问题)

---

## 快速开始

### 环境准备

**必需工具**:
- Java 8+ (用于Mill构建系统)
- Mill 0.11+ (Scala构建工具)
- RISC-V GCC工具链 (可选，用于编译固件)
- Vivado 2019.2+ (可选，用于FPGA综合)

**安装Mill**:
```bash
curl -L https://github.com/com-lihaoyi/mill/releases/download/0.11.6/0.11.6 > mill
chmod +x mill
sudo mv mill /usr/local/bin/
```

**克隆项目**:
```bash
git clone <repository-url>
cd RISCV32
git checkout claude/rv32e-processor-design-011CUuXqQSNoUng4aUnu5GMB
```

---

## 构建项目

### 编译Chisel代码

```bash
# 编译所有Scala/Chisel源文件
mill rv32e.compile

# 查看编译结果
ls out/rv32e/compile.dest/
```

### 运行测试

```bash
# 运行所有测试
mill rv32e.test

# 运行特定测试
mill rv32e.test.testOnly core.ALUTest
mill rv32e.test.testOnly core.RegFileTest
mill rv32e.test.testOnly core.ImmGenTest
mill rv32e.test.testOnly core.BranchUnitTest
```

### 生成Verilog

生成可综合的Verilog代码需要添加生成脚本。创建 `GenerateVerilog.scala`:

```scala
import chisel3._
import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}
import soc.RV32ESoC

object GenerateVerilog extends App {
  (new ChiselStage).execute(
    Array("-X", "verilog", "-td", "generated"),
    Seq(ChiselGeneratorAnnotation(() => new RV32ESoC()))
  )
}
```

然后运行:
```bash
mill rv32e.runMain GenerateVerilog
```

生成的Verilog文件将位于 `generated/` 目录。

---

## 仿真测试

### ChiselTest仿真

ChiselTest提供了内置的波形查看和测试框架。

**示例测试**:
```scala
class SimpleTest extends AnyFlatSpec with ChiselScalatestTester {
  "Core" should "execute ADD instruction" in {
    test(new RV32ECore()) { dut =>
      // 设置测试场景
      dut.io.imem_data.poke("h00208093".U)  // ADDI x1, x1, 2
      dut.clock.step(5)

      // 验证结果
      // ...
    }
  }
}
```

### Verilator仿真

对于更高性能的仿真，可以使用Verilator:

```bash
# 生成C++仿真器
verilator --cc --exe --build -j 0 \
  --top-module RV32ESoC \
  generated/RV32ESoC.v \
  sim/sim_main.cpp

# 运行仿真
./obj_dir/VRV32ESoC
```

---

## 编写固件

### 工具链安装

```bash
# Ubuntu/Debian
sudo apt-get install gcc-riscv64-unknown-elf

# 或从源码编译
git clone https://github.com/riscv/riscv-gnu-toolchain
cd riscv-gnu-toolchain
./configure --prefix=/opt/riscv --with-arch=rv32e --with-abi=ilp32e
make
```

### 编译固件

项目提供了示例固件在 `firmware/` 目录:

**Makefile示例**:
```makefile
CROSS = riscv32-unknown-elf-
CC = $(CROSS)gcc
AS = $(CROSS)as
LD = $(CROSS)ld
OBJCOPY = $(CROSS)objcopy

CFLAGS = -march=rv32e -mabi=ilp32e -O2 -Wall
LDFLAGS = -T linker.ld -nostdlib

all: firmware.bin

firmware.elf: bootloader.o main.o
\t$(LD) $(LDFLAGS) -o $@ $^

firmware.bin: firmware.elf
\t$(OBJCOPY) -O binary $< $@

%.o: %.S
\t$(AS) -march=rv32e -o $@ $<

%.o: %.c
\t$(CC) $(CFLAGS) -c -o $@ $<

clean:
\trm -f *.o *.elf *.bin
```

**编译固件**:
```bash
cd firmware
make
```

### 加载固件到仿真

生成的 `firmware.bin` 可以转换为十六进制格式加载到指令存储器:

```bash
# 转换为十六进制
hexdump -v -e '1/4 "%08x\\n"' firmware.bin > firmware.hex
```

在测试代码中加载:
```scala
// 从文件加载指令
val mem = Module(new InstructionMemory(4096))
loadMemoryFromFile(mem, "firmware.hex")
```

---

## FPGA部署

### Vivado项目

项目包含Vivado TCL脚本用于自动化综合和实现。

**目标开发板**: Arty A7-35T (或其他Xilinx 7系列板)

### 综合流程

1. **生成Verilog**:
```bash
mill rv32e.runMain GenerateVerilog
```

2. **运行综合**:
```bash
cd fpga/scripts
vivado -mode batch -source synthesis.tcl
```

3. **运行实现**:
```bash
vivado -mode batch -source implementation.tcl
```

4. **查看报告**:
```bash
cd ../vivado_project
cat post_route_timing_summary.rpt
cat post_route_utilization.rpt
```

### 资源使用估计

对于Xilinx XC7A35T (Arty A7-35T):

| 资源类型 | 使用量 | 可用量 | 使用率 |
|---------|-------|-------|--------|
| LUT | ~8000 | 20800 | ~38% |
| FF | ~5000 | 41600 | ~12% |
| BRAM | ~20 | 50 | ~40% |
| DSP | 0 | 90 | 0% |

### 烧录bitstream

```bash
# 使用Vivado Hardware Manager
vivado -mode tcl
open_hw
connect_hw_server
open_hw_target
program_hw_devices -file vivado_project/rv32e_soc.bit
```

或使用命令行:
```bash
openocd -f board/arty_s7.cfg \
  -c "init; pld load 0 rv32e_soc.bit; exit"
```

---

## 调试技巧

### 硬件调试

#### 1. 使用ILA (Integrated Logic Analyzer)

在关键信号上插入ILA核心:

```tcl
# 在synthesis.tcl中添加
create_debug_core u_ila_0 ila
set_property C_DATA_DEPTH 1024 [get_debug_cores u_ila_0]
connect_debug_port u_ila_0/clk [get_nets clk]
connect_debug_port u_ila_0/probe0 [get_nets debug_pc*]
connect_debug_port u_ila_0/probe1 [get_nets debug_inst*]
```

#### 2. UART调试输出

在固件中添加调试打印:

```c
void debug_print_hex(uint32_t value) {
    uart_puts("DEBUG: 0x");
    for (int i = 7; i >= 0; i--) {
        uint8_t nibble = (value >> (i * 4)) & 0xF;
        uart_putc(nibble < 10 ? '0' + nibble : 'A' + nibble - 10);
    }
    uart_puts("\\r\\n");
}
```

#### 3. GPIO调试LED

使用GPIO作为状态指示:

```c
#define DEBUG_LED_OK    (1 << 0)
#define DEBUG_LED_ERROR (1 << 1)
#define DEBUG_LED_BUSY  (1 << 2)

void set_debug_status(uint8_t status) {
    GPIO_OUT = (GPIO_OUT & 0xFFF8) | status;
}
```

### 软件调试

#### 使用OpenOCD和GDB

```bash
# 终端1: 启动OpenOCD
openocd -f interface/ftdi/digilent-hs1.cfg -f board/arty_s7.cfg

# 终端2: 启动GDB
riscv32-unknown-elf-gdb firmware.elf
(gdb) target remote :3333
(gdb) load
(gdb) break main
(gdb) continue
```

---

## 常见问题

### Q: 编译失败，提示找不到Mill

A: 确保Mill已正确安装并在PATH中:
```bash
which mill
mill --version
```

### Q: 测试失败，ChiselTest报错

A: 检查测试依赖和Chisel版本:
```bash
mill rv32e.test.ivyDeps
# 应显示 chiseltest:6.0.0
```

### Q: FPGA综合时序不满足

A: 尝试以下方法:
1. 降低目标时钟频率（修改约束文件）
2. 增加流水线级数
3. 使用时序约束引导优化
4. 检查关键路径并优化

### Q: UART无输出

A: 检查清单:
1. 波特率设置是否正确
2. TX/RX引脚是否正确连接
3. UART控制寄存器是否启用
4. 时钟分频器是否正确配置

### Q: 如何添加新外设？

A: 步骤:
1. 在 `rv32e/src/peripherals/` 创建新模块
2. 实现Wishbone Slave接口
3. 在 `SoCAddressMaps` 添加地址映射
4. 在 `RV32ESoC` 实例化并连接
5. 更新REGISTER_MAP.md文档

### Q: 支持哪些RISC-V指令？

A: 当前支持RV32I基础指令集：
- 算术/逻辑: ADD, SUB, AND, OR, XOR, SLT, SLTU
- 立即数: ADDI, ANDI, ORI, XORI, SLTI, SLTIU
- 移位: SLL, SRL, SRA, SLLI, SRLI, SRAI
- 加载/存储: LB, LH, LW, LBU, LHU, SB, SH, SW
- 分支: BEQ, BNE, BLT, BGE, BLTU, BGEU
- 跳转: JAL, JALR
- 上位立即数: LUI, AUIPC

不支持: M扩展(乘除法), A扩展(原子操作), F/D扩展(浮点)

---

## 性能优化建议

### 软件优化

1. **使用-O2或-O3编译**: 启用编译器优化
2. **减少函数调用开销**: 内联小函数
3. **利用寄存器**: RV32E有16个寄存器，合理分配
4. **减少内存访问**: 使用局部变量

### 硬件优化

1. **流水线优化**: 减少冒险发生
2. **缓存实现**: 添加I-cache和D-cache
3. **分支预测**: 实现更智能的分支预测器
4. **总线仲裁**: 优化多Master总线访问

---

## 资源链接

- **RISC-V规范**: https://riscv.org/technical/specifications/
- **Chisel文档**: https://www.chisel-lang.org/chisel3/docs/introduction.html
- **Wishbone规范**: https://cdn.opencores.org/downloads/wbspec_b4.pdf
- **项目仓库**: https://github.com/szhwmaker-cmyk/RISCV32

---

**版本**: 1.0
**最后更新**: 2025-11-08
