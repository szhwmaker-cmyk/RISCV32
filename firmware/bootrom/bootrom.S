# BootROM 启动代码
# 用于 RV32E SoC 初始化和跳转到 RT-Thread

.section .text.boot
.globl _start

_start:
    # 初始化栈指针（SRAM末尾）
    lui  sp, 0x20020      # sp = 0x20020000

    # 初始化全局指针
    lui  gp, 0x20010      # gp = 0x20010000

    # 清零 BSS 段（如果需要）
    # 这里简化，假设 RT-Thread 会处理

    # 初始化 UART (115200 baud)
    lui  t0, 0x10000      # UART base = 0x10000000
    li   t1, 27           # divisor = 50MHz / 115200 / 16 ≈ 27
    sb   t1, 0(t0)        # 写入 DLL

    # 设置 LCR (8N1, DLAB=0)
    li   t1, 0x03
    sb   t1, 0x0C(t0)

    # 输出启动消息
    lui  a0, 0x10000
    la   a1, boot_msg
    call uart_puts

    # 跳转到 RT-Thread 入口（假设在 SRAM 0x20000000）
    lui  t0, 0x20000
    jalr zero, 0(t0)

# UART 输出字符串函数
uart_puts:
    lb   t1, 0(a1)
    beqz t1, uart_puts_done
    sb   t1, 0(a0)
    addi a1, a1, 1
    j    uart_puts
uart_puts_done:
    ret

.section .rodata
boot_msg:
    .ascii "RV32E SoC BootROM v1.0\n"
    .ascii "Jumping to RT-Thread...\n"
    .byte 0
