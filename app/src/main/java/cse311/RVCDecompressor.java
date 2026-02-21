package cse311;

/**
 * RISC-V Compressed (RVC) Instruction Decompressor.
 * Translates 16-bit compressed instructions into their 32-bit RV32I
 * equivalents.
 * 
 * Based on the RISC-V ISA Specification, Chapter 16 (RVC).
 * Compressed instructions use 3 quadrants distinguished by bits [1:0]:
 * Quadrant 0 (00), Quadrant 1 (01), Quadrant 2 (10).
 * Quadrant 3 (11) indicates a standard 32-bit instruction.
 */
public class RVCDecompressor {

    /**
     * Decompress a 16-bit RVC instruction into its 32-bit RV32I equivalent.
     * 
     * @param inst16 The 16-bit compressed instruction (only lower 16 bits used)
     * @return The equivalent 32-bit instruction, or 0 for illegal/unimplemented
     */
    public static int decompress(int inst16) {
        inst16 = inst16 & 0xFFFF;

        int quadrant = inst16 & 0x3;

        switch (quadrant) {
            case 0:
                return decompressQ0(inst16);
            case 1:
                return decompressQ1(inst16);
            case 2:
                return decompressQ2(inst16);
            default:
                return 0; // Should not happen — caller checks bits 1:0 != 11
        }
    }

    /**
     * Check if a 16-bit half-word is a compressed instruction.
     * 
     * @param halfWord The 16-bit value
     * @return true if compressed (bits 1:0 != 11)
     */
    public static boolean isCompressed(int halfWord) {
        return (halfWord & 0x3) != 0x3;
    }

    // ================================
    // Quadrant 0 (bits 1:0 = 00)
    // ================================
    private static int decompressQ0(int inst) {
        int funct3 = (inst >> 13) & 0x7;

        switch (funct3) {
            case 0:
                return decompressCADDI4SPN(inst); // C.ADDI4SPN
            case 2:
                return decompressCLW(inst); // C.LW
            case 6:
                return decompressCSW(inst); // C.SW
            default:
                return 0; // Illegal or unimplemented (C.FLW, C.FSW for F extension)
        }
    }

    // C.ADDI4SPN -> addi rd', x2, nzuimm
    // Format: [15:13]=000 [12:5]=nzuimm [4:2]=rd' [1:0]=00
    private static int decompressCADDI4SPN(int inst) {
        int rd_prime = (inst >> 2) & 0x7;
        int rd = rd_prime + 8; // CIW format: rd' maps to x8-x15

        // nzuimm[5:4|9:6|2|3] from bits [12:5]
        int nzuimm = ((inst >> 6) & 0x1) << 2 // bit 2
                | ((inst >> 5) & 0x1) << 3 // bit 3
                | ((inst >> 11) & 0x3) << 4 // bits 5:4
                | ((inst >> 7) & 0xF) << 6; // bits 9:6

        if (nzuimm == 0)
            return 0; // Illegal (nzuimm must be non-zero)

        // addi rd, x2, nzuimm
        // I-type: imm[11:0] | rs1 | funct3 | rd | opcode
        return (nzuimm << 20) | (2 << 15) | (0 << 12) | (rd << 7) | 0b0010011;
    }

    // C.LW -> lw rd', offset(rs1')
    // Format: [15:13]=010 [12:10]=offset [9:7]=rs1' [6]=offset [5]=offset [4:2]=rd'
    // [1:0]=00
    private static int decompressCLW(int inst) {
        int rd_prime = (inst >> 2) & 0x7;
        int rs1_prime = (inst >> 7) & 0x7;
        int rd = rd_prime + 8;
        int rs1 = rs1_prime + 8;

        // offset[5:3|2|6] from bits [12:10|6|5]
        int offset = ((inst >> 6) & 0x1) << 2 // bit 2
                | ((inst >> 10) & 0x7) << 3 // bits 5:3
                | ((inst >> 5) & 0x1) << 6; // bit 6

        // lw rd, offset(rs1)
        // I-type: imm[11:0] | rs1 | funct3(010) | rd | opcode(0000011)
        return (offset << 20) | (rs1 << 15) | (0b010 << 12) | (rd << 7) | 0b0000011;
    }

