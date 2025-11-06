# RV32E SoC 主 Makefile

.PHONY: all clean help test verilog compile bootloader firmware fpga

# 默认目标
all: compile

##############################################################################
# Chisel 编译和测试
##############################################################################

# 编译 Chisel 代码
compile:
	@echo "=== Compiling Chisel code ==="
	mill rv32e_soc.compile

# 运行所有测试
test:
	@echo "=== Running all tests ==="
	mill rv32e_soc.test

# 运行特定测试
test-%:
	@echo "=== Running test: $* ==="
	mill rv32e_soc.test.testOnly $*

# 生成 Verilog
verilog:
	@echo "=== Generating Verilog ==="
	mill rv32e_soc.verilog
	@echo "Verilog files generated in: generated/"

##############################################################################
# 软件构建
##############################################################################

# 构建 Bootloader
bootloader:
	@echo "=== Building Bootloader ==="
	$(MAKE) -C software/bootloader

# 构建 Firmware
firmware:
	@echo "=== Building Firmware ==="
	$(MAKE) -C software/firmware

# 构建所有软件
software: bootloader firmware
	@echo "=== Software build complete ==="

##############################################################################
# FPGA 综合
##############################################################################

# Vivado 综合
fpga: verilog
	@echo "=== Running Vivado synthesis ==="
	cd fpga/scripts && vivado -mode batch -source vivado_build.tcl

# 清理 Vivado 项目
fpga-clean:
	@echo "=== Cleaning Vivado project ==="
	rm -rf fpga/scripts/vivado_project

##############################################################################
# 仿真
##############################################################################

# Verilator 仿真
sim: verilog
	@echo "=== Building Verilator simulation ==="
	@echo "TODO: Add Verilator simulation scripts"

##############################################################################
# 文档生成
##############################################################################

# 生成 Scaladoc
doc:
	@echo "=== Generating Scaladoc ==="
	mill rv32e_soc.docJar

##############################################################################
# 清理
##############################################################################

# 清理 Chisel 编译产物
clean-chisel:
	@echo "=== Cleaning Chisel artifacts ==="
	mill clean
	rm -rf generated/
	rm -rf out/
	rm -rf test_run_dir/

# 清理软件
clean-software:
	@echo "=== Cleaning software ==="
	$(MAKE) -C software/bootloader clean
	$(MAKE) -C software/firmware clean

# 完全清理
clean: clean-chisel clean-software fpga-clean
	@echo "=== Clean complete ==="

##############################################################################
# 代码格式化
##############################################################################

# 格式化 Scala 代码
format:
	@echo "=== Formatting Scala code ==="
	mill mill.scalalib.scalafmt.ScalafmtModule/reformatAll __.sources

# 检查格式
format-check:
	@echo "=== Checking Scala code format ==="
	mill mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll __.sources

##############################################################################
# 发布
##############################################################################

# 创建发布包
release: clean all software verilog
	@echo "=== Creating release package ==="
	mkdir -p release
	cp -r generated/ release/verilog/
	cp software/bootloader/bootloader.bin release/
	cp software/firmware/firmware.bin release/
	tar czf release/rv32e_soc_$(shell date +%Y%m%d).tar.gz release/
	@echo "Release package created: release/rv32e_soc_$(shell date +%Y%m%d).tar.gz"

##############################################################################
# 开发工具
##############################################################################

# 启动 Mill 交互式 REPL
repl:
	mill -i rv32e_soc.console

# 查看依赖树
deps:
	mill show rv32e_soc.ivyDeps

# 更新依赖
update-deps:
	mill mill.scalalib.Dependency/updates

##############################################################################
# 帮助
##############################################################################

help:
	@echo "RV32E SoC Build System"
	@echo ""
	@echo "Targets:"
	@echo "  all           - Compile Chisel code (default)"
	@echo "  compile       - Compile Chisel code"
	@echo "  test          - Run all tests"
	@echo "  test-<name>   - Run specific test"
	@echo "  verilog       - Generate Verilog"
	@echo ""
	@echo "  bootloader    - Build bootloader"
	@echo "  firmware      - Build firmware"
	@echo "  software      - Build all software"
	@echo ""
	@echo "  fpga          - Synthesize for FPGA (Vivado)"
	@echo "  fpga-clean    - Clean FPGA build artifacts"
	@echo "  sim           - Build Verilator simulation"
	@echo ""
	@echo "  doc           - Generate Scaladoc"
	@echo "  format        - Format Scala code"
	@echo "  format-check  - Check Scala code format"
	@echo ""
	@echo "  clean         - Clean all build artifacts"
	@echo "  clean-chisel  - Clean Chisel artifacts only"
	@echo "  clean-software- Clean software artifacts only"
	@echo ""
	@echo "  release       - Create release package"
	@echo "  repl          - Start interactive REPL"
	@echo "  deps          - Show dependencies"
	@echo "  help          - Show this help"
	@echo ""
	@echo "Examples:"
	@echo "  make test-core.RegFileTest  - Run RegFile test"
	@echo "  make verilog                - Generate Verilog"
	@echo "  make fpga                   - Full FPGA synthesis"

##############################################################################
# 快速测试
##############################################################################

# 快速测试（只运行核心测试）
quick-test:
	@echo "=== Running quick tests ==="
	mill rv32e_soc.test.testOnly core.RegFileTest
	mill rv32e_soc.test.testOnly core.ALUTest

# CI 测试（持续集成）
ci-test: clean compile test

##############################################################################
# 项目信息
##############################################################################

info:
	@echo "=== RV32E SoC Project Information ==="
	@echo "Project: RV32E 5-Stage Pipeline Processor"
	@echo "HDL: Chisel 5.1.0"
	@echo "Build Tool: Mill"
	@echo ""
	@echo "Features:"
	@echo "  - RV32E ISA (16 registers)"
	@echo "  - 5-stage pipeline with forwarding"
	@echo "  - Wishbone B4 bus"
	@echo "  - SPI Flash boot"
	@echo "  - UART, GPIO, SPI, I2C peripherals"
	@echo ""
	@echo "Directories:"
	@echo "  src/main/scala/ - Chisel source code"
	@echo "  src/test/scala/ - Test code"
	@echo "  software/       - Firmware and bootloader"
	@echo "  fpga/           - FPGA synthesis files"
	@echo "  docs/           - Documentation"
	@echo ""
