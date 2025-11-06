# RV32E Firmware 启动代码

    .section .text.init
    .globl _start
    .type _start, @function

_start:
    # 禁用中断
    csrw    mie, x0

    # 初始化栈指针 (x2 = sp)
    # 栈从 RAM 顶部向下增长
    la      x2, __stack_top

    # 设置全局指针 (x3 = gp)
    .option push
    .option norelax
    la      x3, __global_pointer$
    .option pop

    # 设置线程指针 (x4 = tp)
    li      x4, 0

    # 清零所有寄存器（除了 x0, x2, x3, x4）
    li      x1, 0
    li      x5, 0
    li      x6, 0
    li      x7, 0
    li      x8, 0
    li      x9, 0
    li      x10, 0
    li      x11, 0
    li      x12, 0
    li      x13, 0
    li      x14, 0
    li      x15, 0

    # 初始化数据段（如果需要）
    # 在从 Flash 启动的情况下，这通常由 bootloader 完成
    # 但如果直接从 RAM 运行，需要复制 .data 段

    la      x10, __data_start      # 目标地址
    la      x11, __data_end        # 结束地址
    la      x12, __data_load       # 源地址（LMA）

    # 检查是否需要复制
    beq     x10, x12, data_done    # 如果源=目标，跳过

data_copy:
    bge     x10, x11, data_done
    lw      x13, 0(x12)
    sw      x13, 0(x10)
    addi    x10, x10, 4
    addi    x12, x12, 4
    j       data_copy

data_done:
    # 清零 BSS 段
    la      x10, __bss_start
    la      x11, __bss_end

bss_clear:
    bge     x10, x11, bss_done
    sw      x0, 0(x10)
    addi    x10, x10, 4
    j       bss_clear

bss_done:
    # 调用全局构造函数（C++）
    # 对于纯 C 程序可以省略
    # call    __libc_init_array

    # 调用 main 函数
    call    main

    # main 返回后的处理
_exit:
    # 保存返回值
    mv      x5, x10

    # 无限循环
1:
    wfi     # 等待中断（省电）
    j       1b

    .size _start, . - _start

# 异常处理函数（简化版本）
    .section .text
    .globl trap_handler
    .align 4
trap_handler:
    # 保存上下文
    addi    x2, x2, -64
    sw      x1, 0(x2)
    sw      x5, 4(x2)
    sw      x6, 8(x2)
    sw      x7, 12(x2)
    sw      x10, 16(x2)
    sw      x11, 20(x2)
    sw      x12, 24(x2)
    sw      x13, 28(x2)
    sw      x14, 32(x2)
    sw      x15, 36(x2)

    # 读取异常原因
    csrr    x10, mcause

    # 这里应该根据异常类型调用相应的处理函数
    # 简化起见，直接返回

trap_return:
    # 恢复上下文
    lw      x1, 0(x2)
    lw      x5, 4(x2)
    lw      x6, 8(x2)
    lw      x7, 12(x2)
    lw      x10, 16(x2)
    lw      x11, 20(x2)
    lw      x12, 24(x2)
    lw      x13, 28(x2)
    lw      x14, 32(x2)
    lw      x15, 36(x2)
    addi    x2, x2, 64

    # 从异常返回
    mret
