// Xilinx Block RAM Wrapper for RV32E SoC
//
// This module wraps Xilinx Block Memory Generator IP for use in the RV32E SoC.
// It provides a simple interface compatible with FpgaBlockRamBlackBox.
//
// Usage:
// 1. Generate IP using Vivado IP Catalog or TCL script
// 2. Add generated IP to project
// 3. This wrapper adapts the IP to our interface
//
// IP Configuration:
// - Memory Type: Single Port RAM
// - Port A Width: 32 bits
// - Port A Depth: 16384 (for 64KB)
// - Write Enable: Byte Write Enable
// - Operating Mode: Write First
// - Output Register: Optional (for better timing)

module FpgaBlockRamBlackBox #(
    parameter DEPTH = 16384,      // Number of 32-bit words (64KB / 4)
    parameter WIDTH = 32,         // Data width in bits
    parameter INIT_FILE = ""      // Initialization file (.mem or .coe)
) (
    input  wire                    clk,
    input  wire                    en,
    input  wire [3:0]              we,     // Byte write enables
    input  wire [$clog2(DEPTH)-1:0] addr,
    input  wire [WIDTH-1:0]        din,
    output wire [WIDTH-1:0]        dout
);

    // ========================================
    // Option 1: Xilinx Block Memory Generator IP
    // ========================================
    // Uncomment when using generated IP core
    /*
    xilinx_bram_ip #(
        .C_INIT_FILE_NAME(INIT_FILE)
    ) bram_inst (
        .clka(clk),
        .ena(en),
        .wea(we),           // 4-bit byte write enable
        .addra(addr),
        .dina(din),
        .douta(dout)
    );
    */

    // ========================================
    // Option 2: Inferred Block RAM (Generic)
    // ========================================
    // This will be automatically inferred as Block RAM by Vivado
    // Use this for quick builds without generating IP

    // RAM storage
    reg [WIDTH-1:0] ram_mem [0:DEPTH-1];

    // Optional initialization from file
    initial begin
        if (INIT_FILE != "") begin
            $readmemh(INIT_FILE, ram_mem);
        end
    end

    // Read/Write logic
    reg [WIDTH-1:0] dout_reg;

    always @(posedge clk) begin
        if (en) begin
            // Byte-wise write
            if (we[0]) ram_mem[addr][ 7: 0] <= din[ 7: 0];
            if (we[1]) ram_mem[addr][15: 8] <= din[15: 8];
            if (we[2]) ram_mem[addr][23:16] <= din[23:16];
            if (we[3]) ram_mem[addr][31:24] <= din[31:24];

            // Read (write-first mode: new data appears on output)
            dout_reg <= ram_mem[addr];
        end
    end

    assign dout = dout_reg;

    // ========================================
    // Option 3: Explicit BRAM Primitive
    // ========================================
    // For maximum control, use Xilinx BRAM primitives directly
    /*
    genvar i;
    generate
        for (i = 0; i < 4; i = i + 1) begin : byte_bram
            RAMB36E1 #(
                .RAM_MODE("TDP"),              // True Dual Port
                .READ_WIDTH_A(9),              // 8 bits + 1 parity
                .WRITE_WIDTH_A(9),
                .WRITE_MODE_A("WRITE_FIRST"),
                .INIT_FILE(INIT_FILE)
            ) bram_byte (
                .CLKARDCLK(clk),
                .ENARDEN(en),
                .REGCEAREGCE(1'b1),
                .RSTRAMARSTRAM(1'b0),
                .RSTREGARSTREG(1'b0),
                .WEA({4{we[i]}}),
                .ADDRARDADDR({addr, 4'b0}),    // 14-bit address
                .DIADI({{24{1'b0}}, din[i*8 +: 8]}),
                .DOADO(dout_wire[i*8 +: 8]),
                .DIPADIP(1'b0),
                .DOPADOP(),
                // Port B unused (tie off)
                .CLKBWRCLK(1'b0),
                .ENBWREN(1'b0),
                .REGCEB(1'b0),
                .RSTRAMB(1'b0),
                .RSTREGB(1'b0),
                .WEBWE(8'b0),
                .ADDRBWRADDR(14'b0),
                .DIBDI(32'b0),
                .DIPBDIP(4'b0),
                .DOBDO(),
                .DOPBDOP()
            );
        end
    endgenerate
    */

endmodule

// ========================================
// Xilinx-Specific Attributes
// ========================================
// These attributes guide Vivado synthesis to use Block RAM

// synthesis translate_off
// For simulation only
// synthesis translate_on

// Vivado synthesis attributes
(* ram_style = "block" *) // Force Block RAM inference
// (* ram_decomp = "power" *) // Optimize for power
// (* cascade_height = 1 *) // Single BRAM height

