package core

import chisel3._
import chisel3.util._

/**
 * CSR (Control and Status Registers) Module
 * 控制和状态寄存器模块
 *
 * 实现RISC-V Machine Mode的基本CSR寄存器
 * 用于支持中断、异常处理和RT-Thread等操作系统
 */

/**
 * CSR地址定义
 */
object CSRAddr {
  // Machine Information Registers (只读)
  val mvendorid = 0xF11.U(12.W)  // Vendor ID
  val marchid   = 0xF12.U(12.W)  // Architecture ID
  val mimpid    = 0xF13.U(12.W)  // Implementation ID
  val mhartid   = 0xF14.U(12.W)  // Hardware thread ID

  // Machine Trap Setup
  val mstatus   = 0x300.U(12.W)  // Machine status
  val misa      = 0x301.U(12.W)  // ISA and extensions
  val mie       = 0x304.U(12.W)  // Machine interrupt enable
  val mtvec     = 0x305.U(12.W)  // Machine trap-handler base address

  // Machine Trap Handling
  val mscratch  = 0x340.U(12.W)  // Machine scratch register
  val mepc      = 0x341.U(12.W)  // Machine exception program counter
  val mcause    = 0x342.U(12.W)  // Machine trap cause
  val mtval     = 0x343.U(12.W)  // Machine bad address or instruction
  val mip       = 0x344.U(12.W)  // Machine interrupt pending

  // Machine Counter/Timers
  val mcycle    = 0xB00.U(12.W)  // Machine cycle counter
  val minstret  = 0xB02.U(12.W)  // Machine instructions-retired counter
  val mcycleh   = 0xB80.U(12.W)  // Upper 32 bits of mcycle
  val minstreth = 0xB82.U(12.W)  // Upper 32 bits of minstret
}

/**
 * CSR操作类型
 */
object CSROp {
  val width = 3
  val NONE  = 0.U(width.W)
  val RW    = 1.U(width.W)  // Read-Write
  val RS    = 2.U(width.W)  // Read-Set
  val RC    = 3.U(width.W)  // Read-Clear
  val RWI   = 4.U(width.W)  // Read-Write Immediate
  val RSI   = 5.U(width.W)  // Read-Set Immediate
  val RCI   = 6.U(width.W)  // Read-Clear Immediate
}

/**
 * 异常/中断原因编码
 */
object TrapCause {
  // Interrupts (bit 31 = 1)
  val M_SOFTWARE_INT  = (1L << 31 | 3).U(32.W)   // Machine software interrupt
  val M_TIMER_INT     = (1L << 31 | 7).U(32.W)   // Machine timer interrupt
  val M_EXTERNAL_INT  = (1L << 31 | 11).U(32.W)  // Machine external interrupt

  // Exceptions (bit 31 = 0)
  val INST_ADDR_MISALIGNED = 0.U(32.W)
  val INST_ACCESS_FAULT    = 1.U(32.W)
  val ILLEGAL_INST         = 2.U(32.W)
  val BREAKPOINT           = 3.U(32.W)
  val LOAD_ADDR_MISALIGNED = 4.U(32.W)
  val LOAD_ACCESS_FAULT    = 5.U(32.W)
  val STORE_ADDR_MISALIGNED = 6.U(32.W)
  val STORE_ACCESS_FAULT   = 7.U(32.W)
  val ECALL_FROM_M         = 11.U(32.W)
}

/**
 * CSR寄存器文件
 */
class CSR extends Module {
  val io = IO(new Bundle {
    // CSR读写接口
    val csr_addr = Input(UInt(12.W))
    val csr_cmd  = Input(UInt(CSROp.width.W))
    val csr_wdata = Input(UInt(32.W))
    val csr_rdata = Output(UInt(32.W))

    // 异常/中断输入
    val exception = Input(Bool())       // 是否发生异常
    val exception_pc = Input(UInt(32.W)) // 异常发生时的PC
    val exception_cause = Input(UInt(32.W))  // 异常原因
    val exception_tval = Input(UInt(32.W))   // 异常值（地址等）

    val interrupt = Input(Bool())       // 是否有待处理的中断
    val interrupt_cause = Input(UInt(32.W))  // 中断原因

    // 外部中断信号
    val ext_interrupts = Input(UInt(32.W))  // 外部中断源

    // 陷入返回
    val mret = Input(Bool())            // MRET指令

    // 输出控制信号
    val trap_taken = Output(Bool())     // 陷入发生
    val trap_vector = Output(UInt(32.W)) // 陷入向量地址
    val epc = Output(UInt(32.W))        // 异常返回地址
  })

  // ========== Machine Information Registers (只读) ==========
  val mvendorid = 0x00000000.U(32.W)  // 未分配供应商ID
  val marchid   = 0x00000000.U(32.W)  // 未分配架构ID
  val mimpid    = 0x00000001.U(32.W)  // 实现版本1
  val mhartid   = 0x00000000.U(32.W)  // Hart ID 0

