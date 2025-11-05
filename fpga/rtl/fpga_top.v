# FPGA部署指南 - RV32E SoC

**目标**：将RV32E SoC从Chisel仿真部署到真实FPGA硬件

---

## 📋 概述

### 当前状态
- ✅ Chisel HDL 设计完成
- ✅ ChiselTest 仿真验证
- ✅ RT-Thread 软件框架
- ⏳ **待完成：FPGA硬件部署**

### FPGA部署流程
```
Chisel代码 → Verilog生成 → 约束添加 → 综合 → 实现 → 比特流 → 下载 → 测试
```

---

## 🎯 第一步：选择FPGA开发板

### 推荐开发板

#### 1. **Xilinx Artix-7系列（推荐）**

**Digilent Arty A7-35T/100T**
- ✅ 价格：$129-$229
- ✅ FPGA：XC7A35T/100T
- ✅ 逻辑单元：33,280 / 101,440
- ✅ 内存：1,800 Kb / 4,860 Kb BRAM
- ✅ 时钟：100 MHz晶振
- ✅ 外设：4x按键，4x开关，4x LED，UART USB，SPI Flash
- ✅ 优势：性价比高，资源充足，社区支持好

**Nexys A7 (Nexys 4 DDR)**
- ✅ 价格：$299
- ✅ FPGA：XC7A100T
- ✅ 外设更丰富：16x开关，16x LED，5x按键，UART，SPI Flash，DDR2
- ✅ 适合教学和开发

#### 2. **Lattice ECP5系列（开源友好）**

**Colorlight i5**
- ✅ 价格：~$20
- ✅ FPGA：LFE5U-25F
- ✅ 优势：支持开源工具链（yosys + nextpnr）
- ⚠️ 资源有限，需要精简设计

**ULX3S**
- ✅ 价格：$130
- ✅ FPGA：LFE5U-85F
- ✅ 资源充足，支持开源工具

#### 3. **Intel/Altera Cyclone系列**

**DE0-Nano/DE10-Lite**
- ✅ 价格：$99-$150
- ✅ FPGA：Cyclone IV/V
- ✅ 外设：LED，按键，UART，SDRAM
- ✅ Quartus工具链

### 资源需求估算

**RV32E SoC 预计资源使用**（基于类似设计）：

| 资源 | 估算用量 | Arty A7-35T | Arty A7-100T |
|------|---------|-------------|--------------|
| LUT | ~5,000 | 20,800 (24%) | 63,400 (8%) |
| FF | ~3,000 | 41,600 (7%) | 126,800 (2%) |
| BRAM | ~20 | 50 (40%) | 135 (15%) |
| DSP | 0 | 90 (0%) | 240 (0%) |

**结论**：Arty A7-35T 足够，A7-100T 有很大余量。

---

## 🔧 第二步：生成Verilog代码

### 2.1 从Chisel生成Verilog

```bash
cd /home/user/RISCV32

# 生成 Verilog
mill rv32e.runMain circt.stage.ChiselMain \
  --module rv32e.soc.MinimalSoc \
  --target-dir fpga/generated

# 检查生成的文件
ls fpga/generated/
# 预期：MinimalSoc.v, MinimalSoc.fir
```

### 2.2 检查生成的Verilog

```bash
# 查看顶层模块
head -50 fpga/generated/MinimalSoc.v

# 检查端口定义
grep "module MinimalSoc" fpga/generated/MinimalSoc.v
grep "input\|output" fpga/generated/MinimalSoc.v | head -20
```

### 2.3 预期的顶层接口

```verilog
module MinimalSoc(
  input         clock,
  input         reset,
  // UART
  output        io_uart_tx,
  input         io_uart_rx,
  // GPIO
  input  [15:0] io_gpio_in,
  output [15:0] io_gpio_out,
  output [15:0] io_gpio_oe,
  // SPI Flash
  output        io_spi_cs_n,
  output        io_spi_sck,
  output        io_spi_mosi,
  input         io_spi_miso
);
```

---

## 🎨 第三步：创建FPGA顶层包装

Chisel生成的模块需要一个FPGA特定的顶层包装。

### 3.1 创建顶层模块

<function_calls>
<invoke name="Write">
<parameter name="content">//
// FPGA Top-Level Module for RV32E SoC
// Target: Xilinx Arty A7
//

module fpga_top (
    // Clock and Reset
    input  wire         clk_100mhz,     // 100 MHz on-board clock
    input  wire         btn_reset_n,    // Reset button (active low)

    // UART (USB-UART on Arty)
    output wire         uart_txd,
    input  wire         uart_rxd,

    // LEDs (4 on Arty)
    output wire [3:0]   led,

    // RGB LEDs (optional)
    output wire [2:0]   led0_rgb,
    output wire [2:0]   led1_rgb,

    // Buttons (4 on Arty)
    input  wire [3:0]   btn,

    // Switches (4 on Arty)
    input  wire [3:0]   sw,

    // SPI Flash (on-board Quad SPI)
    output wire         qspi_cs,
    output wire         qspi_sck,
    inout  wire [3:0]   qspi_dq
);

    // =========================================================================
    // Clock and Reset Management
    // =========================================================================

    wire clk_25mhz;
    wire clk_locked;
    wire sys_reset;

    // PLL: 100 MHz → 25 MHz
    clk_wiz_0 clk_gen (
        .clk_in1(clk_100mhz),
        .clk_out1(clk_25mhz),
        .locked(clk_locked),
        .reset(~btn_reset_n)
    );

    // Reset synchronizer
    reg [3:0] reset_sync = 4'hF;
    always @(posedge clk_25mhz or negedge clk_locked) begin
        if (!clk_locked)
            reset_sync <= 4'hF;
        else
            reset_sync <= {reset_sync[2:0], 1'b0};
    end
    assign sys_reset = reset_sync[3];

    // =========================================================================
    // SPI Flash Interface
    // =========================================================================

    // Simple SPI mode (not using Quad mode for simplicity)
    wire spi_cs_n;
    wire spi_sck;
    wire spi_mosi;
    wire spi_miso;

    // Map to QSPI pins (SPI mode)
    assign qspi_cs = spi_cs_n;
    assign qspi_sck = spi_sck;
    assign qspi_dq[0] = spi_mosi;  // MOSI
    assign spi_miso = qspi_dq[1];  // MISO
    assign qspi_dq[2] = 1'b1;      // WP# (write protect disabled)
    assign qspi_dq[3] = 1'b1;      // HOLD# (not used)

    // =========================================================================
    // GPIO Mapping
    // =========================================================================

    wire [15:0] gpio_in;
    wire [15:0] gpio_out;
    wire [15:0] gpio_oe;

    // GPIO Input mapping
    assign gpio_in = {
        8'h00,          // [15:8] unused
        btn,            // [7:4] buttons
        sw              // [3:0] switches
    };

    // GPIO Output mapping
    assign led = gpio_out[3:0];
    assign led0_rgb = gpio_out[6:4];
    assign led1_rgb = gpio_out[9:7];

    // =========================================================================
    // SoC Instantiation
    // =========================================================================

    MinimalSoc soc (
        .clock(clk_25mhz),
        .reset(sys_reset),

        // UART
        .io_uart_tx(uart_txd),
        .io_uart_rx(uart_rxd),

        // GPIO
        .io_gpio_in(gpio_in),
        .io_gpio_out(gpio_out),
        .io_gpio_oe(gpio_oe),

        // SPI Flash
        .io_spi_cs_n(spi_cs_n),
        .io_spi_sck(spi_sck),
        .io_spi_mosi(spi_mosi),
        .io_spi_miso(spi_miso)
    );

endmodule
