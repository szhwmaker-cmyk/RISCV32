# RV32E Bootloader
#
# 功能：
# 1. 从 SPI Flash (0x10000000) 读取程序
# 2. 将程序复制到 RAM (0x80000000)
# 3. 跳转到 RAM 执行
#
# 注：由于 RV32E 只有 16 个寄存器 (x0-x15)，需要特别注意寄存器使用

    .section .text.boot
    .globl _start

_start:
    # 初始化栈指针 (x2 = sp)
    li      x2, 0x80010000          # 64KB RAM 顶部

    # 设置全局指针 (x3 = gp)
    la      x3, __global_pointer$

    # 清零 BSS 段
    la      x10, __bss_start
    la      x11, __bss_end
clear_bss:
    bge     x10, x11, clear_bss_done
    sw      x0, 0(x10)
    addi    x10, x10, 4
    j       clear_bss
clear_bss_done:

    # 从 SPI Flash 复制程序到 RAM
    # 源地址: 0x10001000 (Flash 中的程序起始地址)
    # 目标地址: 0x80000000 (RAM 起始地址)
    # 大小: 由链接脚本定义

    li      x10, 0x10001000         # 源地址 (SPI Flash)
    li      x11, 0x80000000         # 目标地址 (RAM)
    li      x12, 0x00010000         # 复制大小 (64KB)

copy_loop:
    beq     x12, x0, copy_done      # 如果剩余大小为 0，完成
    lw      x13, 0(x10)             # 从 Flash 读取
    sw      x13, 0(x11)             # 写入 RAM
    addi    x10, x10, 4             # 源地址 +4
    addi    x11, x11, 4             # 目标地址 +4
    addi    x12, x12, -4            # 剩余大小 -4
    j       copy_loop

copy_done:
    # 跳转到 RAM 中的主程序
    li      x10, 0x80000000
    jalr    x0, 0(x10)              # 跳转并不返回

    # 不应该到达这里
hang:
    j       hang

    .section .data
boot_msg:
    .ascii "RV32E Bootloader v1.0\n\0"

    .section .rodata
version:
    .word   0x00010000              # Version 1.0