  // ========== Machine Status Register ==========
  // mstatus结构（简化版本）：
  // [3]: MIE - Machine Interrupt Enable
  // [7]: MPIE - Machine Previous Interrupt Enable
  val mstatus = RegInit(0x00000000.U(32.W))
  val mie_bit = mstatus(3)
  val mpie_bit = mstatus(7)

  // ========== ISA Register ==========
  // RV32E base with no extensions
  // bit[30:0] = Extensions, bit[31:30] = MXL (32-bit)
  val misa = Cat(
    1.U(2.W),      // MXL = 1 (32-bit)
    0.U(4.W),      // Reserved
    0.U(1.W),      // Z
    0.U(1.W),      // Y
    0.U(1.W),      // X
    0.U(1.W),      // W
    0.U(1.W),      // V
    0.U(1.W),      // U
    0.U(1.W),      // T
    0.U(1.W),      // S
    0.U(1.W),      // R
    0.U(1.W),      // Q
    0.U(1.W),      // P
    0.U(1.W),      // O
    0.U(1.W),      // N
    0.U(1.W),      // M
    0.U(1.W),      // L
    0.U(1.W),      // K
    0.U(1.W),      // J
    1.U(1.W),      // I - Base integer ISA
    0.U(1.W),      // H
    0.U(1.W),      // G
    0.U(1.W),      // F
    1.U(1.W),      // E - RV32E (16 registers)
    0.U(1.W),      // D
    0.U(1.W),      // C
    0.U(1.W),      // B
    0.U(1.W)       // A
  )

  // ========== Machine Interrupt Registers ==========
  val mie = RegInit(0x00000000.U(32.W))   // Interrupt enable
  val mip = RegInit(0x00000000.U(32.W))   // Interrupt pending

  // 中断挂起位自动更新
  val mip_next = Wire(UInt(32.W))
  mip_next := Cat(
    io.ext_interrupts(31, 12),  // External interrupts [31:12]
    io.ext_interrupts(11),      // Machine external interrupt
    0.U(3.W),
    io.ext_interrupts(7),       // Machine timer interrupt
    0.U(3.W),
    io.ext_interrupts(3),       // Machine software interrupt
    0.U(3.W)
  )
  mip := mip_next

  // ========== Machine Trap-Handler Base Address ==========
  // mtvec[31:2] = BASE, mtvec[1:0] = MODE
  // MODE: 0 = Direct, 1 = Vectored
  val mtvec = RegInit(0x00000000.U(32.W))

  // ========== Machine Trap Handling ==========
  val mscratch = RegInit(0x00000000.U(32.W))
  val mepc = RegInit(0x00000000.U(32.W))
  val mcause = RegInit(0x00000000.U(32.W))
  val mtval = RegInit(0x00000000.U(32.W))

  // ========== Machine Counters ==========
  val mcycle = RegInit(0.U(64.W))
  val minstret = RegInit(0.U(64.W))

  // 计数器自增
  mcycle := mcycle + 1.U
  when(io.csr_cmd =/= CSROp.NONE && io.csr_addr =/= CSRAddr.mcycle && io.csr_addr =/= CSRAddr.mcycleh) {
    minstret := minstret + 1.U
  }

  // ========== CSR读操作 ==========
  val csr_rdata_wire = Wire(UInt(32.W))
  csr_rdata_wire := MuxLookup(io.csr_addr, 0.U)(Seq(
    CSRAddr.mvendorid -> mvendorid,
    CSRAddr.marchid   -> marchid,
    CSRAddr.mimpid    -> mimpid,
    CSRAddr.mhartid   -> mhartid,
    CSRAddr.mstatus   -> mstatus,
    CSRAddr.misa      -> misa,
    CSRAddr.mie       -> mie,
    CSRAddr.mtvec     -> mtvec,
    CSRAddr.mscratch  -> mscratch,
    CSRAddr.mepc      -> mepc,
    CSRAddr.mcause    -> mcause,
    CSRAddr.mtval     -> mtval,
    CSRAddr.mip       -> mip,
    CSRAddr.mcycle    -> mcycle(31, 0),
    CSRAddr.mcycleh   -> mcycle(63, 32),
    CSRAddr.minstret  -> minstret(31, 0),
    CSRAddr.minstreth -> minstret(63, 32)
  ))

  io.csr_rdata := csr_rdata_wire

  // ========== CSR写操作 ==========
  val csr_wdata_final = Wire(UInt(32.W))

  csr_wdata_final := MuxLookup(io.csr_cmd, 0.U)(Seq(
    CSROp.RW  -> io.csr_wdata,
    CSROp.RS  -> (csr_rdata_wire | io.csr_wdata),
    CSROp.RC  -> (csr_rdata_wire & ~io.csr_wdata),
    CSROp.RWI -> io.csr_wdata,
    CSROp.RSI -> (csr_rdata_wire | io.csr_wdata),
    CSROp.RCI -> (csr_rdata_wire & ~io.csr_wdata)
  ))

