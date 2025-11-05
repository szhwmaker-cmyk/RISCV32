# =====================================================
# 示例程序 3: UART回显
# =====================================================
# 功能：接收UART字符并回显（Echo Server）
# 外设：UART (基地址 0x20000000)
# 难度：⭐⭐ 初级
# =====================================================

.section .text
.globl _start

_start:
    # ========== 初始化 ==========
    lui  x1, 0x20000            # x1 = UART_BASE

    # ========== 发送欢迎消息 ==========
    # 加载欢迎消息地址
    lui  x2, %hi(welcome_msg)
    addi x2, x2, %lo(welcome_msg)
    jal  ra, uart_puts          # 调用uart_puts函数

main_loop:
    # ========== 接收字符 ==========
wait_rx:
    lw   x3, 8(x1)              # x3 = UART_STATUS
    andi x3, x3, 0x01           # 检查RX_EMPTY位
    bne  x3, x0, wait_rx        # 如果RX FIFO空，继续等待

    # 读取接收到的字符
    lw   x4, 4(x1)              # x4 = UART_RXDATA
    andi x4, x4, 0xFF           # 只保留低8位

    # ========== 回显字符 ==========
wait_tx:
    lw   x3, 8(x1)              # x3 = UART_STATUS
    andi x3, x3, 0x02           # 检查TX_FULL位
    bne  x3, x0, wait_tx        # 如果TX FIFO满，等待

    sw   x4, 0(x1)              # UART_TXDATA = x4（发送字符）

    # ========== 特殊处理：回车换行 ==========
    addi x5, x0, 13             # x5 = '\r' (回车)
    beq  x4, x5, send_newline   # 如果接收到'\r'，发送'\n'

    # 继续下一个字符
    beq  x0, x0, main_loop

send_newline:
    # 发送'\n'
    addi x4, x0, 10             # x4 = '\n'

wait_tx_lf:
    lw   x3, 8(x1)
    andi x3, x3, 0x02
    bne  x3, x0, wait_tx_lf

    sw   x4, 0(x1)              # 发送'\n'
    beq  x0, x0, main_loop


# ========================================
# 子程序：发送字符串
# 输入：x2 = 字符串地址
# 使用：x1 (UART_BASE), x3, x4
# ========================================
uart_puts:
    # 保存返回地址
    addi sp, sp, -4
    sw   ra, 0(sp)

puts_loop:
    lb   x3, 0(x2)              # 读取字符
    beq  x3, x0, puts_done      # 如果NULL，结束

puts_wait:
    lw   x4, 8(x1)              # 读STATUS
    andi x4, x4, 0x02           # 检查TX_FULL
    bne  x4, x0, puts_wait

    sw   x3, 0(x1)              # 发送字符

    addi x2, x2, 1              # 下一个字符
    beq  x0, x0, puts_loop

puts_done:
    # 恢复返回地址并返回
    lw   ra, 0(sp)
    addi sp, sp, 4
    jalr zero, 0(ra)


# ========================================
# 数据段
# ========================================
.section .data
welcome_msg:
    .string "UART Echo Server Ready\r\nType something...\r\n"

# =====================================================
# 预期行为：
# 1. 启动后显示：
#    UART Echo Server Ready
#    Type something...
#
# 2. 输入任何字符都会被回显
#
# 3. 按回车会显示完整的\r\n换行
#
# 测试命令：
# screen /dev/ttyUSB1 115200
# =====================================================
