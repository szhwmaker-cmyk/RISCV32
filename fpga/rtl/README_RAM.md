# FPGA RAM Modules

本目录包含用于FPGA部署的RAM模块实现。

## 📁 文件说明

### `xilinx_bram.v`
- **平台**: Xilinx (Artix-7, Kintex-7, Virtex-7, Zynq, UltraScale)
- **功能**: Block RAM包装器，兼容FpgaBlockRamBlackBox接口
- **实现**: 包含3种选项（默认使用推断式）

### `altera_ram.v`
- **平台**: Intel/Altera (Cyclone, Arria, Stratix)
- **功能**: M10K/M20K RAM包装器
- **实现**: 包含3种选项（默认使用推断式）

### `fpga_top.v`
- **功能**: FPGA顶层模块
- **用途**: 时钟、复位、I/O管理

## 🚀 快速使用

### 默认配置（推荐）

两个RAM文件都配置为**自动推断模式**，无需额外操作：

1. 生成Verilog:
```bash
mill rv32e_soc.runMain circt.stage.ChiselMain --module rv32e.soc.MinimalSoc --target-dir fpga/generated
```

2. 综合时会自动使用Block RAM

### Xilinx高级配置

如果需要使用Block Memory Generator IP:

1. 生成IP:
```bash
cd ../scripts
vivado -mode batch -source create_bram_ip.tcl
```

2. 编辑 `xilinx_bram.v`，取消注释IP实例化部分

3. 在项目中添加生成的XCI文件

### Intel/Altera高级配置

如果需要使用altsyncram:

1. 编辑 `altera_ram.v`
2. 取消注释altsyncram实例化部分
3. 重新综合

## 📊 资源使用

64KB RAM在不同平台的Block RAM使用:

| FPGA平台 | Block RAM类型 | 使用数量 |
|---------|--------------|---------|
| Artix-7 35T | RAMB36E1 | 36/50 (72%) |
| Cyclone V | M10K | 128/128 (100%) |
| Cyclone 10 | M9K | 144/144 (100%) |

> **注意**: 如果Block RAM不足，可以减小RAM大小（在Config.scala中修改RAM_SIZE_KB）

## 🔧 配置选项

### Xilinx

在 `xilinx_bram.v` 中可选择:
- 推断式RAM (默认) - 自动，无需IP
- Block Memory Generator IP - 最优性能
- RAMB36E1原语 - 最大控制

### Intel/Altera

在 `altera_ram.v` 中可选择:
- 推断式RAM (默认) - 使用ramstyle属性
- altsyncram - Quartus megafunction
- Platform Designer IP - GUI生成

## 🧪 验证

### 检查推断结果

**Xilinx (Vivado)**:
```
vivado/rv32e_soc_fpga.runs/synth_1/post_synth_util.rpt
```
查找: `Block RAM Tile`

**Intel (Quartus)**:
```
output_files/rv32e_soc.fit.rpt
```
查找: `M10K blocks`

### 预期结果

应该看到RAM被正确推断为Block RAM，而不是分布式RAM或逻辑单元。

## 📚 更多信息

详细配置指南请参考:
- `../../FPGA_RAM_GUIDE.md` - 完整配置文档
- `../../FPGA_DEPLOYMENT.md` - FPGA部署指南

## ⚠️ 注意事项

1. **字节使能**: 两种实现都支持4位字节使能
2. **延迟**: 默认配置为1周期读延迟
3. **初始化**: 支持从.mem或.coe文件初始化
4. **仿真**: 推断式实现对仿真友好，无需额外库

## 🔄 版本历史

- v1.0 (2025-11-05): 初始版本，支持Xilinx和Intel/Altera
