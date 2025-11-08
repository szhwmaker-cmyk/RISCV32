/* RV32E Linker Script */

OUTPUT_ARCH("riscv")
ENTRY(_start)

MEMORY
{
    ROM (rx)  : ORIGIN = 0x00000000, LENGTH = 16K
    RAM (rwx) : ORIGIN = 0x20000000, LENGTH = 64K
}

SECTIONS
{
    .text : {
        *(.text.init)
        *(.text .text.*)
        *(.rodata .rodata.*)
    } > ROM

    .data : {
        __data_start = .;
        *(.data .data.*)
        *(.sdata .sdata.*)
        __data_end = .;
    } > RAM AT > ROM

    .bss : {
        __bss_start = .;
        *(.bss .bss.*)
        *(.sbss .sbss.*)
        *(COMMON)
        __bss_end = .;
    } > RAM

    /* Stack grows downward from end of RAM */
    .stack : {
        . = ALIGN(16);
        __stack_start = .;
        . = ORIGIN(RAM) + LENGTH(RAM);
        __stack_top = .;
    } > RAM

    /DISCARD/ : {
        *(.comment)
        *(.eh_frame)
    }
}
