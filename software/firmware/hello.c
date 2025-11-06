/**
 * RV32E Hello World 程序
 *
 * 通过 UART 输出 "Hello, RV32E!" 消息
 */

#include <stdint.h>

// 硬件地址定义
#define UART_BASE       0x20000000
#define GPIO_BASE       0x20010000

// UART 寄存器
#define UART_TXDATA     (*(volatile uint32_t*)(UART_BASE + 0x00))
#define UART_RXDATA     (*(volatile uint32_t*)(UART_BASE + 0x04))
#define UART_STATUS     (*(volatile uint32_t*)(UART_BASE + 0x08))
#define UART_BAUD       (*(volatile uint32_t*)(UART_BASE + 0x0C))
#define UART_CTRL       (*(volatile uint32_t*)(UART_BASE + 0x10))

// UART 状态位
#define UART_RX_VALID   (1 << 0)
#define UART_TX_EMPTY   (1 << 1)
#define UART_TX_FULL    (1 << 2)

// GPIO 寄存器
#define GPIO_DATA_IN    (*(volatile uint32_t*)(GPIO_BASE + 0x00))
#define GPIO_DATA_OUT   (*(volatile uint32_t*)(GPIO_BASE + 0x04))
#define GPIO_DIR        (*(volatile uint32_t*)(GPIO_BASE + 0x08))
#define GPIO_OE         (*(volatile uint32_t*)(GPIO_BASE + 0x0C))

/**
 * 初始化 UART
 */
void uart_init(void) {
    // 设置波特率分频器（115200 bps @ 50 MHz）
    UART_BAUD = 434;  // 50000000 / 115200 ≈ 434

    // 使能发送和接收
    UART_CTRL = 0x03; // tx_en | rx_en
}

/**
 * 通过 UART 发送一个字符
 */
void uart_putc(char c) {
    // 等待发送缓冲区空闲
    while (UART_STATUS & UART_TX_FULL);

    // 发送字符
    UART_TXDATA = c;
}

/**
 * 通过 UART 发送字符串
 */
void uart_puts(const char* s) {
    while (*s) {
        uart_putc(*s++);
    }
}

/**
 * 从 UART 接收一个字符
 */
char uart_getc(void) {
    // 等待接收数据有效
    while (!(UART_STATUS & UART_RX_VALID));

    // 读取字符
    return (char)(UART_RXDATA & 0xFF);
}

/**
 * 简单延时函数
 */
void delay(uint32_t count) {
    for (volatile uint32_t i = 0; i < count; i++) {
        __asm__ volatile ("nop");
    }
}

/**
 * 初始化 GPIO
 */
void gpio_init(void) {
    // 设置所有引脚为输出
    GPIO_DIR = 0xFFFF;
    GPIO_OE = 0xFFFF;
}

/**
 * GPIO LED 闪烁测试
 */
void gpio_blink(void) {
    for (int i = 0; i < 5; i++) {
        GPIO_DATA_OUT = 0xAAAA;
        delay(100000);
        GPIO_DATA_OUT = 0x5555;
        delay(100000);
    }
}

/**
 * 主函数
 */
int main(void) {
    // 初始化外设
    uart_init();
    gpio_init();

    // 发送启动消息
    uart_puts("\r\n");
    uart_puts("========================================\r\n");
    uart_puts("  RV32E SoC - Hello World!\r\n");
    uart_puts("========================================\r\n");
    uart_puts("CPU:  RV32E 5-Stage Pipeline\r\n");
    uart_puts("Freq: 50 MHz\r\n");
    uart_puts("RAM:  64 KB\r\n");
    uart_puts("========================================\r\n");
    uart_puts("\r\n");

    // GPIO 测试
    uart_puts("GPIO Blink Test...\r\n");
    gpio_blink();
    uart_puts("GPIO Test Done!\r\n");

    // UART 回环测试
    uart_puts("\r\nUART Echo Test (type any key, 'q' to quit):\r\n");
    while (1) {
        char c = uart_getc();
        uart_putc(c); // 回显

        if (c == 'q' || c == 'Q') {
            break;
        }
    }

    uart_puts("\r\n\r\nTest completed!\r\n");

    // 无限循环
    while (1) {
        delay(1000000);
    }

    return 0;
}

/**
 * 启动代码（汇编部分在 startup.S 中）
 */
