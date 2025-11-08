/*
 * Linker Script for RT-Thread on RV32E SoC
 */

OUTPUT_ARCH("riscv")
ENTRY(_start_rtt)

MEMORY
{
    FLASH (rx)  : ORIGIN = 0x10000000, LENGTH = 256M
    RAM   (rwx) : ORIGIN = 0x80000000, LENGTH = 256K
}

SECTIONS
{
    /* Text section - code in Flash */
    .text : {
        KEEP(*(.text.start))
        *(.text*)
        *(.rodata*)
        *(.srodata*)
    } > FLASH

    /* ARM.exidx for exception handling (not used but kept for compatibility) */
    .ARM.exidx : {
        *(.ARM.exidx*)
    } > FLASH

    /* Data section - initialized data in RAM, loaded from Flash */
    .data : AT(LOADADDR(.text) + SIZEOF(.text)) {
        __data_start = .;
        *(.data*)
        *(.sdata*)
        __data_end = .;
    } > RAM

    /* BSS section - uninitialized data */
    .bss : {
        __bss_start = .;
        *(.bss*)
        *(.sbss*)
        *(COMMON)
        __bss_end = .;
    } > RAM

    /* Heap section */
    .heap : {
        __heap_start = .;
        . = . + 0x8000;  /* 32KB heap */
        __heap_end = .;
    } > RAM

    /* Stack at top of RAM */
    _stack_top = ORIGIN(RAM) + LENGTH(RAM);

    /* Global pointer (for RISC-V) */
    __global_pointer$ = MIN(__data_start + 0x800, MAX(__data_start + 0x800, __bss_end - 0x800));
}
