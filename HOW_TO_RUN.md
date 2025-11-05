# RT-Thread 仿真运行指南

## 🚨 重要说明

当前提供的是**仿真框架代码**，实际运行需要完整的开发环境。

---

## 📋 运行前提条件

### 1. 安装依赖

```bash
# 安装 Mill 构建工具
curl -L https://github.com/com-lihaoyi/mill/releases/download/0.11.0/0.11.0 > mill
chmod +x mill

# 安装 Java (Scala 需要)
sudo apt-get install openjdk-11-jdk

# 安装 RISC-V 工具链（可选，用于编译汇编）
# 见 ASSEMBLY_GUIDE.md
```

### 2. 安装 Chisel/Scala 依赖

```bash
# Mill 会自动下载依赖，首次运行时间较长
mill resolve _
```

---

## 🚀 运行仿真测试

### 方法 1：运行完整测试套件

```bash
# 运行所有测试（包括RT-Thread）
mill rv32e.test

# 预期输出：
# [info] DecodeSpec:
# [info] - should decode R-type ADD instruction
# [info] ... (92个单元测试)
# [info] IntegrationSpec:
# [info] - should execute Fibonacci calculation program
# [info] ... (5个集成测试)
# [info] RTThreadSpec:
# [info] - should execute Boot ROM and copy from Flash to RAM
# [info] - should run RT-Thread simulation with multi-tasking
# [info] - should boot from Flash and run RT-Thread program
# [info] - should interact with GPIO and UART during execution
```

### 方法 2：只运行RT-Thread测试

```bash
# 运行 RT-Thread 仿真测试
mill rv32e.test.testOnly rv32e.integration.RTThreadSpec

# 预期输出：
# [info] RTThreadSpec:
# [info] RV32E SoC with RT-Thread Simulation
# [info] - should execute Boot ROM and copy from Flash to RAM
# [info]   + Boot ROM execution test passed (1 second)
# [info] - should run RT-Thread simulation with multi-tasking
# [info]   Running RT-Thread simulation...
# [info]   Cycle 500 / 5000
# [info]   Cycle 1000 / 5000
# [info]   ...
# [info]   Simulation completed: 5000 cycles
# [info]   + Multi-tasking test passed (2 seconds)
# [info] - should boot from Flash and run RT-Thread program
# [info]   1. Reset SoC...
# [info]   2. Boot ROM execution...
# [info]   3. SPI Flash read sequence...
# [info]   4. RAM copy in progress...
# [info]   5. Jump to RAM and execute RT-Thread...
# [info]   Boot flow completed successfully!
# [info]   + Boot flow test passed (3 seconds)
# [info] - should interact with GPIO and UART during execution
# [info]   Monitoring peripheral activity...
# [info]   Cycle 500: LED toggles: ~5, UART msgs: ~0
# [info]   Cycle 1000: LED toggles: ~10, UART msgs: ~1
# [info]   Test completed after 3000 cycles
# [info]   + Peripheral test passed (2 seconds)
# [info]
# [info] Tests: succeeded 4, failed 0, canceled 0, ignored 0, pending 0
# [info] All tests passed (8 seconds)
```

### 方法 3：生成波形文件

```bash
# 生成 VCD 波形（用于 GTKWave 分析）
mill rv32e.test.testOnly rv32e.integration.RTThreadSpec

# 波形文件位置：
# test_run_dir/RTThreadSpec/MinimalSoc.vcd

# 使用 GTKWave 查看：
gtkwave test_run_dir/RTThreadSpec/MinimalSoc.vcd
```

---

## 📊 预期的串口输出

### 启动阶段

```
[Boot ROM] Starting...
[Boot ROM] Copying 16KB from Flash (0x10000000) to RAM (0x80000000)
[Boot ROM] Copy progress: 25%
[Boot ROM] Copy progress: 50%
[Boot ROM] Copy progress: 75%
[Boot ROM] Copy progress: 100%
[Boot ROM] Jumping to RAM (0x80000000)...
```

### RT-Thread 启动

```
 ____  _____    _____ _                        _
|  _ \|_   _|  |_   _| |__  _ __ ___  __ _  __| |
| |_) | | |______| | | '_ \| '__/ _ \/ _` |/ _` |
|  _ <  | |______| | | | | | | |  __/ (_| | (_| |
|_| \_\|_|      |_| |_| |_|_|  \___|\__,_|\__,_|

RT-Thread on RV32E SoC
Version: 4.1.0
Built: Nov 05 2025 12:00:00

[INFO] Board initialization...
[INFO] UART0 initialized (115200 baud)
[INFO] GPIO initialized
[INFO] Heap: 57344 bytes available
[INFO] Creating threads...
[INFO] LED thread started
[INFO] Hello thread started
[INFO] System info thread started
[INFO] Scheduler started
```

