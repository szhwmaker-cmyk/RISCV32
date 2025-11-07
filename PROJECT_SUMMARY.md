# RV32E SoC 项目总结

## 项目完成情况

✅ **已完成**: 基于Chisel 6.5的RV32E 5级流水线SoC系统的完整实现

## 核心成果

### 1. 处理器核心 (RV32E)

**实现文件**: `src/main/scala/core/`

- ✅ **RegFile.scala**: 16个寄存器（x0-x15），支持双读单写，含写后立即读转发
- ✅ **ALU.scala**: 完整的算术逻辑单元，支持RV32EM指令（包括乘除法）
- ✅ **Decoder.scala**: 指令译码器，支持所有RV32E基础指令
- ✅ **PipelineRegs.scala**: 流水线寄存器定义（IF/ID, ID/EX, EX/MEM, MEM/WB）
- ✅ **HazardUnit.scala**: 冒险检测和数据转发单元
  - EX-EX 转发
  - MEM-EX 转发
  - LOAD-USE 冒险检测和暂停
  - 分支/跳转流水线刷新
- ✅ **RV32ECore.scala**: 完整的5级流水线处理器顶层

**特性**:
- 5级流水线: IF → ID → EX → MEM → WB
- 数据转发减少冒险开销
- 分支预测（静态不跳转）
- 支持M扩展（MUL, DIV, REM）

### 2. 总线系统 (Wishbone B4)

**实现文件**: `src/main/scala/bus/`

- ✅ **WishboneBus.scala**: Wishbone B4接口定义
  - 主设备接口（WishboneMaster）
  - 从设备接口（WishboneSlave）
  - 总线仲裁器
  - 多路复用器
- ✅ **WishboneInterconnect.scala**: 总线互联和地址解码
  - 自动地址路由
  - 双总线支持（指令总线和数据总线）
  - 仲裁逻辑

### 3. 存储器模块

**实现文件**: `src/main/scala/peripherals/`

- ✅ **BootROM.scala**: 64KB 只读存储器
  - 存储启动代码
  - 单周期读取
  - 支持从文件加载初始内容
- ✅ **SRAM.scala**: 128KB 读写存储器
  - 单周期读写
  - 支持字节、半字、字访问
  - 字节选择掩码

### 4. 外设模块

**实现文件**: `src/main/scala/peripherals/`

- ✅ **UART16550.scala**: UART 串口控制器
  - 16550 兼容
  - 可配置波特率（默认115200）
  - 16字节发送/接收FIFO
  - 中断支持

- ✅ **GPIO.scala**: 通用输入输出
  - 32位可配置I/O
  - 独立方向控制
  - 输出使能控制

- ✅ **Timer.scala**: 64位系统定时器
  - 递增计数器
  - 比较匹配中断
  - 兼容RISC-V mtime/mtimecmp

- ✅ **SPIMaster.scala**: SPI 主控制器（简化版）
  - 基本SPI传输
  - 可配置时钟分频
  - 多片选支持

- ✅ **I2CMaster.scala**: I2C 主控制器（简化版）
  - 基本I2C传输
  - 标准/快速模式

### 5. SoC 顶层集成

**实现文件**: `src/main/scala/soc/WishboneSoc.scala`

- ✅ 处理器核心集成
- ✅ 所有外设连接
- ✅ 总线互联配置
- ✅ 中断信号路由
- ✅ Verilog生成主程序

### 6. 固件支持

**实现文件**: `firmware/`

#### BootROM 启动代码
- ✅ `bootrom/bootrom.S`: 汇编启动代码
  - 初始化栈指针
  - 配置UART
  - 输出启动消息
  - 跳转到SRAM
- ✅ `bootrom/bootrom.ld`: 链接脚本
- ✅ `bootrom/Makefile`: 构建脚本

#### RT-Thread 测试程序
- ✅ `rtthread/main.c`: 测试主程序
  - 系统信息输出
  - GPIO测试
  - 外设功能验证
  - LED心跳
- ✅ `rtthread/start.S`: C运行时环境
- ✅ `rtthread/rtthread.ld`: 链接脚本
- ✅ `rtthread/Makefile`: 构建脚本

### 7. 配置和构建

**实现文件**:
- ✅ `build.sbt`: SBT构建配置（Chisel 6.5）
- ✅ `build.sc`: Mill构建配置（Chisel 6.5）
- ✅ `src/main/scala/common/Config.scala`: 系统配置参数
- ✅ `scripts/build.sh`: 自动化构建脚本

## 地址空间映射

