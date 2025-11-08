/*
 * RT-Thread Startup Code for RV32E
 */

    .section .text.start
    .global _start
    .global _start_rtt

_start:
_start_rtt:
    /* Disable interrupts (not used in RV32E SoC) */
    li t0, 0

    /* Set up stack pointer */
    lui sp, %hi(_stack_top)
    addi sp, sp, %lo(_stack_top)

    /* Set up global pointer */
    .option push
    .option norelax
    la gp, __global_pointer$
    .option pop

    /* Clear BSS section */
    la t0, __bss_start
    la t1, __bss_end
bss_clear:
    bgeu t0, t1, bss_done
    sw zero, 0(t0)
    addi t0, t0, 4
    j bss_clear
bss_done:

    /* Call RT-Thread entry */
    call rtthread_startup

    /* Should never reach here */
_exit:
    j _exit

    .section .data
    .align 2

    .section .bss
    .align 2

/*
 * Exception handlers (simplified for RV32E without interrupts)
 */
    .section .text.trap
    .align 2
    .global trap_entry
trap_entry:
    /* Simple trap handler - just loop */
    j trap_entry
