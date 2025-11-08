/**
 * RV32E Main Program Example
 * 简单的LED闪烁和UART通信示例
 */

#include <stdint.h>

// 外设基地址
#define UART_BASE   0x40000000
#define GPIO_BASE   0x40001000
#define TIMER_BASE  0x40002000

// UART寄存器
#define UART_DATA   (*(volatile uint32_t*)(UART_BASE + 0x00))
#define UART_STATUS (*(volatile uint32_t*)(UART_BASE + 0x04))
#define UART_CTRL   (*(volatile uint32_t*)(UART_BASE + 0x08))

// GPIO寄存器
#define GPIO_IN     (*(volatile uint32_t*)(GPIO_BASE + 0x00))
#define GPIO_OUT    (*(volatile uint32_t*)(GPIO_BASE + 0x04))
#define GPIO_DIR    (*(volatile uint32_t*)(GPIO_BASE + 0x08))

// Timer寄存器
#define TIMER_CTRL  (*(volatile uint32_t*)(TIMER_BASE + 0x00))
#define TIMER_LOAD  (*(volatile uint32_t*)(TIMER_BASE + 0x08))

// 函数声明
void delay(uint32_t count);
void uart_putc(char c);
void uart_puts(const char *s);
void led_init(void);
void led_set(uint16_t pattern);

int main(void) {
    // 初始化UART
    UART_CTRL = 0x03;  // 启用TX和RX

    // 初始化LED (GPIO)
    led_init();

    // 打印欢迎消息
    uart_puts("\\r\\nRV32E SoC Test Program\\r\\n");
    uart_puts("LED Blinking Demo\\r\\n");
    uart_puts("====================\\r\\n\\r\\n");

    // LED闪烁模式
    uint16_t patterns[] = {
        0xAAAA,  // 交替
        0x5555,  // 反转
        0xF0F0,  // 半半
        0x0F0F,  // 反半半
        0xFF00,  // 上半
        0x00FF   // 下半
    };
    int pattern_idx = 0;
    int counter = 0;

    while (1) {
        // 设置LED模式
        led_set(patterns[pattern_idx]);

        // 打印状态
        uart_puts("Pattern ");
        uart_putc('0' + pattern_idx);
        uart_puts(": 0x");

        // 打印十六进制值
        uint16_t val = patterns[pattern_idx];
        for (int i = 3; i >= 0; i--) {
            uint8_t nibble = (val >> (i * 4)) & 0xF;
            uart_putc(nibble < 10 ? '0' + nibble : 'A' + nibble - 10);
        }
        uart_puts("\\r\\n");

        // 延时
        delay(500000);

        // 切换到下一个模式
        counter++;
        if (counter >= 10) {
            pattern_idx = (pattern_idx + 1) % 6;
            counter = 0;
        }
    }

    return 0;
}

void delay(uint32_t count) {
    for (uint32_t i = 0; i < count; i++) {
        asm volatile("nop");
    }
}

void uart_putc(char c) {
    // 等待TX就绪
    while ((UART_STATUS & 0x01) == 0);
    UART_DATA = c;
}

void uart_puts(const char *s) {
    while (*s) {
        uart_putc(*s++);
    }
}

void led_init(void) {
    // 设置所有GPIO为输出
    GPIO_DIR = 0xFFFF;
    // 初始状态全灭
    GPIO_OUT = 0x0000;
}

void led_set(uint16_t pattern) {
    GPIO_OUT = pattern;
}
