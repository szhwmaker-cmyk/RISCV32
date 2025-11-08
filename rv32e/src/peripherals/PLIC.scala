package peripherals

import chisel3._
import chisel3.util._
import bus._

/**
 * Platform-Level Interrupt Controller (PLIC)
 * 平台级中断控制器
 *
 * 简化的PLIC实现，用于管理外设中断
 * 支持最多32个中断源，优先级仲裁
 */

/**
 * PLIC配置
 */
case class PLICConfig(
  numSources: Int = 32,  // 中断源数量
  numPriorities: Int = 7 // 优先级级别（0-7，0为最低）
)

/**
 * PLIC核心模块
 */
class PLICCore(config: PLICConfig = PLICConfig()) extends Module {
  val io = IO(new Bundle {
    // 中断源输入（来自外设）
    val interrupts = Input(UInt(config.numSources.W))

    // 中断输出到CPU
    val m_interrupt = Output(Bool())

    // 中断优先级寄存器（每个源4位）
    val priority_wen = Input(Bool())
    val priority_addr = Input(UInt(log2Ceil(config.numSources).W))
    val priority_wdata = Input(UInt(3.W))
    val priority_rdata = Output(UInt(3.W))

    // 中断使能寄存器
    val enable_wen = Input(Bool())
    val enable_wdata = Input(UInt(config.numSources.W))
    val enable_rdata = Output(UInt(config.numSources.W))

    // 中断挂起寄存器（只读）
    val pending_rdata = Output(UInt(config.numSources.W))

    // 中断声明寄存器（读取获取最高优先级中断ID）
    val claim_ren = Input(Bool())
    val claim_rdata = Output(UInt(log2Ceil(config.numSources + 1).W))

    // 中断完成寄存器（写入完成中断）
    val complete_wen = Input(Bool())
    val complete_wdata = Input(UInt(log2Ceil(config.numSources + 1).W))
  })

  // ========== 优先级寄存器 ==========
  // priority[0] 固定为0（中断源0保留）
  val priorities = RegInit(VecInit(Seq.fill(config.numSources)(0.U(3.W))))

  // 读优先级
  io.priority_rdata := priorities(io.priority_addr)

  // 写优先级
  when(io.priority_wen && io.priority_addr =/= 0.U) {
    priorities(io.priority_addr) := io.priority_wdata
  }

  // ========== 中断使能寄存器 ==========
  val enables = RegInit(0.U(config.numSources.W))

  io.enable_rdata := enables

  when(io.enable_wen) {
    enables := io.enable_wdata
  }

  // ========== 中断挂起寄存器 ==========
  // 挂起 = 中断源有效 AND 使能
  val pendings = io.interrupts & enables

  io.pending_rdata := pendings

  // ========== 中断声明和服务 ==========
  // 记录正在服务的中断
  val claiming = RegInit(VecInit(Seq.fill(config.numSources)(false.B)))

  // 找到最高优先级的待处理中断
  val max_priority = Wire(UInt(3.W))
  val max_id = Wire(UInt(log2Ceil(config.numSources + 1).W))

  max_priority := 0.U
  max_id := 0.U

  for (i <- 1 until config.numSources) {
    when(pendings(i) && !claiming(i) && priorities(i) > max_priority) {
      max_priority := priorities(i)
      max_id := i.U
    }
  }

  // 中断声明：返回最高优先级中断ID
  io.claim_rdata := max_id

  when(io.claim_ren && max_id =/= 0.U) {
    claiming(max_id) := true.B
  }

  // 中断完成：清除claiming标志
  when(io.complete_wen && io.complete_wdata =/= 0.U && io.complete_wdata < config.numSources.U) {
    claiming(io.complete_wdata) := false.B
  }

  // ========== 中断输出 ==========
  // 有待处理且未被声明的中断时，发出中断信号
  io.m_interrupt := max_id =/= 0.U
}