  when(io.csr_cmd =/= CSROp.NONE) {
    switch(io.csr_addr) {
      is(CSRAddr.mstatus)  { mstatus := csr_wdata_final }
      is(CSRAddr.mie)      { mie := csr_wdata_final }
      is(CSRAddr.mtvec)    { mtvec := csr_wdata_final }
      is(CSRAddr.mscratch) { mscratch := csr_wdata_final }
      is(CSRAddr.mepc)     { mepc := csr_wdata_final }
      is(CSRAddr.mcause)   { mcause := csr_wdata_final }
      is(CSRAddr.mtval)    { mtval := csr_wdata_final }
    }
  }

  // ========== 中断/异常处理 ==========

  // 检查是否有使能的待处理中断
  val pending_interrupts = mip & mie
  val has_interrupt = pending_interrupts.orR && mie_bit

  // 确定中断原因（优先级：外部 > 软件 > 定时器）
  val interrupt_cause_internal = Wire(UInt(32.W))
  interrupt_cause_internal := MuxCase(0.U, Seq(
    pending_interrupts(11) -> TrapCause.M_EXTERNAL_INT,
    pending_interrupts(3)  -> TrapCause.M_SOFTWARE_INT,
    pending_interrupts(7)  -> TrapCause.M_TIMER_INT
  ))

  // 陷入条件：异常优先于中断
  val trap_taken_wire = io.exception || (has_interrupt && io.interrupt)
  io.trap_taken := trap_taken_wire

  // 陷入向量计算
  val trap_cause = Mux(io.exception, io.exception_cause, interrupt_cause_internal)
  val is_interrupt = trap_cause(31)
  val trap_base = mtvec(31, 2) << 2
  val trap_mode = mtvec(1, 0)

  // MODE = 0: Direct, MODE = 1: Vectored
  val trap_offset = Mux(trap_mode === 1.U && is_interrupt,
    trap_cause(3, 0) << 2,  // Vectored mode: offset by cause
    0.U                      // Direct mode: no offset
  )

  io.trap_vector := trap_base + trap_offset
  io.epc := mepc

  // 陷入时更新CSR
  when(trap_taken_wire) {
    mepc := io.exception_pc
    mcause := trap_cause
    mtval := Mux(io.exception, io.exception_tval, 0.U)

    // 保存并禁用中断
    mstatus := Cat(
      mstatus(31, 8),
      mie_bit,        // MPIE = MIE
      mstatus(6, 4),
      0.U(1.W),       // MIE = 0
      mstatus(2, 0)
    )
  }

  // MRET指令：从陷入返回
  when(io.mret) {
    // 恢复中断使能
    mstatus := Cat(
      mstatus(31, 8),
      1.U(1.W),       // MPIE = 1
      mstatus(6, 4),
      mpie_bit,       // MIE = MPIE
      mstatus(2, 0)
    )
  }
}

/**
 * CSR File with IO Bundle for easy integration
 * 带IO Bundle的CSR模块，便于集成
 */
class CSRFile extends Module {
  val io = IO(new Bundle {
    // Pipeline interface
    val decode_csr_addr = Input(UInt(12.W))
    val decode_csr_cmd = Input(UInt(CSROp.width.W))
    val execute_csr_wdata = Input(UInt(32.W))
    val execute_csr_rdata = Output(UInt(32.W))

    // Exception/Interrupt interface
    val exception = Input(Bool())
    val exception_pc = Input(UInt(32.W))
    val exception_cause = Input(UInt(32.W))
    val exception_tval = Input(UInt(32.W))

    // Interrupt sources
    val timer_irq = Input(Bool())
    val software_irq = Input(Bool())
    val external_irq = Input(Bool())

    // Trap handling
    val mret = Input(Bool())
    val trap_taken = Output(Bool())
    val trap_vector = Output(UInt(32.W))
    val epc = Output(UInt(32.W))
  })

  val csr = Module(new CSR())

  csr.io.csr_addr := io.decode_csr_addr
  csr.io.csr_cmd := io.decode_csr_cmd
  csr.io.csr_wdata := io.execute_csr_wdata
  io.execute_csr_rdata := csr.io.csr_rdata

  csr.io.exception := io.exception
  csr.io.exception_pc := io.exception_pc
  csr.io.exception_cause := io.exception_cause
  csr.io.exception_tval := io.exception_tval

  csr.io.interrupt := true.B  // Always allow interrupt check
  csr.io.interrupt_cause := 0.U  // Determined internally

  // Map external interrupts
  csr.io.ext_interrupts := Cat(
    0.U(20.W),
    io.external_irq,  // [11]
    0.U(3.W),
    io.timer_irq,     // [7]
    0.U(3.W),
    io.software_irq,  // [3]
    0.U(3.W)
  )

  csr.io.mret := io.mret
  io.trap_taken := csr.io.trap_taken
  io.trap_vector := csr.io.trap_vector
  io.epc := csr.io.epc
}
