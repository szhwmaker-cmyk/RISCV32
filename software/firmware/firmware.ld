/* RV32E Firmware 链接脚本 */

OUTPUT_ARCH(riscv)
ENTRY(_start)

/* 内存布局 */
MEMORY
{
    /* 程序运行在 RAM 中 */
    RAM (rwx) : ORIGIN = 0x80000000, LENGTH = 64K
}

/* 栈大小 */
STACK_SIZE = 8K;

SECTIONS
{
    /* 代码段 */
    .text : {
        . = ALIGN(4);
        KEEP(*(.text.init))     /* 启动代码 */
        *(.text*)
        *(.rodata*)
        . = ALIGN(4);
    } > RAM

    /* 只读数据 */
    .rodata : {
        . = ALIGN(4);
        *(.rodata*)
        . = ALIGN(4);
    } > RAM

    /* 数据段 */
    .data : {
        . = ALIGN(4);
        __data_start = .;
        *(.data*)
        *(.sdata*)
        . = ALIGN(4);
        __data_end = .;
    } > RAM

    /* 加载地址（如果从 Flash 加载） */
    __data_load = LOADADDR(.data);

    /* BSS 段 */
    .bss : {
        . = ALIGN(4);
        __bss_start = .;
        *(.bss*)
        *(.sbss*)
        *(COMMON)
        . = ALIGN(4);
        __bss_end = .;
    } > RAM

    /* 堆段 */
    .heap : {
        . = ALIGN(4);
        __heap_start = .;
        . += 4K;  /* 4KB 堆 */
        __heap_end = .;
    } > RAM

    /* 栈段 */
    .stack : {
        . = ALIGN(16);
        __stack_bottom = .;
        . += STACK_SIZE;
        __stack_top = .;
    } > RAM

    /* 全局指针 */
    __global_pointer$ = MIN(__data_start + 0x800, MAX(__data_start + 0x800, __bss_end - 0x800));

    /* 结束标记 */
    _end = .;
    PROVIDE(end = .);
}
