/**
 * =====================================================
 * Linker Script for RV32E SoC
 * =====================================================
 * 目标：64KB RAM @ 0x80000000
 * 栈：从RAM顶部向下增长
 * =====================================================
 */

OUTPUT_ARCH("riscv")
ENTRY(_start)

MEMORY {
    /* 64KB RAM，从0x80000000开始 */
    RAM (rwx) : ORIGIN = 0x80000000, LENGTH = 64K
}

SECTIONS {
    /* 代码段 */
    .text : ALIGN(4) {
        *(.text.start)      /* 启动代码 */
        *(.text*)           /* 所有代码 */
    } > RAM

    /* 只读数据段 */
    .rodata : ALIGN(4) {
        *(.rodata*)
    } > RAM

    /* 初始化数据段 */
    .data : ALIGN(4) {
        __data_start = .;
        *(.data*)
        __data_end = .;
    } > RAM

    /* 未初始化数据段 (BSS) */
    .bss : ALIGN(4) {
        __bss_start = .;
        *(.bss*)
        *(COMMON)
        __bss_end = .;
    } > RAM

    /* 堆（可选，如果需要malloc） */
    .heap : ALIGN(4) {
        __heap_start = .;
        . = . + 0x1000;  /* 4KB堆空间 */
        __heap_end = .;
    } > RAM

    /* 栈指针（在RAM顶部） */
    . = ORIGIN(RAM) + LENGTH(RAM);
    __stack_top = .;

    /* 符号定义 */
    __ram_start = ORIGIN(RAM);
    __ram_end = ORIGIN(RAM) + LENGTH(RAM);
    __ram_size = LENGTH(RAM);

    /* 丢弃不需要的段 */
    /DISCARD/ : {
        *(.note*)
        *(.comment*)
        *(.eh_frame*)
    }
}