    // C.SW -> sw rs2', offset(rs1')
    // Format: [15:13]=110 [12:10]=offset [9:7]=rs1' [6:5]=offset [4:2]=rs2'
    // [1:0]=00
    private static int decompressCSW(int inst) {
        int rs2_prime = (inst >> 2) & 0x7;
        int rs1_prime = (inst >> 7) & 0x7;
        int rs2 = rs2_prime + 8;
        int rs1 = rs1_prime + 8;

        // offset[5:3|2|6] from bits [12:10|6|5]
        int offset = ((inst >> 6) & 0x1) << 2 // bit 2
                | ((inst >> 10) & 0x7) << 3 // bits 5:3
                | ((inst >> 5) & 0x1) << 6; // bit 6

        // sw rs2, offset(rs1)
        // S-type: imm[11:5] | rs2 | rs1 | funct3(010) | imm[4:0] | opcode(0100011)
        int imm_11_5 = (offset >> 5) & 0x7F;
        int imm_4_0 = offset & 0x1F;
        return (imm_11_5 << 25) | (rs2 << 20) | (rs1 << 15) | (0b010 << 12) | (imm_4_0 << 7) | 0b0100011;
    }

    // ================================
    // Quadrant 1 (bits 1:0 = 01)
    // ================================
    private static int decompressQ1(int inst) {
        int funct3 = (inst >> 13) & 0x7;

        switch (funct3) {
            case 0:
                return decompressCADDI(inst); // C.NOP / C.ADDI
            case 1:
                return decompressCJAL(inst); // C.JAL
            case 2:
                return decompressCLI(inst); // C.LI
            case 3:
                return decompressCLUI(inst); // C.LUI / C.ADDI16SP
            case 4:
                return decompressCALU(inst); // C.SRLI/SRAI/ANDI/SUB/XOR/OR/AND
            case 5:
                return decompressCJ(inst); // C.J
            case 6:
                return decompressCBEQZ(inst); // C.BEQZ
            case 7:
                return decompressCBNEZ(inst); // C.BNEZ
            default:
                return 0;
        }
    }

    // C.NOP / C.ADDI -> addi rd, rd, nzimm
    private static int decompressCADDI(int inst) {
        int rd = (inst >> 7) & 0x1F;
        // nzimm[5] from bit 12, nzimm[4:0] from bits 6:2
        int imm = ((inst >> 2) & 0x1F) | (((inst >> 12) & 0x1) << 5);
        // Sign-extend 6-bit immediate
        imm = (imm << 26) >> 26;

        if (rd == 0) {
            // C.NOP -> addi x0, x0, 0
            return 0b0010011; // addi x0, x0, 0
        }

        // addi rd, rd, imm
        return ((imm & 0xFFF) << 20) | (rd << 15) | (0 << 12) | (rd << 7) | 0b0010011;
    }

    // C.JAL -> jal x1, offset (RV32 only)
    private static int decompressCJAL(int inst) {
        int offset = decodeCJOffset(inst);

        // jal x1, offset
        // J-type: imm[20|10:1|11|19:12] | rd | opcode(1101111)
        return encodeJType(offset, 1);
    }

    // C.LI -> addi rd, x0, imm
    private static int decompressCLI(int inst) {
        int rd = (inst >> 7) & 0x1F;
        int imm = ((inst >> 2) & 0x1F) | (((inst >> 12) & 0x1) << 5);
        // Sign-extend 6-bit immediate
        imm = (imm << 26) >> 26;

        if (rd == 0)
            return 0; // Illegal (HINT)

        // addi rd, x0, imm
        return ((imm & 0xFFF) << 20) | (0 << 15) | (0 << 12) | (rd << 7) | 0b0010011;
    }

