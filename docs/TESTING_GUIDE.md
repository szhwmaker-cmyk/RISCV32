# RV32E SoC 测试指南

本指南详细说明如何运行、编写和扩展RV32E SoC项目的测试套件。

---

## 目录

1. [快速开始](#快速开始)
2. [测试架构](#测试架构)
3. [运行测试](#运行测试)
4. [编写新测试](#编写新测试)
5. [调试测试](#调试测试)
6. [CI/CD集成](#cicd集成)

---

## 快速开始

### 环境准备

1. **安装Java JDK 11+**
```bash
java -version  # 应该显示 11 或更高版本
```

2. **安装SBT或Mill**

SBT方式:
```bash
# Debian/Ubuntu
echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
sudo apt-get update
sudo apt-get install sbt

# macOS
brew install sbt
```

Mill方式:
```bash
# Linux/macOS
curl -L https://github.com/com-lihaoyi/mill/releases/download/0.11.6/0.11.6 > mill
chmod +x mill
sudo mv mill /usr/local/bin/
```

### 运行第一个测试

```bash
# 使用SBT
sbt "testOnly core.RegFileSpec"

# 使用Mill
mill rv32e_soc.test.testOnly core.RegFileSpec
```

---

## 测试架构

### 目录结构

```
src/test/scala/
├── core/              # 处理器核心测试
│   ├── RegFileSpec.scala
│   ├── ALUSpec.scala
│   └── DecoderSpec.scala
├── peripherals/       # 外设测试
│   ├── GPIOSpec.scala
│   ├── TimerSpec.scala
│   ├── BootROMSpec.scala
│   └── SRAMSpec.scala
├── bus/               # 总线测试（待添加）
└── soc/               # SoC集成测试（待添加）
```

### 测试框架

我们使用以下测试框架：

- **ChiselTest 6.0**: Chisel官方测试框架
- **ScalaTest 3.2**: Scala标准测试框架
- **FlatSpec**: 测试规范样式

### 测试模式

```scala
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class MyModuleSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "MyModule"

  it should "do something" in {
    test(new MyModule) { dut =>
      // 测试逻辑
    }
  }
}
```

---

## 运行测试

### 运行所有测试

```bash
# SBT
sbt test

# Mill
mill rv32e_soc.test

# 构建脚本
./scripts/build.sh test
```

### 运行特定测试文件

```bash
# SBT - 运行单个测试类
sbt "testOnly core.RegFileSpec"
sbt "testOnly peripherals.GPIOSpec"

# Mill - 运行单个测试类
mill rv32e_soc.test.testOnly core.RegFileSpec
```

### 运行特定测试用例

```bash
# SBT - 使用正则表达式匹配
sbt "testOnly core.ALUSpec -- -z ADD"

# 这将只运行名称包含"ADD"的测试
```

### 并行运行测试

```bash
# SBT - 配置并行执行
sbt "set Test / parallelExecution := true" test
```

### 详细输出

```bash
# SBT - 显示详细信息
sbt "testOnly core.RegFileSpec -- -oD"

# 选项说明：
# -oD: 显示测试持续时间
# -oF: 显示完整堆栈跟踪
```

---

## 编写新测试

### 基本测试模板

```scala
package mypackage

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class MyNewModuleSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "MyNewModule"

  it should "perform basic operation" in {
    test(new MyNewModule) { dut =>
      // 1. 设置输入
      dut.io.input.poke(42.U)

      // 2. 推进时钟
      dut.clock.step(1)

      // 3. 验证输出
      dut.io.output.expect(84.U)
    }
  }
}
```

### ChiselTest API

#### 基本操作

```scala
// 设置输入信号
dut.io.a.poke(10.U)
dut.io.enable.poke(true.B)

// 读取信号值
val result = dut.io.output.peek()

// 验证输出（带消息）
dut.io.result.expect(expected.U, "Result should match expected value")

// 推进时钟
dut.clock.step(1)     // 前进1个周期
dut.clock.step(10)    // 前进10个周期

// 条件等待
dut.clock.step()
while (!dut.io.ready.peek().litToBoolean) {
  dut.clock.step()
}
```

#### 时序控制

```scala
// 在时钟上升沿前设置输入
dut.io.input.poke(value.U)
dut.clock.step(1)
// 在下一个上升沿后检查输出

// 组合逻辑（无需step）
dut.io.a.poke(5.U)
dut.io.b.poke(3.U)
dut.io.sum.expect(8.U)  // 立即检查
```

### 测试Wishbone接口

```scala
it should "respond to Wishbone read" in {
  test(new MyPeripheral) { dut =>
    // 设置读取请求
    dut.io.wb.adr.poke(0x100.U)
    dut.io.wb.stb.poke(true.B)
    dut.io.wb.cyc.poke(true.B)
    dut.io.wb.we.poke(false.B)

    dut.clock.step(1)

    // 等待ACK
    while (!dut.io.wb.ack.peek().litToBoolean) {
      dut.clock.step(1)
    }

    // 验证数据
    dut.io.wb.dat_r.expect(expectedData.U)
  }
}
```

### 测试边界条件

```scala
it should "handle edge cases" in {
  test(new MyModule) { dut =>
    // 最小值
    dut.io.input.poke(0.U)
    dut.clock.step(1)
    // 验证...

    // 最大值
    dut.io.input.poke("hFFFFFFFF".U)
    dut.clock.step(1)
    // 验证...

    // 边界值
    dut.io.input.poke("h7FFFFFFF".U)
    dut.clock.step(1)
    // 验证...
  }
}
```

### 参数化测试

```scala
it should "work for multiple values" in {
  test(new MyAdder) { dut =>
    val testCases = Seq(
      (0, 0, 0),
      (1, 2, 3),
      (100, 200, 300),
      (0xFFFF, 0x0001, 0x10000)
    )

    for ((a, b, expected) <- testCases) {
      dut.io.a.poke(a.U)
      dut.io.b.poke(b.U)
      dut.clock.step(1)
      dut.io.sum.expect(expected.U,
        s"$a + $b should equal $expected")
    }
  }
}
```

---

## 调试测试

### 打印调试信息

```scala
it should "debug example" in {
  test(new MyModule) { dut =>
    dut.io.input.poke(42.U)
    dut.clock.step(1)

    // 打印当前值
    val output = dut.io.output.peek()
    println(s"Output value: ${output.litValue}")

    dut.io.output.expect(84.U)
  }
}
```

### 生成波形文件

ChiselTest自动生成VCD波形文件（如果使用Verilator后端）：

```bash
# 运行测试后，波形文件位于：
# test_run_dir/MyModuleSpec_should_xxx/MyModule.vcd
```

使用GTKWave查看：
```bash
gtkwave test_run_dir/MyModuleSpec_should_do_something/MyModule.vcd
```

### 条件断点

```scala
it should "conditional debug" in {
  test(new MyModule) { dut =>
    for (i <- 0 until 100) {
      dut.io.input.poke(i.U)
      dut.clock.step(1)

      val output = dut.io.output.peek()

      // 条件断点
      if (output.litValue > 50) {
        println(s"Output exceeded 50 at iteration $i: ${output.litValue}")
        assert(output.litValue <= 100, "Output too large")
      }
    }
  }
}
```

### 使用ScalaTest匹配器

```scala
import org.scalatest.matchers.should.Matchers._

it should "use ScalaTest matchers" in {
  test(new MyModule) { dut =>
    val result = dut.io.output.peek().litValue

    result should be >= 0
    result should be <= 100
    result shouldEqual 42
  }
}
```

---

## 测试最佳实践

### 1. 测试命名

好的命名：
```scala
it should "write and read back from register x1"
it should "generate interrupt when mtime >= mtimecmp"
it should "handle divide by zero correctly"
```

不好的命名：
```scala
it should "test register"
it should "work"
it should "check something"
```

### 2. 测试隔离

每个测试应该独立：
```scala
// 好的实践 - 每个测试创建新的DUT
it should "test A" in {
  test(new MyModule) { dut => /* ... */ }
}

it should "test B" in {
  test(new MyModule) { dut => /* ... */ }
}
```

### 3. 使用描述性断言消息

```scala
// 好的实践
dut.io.output.expect(42.U, "ALU result should be 42 for ADD(40, 2)")

// 不好的实践
dut.io.output.expect(42.U)
```

### 4. 测试组织

```scala
class MyModuleSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "MyModule"

  // 分组相关测试
  it should "perform basic operations" in { /* ... */ }
  it should "handle edge cases" in { /* ... */ }

  behavior of "MyModule advanced features"

  it should "support pipelining" in { /* ... */ }
  it should "handle hazards" in { /* ... */ }
}
```

---

## CI/CD集成

### GitHub Actions示例

创建`.github/workflows/test.yml`:

```yaml
name: Run Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v3

    - name: Setup JDK 11
      uses: actions/setup-java@v3
      with:
        java-version: '11'
        distribution: 'temurin'

    - name: Run tests
      run: sbt test

    - name: Upload test results
      if: always()
      uses: actions/upload-artifact@v3
      with:
        name: test-results
        path: target/test-reports/
```

### 测试覆盖率

使用scoverage插件（需要在project/plugins.sbt中添加）：

```scala
// project/plugins.sbt
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.9")
```

运行覆盖率测试：
```bash
sbt clean coverage test coverageReport
```

---

## 故障排除

### 常见问题

#### 1. "Module X not found"
确保模块已编译：
```bash
sbt compile
sbt test:compile
```

#### 2. 测试超时
增加超时时间：
```scala
it should "long running test" in {
  test(new MyModule).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
    // 测试逻辑
  }
}
```

#### 3. 波形文件未生成
添加VCD注解：
```scala
test(new MyModule).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
  // 测试逻辑
}
```

---

## 下一步

1. **添加更多测试**: 参考现有测试模板
2. **提高覆盖率**: 使用scoverage分析
3. **集成测试**: 测试多个模块协同工作
4. **性能测试**: 测量CPI和吞吐量

---

## 参考资料

- [ChiselTest Documentation](https://github.com/ucb-bar/chiseltest)
- [ScalaTest User Guide](https://www.scalatest.org/user_guide)
- [SBT Documentation](https://www.scala-sbt.org/1.x/docs/)

---

**最后更新**: 2025-11-07
**维护者**: RV32E SoC Team
