# RV32E SoC 测试和验证指南

## 测试策略概述

本项目采用多层次的测试策略，从单元测试到系统集成测试，确保每个模块和整个系统的正确性。

## 测试层次

```
┌─────────────────────────────────────────┐
│   System Integration Test (L4)         │  ← 完整系统测试
├─────────────────────────────────────────┤
│   Subsystem Test (L3)                  │  ← 子系统集成测试
├─────────────────────────────────────────┤
│   Module Test (L2)                     │  ← 模块级测试
├─────────────────────────────────────────┤
│   Unit Test (L1)                       │  ← 单元测试
└─────────────────────────────────────────┘
```

## 1. 单元测试 (L1)

### 1.1 RegFile 测试

**测试文件**: `src/test/scala/core/RegFileTest.scala`

**测试用例**:
```scala
// 1. x0 始终为 0
it should "keep x0 as zero"

// 2. 读写正确性
it should "read and write registers correctly"

// 3. 写后读冒险
it should "handle write-then-read hazard"

// 4. 所有寄存器测试
it should "write to all registers"
```

**运行测试**:
```bash
make test-core.RegFileTest
```

**验证点**:
- ✓ x0 不可写，始终读出 0
- ✓ x1-x15 可以正常读写
- ✓ 双读端口独立工作
- ✓ 写后立即读取正确

### 1.2 ALU 测试

**测试文件**: `src/test/scala/core/ALUTest.scala`

**测试用例**:
```scala
// 算术运算
it should "perform ADD operation"
it should "perform SUB operation"

// 逻辑运算
it should "perform AND operation"
it should "perform OR operation"
it should "perform XOR operation"

// 移位运算
it should "perform SLL (shift left logical)"
it should "perform SRL (shift right logical)"
it should "perform SRA (shift right arithmetic)"

// 比较运算
it should "perform SLT (set less than signed)"
it should "perform SLTU (set less than unsigned)"
```

**运行测试**:
```bash
make test-core.ALUTest
```

**验证点**:
- ✓ 所有算术运算正确
- ✓ 逻辑运算正确
- ✓ 移位运算（包括算术右移）
- ✓ 有符号/无符号比较
- ✓ zero 和 negative 标志

### 1.3 UART 测试

**测试文件**: `src/test/scala/peripherals/UartTest.scala`

**测试场景**:

1. **UART 发送器测试**
```scala
it should "transmit a byte correctly" in {
  // 1. 配置波特率
  // 2. 发送数据 0x55
  // 3. 验证起始位、数据位、停止位时序
}
```

2. **UART 接收器测试**
```scala
it should "receive a byte correctly" in {
  // 1. 模拟 RX 输入波形
  // 2. 接收数据 0xA5
  // 3. 验证接收正确
}
```

3. **寄存器访问测试**
```scala
it should "handle register read/write" in {
  // 1. 写入波特率寄存器
  // 2. 读回验证
  // 3. 测试其他寄存器
}
```

**运行测试**:
```bash
make test-peripherals.UartTest
```

### 1.4 GPIO 测试

**测试文件**: `src/test/scala/peripherals/GpioTest.scala`

**测试用例**:
```scala
it should "read input pins"
it should "write output pins"
it should "support mixed input/output configuration"
it should "handle read-write-read sequence"
```

**运行测试**:
```bash
make test-peripherals.GpioTest
```

## 2. 模块测试 (L2)

### 2.1 流水线阶段测试

虽然单个阶段已在单元测试中验证，模块测试关注阶段间的协同：

**测试重点**:
- IF → ID 数据传递
- ID → EX 控制信号
- EX → MEM ALU 结果
- MEM → WB 写回数据

**测试方法**:
```scala
// 伪代码
test(new PipelineModule) { dut =>
  // 1. 注入指令
  dut.io.inst.poke(ADDI_INST)

  // 2. 跟踪流水线各阶段
  dut.clock.step()
  // 验证 IF/ID 寄存器

  dut.clock.step()
  // 验证 ID/EX 寄存器

  // ...

  // 5. 验证最终结果
  dut.io.regfile.read(rd).expect(expected)
}
```

