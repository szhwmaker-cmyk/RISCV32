# =====================================================
# 示例程序 1: LED闪烁
# =====================================================
# 功能：控制GPIO LED以1Hz频率闪烁
# 外设：GPIO (基地址 0x20001000)
# 难度：⭐ 入门级
# =====================================================

.section .text
.globl _start

_start:
    # ========== 初始化GPIO ==========
    lui  x1, 0x20001            # x1 = GPIO_BASE = 0x20001000

    # 设置GPIO方向寄存器（DIR）
    addi x2, x0, 0x0F           # x2 = 0x0F (GPIO[3:0]设为输出)
    sw   x2, 4(x1)              # GPIO_DIR = 0x0F

    # 设置GPIO输出使能（OE）
    addi x2, x0, 0x0F           # x2 = 0x0F (使能输出)
    sw   x2, 8(x1)              # GPIO_OE = 0x0F

main_loop:
    # ========== LED ON ==========
    addi x2, x0, 0x01           # x2 = 0x01 (LED[0] = 1)
    sw   x2, 0(x1)              # GPIO_DATA = 0x01

    # 延时约500ms (@25MHz: 12,500,000 cycles)
    lui  x3, 0x00BEB            # x3 = 0x00BEB000
    addi x3, x3, 0x640          # x3 = 0x00BEB640 = 12,500,000

delay_on:
    addi x3, x3, -1             # x3--
    bne  x3, x0, delay_on       # 如果x3 != 0，继续延时

    # ========== LED OFF ==========
    addi x2, x0, 0x00           # x2 = 0x00 (LED[0] = 0)
    sw   x2, 0(x1)              # GPIO_DATA = 0x00

    # 延时约500ms
    lui  x3, 0x00BEB
    addi x3, x3, 0x640

delay_off:
    addi x3, x3, -1
    bne  x3, x0, delay_off

    # ========== 循环 ==========
    beq  x0, x0, main_loop      # 无条件跳转到main_loop

# =====================================================
# 预期行为：
# - LED[0]以1Hz频率闪烁（ON 0.5s, OFF 0.5s）
# - 在FPGA上应该看到LED规律闪烁
# =====================================================