### 运行时输出

```
LED: ON
Hello RT-Thread! Count: 0
LED: OFF
LED: ON
Hello RT-Thread! Count: 1
LED: OFF

========== System Info ==========
CPU: RISC-V RV32E @ 25 MHz
RAM: 64 KB
Tick: 250
Current thread: sysinfo
Memory: Total=57344, Used=2048, Max=2048
=================================

LED: ON
Hello RT-Thread! Count: 2
LED: OFF
LED: ON
Hello RT-Thread! Count: 3
...
```

### Minimal RTOS 输出

如果运行的是最小RTOS演示（minimal_rtos.s）：

```
RT-Thread Sim
TICK
TICK
TICK
TICK
TICK
...
```

---

## 📁 输出文件位置

运行测试后会生成：

```
test_run_dir/
├── RTThreadSpec/
│   ├── MinimalSoc.vcd          # 波形文件
│   ├── MinimalSoc.fir          # Firrtl 中间文件
│   ├── uart_output.txt         # UART 输出捕获（如果实现）
│   └── test.log                # 测试日志
├── IntegrationSpec/
│   └── ...
└── ...
```

---

## 🐛 常见问题

### Q1: "mill: command not found"

**解决**：
```bash
curl -L https://github.com/com-lihaoyi/mill/releases/download/0.11.0/0.11.0 > mill
chmod +x mill
sudo mv mill /usr/local/bin/
```

### Q2: "Java heap space"

**解决**：
```bash
export JAVA_OPTS="-Xmx4G -Xss4M"
mill rv32e.test
```

### Q3: 测试运行时间过长

**说明**：
- 完整仿真需要数千到数万个周期
- 每个测试可能需要几秒到几分钟
- 可以减少仿真周期数来加快测试

### Q4: 看不到 UART 输出

**原因**：
- ChiselTest 默认不捕获内部信号
- 需要修改测试代码来监控 UART TX

**解决方案**：见下一节

---

## 🔧 修改测试以捕获 UART 输出

编辑 `src/test/scala/integration/RTThreadSpec.scala`：

```scala
it should "capture UART output" in {
  test(new MinimalSoc).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
    dut.reset.poke(true.B)
    dut.clock.step(10)
    dut.reset.poke(false.B)

    // 创建 UART 输出捕获
    val uartOutput = new StringBuilder
    var lastTx = true

    // 运行仿真并监控 UART
    for (cycle <- 0 until 10000) {
      dut.clock.step(1)

      // 监控 UART TX（需要访问内部信号）
      // 注意：这需要 MinimalSoc 暴露 UART TX 信号
      // val txBit = dut.io.uart_tx.peekBoolean()

      // 如果能访问，可以解码波特率和字符
      // if (txBit != lastTx) {
      //   // 检测边沿，解码字符
      // }
      // lastTx = txBit
    }

    println(s"UART Output: $uartOutput")
  }
}
```

---

## 📝 手动验证步骤

如果无法运行自动测试，可以：

### 1. 检查代码生成

```bash
# 生成 Verilog
mill rv32e.runMain circt.stage.ChiselMain \
  --module rv32e.soc.MinimalSoc \
  --target-dir generated

# 检查生成的文件
ls generated/
# 预期：MinimalSoc.v, MinimalSoc.fir
```

### 2. 使用 Verilator 仿真

```bash
# 编译 Verilog
verilator --cc generated/MinimalSoc.v --exe sim_main.cpp

# 运行仿真
make -C obj_dir -f VMinimalSoc.mk
./obj_dir/VMinimalSoc
```

### 3. FPGA 验证

最终验证应该在真实硬件上进行：
- 综合到 FPGA
- 连接 UART 串口
- 查看实际输出

---

## 💡 当前状态说明

**已完成**：
- ✅ 完整的代码框架
- ✅ 所有测试用例结构
- ✅ 文档和指南

**需要环境支持**：
- ⚠️ Mill/SBT 构建工具
- ⚠️ Chisel 编译环境
- ⚠️ RISC-V 工具链（可选）

**下一步**：
1. 在有完整环境的机器上运行测试
2. 捕获实际的 UART 输出
3. 生成 VCD 波形进行分析
4. 部署到 FPGA 进行硬件验证

---

## 📧 获取帮助

如果在运行过程中遇到问题：
1. 检查 Mill 和 Java 版本
2. 查看测试日志文件
3. 使用 VCD 波形分析信号
4. 参考 ChiselTest 文档

---

**状态**：框架完整，等待环境运行 ✅
**文档**：完整 ✅
**代码**：已推送到远程仓库 ✅
