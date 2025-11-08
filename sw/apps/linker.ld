/*
 * RV32E SoC 链接脚本
 */

OUTPUT_ARCH("riscv")
ENTRY(_start)

MEMORY
{
    FLASH (rx)  : ORIGIN = 0x10000000, LENGTH = 256M
    RAM   (rwx) : ORIGIN = 0x80000000, LENGTH = 256K
}

SECTIONS
{
    /* 代码段 - 从Flash启动 */
    .text : {
        *(.text.start)
        *(.text*)
        *(.rodata*)
    } > FLASH

    /* 数据段 - 在RAM中 */
    .data : {
        *(.data*)
        *(.sdata*)
    } > RAM AT > FLASH

    /* BSS段 - 未初始化数据 */
    .bss : {
        *(.bss*)
        *(.sbss*)
        *(COMMON)
    } > RAM

    /* 堆栈 - 256KB RAM顶部 */
    _stack_top = ORIGIN(RAM) + LENGTH(RAM);
}