    // C.LUI / C.ADDI16SP
    private static int decompressCLUI(int inst) {
        int rd = (inst >> 7) & 0x1F;

        if (rd == 2) {
            // C.ADDI16SP -> addi x2, x2, nzimm
            // nzimm[9] from bit 12, nzimm[4|6|8:7|5] from bits 6:2
            int nzimm = ((inst >> 2) & 0x1) << 5 // bit 5
                    | ((inst >> 3) & 0x3) << 7 // bits 8:7
                    | ((inst >> 5) & 0x1) << 6 // bit 6
                    | ((inst >> 6) & 0x1) << 4 // bit 4
                    | ((inst >> 12) & 0x1) << 9; // bit 9
            // Sign-extend 10-bit immediate
            nzimm = (nzimm << 22) >> 22;

            if (nzimm == 0)
                return 0; // Illegal

            // addi x2, x2, nzimm
            return ((nzimm & 0xFFF) << 20) | (2 << 15) | (0 << 12) | (2 << 7) | 0b0010011;
        } else {
            // C.LUI -> lui rd, nzimm
            int nzimm = ((inst >> 2) & 0x1F) | (((inst >> 12) & 0x1) << 5);
            // Sign-extend 6-bit immediate
            nzimm = (nzimm << 26) >> 26;

            if (nzimm == 0 || rd == 0)
                return 0; // Illegal

            // lui rd, nzimm (nzimm is the upper immediate, bits [17:12])
            // U-type: imm[31:12] | rd | opcode(0110111)
            return ((nzimm & 0xFFFFF) << 12) | (rd << 7) | 0b0110111;
        }
    }

    // C.SRLI, C.SRAI, C.ANDI, C.SUB, C.XOR, C.OR, C.AND
    private static int decompressCALU(int inst) {
        int funct2 = (inst >> 10) & 0x3;
        int rd_prime = (inst >> 7) & 0x7;
        int rd = rd_prime + 8;

        switch (funct2) {
            case 0: { // C.SRLI
                int shamt = ((inst >> 2) & 0x1F) | (((inst >> 12) & 0x1) << 5);
                // srli rd, rd, shamt
                return (shamt << 20) | (rd << 15) | (0b101 << 12) | (rd << 7) | 0b0010011;
            }
            case 1: { // C.SRAI
                int shamt = ((inst >> 2) & 0x1F) | (((inst >> 12) & 0x1) << 5);
                // srai rd, rd, shamt
                return (0b0100000 << 25) | (shamt << 20) | (rd << 15) | (0b101 << 12) | (rd << 7) | 0b0010011;
            }
            case 2: { // C.ANDI
                int imm = ((inst >> 2) & 0x1F) | (((inst >> 12) & 0x1) << 5);
                imm = (imm << 26) >> 26; // Sign-extend
                // andi rd, rd, imm
                return ((imm & 0xFFF) << 20) | (rd << 15) | (0b111 << 12) | (rd << 7) | 0b0010011;
            }
            case 3: { // Register-register ops
                int funct1 = (inst >> 12) & 0x1;
                int funct2b = (inst >> 5) & 0x3;
                int rs2_prime = (inst >> 2) & 0x7;
                int rs2 = rs2_prime + 8;

                if (funct1 == 0) {
                    switch (funct2b) {
                        case 0: // C.SUB -> sub rd, rd, rs2
                            return (0b0100000 << 25) | (rs2 << 20) | (rd << 15) | (0b000 << 12) | (rd << 7) | 0b0110011;
                        case 1: // C.XOR -> xor rd, rd, rs2
                            return (rs2 << 20) | (rd << 15) | (0b100 << 12) | (rd << 7) | 0b0110011;
                        case 2: // C.OR -> or rd, rd, rs2
                            return (rs2 << 20) | (rd << 15) | (0b110 << 12) | (rd << 7) | 0b0110011;
                        case 3: // C.AND -> and rd, rd, rs2
                            return (rs2 << 20) | (rd << 15) | (0b111 << 12) | (rd << 7) | 0b0110011;
                    }
                }
                return 0; // Illegal (funct1=1 is RV64C only)
            }
            default:
                return 0;
        }
    }

