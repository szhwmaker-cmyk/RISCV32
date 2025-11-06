# RV32E SoC 已知问题与限制

**创建日期**: 2025-11-05
**最后更新**: 2025-11-06
**状态**: 问题追踪中

---

## 📋 问题分类

| 分类 | 严重性 | 数量 | 状态 |
|------|--------|------|------|
| 🔴 关键问题 | 高 | 3 | 2已修复, 1已记录 |
| 🟡 重要问题 | 中 | 5 | 部分修复 |
| 🟢 次要问题 | 低 | 4 | 3已记录, 1已修复 |

---

## 🔴 关键问题（高优先级）

### 问题 #1: 位宽比较隐患

**文件**: `src/main/scala/core/Hazard.scala`, `src/main/scala/core/RegFile.scala`

**问题描述**:
在多处使用 `0.U` 或 `1.U` 进行比较时，未显式指定位宽，可能导致 FIRRTL 编译警告或错误。

**具体位置**:
```scala
// Hazard.scala
io.ex_mem_rd =/= 0.U  // ex_mem_rd 是 4 位，0.U 默认 1 位
io.ex_mem_rd === io.id_ex_rs1  // 可能位宽不匹配

// RegFile.scala
io.rd_addr =/= 0.U  // rd_addr 是 4 位
```

**影响**:
- FIRRTL 编译可能失败或警告
- 仿真行为可能与预期不符
- 综合后逻辑可能异常

**解决方案**:
```scala
// 正确写法
io.ex_mem_rd =/= 0.U(4.W)  // 显式 4 位宽度
io.rd_addr =/= 0.U(4.W)
```

**优先级**: 🔴 高
**预计修复时间**: 1小时
**状态**: ✅ 已修复 (2025-11-05)

---

### 问题 #2: SPI Flash 自动读取未实现

**文件**: `src/main/scala/peripherals/SpiFlash.scala`

**问题描述**:
代码中存在 TODO 注释：
```scala
// TODO: Implement automatic read triggered by memory access
```

这意味着 XIP (Execute-In-Place) 模式未完整实现。

**影响**:
- Boot 流程依赖手动从 Flash 拷贝到 RAM
- 如果 Boot ROM 未正确实现，系统无法启动
- 文档中提到的"SPI Flash XIP"功能实际不可用

**当前状态**:
- SPI Flash 仅支持手动读取（寄存器访问）
- 不支持直接从 Flash 地址空间取指令

**解决方案**:
1. **短期**: 在文档中明确说明当前限制
2. **长期**: 实现自动 read-on-access 逻辑

**优先级**: 🔴 高（功能缺失）
**预计工作量**: 2-3天
**状态**: 📝 已记录，已详细分析
**详细分析**: 参见 `BOOT_FLOW_ANALYSIS.md`，已创建 FlashMemoryModel 用于仿真测试

---

### 问题 #3: 测试覆盖不足

**问题描述**:
项目缺少关键功能的单元测试和断言，包括：

**缺失的测试**:
1. ❌ Reset 后寄存器/PC 初始化值验证
2. ❌ Load-Use Hazard 的完整测试向量
3. ❌ 分支 Flush 机制验证
4. ❌ Wishbone ACK 超时处理
5. ❌ 异常处理流程测试（ECALL/EBREAK）
6. ❌ CSR 读写正确性测试

**缺失的运行时断言**:
```scala
// 应该添加的 Chisel assert
assert(pc_reg < Config.MAX_PC.U, "PC overflow")
assert(!load_use_hazard || stall, "Load-use not stalled")
```

**影响**:
- 隐藏的 Bug 难以发现
- 仿真中错误可能被忽略
- 难以确保正确性

**解决方案**:
1. 添加 Chisel `assert` 语句到关键路径
2. 编写针对性测试用例
3. 使用 ChiselTest 验证边界条件

**优先级**: 🔴 高
**预计工作量**: 3-5天
**状态**: 📝 已记录

---

## 🟡 重要问题（中优先级）

### 问题 #4: RegFile 时序语义不明确

**文件**: `src/main/scala/core/RegFile.scala`

**问题描述**:
RegFile 实现为"2 异步读 + 1 同步写 + write-through"，但混合使用了：
- `Vec(Reg)` - 同步存储
- 组合读逻辑 - 异步读

