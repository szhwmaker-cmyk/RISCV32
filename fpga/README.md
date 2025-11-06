# RV32E SoC FPGA 部署文档

本文档介绍如何将 RV32E SoC 部署到 FPGA 开发板上。

## 支持的 FPGA 平台

### Xilinx 系列
- **Artix-7** (XC7A35T, XC7A100T)
- **Spartan-7** (XC7S50, XC7S100)
- **Zynq-7000** (XC7Z010, XC7Z020)

### Lattice 系列
- **ECP5** (LFE5U-25F, LFE5U-85F)
- **iCE40 UltraPlus** (iCE40UP5K)

### Gowin 系列
- **GW1N** (GW1NR-9, GW1N-4)

## 资源使用估算

### Xilinx Artix-7 (XC7A35T)
| 资源 | 使用量 | 可用量 | 利用率 |
|------|--------|--------|--------|
| LUT | ~3000 | 20800 | 14% |
| FF | ~2000 | 41600 | 5% |
| BRAM | ~10 | 50 | 20% |
| DSP | 0 | 90 | 0% |
| IO | ~30 | 106 | 28% |

### 时序
- **最大频率**: 60-80 MHz (取决于布局布线)
- **目标频率**: 50 MHz
- **典型 WNS**: +5 ns @ 50 MHz

## 硬件连接

### 引脚分配

#### UART (串口)
| 信号 | 方向 | 描述 |
|------|------|------|
| uart_tx | 输出 | UART 发送 |
| uart_rx | 输入 | UART 接收 |

#### SPI Flash
| 信号 | 方向 | 描述 |
|------|------|------|
| spi_sck | 输出 | SPI 时钟 |
| spi_cs_n | 输出 | 片选（低有效） |
| spi_mosi | 输出 | 主出从入 |
| spi_miso | 输入 | 主入从出 |

#### GPIO
| 信号 | 方向 | 描述 |
|------|------|------|
| gpio[15:0] | 双向 | 通用 I/O |

#### 系统
| 信号 | 方向 | 描述 |
|------|------|------|
| clk | 输入 | 系统时钟 (50 MHz) |
| rst_n | 输入 | 复位（低有效） |

## Vivado 综合流程

### 1. 生成 Verilog

```bash
cd /path/to/RISCV32
mill rv32e_soc.verilog
```

生成的 Verilog 文件在 `generated/` 目录下。

### 2. 创建 Vivado 项目

```bash
cd fpga/vivado
vivado -mode batch -source create_project.tcl
```

或手动创建：

1. 启动 Vivado
2. 创建新项目
3. 选择目标器件 (如 XC7A35T-CPG236-1)
4. 添加源文件：
   - RTL: `generated/*.v`
   - 约束: `constraints/rv32e_soc_artix7.xdc`
5. 设置顶层模块: `RV32ESoC`

### 3. 综合和实现

```tcl
# 综合
launch_runs synth_1
wait_on_run synth_1

# 实现
launch_runs impl_1 -to_step write_bitstream
wait_on_run impl_1

# 生成比特流
open_run impl_1
write_bitstream -force rv32e_soc.bit
```

或使用 Makefile：

```bash
make vivado
```

### 4. 下载到 FPGA

```bash
# 使用 Vivado
vivado -mode batch -source program.tcl

# 或使用 xc3sprog (开源工具)
xc3sprog -c ftdi rv32e_soc.bit
```

## Quartus Prime 综合流程（Intel/Altera）

虽然主要支持 Xilinx，但也可以移植到 Intel FPGA：

### 1. 创建项目

```bash
cd fpga/quartus
quartus_sh -t create_project.tcl
```

### 2. 综合和适配

```bash
quartus_map rv32e_soc
quartus_fit rv32e_soc
quartus_asm rv32e_soc
```

### 3. 下载

```bash
quartus_pgm -c USB-Blaster -m JTAG -o "p;rv32e_soc.sof"
```

## 时钟配置

### Xilinx 时钟向导

如果输入时钟不是 50 MHz，需要使用 MMCM/PLL：

```verilog
module ClockWizard (
    input  wire clk_in,    // 输入时钟 (如 100 MHz)
    input  wire reset,
    output wire clk_out,   // 输出 50 MHz
    output wire locked
);

    // Xilinx MMCM 配置
    // ...

endmodule
```

### 约束文件中的时钟定义