### 2.2 冒险检测测试

**测试场景**:

1. **数据转发测试**
```assembly
addi x1, x0, 10    # x1 = 10
addi x2, x1, 20    # x2 = 30 (需要从 EX/MEM 转发)
addi x3, x2, 30    # x3 = 60 (需要从 MEM/WB 转发)
```

2. **LOAD-USE 冒险测试**
```assembly
lw   x1, 0(x2)     # 加载 x1
addi x3, x1, 10    # 使用 x1（需要暂停 1 周期）
```

3. **分支冒险测试**
```assembly
beq  x1, x2, label # 分支跳转
addi x3, x0, 1     # 应该被 flush
addi x4, x0, 2     # 应该被 flush
label:
addi x5, x0, 3     # 正确执行
```

### 2.3 总线互连测试

**测试重点**:
- 地址译码正确性
- 多从设备选择
- 数据路由
- 应答信号

## 3. 子系统测试 (L3)

### 3.1 处理器核心 + RAM 测试

**测试程序**:
```assembly
# 简单的累加程序
    li   x1, 0          # sum = 0
    li   x2, 1          # i = 1
    li   x3, 10         # n = 10
loop:
    add  x1, x1, x2     # sum += i
    addi x2, x2, 1      # i++
    blt  x2, x3, loop   # if i < n goto loop
    # 结果：x1 = 45 (1+2+...+9)
```

**验证方法**:
```scala
test(new CoreWithRam) { dut =>
  // 1. 加载程序到 RAM
  loadProgram(dut, program)

  // 2. 运行足够周期
  dut.clock.step(100)

  // 3. 验证结果
  dut.io.debug.regs(1).expect(45.U)
}
```

### 3.2 Core + UART 测试

**测试场景**: UART 输出 "Hello"

```c
void uart_puts(const char* s) {
    while (*s) {
        uart_putc(*s++);
    }
}

int main() {
    uart_puts("Hello");
    return 0;
}
```

**验证**: 监控 UART TX 输出，验证时序和数据。

## 4. 系统集成测试 (L4)

### 4.1 SoC 启动测试

**测试文件**: `src/test/scala/soc/SoCIntegrationTest.scala`

**测试场景**:
```scala
it should "execute a simple program" in {
  test(new MinimalSoC) { dut =>
    // 1. 复位
    dut.reset.poke(true.B)
    dut.clock.step(10)
    dut.reset.poke(false.B)

    // 2. 运行程序
    dut.clock.step(1000)

    // 3. 验证输出（通过 UART 或 GPIO）
  }
}
```

### 4.2 完整功能测试

**测试清单**:

- [ ] 处理器正确执行所有 RV32E 指令
- [ ] SPI Flash 读取正确
- [ ] UART 发送接收正确
- [ ] GPIO 输入输出正确
- [ ] I2C 通信正确
- [ ] SPI Master 通信正确
- [ ] 中断处理正确（未实现）

## 5. 指令集测试

### 5.1 RV32E 指令覆盖

**R-Type 指令**:
```
ADD, SUB, SLL, SLT, SLTU, XOR, SRL, SRA, OR, AND
```

**I-Type 指令**:
```
ADDI, SLTI, SLTIU, XORI, ORI, ANDI, SLLI, SRLI, SRAI
LB, LH, LW, LBU, LHU
JALR
```

**S-Type 指令**:
```
SB, SH, SW
```

**B-Type 指令**:
```
BEQ, BNE, BLT, BGE, BLTU, BGEU
```

**U-Type 指令**:
```
LUI, AUIPC
```

**J-Type 指令**:
```
JAL
```

### 5.2 测试程序示例

```assembly
# test_add_sub.S
    addi x1, x0, 10     # x1 = 10
    addi x2, x0, 5      # x2 = 5
    add  x3, x1, x2     # x3 = 15
    sub  x4, x1, x2     # x4 = 5

    # 验证结果
    addi x5, x0, 15
    bne  x3, x5, fail
    addi x5, x0, 5
    bne  x4, x5, fail

    # 成功
    addi x10, x0, 0
    j    exit

fail:
    addi x10, x0, 1
exit:
    # 结束
```