    // C.J -> jal x0, offset
    private static int decompressCJ(int inst) {
        int offset = decodeCJOffset(inst);
        // jal x0, offset
        return encodeJType(offset, 0);
    }

    // C.BEQZ -> beq rs1', x0, offset
    private static int decompressCBEQZ(int inst) {
        int rs1_prime = (inst >> 7) & 0x7;
        int rs1 = rs1_prime + 8;
        int offset = decodeCBOffset(inst);

        // beq rs1, x0, offset
        return encodeBType(offset, rs1, 0, 0b000);
    }

    // C.BNEZ -> bne rs1', x0, offset
    private static int decompressCBNEZ(int inst) {
        int rs1_prime = (inst >> 7) & 0x7;
        int rs1 = rs1_prime + 8;
        int offset = decodeCBOffset(inst);

        // bne rs1, x0, offset
        return encodeBType(offset, rs1, 0, 0b001);
    }

    // ================================
    // Quadrant 2 (bits 1:0 = 10)
    // ================================
    private static int decompressQ2(int inst) {
        int funct3 = (inst >> 13) & 0x7;

        switch (funct3) {
            case 0:
                return decompressCSLLI(inst); // C.SLLI
            case 2:
                return decompressCLWSP(inst); // C.LWSP
            case 4:
                return decompressCR(inst); // C.JR/C.MV/C.EBREAK/C.JALR/C.ADD
            case 6:
                return decompressCSWSP(inst); // C.SWSP
            default:
                return 0; // Unimplemented (FP)
        }
    }

    // C.SLLI -> slli rd, rd, shamt
    private static int decompressCSLLI(int inst) {
        int rd = (inst >> 7) & 0x1F;
        int shamt = ((inst >> 2) & 0x1F) | (((inst >> 12) & 0x1) << 5);

        if (rd == 0)
            return 0; // HINT

        // slli rd, rd, shamt
        return (shamt << 20) | (rd << 15) | (0b001 << 12) | (rd << 7) | 0b0010011;
    }

    // C.LWSP -> lw rd, offset(x2)
    private static int decompressCLWSP(int inst) {
        int rd = (inst >> 7) & 0x1F;
        // offset[5] from bit 12, offset[4:2|7:6] from bits 6:2
        int offset = ((inst >> 2) & 0x3) << 6 // bits 7:6
                | ((inst >> 4) & 0x7) << 2 // bits 4:2
                | ((inst >> 12) & 0x1) << 5; // bit 5

        if (rd == 0)
            return 0; // Illegal

        // lw rd, offset(x2)
        return (offset << 20) | (2 << 15) | (0b010 << 12) | (rd << 7) | 0b0000011;
    }

    // C.JR / C.MV / C.EBREAK / C.JALR / C.ADD
    private static int decompressCR(int inst) {
        int rd = (inst >> 7) & 0x1F;
        int rs2 = (inst >> 2) & 0x1F;
        int bit12 = (inst >> 12) & 0x1;

        if (bit12 == 0) {
            if (rs2 == 0) {
                // C.JR -> jalr x0, rs1, 0
                if (rd == 0)
                    return 0; // Illegal
                return (rd << 15) | (0 << 12) | (0 << 7) | 0b1100111;
            } else {
                // C.MV -> add rd, x0, rs2
                if (rd == 0)
                    return 0; // HINT
                return (rs2 << 20) | (0 << 15) | (0 << 12) | (rd << 7) | 0b0110011;
            }
        } else {
            if (rs2 == 0) {
                if (rd == 0) {
                    // C.EBREAK -> ebreak
                    return 0x00100073;
                } else {
                    // C.JALR -> jalr x1, rs1, 0
                    return (rd << 15) | (0 << 12) | (1 << 7) | 0b1100111;
                }
            } else {
                // C.ADD -> add rd, rd, rs2
                if (rd == 0)
                    return 0; // HINT
                return (rs2 << 20) | (rd << 15) | (0 << 12) | (rd << 7) | 0b0110011;
            }
        }
    }