```tcl
# 输入时钟
create_clock -period 20.000 -name sys_clk [get_ports clk]

# 时钟不确定性
set_clock_uncertainty 0.500 [get_clocks sys_clk]

# 输入延迟
set_input_delay -clock sys_clk -max 5.000 [all_inputs]
set_input_delay -clock sys_clk -min 1.000 [all_inputs]

# 输出延迟
set_output_delay -clock sys_clk -max 5.000 [all_outputs]
set_output_delay -clock sys_clk -min 1.000 [all_outputs]

# 伪路径（异步信号）
set_false_path -from [get_ports rst_n]
```

## 调试

### ILA (Integrated Logic Analyzer)

在关键信号上插入 ILA 进行调试：

```tcl
# 创建 ILA
create_debug_core u_ila_0 ila
set_property C_DATA_DEPTH 4096 [get_debug_cores u_ila_0]
set_property C_TRIGIN_EN false [get_debug_cores u_ila_0]
set_property C_TRIGOUT_EN false [get_debug_cores u_ila_0]

# 添加探针
connect_debug_port u_ila_0/clk [get_nets clk]
set_property port_width 32 [get_debug_ports u_ila_0/probe0]
connect_debug_port u_ila_0/probe0 [get_nets {pc[*]}]
```

### ChipScope/SignalTap

对于 Intel FPGA，使用 SignalTap II：

```tcl
set_global_assignment -name ENABLE_SIGNALTAP ON
set_global_assignment -name USE_SIGNALTAP_FILE stp1.stp
```

## 串口连接

### 波特率配置
- 默认：115200 bps
- 数据位：8
- 停止位：1
- 校验：无
- 流控：无

### 终端软件
- **Linux**: minicom, screen, putty
- **Windows**: PuTTY, Tera Term
- **macOS**: screen, minicom

示例（Linux）：
```bash
sudo minicom -D /dev/ttyUSB0 -b 115200
# 或
sudo screen /dev/ttyUSB0 115200
```

## 编程 SPI Flash

将程序烧录到 SPI Flash 以实现上电自启动：

### 使用 Vivado

1. 生成 MCS 文件：
```tcl
write_cfgmem -format mcs -size 16 -interface SPIx4 \
    -loadbit "up 0x00000000 rv32e_soc.bit" \
    -loaddata "up 0x00800000 firmware.bin" \
    -file rv32e_soc.mcs
```

2. 烧录到 Flash：
```tcl
program_hw_cfgmem -hw_cfgmem [get_hw_cfgmems *] -mcsfile rv32e_soc.mcs
```

### 使用 flashrom (开源)

```bash
flashrom -p ft2232_spi:type=2232H -w firmware.bin
```

## 常见问题

### Q1: 时序不收敛怎么办？

A: 尝试以下方法：
1. 降低目标频率
2. 添加流水线寄存器
3. 使用更快的速度等级器件
4. 优化关键路径

### Q2: 资源不足？

A: 优化策略：
1. 减少 FIFO 深度
2. 禁用不需要的外设
3. 使用 BRAM 而非 LUT RAM
4. 选择更大的 FPGA

### Q3: UART 无输出？

A: 检查：
1. 波特率设置是否正确
2. TX/RX 引脚是否接反
3. 电平标准（3.3V/5V）
4. 复位信号是否正常

### Q4: SPI Flash 启动失败？

A: 验证：
1. Flash 是否正确烧录
2. SPI 时序是否满足
3. 地址映射是否正确
4. Bootloader 是否正常

## 性能调优

### 提高频率

1. **寄存器重定时**
```tcl
set_property PHYS_OPT_DESIGN.IS_ENABLED true [current_design]
```

2. **布局优化**
```tcl
place_design -directive ExtraNetDelay_high
route_design -directive AggressiveExplore
```

### 降低功耗

1. **时钟门控**
```verilog
assign clk_gated = clk & enable;
```

2. **电源优化**
```tcl
set_property POWER_OPT_DESIGN.IS_ENABLED true [current_design]
```

## 参考资料

- [Vivado 用户指南](https://www.xilinx.com/support/documentation.html)
- [Artix-7 数据手册](https://www.xilinx.com/support/documentation/data_sheets/ds181_Artix_7_Data_Sheet.pdf)
- [RISC-V 规范](https://riscv.org/specifications/)

## 技术支持

如有问题，请提交 Issue：
https://github.com/szhwmaker-cmyk/RISCV32/issues
