# RV32E SoC Simulation

本目录包含用于仿真 RV32E SoC 的测试平台和工具。

## 文件说明

### 测试平台

- **tb_rtthread_boot.sv** - SystemVerilog 测试平台
  - 用于 Verilator、VCS、ModelSim 等外部仿真器
  - 包含完整的 Flash 模型和 UART 监控
  - 可以加载实际的启动镜像并验证系统启动

### 启动镜像

- **flash_boot.hex** - Flash 启动镜像（Verilog hex 格式）
  - 包含简单的 RV32E 启动程序
  - 输出 "RT-Thread Boot\n" 到 UART
  - 可直接用于仿真测试

- **generate_boot_image.py** - 启动镜像生成脚本
  - Python 脚本，用于生成自定义启动程序
  - 支持任意消息输出到 UART
  - 生成 .hex 和 .bin 两种格式

## 使用方法

### 方法 1: 使用 ChiselTest（推荐用于快速验证）

```bash
cd /home/user/RISCV32
mill rv32esoc.test
```

这将运行所有 Scala 测试，包括 RTThreadSpec。

### 方法 2: 使用 Verilator（完整仿真）

1. 生成 Verilog 代码：
```bash
cd /home/user/RISCV32
mill rv32esoc.runMain soc.RV32E_SoCMain
```

2. 运行 Verilator 仿真：
```bash
cd sim
verilator --cc --exe --build -Wall \
  --top-module RV32E_SoC \
  tb_rtthread_boot.sv \
  ../generated/RV32E_SoC.v

./obj_dir/VRV32E_SoC
```

### 方法 3: 使用 VCS

```bash
cd sim
vcs -sverilog +v2k \
  -timescale=1ns/1ps \
  -debug_access+all \
  tb_rtthread_boot.sv \
  ../generated/RV32E_SoC.v

./simv
```

### 方法 4: 使用 ModelSim

```bash
cd sim
vlog -sv tb_rtthread_boot.sv ../generated/RV32E_SoC.v
vsim -c tb_rtthread_boot -do "run -all; quit"
```

## 生成自定义启动镜像

使用 Python 脚本生成自定义消息：

```bash
cd sim
./generate_boot_image.py "Hello RV32E\n"
```

这将生成：
- `flash_boot.hex` - 用于仿真的 hex 文件
- `flash_boot.bin` - 二进制镜像文件

## 预期输出

成功的仿真应该在 UART 输出：
```
RT-Thread Boot
```

测试平台会捕获并显示这个输出，验证：
1. Flash 接口工作正常
2. 处理器能够取指并执行
3. UART 发送功能正常
4. 整个 SoC 系统集成成功

## 仿真参数

- **时钟频率**: 50 MHz (20ns 周期)
- **UART 波特率**: 115200
- **Flash 大小**: 1 MB
- **RAM 大小**: 256 KB
- **仿真时间**: 100 ms (可调整)

## 调试

### 查看波形

所有仿真器都会生成 `rtthread_boot.vcd` 波形文件：

```bash
gtkwave rtthread_boot.vcd
```

推荐查看的信号：
- `dut.clock`, `dut.reset`
- `dut.io_uart_tx` - UART 输出
- `dut.io_flash_*` - Flash SPI 接口
- 处理器内部状态（如果需要）

### 常见问题

1. **没有 UART 输出**
   - 检查 Flash 镜像是否正确加载
   - 检查时钟和复位信号
   - 查看处理器 PC 是否正常递增

2. **仿真超时**
   - 增加仿真时间（修改 `SIM_TIME` 参数）
   - 检查时钟生成是否正确

3. **指令执行错误**
   - 验证 Flash 镜像内容
   - 检查处理器译码逻辑

## RT-Thread 完整移植

要测试完整的 RT-Thread 系统：

1. 编译 RT-Thread（需要 RISC-V 工具链）：
```bash
cd /home/user/RISCV32/sw/rtos
make
```

2. 转换 ELF 为 hex 格式：
```bash
riscv32-unknown-elf-objcopy -O verilog rtthread.elf flash_boot.hex
```

3. 将 hex 文件复制到 sim 目录并运行仿真

注意：完整 RT-Thread 需要：
- 正确的内存布局（linker script）
- 启动代码（start_rtt.S）
- 上下文切换代码（context_gcc.S）
- BSP 初始化代码（board.c）

## 参考资料

- [RV32E SoC 架构文档](../docs/architecture.md)
- [Chisel 项目结构](../README.md)
- [RT-Thread 移植文档](../sw/rtos/README.md)
