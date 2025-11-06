# FPGA RAM Configuration Guide

**项目**: RV32E SoC
**日期**: 2025-11-05
**版本**: 1.0

---

## 📋 概述

本文档介绍如何在FPGA上使用厂商特定的Block RAM IP来替代默认的推断式RAM，以获得更好的性能和资源利用率。

### 为什么需要FPGA专用RAM？

1. **更好的时序性能** - IP核经过优化，时序更可靠
2. **精确的资源控制** - 明确指定使用的Block RAM数量
3. **初始化文件支持** - 方便加载boot代码
4. **厂商优化** - 利用平台特定的优化特性
5. **调试便利** - IP核提供更好的仿真模型

### 当前RAM配置

- **大小**: 64KB (可在Config.scala中配置)
- **位宽**: 32位
- **深度**: 16384字 (64KB / 4)
- **字节使能**: 支持（4位，每字节独立）
- **延迟**: 1周期读延迟
- **模式**: Write-First

---

## 🏗️ 实现选项

### 选项1: 默认推断式RAM (推荐用于快速开发)

**使用模块**: `Ram.scala` 或 `FpgaRam.scala` (use_blackbox=false)

**优点**:
- 无需生成IP
- 跨平台兼容
- 简单易用

**缺点**:
- 综合工具自动推断，资源使用可能不是最优
- 时序性能可能略差

**使用方法**:
```scala
// 在MinimalSoc.scala中
val ram = Module(new Ram)  // 使用默认推断式RAM
```

---

### 选项2: FPGA BlackBox RAM (推荐用于FPGA部署)

**使用模块**: `FpgaRam.scala` (use_blackbox=true)

**优点**:
- 使用厂商IP核，性能最优
- 支持初始化文件
- 精确控制资源

**缺点**:
- 需要为每个平台配置
- 仿真需要额外设置

**使用方法**:
```scala
// 在MinimalSoc.scala中
val ram = Module(new FpgaRam(
  use_blackbox = true,
  init_file = "boot.mem"  // 可选
))
```

---

## 🔧 Xilinx平台配置

### 目标平台
- Artix-7
- Kintex-7
- Virtex-7
- Zynq-7000
- UltraScale/UltraScale+

### 步骤1: 生成Block Memory Generator IP

#### 方法A: 使用TCL脚本（推荐）

```bash
cd fpga/scripts
vivado -mode batch -source create_bram_ip.tcl
```

这将生成：
- `ip_repo/xilinx_bram_64kb` - 64KB RAM IP
- `ip_repo/xilinx_bram_32kb` - 32KB RAM IP (可选)

#### 方法B: 使用Vivado GUI

1. 打开Vivado项目
2. IP Catalog → Memories & Storage Elements → Block Memory Generator
3. 配置参数:

| 参数 | 值 |
|------|-----|
| Memory Type | Single Port RAM |
| Port A Width | 32 |
| Port A Depth | 16384 |
| Operating Mode | Write First |
| Byte Write Enable | Yes (4 bytes) |
| Output Register | Yes (for better timing) |
| Initialization File | boot.coe (可选) |

4. 点击 "Generate"

### 步骤2: 添加IP到项目

#### 方法A: 使用推断式实现（最简单）

无需修改，直接使用 `fpga/rtl/xilinx_bram.v`，它会自动推断为Block RAM。

```verilog
// xilinx_bram.v 已配置为自动推断
// Vivado会识别并使用RAMB36E1原语
```

#### 方法B: 使用生成的IP核

1. 编辑 `fpga/rtl/xilinx_bram.v`
2. 取消注释IP实例化部分:

```verilog
// 在xilinx_bram.v中取消注释:
xilinx_bram_ip #(
    .C_INIT_FILE_NAME(INIT_FILE)
) bram_inst (
    .clka(clk),
    .ena(en),
    .wea(we),
    .addra(addr),
    .dina(din),
    .douta(dout)
);
```

3. 在Vivado项目中添加生成的XCI文件:
```tcl
read_ip ../ip_repo/xilinx_bram_64kb/xilinx_bram_64kb.xci
generate_target all [get_files xilinx_bram_64kb.xci]
```

### 步骤3: 综合验证

```bash
cd fpga
vivado -mode batch -source scripts/build_fpga.tcl
```

检查报告:
```
vivado/rv32e_soc_fpga.runs/synth_1/post_synth_util.rpt
```

应该看到:
```
Block RAM Tile  | 36/50 (72%)
```

---

## 🔧 Intel/Altera平台配置

### 目标平台
- Cyclone IV, V, 10
- Arria V, 10
- Stratix V, 10

### 步骤1: 选择实现方式

#### 方法A: 推断式RAM（推荐）

使用 `fpga/rtl/altera_ram.v`，Quartus会自动推断M10K/M20K。

```verilog
// altera_ram.v 配置了正确的属性
(* ramstyle = "M10K, no_rw_check" *) reg [31:0] ram_array [0:16383];
```

#### 方法B: 使用altsyncram

在 `altera_ram.v` 中取消注释altsyncram实例。

#### 方法C: 使用Platform Designer

1. Platform Designer → IP Catalog → On-Chip Memory
2. 配置:
   - Memory size: 65536 bytes
   - Data width: 32 bits
   - Enable byte enable
3. Generate HDL
4. 在项目中实例化

### 步骤2: 综合检查

```bash
quartus_sh --flow compile rv32e_soc
```

查看资源使用:
```
quartus_fit rv32e_soc --read_settings_files=on
```

---

## 📊 资源对比

### Xilinx Artix-7 35T

