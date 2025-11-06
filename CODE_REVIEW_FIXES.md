# Code Review Fixes - Progress Report

**Date**: 2025-11-06
**Status**: ✅ **5/5 Critical Issues Fixed**, ⏳ **4/8 Major Issues Remaining**

---

## ✅ Completed Fixes (Committed)

### Critical Issue #1: LOAD-USE Hazard Detection Logic Error
**Status**: ✅ **FIXED**
**Commit**: b69f511

**Problem**: LOAD-USE hazard detection used `ex_mem_rd` (EX/MEM stage) instead of `id_ex_rd` (ID/EX stage), causing hazards to be detected one cycle too late.

**Impact**: Data hazards could be missed, leading to incorrect program execution.

**Fix**:
- Added `id_ex_rd` and `id_ex_rd_valid` signals to `HazardIO`
- Updated LOAD-USE detection logic in `Hazard.scala:113-117`:
  ```scala
  val load_use_hazard = io.id_ex_mem_read &&
                        io.id_ex_rd_valid &&
                        (io.id_ex_rd =/= 0.U(4.W)) &&
                        ((io.id_ex_rd === io.id_ex_rs1) ||
                         (io.id_ex_rd === io.id_ex_rs2))
  ```
- Connected new signals in `Core.scala:87-88`

**Files Modified**:
- `src/main/scala/core/Hazard.scala`
- `src/main/scala/core/Core.scala`

---

### Critical Issue #2: Exception Detection Stage Mismatch
**Status**: ✅ **FIXED**
**Commit**: b69f511

**Problem**: Exception detection mixed signals from different pipeline stages (`ex_stage.io.ex_mem.valid` with `id_stage.io.id_ex.ctrl.is_ecall`).

**Impact**: Exceptions could be detected at the wrong time or with incorrect PC values.

**Fix**:
- Added `is_ecall` and `is_ebreak` fields to `EX_MEM_Reg`
- Propagate exception signals through EX stage pipeline register
- Use consistent EX/MEM signals for exception detection:
  ```scala
  val exception_detected = ex_stage.io.ex_mem.valid &&
    (ex_stage.io.ex_mem.is_ecall || ex_stage.io.ex_mem.is_ebreak)
  ```

**Files Modified**:
- `src/main/scala/core/PipelineRegs.scala`
- `src/main/scala/core/EX.scala`
- `src/main/scala/core/Core.scala`

---

### Critical Issue #3: Forwarding Signal Naming Confusion
**Status**: ✅ **FIXED**
**Commit**: b69f511

**Problem**: Signal names `fwd_ex_sel` and `fwd_mem_sel` were misleading - they actually controlled rs1 and rs2 forwarding, not EX and MEM stage forwarding.

**Impact**: Code maintainability, future developers could misunderstand the design.

**Fix**:
- Renamed `fwd_ex_sel` → `fwd_rs1_sel`
- Renamed `fwd_mem_sel` → `fwd_rs2_sel`
- Updated all usages in `EX.scala` and `Core.scala`

**Files Modified**:
- `src/main/scala/core/EX.scala`
- `src/main/scala/core/Core.scala`

---

### Critical Issue #4: RegFile Write-Through Logic
**Status**: ✅ **FIXED**
**Commit**: b69f511

**Problem**: RegFile used double assignment pattern that relies on "last connection wins" Chisel semantics, which could synthesize inconsistently.

**Impact**: Timing issues, unpredictable synthesis behavior.

**Fix**:
- Refactored to single nested Mux expression with clear priority:
  ```scala
  io.rs1_data := Mux(io.rd_wen && io.rd_addr === io.rs1_addr && io.rd_addr =/= 0.U(4.W),
                     io.rd_data,  // Write-through
                     Mux(io.rs1_addr === 0.U(4.W),
                         0.U(32.W),  // x0 hardwired to zero
                         regfile(io.rs1_addr)))  // Normal read
  ```

**Files Modified**:
- `src/main/scala/core/RegFile.scala`

---

### Critical Issue #5: PC Overflow and Misalignment Protection
**Status**: ✅ **FIXED**
**Commit**: b69f511

**Problem**: No validation of PC values or branch targets for overflow or misalignment.

**Impact**: System could crash or hang on invalid PC values.

**Fix**:
- Added `MAX_PC` constant to `Config.scala` (0xFFFFFFFC)
- Added PC misalignment assertions:
  ```scala
  assert(pc_next(1, 0) === 0.U, "PC misalignment detected: PC must be 4-byte aligned")
  ```
- Added PC overflow assertions:
  ```scala
  assert(pc_next < Config.MAX_PC.U, cf"PC overflow detected: PC = 0x${Hexadecimal(pc_next)} >= MAX_PC")
  ```
- Added branch target validation

**Files Modified**:
- `src/main/scala/Config.scala`
- `src/main/scala/core/IF.scala`

---

### Major Issue #7: CSR Initialization from Config
**Status**: ✅ **FIXED**
**Commit**: b69f511

**Problem**: CSR initial values were hardcoded instead of using configurable values.

**Impact**: Less flexible configuration.

**Fix**:
- Added `MTVEC_BASE` and `MSTATUS_INIT` to `Config.scala`
- Updated CSR initialization:
  ```scala
  val mstatus = RegInit(Config.MSTATUS_INIT.U(32.W))
  val mtvec   = RegInit(Config.MTVEC_BASE.U(32.W))
  ```

**Files Modified**:
- `src/main/scala/Config.scala`
- `src/main/scala/core/CSR.scala`

---

## ⏳ Remaining Issues (To Be Fixed)

### Major Issue #6: Wishbone Timeout Mechanism
**Status**: ⏳ **PENDING**
**Priority**: Medium

**Problem**: IF and MEM stages don't have timeout protection for Wishbone bus transactions.

**Risk**: If a slave doesn't respond, the pipeline will stall forever.

**Recommended Fix**:
```scala
// Add to IF and MEM stages
val timeout_counter = RegInit(0.U(8.W))
val timeout = timeout_counter === 255.U

when(io.imem.stb && !io.imem.ack) {
  timeout_counter := timeout_counter + 1.U
}.otherwise {
  timeout_counter := 0.U
}

when(timeout) {
  assert(false.B, "Wishbone bus timeout")
  // Option: Insert NOP and continue
  if_id_reg.inst := 0x00000013.U  // NOP
}
```

### Major Issue #8: Branch Prediction Documentation
**Status**: ⏳ **PENDING**
**Priority**: Low

**Problem**: Static not-taken branch prediction strategy not documented in code.

**Recommended Fix**: Add detailed comments to `Core.scala` and `IF.scala` explaining:
- Static not-taken prediction strategy
- Branch misprediction penalty (2 cycles)
- Why this strategy was chosen

### Major Issue #9: RV32M Extension Check
**Status**: ⏳ **PENDING**
**Priority**: Medium

**Problem**: No explicit check for unsupported RV32M instructions (MUL/DIV).

**Recommended Fix**:
```scala
// In Decode.scala
when(opcode === Opcode.OP && funct7 === 0x01.U) {
  // RV32M extension (MUL/DIV/REM)
  assert(false.B, "RV32M extension not supported")
}
```

### Major Issue #10: Interconnect Multi-Master Testing
**Status**: ⏳ **PENDING**
**Priority**: Low

**Problem**: Multi-master arbitration logic not tested.

**Note**: Current design only has single master (Core), so risk is low.

### Major Issue #11: Memory Alignment Check
**Status**: ⏳ **PENDING**
**Priority**: Medium

**Problem**: MEM stage doesn't check for misaligned memory accesses.

**Recommended Fix**:
```scala
// In MEM.scala
val addr_aligned = MuxLookup(io.ex_mem.mem_size, true.B)(Seq(
  0.U -> true.B,                    // Byte - always aligned
  1.U -> (io.ex_mem.alu_result(0) === 0.U),    // Half - 2-byte aligned
  2.U -> (io.ex_mem.alu_result(1,0) === 0.U)   // Word - 4-byte aligned
))

assert(addr_aligned || (!io.ex_mem.mem_read && !io.ex_mem.mem_write),
       "Misaligned memory access detected")
```

### Major Issue #12: SPI Flash State Machine Timeout
**Status**: ⏳ **PENDING**
**Priority**: Low

**Problem**: SPI Flash state machine could deadlock on errors.

**Recommended Fix**: Add timeout and error recovery to SPI Flash controller.

### Major Issue #13: UART FIFO Overflow Protection
**Status**: ⏳ **PENDING**
**Priority**: Low

**Problem**: UART RX FIFO overflow behavior not defined.

**Recommended Fix**: Add overflow flag and document overflow behavior (drop vs overwrite).

---

## 🟢 Minor Issues / Enhancements (To Be Addressed)

### Minor #1: Runtime Assertions
**Status**: ⏳ **PENDING**

**Recommended assertions**:
```scala
// Hazard.scala
assert(!(load_use_hazard && branch_taken), "LOAD-USE and branch conflict")

// Decode.scala
assert(!ctrl.mem_read || !ctrl.mem_write, "Simultaneous read/write")

// Core.scala
assert(!(exception_detected && branch_taken), "Exception priority undefined")

// RegFile.scala
when(io.rd_wen) {
  assert(io.rd_addr < 16.U, "Invalid register address for RV32E")
}
```

### Minor #2: Signal Naming Standardization
**Status**: ⏳ **PENDING**

Use consistent `snake_case` naming throughout project.

### Minor #3: Enhanced Test Coverage
**Status**: ⏳ **PENDING**

Add tests for:
- Consecutive LOAD-USE hazards
- Exception + branch priority
- Boundary conditions (x15 register)
- Non-aligned accesses
- Bus timeouts

### Minor #4: Performance Counters
**Status**: ⏳ **PENDING**

Add CSRs for:
- mcycle (cycle counter)
- minstret (instruction counter)
- Custom: stall_cycles, branch_misses

### Minor #5: Documentation Updates
**Status**: ⏳ **PENDING**

Update documentation to match implementation.

### Minor #6: Code Modularization
**Status**: ⏳ **PENDING**

Extract common constants and functions to shared modules.

---

## 📊 Fix Summary

| Category | Total | Fixed | Remaining | % Complete |
|----------|-------|-------|-----------|-----------|
| 🔴 Critical | 5 | 5 | 0 | **100%** |
| 🟡 Major | 8 | 1 | 7 | 12% |
| 🟢 Minor | 6 | 0 | 6 | 0% |
| **Total** | **19** | **6** | **13** | **32%** |

---

## 🎯 Next Steps

### Immediate (This Session)
1. ✅ Fix all 5 critical issues
2. ✅ Fix CSR initialization (Major #7)
3. ⏳ Add Wishbone timeout (Major #6)
4. ⏳ Add memory alignment check (Major #11)
5. ⏳ Add RV32M check (Major #9)

### Short Term (Next Session)
1. Add runtime assertions (Minor #1)
2. Update tests for all fixes
3. Add branch prediction documentation (Major #8)

### Long Term
1. Enhance test coverage (Minor #3)
2. Add performance counters (Minor #4)
3. Update documentation (Minor #5)

---

## 🔍 Test Impact

### Tests Requiring Updates

**HazardSpec.scala**:
- Update LOAD-USE tests to use new `id_ex_rd` signals
- Remove old `ex_mem_rd` based tests
- Add tests for `id_ex_rd_valid` checking

**CoreSpec.scala** (if exists):
- Update exception handling tests
- Test exception priority over branches
- Verify PC validation assertions

**RegFileSpec.scala** (if exists):
- Test write-through with new Mux logic
- Verify x0 hardwiring still works
- Test edge cases (write to x0, etc.)

---

## 📚 References

- **Review Report**: Original code review document
- **RISC-V Spec**: https://riscv.org/technical/specifications/
- **Chisel Guide**: https://www.chisel-lang.org/
- **Wishbone B4 Spec**: https://cdn.opencores.org/downloads/wbspec_b4.pdf

---

**Last Updated**: 2025-11-06
**Document Version**: 1.0
**Status**: Active Development
