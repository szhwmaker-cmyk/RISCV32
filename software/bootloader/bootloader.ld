/* RV32E Bootloader 链接脚本 */

OUTPUT_ARCH(riscv)
ENTRY(_start)

/* 内存布局 */
MEMORY
{
    /* Bootloader 位于 SPI Flash 起始位置 */
    FLASH (rx)  : ORIGIN = 0x10000000, LENGTH = 4K

    /* RAM 用于栈和数据 */
    RAM (rwx)   : ORIGIN = 0x80000000, LENGTH = 64K
}

SECTIONS
{
    /* Bootloader 代码段 */
    .text : {
        *(.text.boot)
        *(.text*)
        *(.rodata*)
    } > FLASH

    /* 数据段（初始化数据） */
    .data : AT(LOADADDR(.text) + SIZEOF(.text)) {
        __data_start = .;
        *(.data*)
        __data_end = .;
    } > RAM

    /* BSS 段（未初始化数据） */
    .bss : {
        __bss_start = .;
        *(.bss*)
        *(COMMON)
        __bss_end = .;
    } > RAM

    /* 栈 */
    .stack : {
        . = ALIGN(16);
        __stack_start = .;
        . += 4K;
        __stack_end = .;
    } > RAM

    /* 全局指针 */
    __global_pointer$ = MIN(__data_start + 0x800, MAX(__data_start + 0x800, __bss_end - 0x800));
}