**潜在风险**:
```scala
// 当前实现
val regfile = Reg(Vec(16, UInt(32.W)))
io.rs1_data := Mux(io.rd_wen && io.rd_addr === io.rs1_addr,
                   io.rd_data, regfile(io.rs1_addr))
```

这在仿真中可能正常，但综合后时序可能不同：
- 某些工具可能推断为寄存器文件 vs 分布式 RAM
- Write-through 逻辑增加组合路径延迟

**建议**:
1. 明确注释时序假设
2. 考虑使用 `SyncReadMem` 替代 `Vec(Reg)` 以减少资源
3. 或显式使用异步 RAM 原语

**优先级**: 🟡 中
**状态**: ⚠️ 需要评审

---

### 问题 #5: Hazard 控制逻辑简化

**文件**: `src/main/scala/core/Hazard.scala`

**问题描述**:
Load-Use Hazard 检测仅检查 `ex_mem` 阶段：
```scala
val load_use_hazard = io.ex_mem_mem_read && (
  (io.ex_mem_rd === io.id_ex_rs1) ||
  (io.ex_mem_rd === io.id_ex_rs2)
)
```

**潜在缺陷**:
1. 未考虑 `id_ex` 阶段的 load（如果需要）
2. 分支 + Load-Use 同时发生时的优先级不明确
3. 异常发生时的 stall/flush 交互未测试

**实际影响**:
- 当前实现对于简单 5 级流水线是正确的
- 但扩展到多周期指令时可能有问题

**建议**:
添加更详细的注释说明假设和限制

**优先级**: 🟡 中
**状态**: ✅ 当前实现正确，但需文档化

---

### 问题 #6: Boot ROM 功能占位

**文件**: `src/main/scala/boot/BootROM.scala`, `src/main/scala/boot/BootController.scala`

**问题描述**:
Boot 相关代码包含大量注释示例，但实际实现可能不完整或为占位符。

**当前状态**:
- BootROM 存在但功能有限
- Boot Controller 状态机存在但可能未测试
- 依赖外部加载程序到 RAM

**影响**:
- 无法从 Flash 自动启动
- 需要手动初始化 RAM

**建议**:
1. 文档中明确说明当前 Boot 流程
2. 提供手动加载程序的示例
3. 标记为"未来增强"功能

**优先级**: 🟡 中
**状态**: 📝 已记录

---

### 问题 #7: Wishbone 互联验证不足

**文件**: `src/main/scala/bus/Interconnect.scala`

**问题描述**:
Wishbone Pipelined 模式的复杂时序未充分验证：
- STB/CYC/ACK 握手在并发请求时的行为
- 地址解码与仲裁的优先级
- 突发传输支持

**潜在风险**:
- 多 Master 同时访问时可能死锁
- ACK 信号路由错误
- 地址冲突处理不当

**缓解措施**:
- 当前只有单 Master (Core)，风险较低
- 简单仲裁逻辑对当前设计足够

**建议**:
添加 Wishbone 协议检查器（Protocol Checker）

**优先级**: 🟡 中
**状态**: ⚠️ 需要测试

---

### 问题 #8: 异常处理路径未充分测试

**文件**: `src/main/scala/core/Core.scala`, `src/main/scala/core/CSR.scala`

**问题描述**:
新增的 ECALL/EBREAK 异常处理逻辑：
- 未经过完整的仿真测试
- 异常 + 分支同时发生的优先级未明确
- CSR 寄存器初始值和复位行为未验证

**建议测试场景**:
1. ECALL 指令执行后 PC 跳转到 mtvec
2. EBREAK 在分支延迟槽（RV32I 无延迟槽，但概念上）
3. 嵌套异常处理
4. mtvec/mepc/mcause 的读写正确性

**优先级**: 🟡 中
**状态**: 📝 需要添加测试

---

## 🟢 次要问题（低优先级）

### 问题 #9: 位宽截断未显式说明

**文件**: `src/main/scala/core/Decode.scala`

**问题描述**:
指令字段提取时，寄存器地址从 5 位截断到 4 位：
```scala
io.rs1 := rs1(3, 0)  // inst(19,15) -> 4 bits
```

**影响**:
- 对于合法的 RV32E 指令，高位始终为 0
- 但如果指令错误（如使用 x16-x31），会静默截断

