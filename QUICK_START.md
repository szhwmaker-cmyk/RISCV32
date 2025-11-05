# RV32E SoC 快速入门教程

**从零到LED闪烁 - 10分钟快速体验**

本教程将引导您在10分钟内完成RV32E SoC的基本验证，让您快速体验RISC-V处理器的魅力。

---

## 📋 目录

1. [环境准备](#环境准备)
2. [验证仿真](#验证仿真)
3. [运行测试](#运行测试)
4. [FPGA部署](#fpga部署)
5. [第一个程序](#第一个程序)
6. [常见问题](#常见问题)

---

## 🚀 环境准备

### 最小系统要求

- **操作系统**: Linux (Ubuntu 20.04+) 或 macOS
- **内存**: 8GB+ RAM
- **存储**: 5GB 可用空间
- **JDK**: Java 11 或更高版本

### 安装基础工具

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install -y git default-jdk curl

# macOS
brew install git openjdk@11 curl

# 验证Java安装
java -version
# 应该显示: java version "11" 或更高
```

### 克隆项目

```bash
# 克隆仓库
git clone https://github.com/your-org/RISCV32.git
cd RISCV32

# 查看项目结构
ls -la
```

**预期输出**:
```
ARCHITECTURE.md
FPGA_DEPLOYMENT.md
README.md
build.sc
fpga/
rtthread/
src/
```

### 安装Mill构建工具

```bash
# 下载Mill
curl -L https://github.com/com-lihaoyi/mill/releases/download/0.11.0/0.11.0 > mill
chmod +x mill

# 移动到系统路径
sudo mv mill /usr/local/bin/

# 验证安装
mill --version
# 应该显示: Mill Build Tool version 0.11.0
```

**完成！** ✅ 环境准备完毕，用时约5分钟。

---

## 🧪 验证仿真

### 编译项目

```bash
cd /path/to/RISCV32

# 第一次编译（可能需要2-3分钟下载依赖）
mill rv32e.compile
```

**预期输出**:
```
[info] Compiling Chisel sources...
[info] Compilation completed successfully
```

### 生成Verilog

```bash
# 生成单个模块的Verilog（快速测试）
mill rv32e.runMain circt.stage.ChiselMain \
  --module rv32e.core.ALU \
  --target-dir generated_test

# 查看生成的Verilog
ls generated_test/
cat generated_test/ALU.v
```

**预期输出**:
```verilog
module ALU(
  input  [31:0] io_a,
  input  [31:0] io_b,
  input  [3:0]  io_alu_op,
  output [31:0] io_result,
  output        io_zero
);
  // ALU logic...
endmodule
```

**成功！** ✅ Chisel到Verilog转换正常工作。

---

## 🧪 运行测试

### 运行所有测试

```bash
# 运行完整测试套件（约5-10分钟）
mill rv32e.test

# 如果只想快速验证，运行单个测试
mill rv32e.test.testOnly rv32e.core.ALUSpec
```

**预期输出**（ALUSpec示例）:
```
[info] ALUSpec:
[info] ALU
[info] - should perform ADD operation
[info] - should perform SUB operation
[info] - should perform AND operation
[info] - should perform OR operation
[info] - should perform XOR operation
[info] - should perform SLL (shift left logical)
[info] - should perform SRL (shift right logical)
[info] - should perform SRA (shift right arithmetic)
[info] - should perform SLT (set less than)
[info] - should perform SLTU (set less than unsigned)
[info] - should set zero flag correctly
[info] - should handle edge cases (max values)
[info] Run completed in 3 seconds.
[info] Total number of tests run: 12
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 12, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

### 查看测试波形（可选）

```bash
# 某些测试会生成VCD波形文件
ls test_run_dir/

# 使用GTKWave查看波形
sudo apt install gtkwave  # Ubuntu
brew install gtkwave      # macOS

gtkwave test_run_dir/*/ALUSpec.vcd
```

**完成！** ✅ 所有测试通过，处理器核心工作正常。

---

## 🔧 FPGA部署

### 前置条件

**硬件**:
- Digilent Arty A7-35T 开发板
- USB Micro-B 数据线
- （可选）5V/2A 电源适配器

**软件**:
- Xilinx Vivado 2021.2 或更高版本

### 安装Vivado（如果还没有）

```bash
# 下载Vivado（需要Xilinx账号）
# https://www.xilinx.com/support/download.html

# 安装后，添加到PATH
echo 'source /tools/Xilinx/Vivado/2021.2/settings64.sh' >> ~/.bashrc
source ~/.bashrc

# 验证安装
vivado -version
```

### 一键部署

```bash
cd RISCV32/fpga

# 自动完成：Verilog生成 → 综合 → 实现 → 编程
./build_and_program.sh
```

**执行过程**（约30-40分钟）:

```
========================================
STEP 1: Generate Verilog from Chisel
========================================
Generating Verilog using Chisel...
Generated 23 Verilog files ✓

========================================
STEP 2: Create Vivado Project
========================================
Creating project rv32e_soc_fpga...
Project created ✓

========================================
STEP 3: Build FPGA Bitstream
========================================
[Synthesis] Running... (10-15 minutes)
  LUTs:  6,150 / 20,800 (29.6%) ✓
  FFs:   2,712 / 41,600 (6.5%)  ✓
  BRAMs: 36 / 50 (72.0%)        ✓

[Implementation] Running... (10-15 minutes)
  WNS: 1.125 ns ✓ (Timing met!)

[Bitstream] Generating... (5 minutes)
  Size: 1.2 MB ✓

========================================
STEP 4: Program FPGA Device
========================================
Detecting FPGA...
  Found: xc7a35ticsg324-1L ✓
Programming device... (10 seconds)
  Programming successful! ✓

========================================
BUILD AND PROGRAM COMPLETE!
========================================
```

### 验证硬件

**1. LED测试**

连接开发板后，应该看到：
- ✅ DONE LED（绿色，靠近USB口）常亮
- ✅ LED[0]（最右侧LED）以约2Hz频率闪烁

如果LED[0]闪烁，说明：
- ✅ FPGA编程成功
- ✅ 时钟和复位正常
- ✅ CPU正在执行代码
- ✅ RT-Thread RTOS运行中

**2. UART测试**

```bash
# 找到UART设备（Linux）
ls -l /dev/ttyUSB*
# 应该看到: /dev/ttyUSB0 (JTAG) 和 /dev/ttyUSB1 (UART)

# 连接串口（选择USB1）
screen /dev/ttyUSB1 115200

# 或使用minicom
minicom -D /dev/ttyUSB1 -b 115200

# macOS
ls -l /dev/tty.usbserial*
screen /dev/tty.usbserial-XXXX 115200
```

**预期UART输出**:
```
RT-Thread Sim
TICK
TICK
TICK
TICK
...
```

每秒应该输出一个 "TICK"。

**3. 按钮测试**（可选）

按下开发板上的按钮：
- BTN0 - 可能改变LED闪烁模式（取决于程序）
- RESET - 重启系统（UART会重新输出启动消息）

**成功！** ✅ RV32E SoC在真实硬件上运行！

---

## 💻 第一个程序

现在让我们编写并运行一个简单的汇编程序。

### 示例1：LED闪烁

创建文件 `examples/led_blink.s`:

```assembly
# LED闪烁程序
# 功能：控制GPIO LED[0]以1Hz频率闪烁

.section .text
.globl _start

_start:
    # 设置GPIO基地址
    lui  x1, 0x20001        # GPIO_BASE = 0x20001000

    # 设置GPIO方向（输出）
    addi x2, x0, 0x0F       # 设置GPIO[3:0]为输出
    sw   x2, 4(x1)          # 写入GPIO_DIR寄存器

    # 设置GPIO输出使能
    addi x2, x0, 0x0F       # 使能GPIO[3:0]输出
    sw   x2, 8(x1)          # 写入GPIO_OE寄存器

main_loop:
    # LED ON
    addi x2, x0, 0x01       # LED[0] = 1
    sw   x2, 0(x1)          # 写入GPIO_DATA寄存器

    # 延时（约0.5秒 @ 25MHz）
    lui  x3, 0x00BEB        # 延时计数 = 12500000
    addi x3, x3, 0x640
delay_on:
    addi x3, x3, -1
    bne  x3, x0, delay_on

    # LED OFF
    addi x2, x0, 0x00       # LED[0] = 0
    sw   x2, 0(x1)

    # 延时（约0.5秒）
    lui  x3, 0x00BEB
    addi x3, x3, 0x640
delay_off:
    addi x3, x3, -1
    bne  x3, x0, delay_off

    # 循环
    beq  x0, x0, main_loop
```

### 编译和运行

```bash
# 1. 汇编程序
riscv32-unknown-elf-as -march=rv32e -mabi=ilp32e led_blink.s -o led_blink.o

# 2. 链接
riscv32-unknown-elf-ld -T linker.ld led_blink.o -o led_blink.elf

# 3. 生成二进制文件
riscv32-unknown-elf-objcopy -O binary led_blink.elf led_blink.bin

# 4. 创建SPI Flash映像
dd if=led_blink.bin of=flash_image.bin bs=4096 count=4

# 5. 在仿真中测试
mill rv32e.test.testOnly rv32e.integration.IntegrationSpec -- -z led_blink
```

### 示例2：UART Hello World

创建文件 `examples/uart_hello.s`:

```assembly
# UART Hello World
# 功能：通过UART发送 "Hello RV32E!"

.section .data
message:
    .string "Hello RV32E!\n"

.section .text
.globl _start

_start:
    # 设置UART基地址
    lui  x1, 0x20000        # UART_BASE = 0x20000000

    # 加载消息地址
    lui  x2, %hi(message)
    addi x2, x2, %lo(message)

send_loop:
    # 读取字符
    lb   x3, 0(x2)

    # 检查是否为字符串结尾
    beq  x3, x0, done

wait_tx:
    # 等待UART发送就绪
    lw   x4, 8(x1)          # 读STATUS寄存器
    andi x4, x4, 0x02       # 检查TX_FULL位
    bne  x4, x0, wait_tx    # 如果FIFO满，继续等待

    # 发送字符
    sw   x3, 0(x1)          # 写入TXDATA寄存器

    # 下一个字符
    addi x2, x2, 1
    beq  x0, x0, send_loop

done:
    # 结束
    beq  x0, x0, done
```

**UART输出**:
```
Hello RV32E!
```

### 示例3：简单计算

创建文件 `examples/fibonacci.s`:

```assembly
# Fibonacci数列计算
# 功能：计算第10个Fibonacci数并通过GPIO输出

.section .text
.globl _start

_start:
    # 初始化
    addi x1, x0, 0          # F(0) = 0
    addi x2, x0, 1          # F(1) = 1
    addi x3, x0, 10         # 计算到第10项
    addi x4, x0, 0          # 计数器

fib_loop:
    # 检查是否完成
    beq  x4, x3, done

    # 计算下一项: F(n) = F(n-1) + F(n-2)
    add  x5, x1, x2         # x5 = F(n)
    add  x1, x2, x0         # x1 = F(n-1)
    add  x2, x5, x0         # x2 = F(n)

    # 递增计数器
    addi x4, x4, 1
    beq  x0, x0, fib_loop

done:
    # 输出结果到GPIO
    lui  x6, 0x20001        # GPIO_BASE
    sw   x2, 0(x6)          # 显示在LED上（低8位）

    # 停止
    beq  x0, x0, done

# 结果: F(10) = 55 (0x37)
# LED应该显示: 0b00110111
```

---

## 🎓 学到了什么？

通过这个快速入门，您已经：

1. ✅ **设置了开发环境** - Mill, JDK
2. ✅ **编译了Chisel项目** - 理解了硬件描述语言
3. ✅ **运行了测试套件** - 验证了处理器正确性
4. ✅ **生成了Verilog** - 了解了设计流程
5. ✅ **部署到FPGA** - 看到了真实硬件运行
6. ✅ **编写了汇编程序** - 学会了RV32E编程

---

## 📚 下一步学习

现在您已经掌握了基础，可以继续学习：

### 初级教程
- 📖 [用户手册](USER_MANUAL.md) - 完整的系统参考
- 💻 [编程指南](PROGRAMMING_GUIDE.md) - 深入学习RV32E编程
- 🔧 [外设API](PERIPHERAL_API.md) - UART, GPIO, SPI, I2C使用

### 中级教程
- 🏗️ [架构文档](ARCHITECTURE.md) - 理解处理器内部结构
- 🧪 [测试指南](HOW_TO_RUN.md) - 编写自己的测试
- 🔬 [调试技巧](DEBUGGING.md) - 波形分析和问题定位

### 高级教程
- 🚀 [性能优化](PERFORMANCE.md) - 提升系统性能
- 🎛️ [FPGA定制](FPGA_DEPLOYMENT.md) - 适配不同开发板
- 🔌 [添加外设](ADD_PERIPHERAL.md) - 扩展系统功能

---

## ❓ 常见问题

### Q1: Mill下载依赖很慢怎么办？

**A**: 配置国内镜像：

```bash
# 创建 ~/.mill/ammonite/predef.sc
mkdir -p ~/.mill/ammonite
cat > ~/.mill/ammonite/predef.sc << 'EOF'
interp.repositories() ++= Seq(
  coursierapi.MavenRepository.of("https://maven.aliyun.com/repository/public")
)
EOF
```

### Q2: 测试失败怎么办？

**A**: 查看详细错误信息：

```bash
# 运行单个测试并查看详细输出
mill rv32e.test.testOnly rv32e.core.ALUSpec -- -oF

# 查看生成的波形
ls test_run_dir/
gtkwave test_run_dir/*/ALUSpec.vcd
```

### Q3: FPGA编程后没有LED闪烁？

**A**: 检查清单：

1. DONE LED是否亮起？
   - 是 → FPGA编程成功
   - 否 → 重新编程或检查连接

2. 检查时钟配置：
   ```bash
   # 查看PLL设置
   cat fpga/scripts/create_pll.tcl
   ```

3. 查看综合报告：
   ```bash
   cat fpga/vivado/rv32e_soc_fpga.runs/reports/post_impl_timing.rpt
   # 确认 WNS > 0
   ```

### Q4: UART没有输出？

**A**: 故障排除：

```bash
# 1. 确认设备存在
ls -l /dev/ttyUSB*

# 2. 检查权限
sudo chmod 666 /dev/ttyUSB1

# 3. 测试回环
echo "test" > /dev/ttyUSB1

# 4. 尝试不同波特率
screen /dev/ttyUSB1 9600    # 如果115200不行
screen /dev/ttyUSB1 115200
```

### Q5: 如何在Windows上使用？

**A**: Windows支持选项：

1. **WSL2** (推荐):
   ```powershell
   # 安装WSL2
   wsl --install -d Ubuntu-22.04

   # 在WSL中按照Linux步骤操作
   ```

2. **使用PuTTY连接UART**:
   - 下载PuTTY
   - 设置: COM端口, 115200波特率, 8N1

3. **Vivado在Windows原生运行**:
   - Verilog生成在WSL中
   - Vivado在Windows中运行

### Q6: 编译Chisel很慢？

**A**: 优化编译速度：

```bash
# 增加JVM内存
export JAVA_OPTS="-Xmx4G -Xss4M"

# 并行编译
mill -j 4 rv32e.compile

# 使用SSD存储项目
```

### Q7: 如何更换FPGA开发板？

**A**: 适配新板卡步骤：

1. 创建新约束文件：
   ```bash
   cp fpga/constraints/arty_a7.xdc fpga/constraints/my_board.xdc
   # 修改引脚定义
   ```

2. 修改FPGA型号：
   ```bash
   # 编辑 fpga/scripts/create_project.tcl
   set fpga_part "xc7a100tcsg324-1"  # 改为您的FPGA型号
   ```

3. 调整时钟频率（如果需要）：
   ```bash
   # 编辑 fpga/scripts/create_pll.tcl
   CONFIG.PRIM_IN_FREQ {100.000}  # 输入时钟频率
   ```

---

## 🎉 恭喜！

您已经完成了RV32E SoC的快速入门！

现在您可以：
- ✅ 编译和测试处理器
- ✅ 部署到FPGA硬件
- ✅ 编写简单的汇编程序
- ✅ 使用GPIO和UART外设

继续探索更多功能，祝您学习愉快！🚀

---

**有问题？**
- 📖 查看 [完整用户手册](USER_MANUAL.md)
- 💬 提交 [GitHub Issue](https://github.com/your-org/RISCV32/issues)
- 📧 发送邮件到: support@example.com

**想贡献？**
- 🌟 Star 本项目
- 🔀 Fork 并提交PR
- 📝 改进文档
- 🐛 报告Bug

---

**文档版本**: 1.0
**最后更新**: 2025-11-05
**适用版本**: RV32E SoC v1.0+