    // C.SWSP -> sw rs2, offset(x2)
    private static int decompressCSWSP(int inst) {
        int rs2 = (inst >> 2) & 0x1F;
        // offset[5:2|7:6] from bits 12:7
        int offset = ((inst >> 7) & 0x3) << 6 // bits 7:6
                | ((inst >> 9) & 0xF) << 2; // bits 5:2

        // sw rs2, offset(x2)
        int imm_11_5 = (offset >> 5) & 0x7F;
        int imm_4_0 = offset & 0x1F;
        return (imm_11_5 << 25) | (rs2 << 20) | (2 << 15) | (0b010 << 12) | (imm_4_0 << 7) | 0b0100011;
    }

    // ================================
    // Helper: Decode CJ-type offset
    // ================================
    // Used by C.J and C.JAL
    // offset[11|4|9:8|10|6|7|3:1|5] from bits [12:2]
    private static int decodeCJOffset(int inst) {
        int offset = ((inst >> 2) & 0x1) << 5 // bit 5
                | ((inst >> 3) & 0x7) << 1 // bits 3:1
                | ((inst >> 6) & 0x1) << 7 // bit 7
                | ((inst >> 7) & 0x1) << 6 // bit 6
                | ((inst >> 8) & 0x1) << 10 // bit 10
                | ((inst >> 9) & 0x3) << 8 // bits 9:8
                | ((inst >> 11) & 0x1) << 4 // bit 4
                | ((inst >> 12) & 0x1) << 11; // bit 11 (sign)
        // Sign-extend 12-bit offset
        offset = (offset << 20) >> 20;
        return offset;
    }

    // ================================
    // Helper: Decode CB-type offset
    // ================================
    // Used by C.BEQZ and C.BNEZ
    // offset[8|4:3] from bits [12:10], offset[7:6|2:1|5] from bits [6:2]
    private static int decodeCBOffset(int inst) {
        int offset = ((inst >> 2) & 0x1) << 5 // bit 5
                | ((inst >> 3) & 0x3) << 1 // bits 2:1
                | ((inst >> 5) & 0x3) << 6 // bits 7:6
                | ((inst >> 10) & 0x3) << 3 // bits 4:3
                | ((inst >> 12) & 0x1) << 8; // bit 8 (sign)
        // Sign-extend 9-bit offset
        offset = (offset << 23) >> 23;
        return offset;
    }

    // ================================
    // Helper: Encode J-type 32-bit instruction
    // ================================
    private static int encodeJType(int offset, int rd) {
        // J-type: imm[20|10:1|11|19:12] | rd | opcode(1101111)
        int imm20 = (offset >> 20) & 0x1;
        int imm10_1 = (offset >> 1) & 0x3FF;
        int imm11 = (offset >> 11) & 0x1;
        int imm19_12 = (offset >> 12) & 0xFF;

        return (imm20 << 31) | (imm10_1 << 21) | (imm11 << 20) | (imm19_12 << 12) | (rd << 7) | 0b1101111;
    }

    // ================================
    // Helper: Encode B-type 32-bit instruction
    // ================================
    private static int encodeBType(int offset, int rs1, int rs2, int funct3) {
        // B-type: imm[12|10:5] | rs2 | rs1 | funct3 | imm[4:1|11] | opcode(1100011)
        int imm12 = (offset >> 12) & 0x1;
        int imm10_5 = (offset >> 5) & 0x3F;
        int imm4_1 = (offset >> 1) & 0xF;
        int imm11 = (offset >> 11) & 0x1;

        return (imm12 << 31) | (imm10_5 << 25) | (rs2 << 20) | (rs1 << 15) | (funct3 << 12)
                | (imm4_1 << 8) | (imm11 << 7) | 0b1100011;
    }
}
