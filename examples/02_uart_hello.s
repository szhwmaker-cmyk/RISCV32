# =====================================================
# 示例程序 2: UART Hello World
# =====================================================
# 功能：通过UART发送 "Hello RV32E!\n"
# 外设：UART (基地址 0x20000000)
# 难度：⭐⭐ 初级
# =====================================================

.section .data
    # 要发送的消息（以NULL结尾）
    message: .string "Hello RV32E!\n"

.section .text
.globl _start

_start:
    # ========== 初始化UART基地址 ==========
    lui  x1, 0x20000            # x1 = UART_BASE = 0x20000000

    # ========== 加载消息地址 ==========
    lui  x2, %hi(message)       # x2 = 消息地址的高20位
    addi x2, x2, %lo(message)   # x2 = 完整消息地址

send_loop:
    # ========== 读取当前字符 ==========
    lb   x3, 0(x2)              # x3 = *x2 (读取字节)

    # ========== 检查是否为字符串结尾 ==========
    beq  x3, x0, done           # 如果x3 == 0 (NULL)，跳转到done

wait_tx_ready:
    # ========== 等待UART发送就绪 ==========
    lw   x4, 8(x1)              # x4 = UART_STATUS
    andi x4, x4, 0x02           # x4 = STATUS & TX_FULL位
    bne  x4, x0, wait_tx_ready  # 如果TX FIFO满，继续等待

    # ========== 发送字符 ==========
    sw   x3, 0(x1)              # UART_TXDATA = x3

    # ========== 移动到下一个字符 ==========
    addi x2, x2, 1              # x2++（指向下一个字节）
    beq  x0, x0, send_loop      # 跳转到send_loop

done:
    # ========== 程序结束（无限循环） ==========
    beq  x0, x0, done

# =====================================================
# 预期UART输出（115200波特率，8N1）：
# Hello RV32E!
#
# 测试方法：
# screen /dev/ttyUSB1 115200
# 或
# minicom -D /dev/ttyUSB1 -b 115200
# =====================================================
