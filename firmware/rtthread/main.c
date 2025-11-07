/**
 * RT-Thread 测试程序
 * 用于验证 RV32E SoC 功能
 */

#include <stdint.h>

// 外设基地址
#define UART_BASE   0x10000000
#define GPIO_BASE   0x10003000
#define TIMER_BASE  0x10004000
#define SPI_BASE    0x10001000
#define I2C_BASE    0x10002000

// UART 寄存器
#define UART_THR    (*(volatile uint32_t*)(UART_BASE + 0x00))
#define UART_LSR    (*(volatile uint32_t*)(UART_BASE + 0x14))

// GPIO 寄存器
#define GPIO_DATA_IN  (*(volatile uint32_t*)(GPIO_BASE + 0x00))
#define GPIO_DATA_OUT (*(volatile uint32_t*)(GPIO_BASE + 0x04))
#define GPIO_DIR      (*(volatile uint32_t*)(GPIO_BASE + 0x08))
#define GPIO_OE       (*(volatile uint32_t*)(GPIO_BASE + 0x0C))

// Timer 寄存器
#define TIMER_MTIME_LO   (*(volatile uint32_t*)(TIMER_BASE + 0x00))
#define TIMER_MTIME_HI   (*(volatile uint32_t*)(TIMER_BASE + 0x04))

// 简单延迟函数
void delay(uint32_t count) {
    for (volatile uint32_t i = 0; i < count; i++) {
        asm volatile("nop");
    }
}

// UART 输出字符
void uart_putc(char c) {
    // 等待 THR 空闲
    while ((UART_LSR & (1 << 5)) == 0);
    UART_THR = c;
}

// UART 输出字符串
void uart_puts(const char* s) {
    while (*s) {
        if (*s == '\n') {
            uart_putc('\r');
        }
        uart_putc(*s++);
    }
}

// 获取系统时间
uint64_t get_time() {
    uint32_t lo = TIMER_MTIME_LO;
    uint32_t hi = TIMER_MTIME_HI;
    return ((uint64_t)hi << 32) | lo;
}

// GPIO 测试
void gpio_test() {
    uart_puts("\n[GPIO Test]\n");

    // 设置所有 GPIO 为输出
    GPIO_DIR = 0xFFFFFFFF;
    GPIO_OE = 0xFFFFFFFF;

    // LED 闪烁模式
    for (int i = 0; i < 5; i++) {
        GPIO_DATA_OUT = 0xAAAAAAAA;
        delay(100000);
        GPIO_DATA_OUT = 0x55555555;
        delay(100000);
    }

    GPIO_DATA_OUT = 0;
    uart_puts("GPIO test completed\n");
}

// 主程序
int main(void) {
    // 初始化 UART（假设 BootROM 已初始化）

    // 输出启动信息
    uart_puts("\n");
    uart_puts("========================================\n");
    uart_puts("RV32E SoC Boot Success!\n");
    uart_puts("========================================\n");
    uart_puts("System Information:\n");
    uart_puts("  CPU: RV32E 5-Stage Pipeline\n");
    uart_puts("  Frequency: 50 MHz\n");
    uart_puts("  Memory: 128KB SRAM\n");
    uart_puts("  Peripherals: UART, GPIO, Timer, SPI, I2C\n");
    uart_puts("========================================\n");

    // 读取并显示系统时间
    uart_puts("\n[System Timer]\n");
    uint64_t time = get_time();
    uart_puts("Current time: 0x");
    // 简化：不输出具体数值
    uart_puts("...\n");

    // GPIO 测试
    gpio_test();

    // SPI Flash 测试（占位符）
    uart_puts("\n[SPI Flash Test]\n");
    uart_puts("SPI Flash test: SKIPPED (not implemented)\n");

    // I2C 设备扫描（占位符）
    uart_puts("\n[I2C Device Scan]\n");
    uart_puts("I2C scan: SKIPPED (not implemented)\n");

    // 测试完成
    uart_puts("\n========================================\n");
    uart_puts("All tests completed!\n");
    uart_puts("System running in idle loop...\n");
    uart_puts("========================================\n\n");

    // 进入空闲循环
    while (1) {
        // LED 心跳
        GPIO_DATA_OUT = 0x00000001;
        delay(500000);
        GPIO_DATA_OUT = 0x00000000;
        delay(500000);
    }

    return 0;
}
