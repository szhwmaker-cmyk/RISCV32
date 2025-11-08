/*
 * Hello World 应用程序示例
 * 通过UART输出 "Hello from RV32E SoC!"
 */

#define UART_BASE    0x20000000
#define UART_TXDATA  (*(volatile unsigned int*)(UART_BASE + 0x00))
#define UART_RXDATA  (*(volatile unsigned int*)(UART_BASE + 0x04))
#define UART_STATUS  (*(volatile unsigned int*)(UART_BASE + 0x08))
#define UART_BAUD    (*(volatile unsigned int*)(UART_BASE + 0x0C))
#define UART_CTRL    (*(volatile unsigned int*)(UART_BASE + 0x10))

#define GPIO_BASE    0x20010000
#define GPIO_DATA_OUT (*(volatile unsigned int*)(GPIO_BASE + 0x04))
#define GPIO_DIR      (*(volatile unsigned int*)(GPIO_BASE + 0x08))

// UART TX状态位
#define UART_TX_BUSY  (1 << 0)
#define UART_TX_EMPTY (1 << 1)

void uart_putc(char c) {
    // 等待TX不忙
    while (UART_STATUS & UART_TX_BUSY);
    UART_TXDATA = c;
}

void uart_puts(const char* s) {
    while (*s) {
        uart_putc(*s++);
    }
}

void delay(int count) {
    for (volatile int i = 0; i < count; i++);
}

int main() {
    // 初始化UART (115200 bps)
    UART_BAUD = 434;  // 50MHz / 115200
    UART_CTRL = 0x03; // 使能 TX 和 RX

    // 初始化GPIO (输出模式)
    GPIO_DIR = 0xFFFF;

    // 输出欢迎信息
    uart_puts("\\r\\n");
    uart_puts("======================================\\r\\n");
    uart_puts("  RV32E SoC - Hello World Example    \\r\\n");
    uart_puts("======================================\\r\\n");
    uart_puts("CPU: RV32E 5-Stage Pipeline\\r\\n");
    uart_puts("Frequency: 50 MHz\\r\\n");
    uart_puts("UART: 115200 bps\\r\\n");
    uart_puts("\\r\\n");

    // LED 闪烁计数器
    int counter = 0;

    uart_puts("Starting LED blink loop...\\r\\n\\r\\n");

    while (1) {
        // 切换LED状态
        GPIO_DATA_OUT = counter & 0xFFFF;

        // 输出计数器值
        uart_puts("Counter: ");
        // 简单的整数转字符串（仅用于演示）
        char num_str[16];
        int n = counter;
        int i = 0;

        if (n == 0) {
            num_str[i++] = '0';
        } else {
            int temp = n;
            int digits = 0;
            while (temp > 0) {
                digits++;
                temp /= 10;
            }

            for (int j = digits - 1; j >= 0; j--) {
                int digit = n;
                for (int k = 0; k < j; k++) {
                    digit /= 10;
                }
                num_str[i++] = '0' + (digit % 10);
            }
        }
        num_str[i] = '\\0';

        uart_puts(num_str);
        uart_puts(" (GPIO: 0x");

        // 输出十六进制
        char hex[] = "0123456789ABCDEF";
        uart_putc(hex[(GPIO_DATA_OUT >> 12) & 0xF]);
        uart_putc(hex[(GPIO_DATA_OUT >> 8) & 0xF]);
        uart_putc(hex[(GPIO_DATA_OUT >> 4) & 0xF]);
        uart_putc(hex[GPIO_DATA_OUT & 0xF]);
        uart_puts(")\\r\\n");

        // 延迟
        delay(1000000);

        counter++;
        if (counter >= 16) counter = 0;
    }

    return 0;
}
