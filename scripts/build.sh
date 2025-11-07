#!/bin/bash
# RV32E SoC 构建脚本

set -e

echo "=========================================="
echo "RV32E SoC Build Script"
echo "=========================================="

# 检查 mill 是否安装
if ! command -v mill &> /dev/null; then
    echo "Error: mill is not installed"
    echo "Please install mill from: https://mill-build.com/"
    exit 1
fi

# 创建输出目录
mkdir -p generated

# 选择构建目标
case "${1:-compile}" in
    "compile")
        echo "Compiling Chisel code..."
        mill rv32e_soc.compile
        ;;

    "test")
        echo "Running tests..."
        mill rv32e_soc.test
        ;;

    "verilog")
        echo "Generating Verilog..."
        mill rv32e_soc.runMain soc.WishboneSocMain
        ;;

    "firmware")
        echo "Building firmware..."
        cd firmware/bootrom
        make clean
        make
        cd ../rtthread
        make clean
        make
        cd ../..
        ;;

    "all")
        echo "Building everything..."
        mill rv32e_soc.compile
        mill rv32e_soc.test
        mill rv32e_soc.runMain soc.WishboneSocMain
        echo "Build firmware (requires RISC-V toolchain)..."
        if command -v riscv32-unknown-elf-gcc &> /dev/null; then
            cd firmware/bootrom && make && cd ../..
            cd firmware/rtthread && make && cd ../..
        else
            echo "Warning: RISC-V toolchain not found, skipping firmware build"
        fi
        ;;

    "clean")
        echo "Cleaning..."
        mill clean
        rm -rf generated/*
        rm -rf out/
        cd firmware/bootrom && make clean && cd ../..
        cd firmware/rtthread && make clean && cd ../..
        ;;

    *)
        echo "Usage: $0 {compile|test|verilog|firmware|all|clean}"
        exit 1
        ;;
esac

echo "=========================================="
echo "Build completed successfully!"
echo "=========================================="