| 地址范围 | 大小 | 设备 | 描述 |
|---------|------|------|------|
| `0x0000_0000 - 0x0000_FFFF` | 64KB | BootROM | 启动代码 |
| `0x1000_0000 - 0x1000_0FFF` | 4KB | UART | 串口控制器 |
| `0x1000_1000 - 0x1000_1FFF` | 4KB | SPI | SPI主控制器 |
| `0x1000_2000 - 0x1000_2FFF` | 4KB | I2C | I2C主控制器 |
| `0x1000_3000 - 0x1000_3FFF` | 4KB | GPIO | 通用IO |
| `0x1000_4000 - 0x1000_4FFF` | 4KB | Timer | 系统定时器 |
| `0x2000_0000 - 0x2001_FFFF` | 128KB | SRAM | 主内存 |

## 技术栈

- **硬件描述语言**: Chisel 6.5
- **构建工具**: Mill / SBT
- **Scala版本**: 2.13.14
- **目标**: Verilog/SystemVerilog生成

## 启动流程

```
1. 复位 → PC = 0x00000000 (BootROM)
   ↓
2. BootROM初始化
   - 设置栈指针: SP = 0x20020000
   - 配置UART (115200 bps)
   - 输出启动消息
   ↓
3. 跳转到SRAM (0x20000000)
   ↓
4. 执行RT-Thread测试程序
   - 系统信息输出
   - GPIO测试
   - 外设验证
   ↓
5. 进入空闲循环（LED心跳）
```

## 性能指标

| 指标 | 目标值 | 说明 |
|-----|--------|------|
| 最大频率 | 50 MHz | FPGA综合目标 |
| 平均CPI | ~1.35 | 包含冒险开销 |
| LOAD-USE惩罚 | 1周期 | 流水线暂停 |
| 分支错误预测 | 2周期 | 流水线刷新 |
| 内存访问 | 1-2周期 | 单周期RAM |

## 项目结构

```
rv32e_soc/
├── src/main/scala/
│   ├── common/           # 系统配置
│   ├── core/             # 处理器核心（7个文件）
│   ├── bus/              # Wishbone总线（2个文件）
│   ├── peripherals/      # 外设模块（7个文件）
│   └── soc/              # SoC顶层（1个文件）
├── firmware/
│   ├── bootrom/          # 启动代码（3个文件）
│   └── rtthread/         # 测试程序（4个文件）
├── scripts/              # 构建脚本
├── docs/                 # 文档
├── build.sbt            # SBT配置
├── build.sc             # Mill配置
└── README.md            # 项目说明
```

**统计**:
- Scala源文件: 17个
- 总代码行数: 约2600行
- 固件文件: 7个
- 文档文件: 2个

## 使用方法

### 编译Chisel代码
```bash
mill rv32e_soc.compile
# 或
sbt compile
```

### 生成Verilog
```bash
mill rv32e_soc.runMain soc.WishboneSocMain
# 或
./scripts/build.sh verilog
```

### 编译固件（需要RISC-V工具链）
```bash
cd firmware/bootrom && make
cd firmware/rtthread && make
```

### 一键构建
```bash
./scripts/build.sh all
```

## 下一步计划

### 短期（已规划）
- [ ] 实现CSR寄存器
- [ ] 添加异常和中断处理
- [ ] 编写完整测试套件
- [ ] 完善SPI和I2C实现
- [ ] FPGA综合和验证

### 中期（可选扩展）
- [ ] 添加JTAG调试接口
- [ ] 实现缓存系统
- [ ] 性能优化
- [ ] 支持更多外设（Ethernet, USB等）

### 长期（研究方向）
- [ ] 多核支持
- [ ] 动态分支预测
- [ ] 乱序执行
- [ ] 虚拟内存

## 验证状态

| 模块 | 单元测试 | 集成测试 | 状态 |
|-----|---------|---------|------|
| RegFile | 待添加 | - | ⚠️ |
| ALU | 待添加 | - | ⚠️ |
| Decoder | 待添加 | - | ⚠️ |
| Pipeline | 待添加 | - | ⚠️ |
| Wishbone | 待添加 | - | ⚠️ |
| BootROM | 待添加 | - | ⚠️ |
| SRAM | 待添加 | - | ⚠️ |
| UART | 待添加 | - | ⚠️ |
| SoC Top | 待添加 | 待添加 | ⚠️ |

## 贡献者

- 设计和实现: Claude (AI)
- 项目发起: szhwmaker-cmyk

## 许可证

MIT License

## 参考资料

1. [RISC-V 指令集规范](https://riscv.org/specifications/)
2. [Wishbone B4 规范](https://cdn.opencores.org/downloads/wbspec_b4.pdf)
3. [Chisel 6.5 文档](https://www.chisel-lang.org/)
4. [RT-Thread 文档](https://www.rt-thread.org/)

---

**项目状态**: ✅ 核心功能已完成，待测试验证

**最后更新**: 2025-11-07

**仓库**: https://github.com/szhwmaker-cmyk/RISCV32