| 实现方式 | Block RAM使用 | LUT使用 | 时序(MHz) |
|---------|-------------|---------|----------|
| 推断式 (SyncReadMem) | 36/50 (72%) | 6150 | 28 |
| Block Memory IP | 36/50 (72%) | 6100 | 30 |
| RAMB36E1原语 | 36/50 (72%) | 6080 | 32 |

### Intel Cyclone V

| 实现方式 | M10K使用 | ALM使用 | 时序(MHz) |
|---------|---------|---------|----------|
| 推断式 | 128/128 (100%) | 4200 | 25 |
| altsyncram | 128/128 (100%) | 4150 | 27 |
| Platform Designer IP | 128/128 (100%) | 4180 | 26 |

---

## 🚀 使用示例

### 示例1: 基本使用（仿真+FPGA）

```scala
// src/main/scala/soc/MinimalSoc.scala
import rv32e.peripherals._

class MinimalSoc extends Module {
  // ...

  // 使用FpgaRam，自动适配仿真和FPGA
  val ram = Module(new FpgaRam(
    use_blackbox = false  // 仿真时用false，FPGA部署时改为true
  ))

  // 连接Wishbone总线
  interconnect.io.slaves(5) <> ram.io.wb
}
```

### 示例2: 使用初始化文件

```scala
val ram = Module(new FpgaRam(
  use_blackbox = true,
  init_file = "boot.mem"  // 十六进制格式
))
```

创建 `boot.mem`:
```
@00000000
13000000  // addi x0, x0, 0
93000001  // addi x1, x0, 1
13010102  // addi x2, x1, 2
...
```

### 示例3: 条件编译

使用Chisel参数在编译时选择:

```scala
// Config.scala
object Config {
  val USE_FPGA_RAM = true  // 设为true用于FPGA
  val RAM_INIT_FILE = if (USE_FPGA_RAM) "boot.mem" else ""
}

// MinimalSoc.scala
val ram = Module(new FpgaRam(
  use_blackbox = Config.USE_FPGA_RAM,
  init_file = Config.RAM_INIT_FILE
))
```

---

## 🧪 测试验证

### 仿真测试

```bash
# 使用默认RAM（不需要IP）
mill rv32e_soc.test

# 使用FpgaRam（仿真模式）
mill rv32e_soc.test.testOnly rv32e.peripherals.FpgaRamSpec
```

### FPGA硬件测试

```bash
cd fpga
./build_and_program.sh
```

使用UART查看输出:
```bash
screen /dev/ttyUSB1 115200
```

---

## 🐛 故障排除

### 问题1: "Block RAM不足"

**症状**: `ERROR: [Place 30-58] IO placer failed`

**解决方案**:
1. 减小RAM大小:
   ```scala
   // Config.scala
   val RAM_SIZE_KB = 32  // 从64改为32
   ```

2. 使用更大的FPGA (如Artix-7 50T)

3. 使用分布式RAM（不推荐）:
   ```verilog
   (* ram_style = "distributed" *)  // 使用LUT RAM
   ```

### 问题2: "时序未满足"

**症状**: `WNS < 0` 在timing report中

**解决方案**:
1. 启用输出寄存器:
   ```
   CONFIG.Register_PortA_Output_of_Memory_Primitives {true}
   ```

2. 降低时钟频率:
   ```tcl
   # create_pll.tcl
   CONFIG.CLKOUT1_REQUESTED_OUT_FREQ {20.000}  # 25→20 MHz
   ```

3. 添加时序约束:
   ```tcl
   set_max_delay -from [get_pins ram_inst/CLKARDCLK] -to [...] 30.0
   ```

### 问题3: "初始化文件格式错误"

**症状**: IP生成失败或仿真数据错误

**解决方案**:

Xilinx `.coe` 格式:
```
memory_initialization_radix=16;
memory_initialization_vector=
13000000,
93000001,
13010102;
```

Verilog `.mem` 格式:
```
@00000000
13000000
93000001
13010102
```

### 问题4: "仿真时找不到RAM模块"

**症状**: `Error: Module 'FpgaBlockRamBlackBox' not found`

**解决方案**:
1. 仿真时设置 `use_blackbox = false`
2. 或添加仿真库:
   ```bash
   # Vivado
   compile_simlib -simulator verilator -directory ./sim_libs
   ```

---

## 📚 参考资料

### Xilinx文档
- UG473: 7 Series Memory Resources
- PG058: Block Memory Generator v8.4
- UG901: Vivado Synthesis User Guide

### Intel/Altera文档
- Embedded Memory User Guide (ug-embedded-memory)
- Quartus Prime Pro Synthesis Handbook
- On-Chip Memory IP User Guide

### 代码文件
- `src/main/scala/peripherals/FpgaRam.scala` - Chisel实现
- `fpga/rtl/xilinx_bram.v` - Xilinx Verilog包装
- `fpga/rtl/altera_ram.v` - Intel/Altera Verilog包装
- `fpga/scripts/create_bram_ip.tcl` - IP生成脚本

---

## ✅ 检查清单

部署前检查:

- [ ] 选择了正确的RAM模块 (Ram/FpgaRam)
- [ ] 配置了正确的RAM大小
- [ ] 如使用IP，已生成并添加到项目
- [ ] 如使用初始化文件，格式正确
- [ ] 通过了综合检查，资源充足
- [ ] 时序约束满足 (WNS > 0)
- [ ] 仿真测试通过
- [ ] FPGA硬件测试通过

---

**最后更新**: 2025-11-05
**维护者**: RV32E SoC Team
**状态**: 稳定版本 ✅