**建议**:
添加断言检查高位为 0：
```scala
assert(rs1(4) === 0.U, "Invalid register for RV32E")
```

**优先级**: 🟢 低
**状态**: 📝 已记录

---

### 问题 #10: 跨时钟域考虑

**问题描述**:
文档提到"统一时钟域"，但未来扩展到多时钟时需要考虑：
- 外设（UART, SPI）可能需要独立时钟
- 跨时钟域信号需要同步器（CDC）

**当前状态**:
- 单时钟设计，无 CDC 问题
- 所有模块使用同一个 `clock` 和 `reset`

**建议**:
在 ARCHITECTURE.md 中明确说明：
- "当前版本仅支持单时钟域"
- "多时钟支持需要添加 CDC 逻辑"

**优先级**: 🟢 低
**状态**: 📝 文档化

---

### 问题 #11: SPI Flash 测试模型不完整

**文件**: `src/test/scala/peripherals/SpiFlashModel.scala` (如果存在)

**问题描述**:
仿真测试中的 SPI Flash 模型可能包含占位符，导致测试覆盖不足。

**影响**:
- 无法在仿真中验证 Flash 读取功能
- Boot 流程测试受限

**建议**:
实现简单的 Flash 模拟器：
- 支持基本的 Read 命令
- 可从文件加载初始数据

**优先级**: 🟢 低
**状态**: ✅ 已修复 (2025-11-06)
**修复方案**: 创建了 `FlashMemoryModel.scala` 用于仿真，支持程序加载和完整的 Wishbone 接口

---

### 问题 #12: 文档与代码不完全一致

**问题描述**:
部分文档描述的功能在代码中为占位或简化实现：

| 文档声称 | 实际实现 |
|---------|---------|
| "SPI Flash XIP 支持" | 仅手动读取 |
| "完整 Boot 流程" | 依赖外部初始化 |
| "Wishbone 突发传输" | 未实现 |

**建议**:
更新文档，明确标注：
- ✅ 已实现
- ⏳ 部分实现
- 📝 计划实现
- ❌ 不支持

**优先级**: 🟢 低
**状态**: 📝 需要更新

---

## 🔧 修复计划

### Phase 1: 立即修复（本周）
- [x] 创建问题追踪文档
- [x] 修复关键位宽比较问题 (#1) - ✅ 完成
- [x] 创建 Flash 仿真模型 (#11) - ✅ 完成
- [x] 分析并文档化 Boot 流程 (#2, #6) - ✅ 完成
- [ ] 添加基础运行时断言
- [x] 更新文档说明限制 - ✅ 完成

### Phase 2: 短期改进（2周内）
- [ ] 添加 Load-Use Hazard 测试
- [ ] 实现异常处理测试用例
- [ ] 完善 RegFile 文档和注释
- [ ] 添加 Wishbone 协议检查

### Phase 3: 长期增强（未来版本）
- [ ] 实现 SPI Flash XIP
- [ ] 完整 Boot ROM 实现
- [ ] 多时钟域支持
- [ ] 全面测试覆盖

---

## 📊 问题统计

```
总问题数: 12
├─ 🔴 关键问题: 3 (25%)
├─ 🟡 重要问题: 5 (42%)
└─ 🟢 次要问题: 4 (33%)

修复状态:
├─ ✅ 已修复: 2 (#1 位宽, #11 Flash模型)
├─ ⏳ 修复中: 0
├─ 📝 已记录: 9 (包括详细分析 #2, #6)
└─ ❌ 不修复: 1
```

---

## 🎯 优先级定义

| 级别 | 定义 | 示例 |
|------|------|------|
| 🔴 高 | 影响功能正确性或编译 | 位宽错误、功能缺失 |
| 🟡 中 | 影响可靠性或扩展性 | 测试不足、文档不清 |
| 🟢 低 | 改进或优化建议 | 代码风格、未来增强 |

---

## 📚 参考资料

- **RISC-V 规范**: https://riscv.org/technical/specifications/
- **Chisel 最佳实践**: https://www.chisel-lang.org/
- **Wishbone B4 规范**: https://cdn.opencores.org/downloads/wbspec_b4.pdf

---

## 🙏 致谢

感谢代码审查者发现这些问题，帮助提升项目质量！

---

**维护者**: RV32E SoC Team
**联系方式**: 项目 Issue Tracker
**最后更新**: 2025-11-05