/**
 * PLIC with Wishbone Interface
 * 带Wishbone接口的PLIC
 *
 * 寄存器映射：
 * 0x00000000-0x0000007C: 优先级寄存器 (32个源 x 4字节)
 * 0x00001000: 中断挂起寄存器 (32位，只读)
 * 0x00002000: 中断使能寄存器 (32位)
 * 0x00200004: 优先级阈值 (保留，未实现)
 * 0x00200008: 中断声明/完成寄存器
 */
class PLIC(config: PLICConfig = PLICConfig()) extends Module {
  val io = IO(new Bundle {
    // Wishbone Slave接口
    val wb = new WishboneSlave()

    // 中断源输入
    val interrupts = Input(UInt(config.numSources.W))

    // 中断输出
    val m_interrupt = Output(Bool())
  })

  val core = Module(new PLICCore(config))

  // 连接中断源和输出
  core.io.interrupts := io.interrupts
  io.m_interrupt := core.io.m_interrupt

  // Wishbone接口
  val addr = io.wb.adr_i(15, 0)  // 使用低16位地址
  val is_write = io.wb.cyc_i && io.wb.stb_i && io.wb.we_i
  val is_read = io.wb.cyc_i && io.wb.stb_i && !io.wb.we_i

  // 地址解码
  val is_priority = addr < 0x0080.U
  val is_pending = addr === 0x1000.U
  val is_enable = addr === 0x2000.U
  val is_threshold = addr === 0x200004.U  // 未实现
  val is_claim_complete = addr === 0x200008.U

  // 默认输出
  core.io.priority_wen := false.B
  core.io.priority_addr := 0.U
  core.io.priority_wdata := 0.U
  core.io.enable_wen := false.B
  core.io.enable_wdata := 0.U
  core.io.claim_ren := false.B
  core.io.complete_wen := false.B
  core.io.complete_wdata := 0.U

  val rdata = Wire(UInt(32.W))
  rdata := 0.U

  // 优先级寄存器
  when(is_priority) {
    val priority_idx = addr(6, 2)  // 字地址
    core.io.priority_addr := priority_idx
    rdata := core.io.priority_rdata

    when(is_write) {
      core.io.priority_wen := true.B
      core.io.priority_wdata := io.wb.dat_i(2, 0)
    }
  }

  // 中断挂起寄存器（只读）
  when(is_pending) {
    rdata := core.io.pending_rdata
  }

  // 中断使能寄存器
  when(is_enable) {
    rdata := core.io.enable_rdata

    when(is_write) {
      core.io.enable_wen := true.B
      core.io.enable_wdata := io.wb.dat_i
    }
  }

  // 中断声明/完成寄存器
  when(is_claim_complete) {
    when(is_read) {
      core.io.claim_ren := true.B
      rdata := core.io.claim_rdata
    }.elsewhen(is_write) {
      core.io.complete_wen := true.B
      core.io.complete_wdata := io.wb.dat_i(log2Ceil(config.numSources), 0)
    }
  }

  // Wishbone响应
  val ack = RegInit(false.B)
  ack := io.wb.cyc_i && io.wb.stb_i && !ack

  io.wb.dat_o := rdata
  io.wb.ack_o := ack
  io.wb.err_o := false.B
  io.wb.rty_o := false.B
}

/**
 * 中断源映射（SoC级别定义）
 */
object InterruptSource {
  val UART_TX    = 1   // UART发送完成
  val UART_RX    = 2   // UART接收就绪
  val UART_ERR   = 3   // UART错误
  val GPIO_0     = 4   // GPIO[0]中断
  val GPIO_1     = 5   // GPIO[1]中断
  val GPIO_2     = 6   // GPIO[2]中断
  val GPIO_3     = 7   // GPIO[3]中断
  val TIMER      = 8   // Timer溢出
  val SPI        = 9   // SPI传输完成
  val I2C        = 10  // I2C事件
  // 11-31 保留
}
