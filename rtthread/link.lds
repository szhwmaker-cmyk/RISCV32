/*
 * Linker Script for RV32E SoC RT-Thread
 * Target: RISC-V RV32E (16 registers)
 */

OUTPUT_ARCH("riscv")
ENTRY(_start)

MEMORY
{
    FLASH (rx)  : ORIGIN = 0x10000000, LENGTH = 16M
    RAM   (rwx) : ORIGIN = 0x80000000, LENGTH = 64K
}

SECTIONS
{
    /* Text section in RAM (loaded from Flash by bootloader) */
    .text : ALIGN(4)
    {
        KEEP(*(.text.entry))
        *(.text)
        *(.text.*)
        *(.rodata)
        *(.rodata.*)
        . = ALIGN(4);
    } > RAM AT > FLASH

    /* Data section */
    .data : ALIGN(4)
    {
        __data_start = .;
        *(.data)
        *(.data.*)
        *(.sdata)
        *(.sdata.*)
        . = ALIGN(4);
        __data_end = .;
    } > RAM AT > FLASH

    __data_load = LOADADDR(.data);

    /* BSS section */
    .bss : ALIGN(4)
    {
        __bss_start = .;
        *(.bss)
        *(.bss.*)
        *(.sbss)
        *(.sbss.*)
        *(COMMON)
        . = ALIGN(4);
        __bss_end = .;
    } > RAM

    /* Stack section */
    .stack : ALIGN(16)
    {
        . = . + 2048;  /* 2KB stack */
        __stack_top = .;
    } > RAM

    /* Heap section */
    .heap : ALIGN(4)
    {
        __heap_start = .;
        . = ORIGIN(RAM) + LENGTH(RAM);
        __heap_end = .;
    } > RAM

    /* Discard debugging information */
    /DISCARD/ :
    {
        *(.comment)
        *(.eh_frame)
    }
}