## 6. 性能测试

### 6.1 CPI 测量

```scala
test(new Core) { dut =>
  val instructions = Seq(/* 指令序列 */)
  val startCycle = dut.clock.getCycle()

  // 运行程序
  runProgram(dut, instructions)

  val endCycle = dut.clock.getCycle()
  val cycles = endCycle - startCycle
  val cpi = cycles.toDouble / instructions.length

  println(s"CPI = $cpi")
  assert(cpi < 1.5, "CPI too high")
}
```

### 6.2 吞吐量测试

测量每秒执行的指令数 (MIPS):

```
MIPS = (Clock Freq / CPI) / 1,000,000
     = (50 MHz / 1.35) / 1M
     ≈ 37 MIPS
```

## 7. 波形分析

### 7.1 生成 VCD 波形

在测试中启用 VCD：

```scala
test(new MyModule).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
  // 测试代码
}
```

### 7.2 查看波形

```bash
gtkwave test_run_dir/*/MyModule.vcd
```

### 7.3 关键信号

**处理器核心**:
- PC
- inst (指令)
- regfile.regs[*] (寄存器值)
- hazard 信号

**流水线**:
- IF/ID, ID/EX, EX/MEM, MEM/WB 寄存器
- 转发信号
- 暂停信号

**总线**:
- wb.adr
- wb.dat_w / wb.dat_r
- wb.we
- wb.stb, wb.cyc, wb.ack

## 8. 覆盖率分析

### 8.1 代码覆盖率

使用 ChiselTest 的覆盖率功能：

```scala
test(new MyModule).withAnnotations(Seq(
  CoverageAnnotation
)) { dut =>
  // 测试代码
}
```

### 8.2 功能覆盖率

手动检查列表：

- [ ] 所有指令类型
- [ ] 所有 ALU 操作
- [ ] 所有冒险场景
- [ ] 所有外设寄存器
- [ ] 边界条件

## 9. 回归测试

### 9.1 自动化测试

```bash
# 运行所有测试
make test

# 生成测试报告
make test > test_report.txt
```

### 9.2 CI 集成

```yaml
# .github/workflows/ci.yml
name: CI

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Setup Java
        uses: actions/setup-java@v2
        with:
          java-version: '11'
      - name: Run tests
        run: make ci-test
```

## 10. 调试技巧

### 10.1 使用 printf

```scala
printf(p"Cycle ${cycle}: PC=0x${Hexadecimal(io.pc)}\n")
```

### 10.2 断言

```scala
assert(io.alu.out === expected,
       s"ALU output mismatch: got ${io.alu.out.peek()}, expected $expected")
```

### 10.3 条件断点

```scala
when(io.pc === 0x1000.U) {
  printf("Reached breakpoint at 0x1000\n")
  // 插入检查代码
}
```

## 11. 测试最佳实践

### 11.1 测试命名

```scala
behavior of "RegFile"

it should "keep x0 as zero" in {
  // 测试描述清晰
}
```

### 11.2 独立性

每个测试应该独立，不依赖其他测试的状态。

### 11.3 可重复性

测试结果应该可重复，避免随机性（除非测试随机场景）。

### 11.4 快速反馈

单元测试应该快速完成（< 1 秒），集成测试可以稍慢。

## 12. 常见问题

### Q1: 测试超时

**原因**: 时钟步进不足或死锁

**解决**: 增加 timeout 或检查逻辑

```scala
dut.clock.setTimeout(10000)
```

### Q2: 波形文件太大

**原因**: 运行周期过多

**解决**: 减少测试周期或只记录关键信号

### Q3: 测试通过但实际不工作

**原因**: 测试覆盖不全

**解决**: 增加边界条件和异常场景测试

## 总结

完善的测试体系是保证硬件设计正确性的关键。通过多层次的测试策略，我们能够：

1. ✓ 及早发现问题
2. ✓ 提高设计质量
3. ✓ 降低调试成本
4. ✓ 增强设计信心

**记住**: 测试永远不嫌多！
