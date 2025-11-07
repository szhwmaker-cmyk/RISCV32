/* BootROM 链接脚本 */

OUTPUT_ARCH("riscv")
ENTRY(_start)

MEMORY
{
    ROM (rx) : ORIGIN = 0x00000000, LENGTH = 64K
}

SECTIONS
{
    .text : {
        *(.text.boot)
        *(.text*)
        *(.rodata*)
    } > ROM

    .data : {
        *(.data*)
    } > ROM

    .bss : {
        *(.bss*)
        *(COMMON)
    } > ROM
}
