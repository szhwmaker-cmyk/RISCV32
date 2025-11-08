/*
 * RV32E 启动代码
 * 设置堆栈并跳转到main函数
 */

.section .text.start
.global _start

_start:
    # 设置堆栈指针 (RAM 顶部)
    lui sp, %hi(0x80040000)
    addi sp, sp, %lo(0x80040000)

    # 清零 BSS 段 (如果有)
    # (简化版本，假设没有BSS)

    # 跳转到 main 函数
    jal ra, main

    # 如果 main 返回，进入死循环
_exit:
    j _exit

# 中断向量表 (简化版本，RV32E不需要复杂的中断处理)
.section .text.trap
.global trap_vector
trap_vector:
    j trap_vector   # 简单的死循环
