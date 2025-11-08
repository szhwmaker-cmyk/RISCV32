# RV32E Bootloader
# 简单的启动加载程序
# 功能：初始化系统并跳转到主程序

.section .text
.global _start

_start:
    # 设置栈指针
    lui sp, 0x20010      # SP = 0x20010000 (RAM顶部)

    # 清除BSS段
    la  a0, __bss_start
    la  a1, __bss_end
clear_bss:
    bgeu a0, a1, bss_done
    sw  zero, 0(a0)
    addi a0, a0, 4
    j   clear_bss

bss_done:
    # 初始化UART
    li  a0, 0x40000000   # UART基地址
    li  a1, 0x03         # 启用TX和RX
    sw  a1, 8(a0)        # 写入CTRL寄存器

    # 打印启动消息
    la  a0, boot_msg
    call uart_print

    # 初始化GPIO
    li  a0, 0x40001000   # GPIO基地址
    li  a1, 0xFFFF       # 所有引脚为输出
    sw  a1, 8(a0)        # 写入DIR寄存器

    # LED闪烁测试
    li  a2, 0xAAAA
    sw  a2, 4(a0)        # 写入DATA_OUT

    # 跳转到主程序
    la  ra, main
    jr  ra

# UART打印函数
# a0 = 字符串地址
uart_print:
    li  t0, 0x40000000   # UART基地址
print_loop:
    lbu t1, 0(a0)
    beqz t1, print_done

    # 等待TX就绪
wait_tx:
    lw  t2, 4(t0)        # 读取STATUS
    andi t2, t2, 1       # 检查TX_READY位
    beqz t2, wait_tx

    # 发送字符
    sw  t1, 0(t0)        # 写入DATA
    addi a0, a0, 1
    j   print_loop

print_done:
    ret

.section .rodata
boot_msg:
    .string "RV32E Bootloader v1.0\\r\\nInitializing...\\r\\n"

.section .bss
__bss_start:
.section .bss.end
__bss_end:
