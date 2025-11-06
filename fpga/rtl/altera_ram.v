// Intel/Altera RAM Wrapper for RV32E SoC
//
// This module provides RAM implementation for Intel/Altera FPGAs
// Compatible with Quartus Prime synthesis tools.
//
// Supports:
// - Cyclone IV, V, 10
// - Arria V, 10
// - Stratix V, 10
//
// IP Options:
// 1. Inferred RAM (automatic)
// 2. On-Chip Memory (Intel FPGA IP)
// 3. altsyncram megafunction

module FpgaBlockRamBlackBox #(
    parameter DEPTH = 16384,
    parameter WIDTH = 32,
    parameter INIT_FILE = ""
) (
    input  wire                      clk,
    input  wire                      en,
    input  wire [3:0]                we,
    input  wire [$clog2(DEPTH)-1:0]  addr,
    input  wire [WIDTH-1:0]          din,
    output wire [WIDTH-1:0]          dout
);

    // ========================================
    // Option 1: Inferred RAM (Recommended for Quartus)
    // ========================================
    // Quartus automatically infers M10K/M20K blocks

    (* ramstyle = "M10K, no_rw_check" *) reg [WIDTH-1:0] ram_array [0:DEPTH-1];
    reg [WIDTH-1:0] dout_reg;

    // Optional initialization
    initial begin
        if (INIT_FILE != "") begin
            $readmemh(INIT_FILE, ram_array);
        end
    end

    // RAM logic with byte enables
    always @(posedge clk) begin
        if (en) begin
            // Byte-wise write
            if (we[0]) ram_array[addr][ 7: 0] <= din[ 7: 0];
            if (we[1]) ram_array[addr][15: 8] <= din[15: 8];
            if (we[2]) ram_array[addr][23:16] <= din[23:16];
            if (we[3]) ram_array[addr][31:24] <= din[31:24];

            // Read (registered output)
            dout_reg <= ram_array[addr];
        end
    end

    assign dout = dout_reg;

    // ========================================
    // Option 2: altsyncram Megafunction
    // ========================================
    /*
    altsyncram #(
        .operation_mode("SINGLE_PORT"),
        .width_a(WIDTH),
        .widthad_a($clog2(DEPTH)),
        .numwords_a(DEPTH),
        .outdata_reg_a("CLOCK0"),
        .address_aclr_a("NONE"),
        .outdata_aclr_a("NONE"),
        .indata_aclr_a("NONE"),
        .wrcontrol_aclr_a("NONE"),
        .byteena_aclr_a("NONE"),
        .width_byteena_a(4),
        .byte_size(8),
        .read_during_write_mode_port_a("NEW_DATA_NO_NBE_READ"),
        .lpm_type("altsyncram"),
        .lpm_hint("ENABLE_RUNTIME_MOD=NO"),
        .init_file(INIT_FILE)
    ) ram_inst (
        .clock0(clk),
        .address_a(addr),
        .data_a(din),
        .wren_a(|we),
        .byteena_a(we),
        .q_a(dout),
        // Unused ports
        .aclr0(1'b0),
        .aclr1(1'b0),
        .addressstall_a(1'b0),
        .addressstall_b(1'b0),
        .clock1(1'b0),
        .clocken0(1'b1),
        .clocken1(1'b1),
        .clocken2(1'b1),
        .clocken3(1'b1),
        .eccstatus(),
        .rden_a(1'b1),
        .rden_b(1'b1)
    );
    */

    // ========================================
    // Option 3: Intel FPGA IP On-Chip Memory
    // ========================================
    // Generate using Platform Designer / Qsys
    // IP Catalog -> On-Chip Memory (RAM or ROM)
    /*
    onchip_ram_64kb ram_ip (
        .clk(clk),
        .address(addr),
        .writedata(din),
        .byteenable(we),
        .write(|we),
        .readdata(dout)
    );
    */

endmodule

// ========================================
// Quartus Synthesis Attributes
// ========================================

// Specify RAM style (choose one):
// (* ramstyle = "M10K" *)     // Force M10K blocks
// (* ramstyle = "M20K" *)     // Force M20K blocks (Arria 10, Stratix 10)
// (* ramstyle = "MLAB" *)     // Use MLABs (small distributed RAM)
// (* ramstyle = "logic" *)    // Use logic cells (not recommended for large RAM)

// Additional synthesis directives:
// (* no_rw_check *)           // Disable read-during-write checks
// (* preserve *)              // Prevent optimization
