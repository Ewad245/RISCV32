package cse311;

/**
 * RISC-V Disassembler
 * Converts 32-bit machine code into human-readable assembly.
 * Supports RV32I + M-extension + RVC (Compressed).
 */
public class Disassembler {

    private static final String[] REG_NAMES = {
            "zero", "ra", "sp", "gp", "tp", "t0", "t1", "t2",
            "s0", "s1", "a0", "a1", "a2", "a3", "a4", "a5",
            "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7",
            "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"
    };

    /**
     * Disassemble a 16-bit compressed instruction into human-readable assembly.
     * 
     * @param inst16 The 16-bit compressed instruction
     * @param pc     The program counter of this instruction
     * @return Human-readable assembly string
     */
    public static String disassembleCompressed(int inst16, int pc) {
        try {
            inst16 = inst16 & 0xFFFF;
            int quadrant = inst16 & 0x3;
            int funct3 = (inst16 >> 13) & 0x7;

            switch (quadrant) {
                case 0: // Quadrant 0
                    switch (funct3) {
                        case 0: { // C.ADDI4SPN
                            int rd = ((inst16 >> 2) & 0x7) + 8;
                            int nzuimm = ((inst16 >> 6) & 0x1) << 2
                                    | ((inst16 >> 5) & 0x1) << 3
                                    | ((inst16 >> 11) & 0x3) << 4
                                    | ((inst16 >> 7) & 0xF) << 6;
                            return String.format("c.addi4spn %s, sp, %d", reg(rd), nzuimm);
                        }
                        case 2: { // C.LW
                            int rd = ((inst16 >> 2) & 0x7) + 8;
                            int rs1 = ((inst16 >> 7) & 0x7) + 8;
                            int off = ((inst16 >> 6) & 0x1) << 2
                                    | ((inst16 >> 10) & 0x7) << 3
                                    | ((inst16 >> 5) & 0x1) << 6;
                            return String.format("c.lw    %s, %d(%s)", reg(rd), off, reg(rs1));
                        }
                        case 6: { // C.SW
                            int rs2 = ((inst16 >> 2) & 0x7) + 8;
                            int rs1 = ((inst16 >> 7) & 0x7) + 8;
                            int off = ((inst16 >> 6) & 0x1) << 2
                                    | ((inst16 >> 10) & 0x7) << 3
                                    | ((inst16 >> 5) & 0x1) << 6;
                            return String.format("c.sw    %s, %d(%s)", reg(rs2), off, reg(rs1));
                        }
                    }
                    break;

                case 1: // Quadrant 1
                    switch (funct3) {
                        case 0: { // C.NOP / C.ADDI
                            int rd = (inst16 >> 7) & 0x1F;
                            int imm = ((inst16 >> 2) & 0x1F) | (((inst16 >> 12) & 0x1) << 5);
                            imm = (imm << 26) >> 26;
                            if (rd == 0)
                                return "c.nop";
                            return String.format("c.addi  %s, %d", reg(rd), imm);
                        }
                        case 1: { // C.JAL
                            int off = decodeCJOffset(inst16);
                            return String.format("c.jal   0x%x", pc + off);
                        }
                        case 2: { // C.LI
                            int rd = (inst16 >> 7) & 0x1F;
                            int imm = ((inst16 >> 2) & 0x1F) | (((inst16 >> 12) & 0x1) << 5);
                            imm = (imm << 26) >> 26;
                            return String.format("c.li    %s, %d", reg(rd), imm);
                        }
                        case 3: { // C.LUI / C.ADDI16SP
                            int rd = (inst16 >> 7) & 0x1F;
                            if (rd == 2) {
                                int nzimm = ((inst16 >> 2) & 0x1) << 5
                                        | ((inst16 >> 3) & 0x3) << 7
                                        | ((inst16 >> 5) & 0x1) << 6
                                        | ((inst16 >> 6) & 0x1) << 4
                                        | ((inst16 >> 12) & 0x1) << 9;
                                nzimm = (nzimm << 22) >> 22;
                                return String.format("c.addi16sp sp, %d", nzimm);
                            } else {
                                int imm = ((inst16 >> 2) & 0x1F) | (((inst16 >> 12) & 0x1) << 5);
                                imm = (imm << 26) >> 26;
                                return String.format("c.lui   %s, 0x%x", reg(rd), imm & 0xFFFFF);
                            }
                        }
                        case 4: { // ALU ops
                            int funct2 = (inst16 >> 10) & 0x3;
                            int rd = ((inst16 >> 7) & 0x7) + 8;
                            switch (funct2) {
                                case 0: {
                                    int shamt = ((inst16 >> 2) & 0x1F) | (((inst16 >> 12) & 0x1) << 5);
                                    return String.format("c.srli  %s, %d", reg(rd), shamt);
                                }
                                case 1: {
                                    int shamt = ((inst16 >> 2) & 0x1F) | (((inst16 >> 12) & 0x1) << 5);
                                    return String.format("c.srai  %s, %d", reg(rd), shamt);
                                }
                                case 2: {
                                    int imm = ((inst16 >> 2) & 0x1F) | (((inst16 >> 12) & 0x1) << 5);
                                    imm = (imm << 26) >> 26;
                                    return String.format("c.andi  %s, %d", reg(rd), imm);
                                }
                                case 3: {
                                    int funct1 = (inst16 >> 12) & 0x1;
                                    int funct2b = (inst16 >> 5) & 0x3;
                                    int rs2 = ((inst16 >> 2) & 0x7) + 8;
                                    if (funct1 == 0) {
                                        switch (funct2b) {
                                            case 0:
                                                return String.format("c.sub   %s, %s", reg(rd), reg(rs2));
                                            case 1:
                                                return String.format("c.xor   %s, %s", reg(rd), reg(rs2));
                                            case 2:
                                                return String.format("c.or    %s, %s", reg(rd), reg(rs2));
                                            case 3:
                                                return String.format("c.and   %s, %s", reg(rd), reg(rs2));
                                        }
                                    }
                                    break;
                                }
                            }
                            break;
                        }
                        case 5: { // C.J
                            int off = decodeCJOffset(inst16);
                            return String.format("c.j     0x%x", pc + off);
                        }
                        case 6: { // C.BEQZ
                            int rs1 = ((inst16 >> 7) & 0x7) + 8;
                            int off = decodeCBOffset(inst16);
                            return String.format("c.beqz  %s, 0x%x", reg(rs1), pc + off);
                        }
                        case 7: { // C.BNEZ
                            int rs1 = ((inst16 >> 7) & 0x7) + 8;
                            int off = decodeCBOffset(inst16);
                            return String.format("c.bnez  %s, 0x%x", reg(rs1), pc + off);
                        }
                    }
                    break;

                case 2: // Quadrant 2
                    switch (funct3) {
                        case 0: { // C.SLLI
                            int rd = (inst16 >> 7) & 0x1F;
                            int shamt = ((inst16 >> 2) & 0x1F) | (((inst16 >> 12) & 0x1) << 5);
                            return String.format("c.slli  %s, %d", reg(rd), shamt);
                        }
                        case 2: { // C.LWSP
                            int rd = (inst16 >> 7) & 0x1F;
                            int off = ((inst16 >> 2) & 0x3) << 6
                                    | ((inst16 >> 4) & 0x7) << 2
                                    | ((inst16 >> 12) & 0x1) << 5;
                            return String.format("c.lwsp  %s, %d(sp)", reg(rd), off);
                        }
                        case 4: { // C.JR / C.MV / C.EBREAK / C.JALR / C.ADD
                            int rd = (inst16 >> 7) & 0x1F;
                            int rs2 = (inst16 >> 2) & 0x1F;
                            int bit12 = (inst16 >> 12) & 0x1;
                            if (bit12 == 0) {
                                if (rs2 == 0)
                                    return String.format("c.jr    %s", reg(rd));
                                return String.format("c.mv    %s, %s", reg(rd), reg(rs2));
                            } else {
                                if (rs2 == 0 && rd == 0)
                                    return "c.ebreak";
                                if (rs2 == 0)
                                    return String.format("c.jalr  %s", reg(rd));
                                return String.format("c.add   %s, %s", reg(rd), reg(rs2));
                            }
                        }
                        case 6: { // C.SWSP
                            int rs2 = (inst16 >> 2) & 0x1F;
                            int off = ((inst16 >> 7) & 0x3) << 6
                                    | ((inst16 >> 9) & 0xF) << 2;
                            return String.format("c.swsp  %s, %d(sp)", reg(rs2), off);
                        }
                    }
                    break;
            }
            return String.format("c.unk   0x%04x", inst16);
        } catch (Exception e) {
            return "c.de-err";
        }
    }

    // Helper: decode CJ-type offset for disassembler
    private static int decodeCJOffset(int inst) {
        int offset = ((inst >> 2) & 0x1) << 5
                | ((inst >> 3) & 0x7) << 1
                | ((inst >> 6) & 0x1) << 7
                | ((inst >> 7) & 0x1) << 6
                | ((inst >> 8) & 0x1) << 10
                | ((inst >> 9) & 0x3) << 8
                | ((inst >> 11) & 0x1) << 4
                | ((inst >> 12) & 0x1) << 11;
        return (offset << 20) >> 20;
    }

    // Helper: decode CB-type offset for disassembler
    private static int decodeCBOffset(int inst) {
        int offset = ((inst >> 2) & 0x1) << 5
                | ((inst >> 3) & 0x3) << 1
                | ((inst >> 5) & 0x3) << 6
                | ((inst >> 10) & 0x3) << 3
                | ((inst >> 12) & 0x1) << 8;
        return (offset << 23) >> 23;
    }

    public static String disassemble(int instruction, int pc) {
        try {
            int opcode = instruction & 0x7F;
            int rd = (instruction >> 7) & 0x1F;
            int func3 = (instruction >> 12) & 0x7;
            int rs1 = (instruction >> 15) & 0x1F;
            int rs2 = (instruction >> 20) & 0x1F;
            int func7 = (instruction >> 25) & 0x7F;

            // Immediate decoding
            int imm_i = (instruction >> 20); // Sign-extended by Java >> operator
            int imm_s = ((instruction >> 25) << 5) | ((instruction >> 7) & 0x1F);
            imm_s = (imm_s << 20) >> 20; // Sign extend
            int imm_b = ((instruction >> 31) << 12) | ((instruction & 0x80) << 4) | ((instruction >> 20) & 0x7E0)
                    | ((instruction >> 7) & 0x1E);
            imm_b = (imm_b << 19) >> 19; // Sign extend
            int imm_u = instruction & 0xFFFFF000;
            int imm_j = ((instruction >> 31) << 20) | ((instruction & 0xFF000) >> 0) | ((instruction & 0x100000) >> 9)
                    | ((instruction & 0x7FE00000) >> 20); // Buggy manual extraction, let's use the known good one from
                                                          // RV32Cpu
            // Re-doing imm_j correctly based on spec:
            // imm[20|10:1|11|19:12]
            imm_j = ((instruction >> 31) << 20) |
                    ((instruction >> 12) & 0xFF) << 12 |
                    ((instruction >> 20) & 0x1) << 11 |
                    ((instruction >> 21) & 0x3FF) << 1;
            imm_j = (imm_j << 11) >> 11; // Sign extend

            // R-Type
            if (opcode == 0x33) {
                String op = "unknown";
                if (func7 == 0x00) {
                    switch (func3) {
                        case 0b000:
                            op = "add";
                            break;
                        case 0b001:
                            op = "sll";
                            break;
                        case 0b010:
                            op = "slt";
                            break;
                        case 0b011:
                            op = "sltu";
                            break;
                        case 0b100:
                            op = "xor";
                            break;
                        case 0b101:
                            op = "srl";
                            break;
                        case 0b110:
                            op = "or";
                            break;
                        case 0b111:
                            op = "and";
                            break;
                    }
                } else if (func7 == 0x20) {
                    switch (func3) {
                        case 0b000:
                            op = "sub";
                            break;
                        case 0b101:
                            op = "sra";
                            break;
                    }
                } else if (func7 == 0x01) { // M-Extension
                    switch (func3) {
                        case 0b000:
                            op = "mul";
                            break;
                        case 0b001:
                            op = "mulh";
                            break;
                        case 0b010:
                            op = "mulhsu";
                            break;
                        case 0b011:
                            op = "mulhu";
                            break;
                        case 0b100:
                            op = "div";
                            break;
                        case 0b101:
                            op = "divu";
                            break;
                        case 0b110:
                            op = "rem";
                            break;
                        case 0b111:
                            op = "remu";
                            break;
                    }
                }
                return String.format("%-7s %s, %s, %s", op, reg(rd), reg(rs1), reg(rs2));
            }

            // I-Type ALU
            if (opcode == 0x13) {
                String op = "unknown";
                switch (func3) {
                    case 0b000:
                        if (rd == 0 && rs1 == 0 && imm_i == 0)
                            return "nop";
                        return (rs1 == 0) ? String.format("%-7s %s, %d", "li", reg(rd), imm_i)
                                : String.format("%-7s %s, %s, %d", "addi", reg(rd), reg(rs1), imm_i);
                    case 0b001:
                        return String.format("%-7s %s, %s, 0x%x", "slli", reg(rd), reg(rs1), imm_i & 0x1F);
                    case 0b010:
                        return String.format("%-7s %s, %s, %d", "slti", reg(rd), reg(rs1), imm_i);
                    case 0b011:
                        return String.format("%-7s %s, %s, %d", "sltiu", reg(rd), reg(rs1), imm_i);
                    case 0b100:
                        return String.format("%-7s %s, %s, %d", "xori", reg(rd), reg(rs1), imm_i);
                    case 0b101:
                        if (((instruction >> 25) & 0x7F) == 0x00)
                            return String.format("%-7s %s, %s, 0x%x", "srli", reg(rd), reg(rs1), imm_i & 0x1F);
                        if (((instruction >> 25) & 0x7F) == 0x20)
                            return String.format("%-7s %s, %s, 0x%x", "srai", reg(rd), reg(rs1), imm_i & 0x1F);
                        break;
                    case 0b110:
                        return String.format("%-7s %s, %s, %d", "ori", reg(rd), reg(rs1), imm_i);
                    case 0b111:
                        return String.format("%-7s %s, %s, %d", "andi", reg(rd), reg(rs1), imm_i);
                }
                return op;
            }

            // Load
            if (opcode == 0x03) {
                String op = switch (func3) {
                    case 0b000 -> "lb";
                    case 0b001 -> "lh";
                    case 0b010 -> "lw";
                    case 0b100 -> "lbu";
                    case 0b101 -> "lhu";
                    default -> "load?";
                };
                return String.format("%-7s %s, %d(%s)", op, reg(rd), imm_i, reg(rs1));
            }

            // Store
            if (opcode == 0x23) {
                String op = switch (func3) {
                    case 0b000 -> "sb";
                    case 0b001 -> "sh";
                    case 0b010 -> "sw";
                    default -> "store?";
                };
                return String.format("%-7s %s, %d(%s)", op, reg(rs2), imm_s, reg(rs1));
            }

            // Branch
            if (opcode == 0x63) {
                String op = switch (func3) {
                    case 0b000 -> "beq";
                    case 0b001 -> "bne";
                    case 0b100 -> "blt";
                    case 0b101 -> "bge";
                    case 0b110 -> "bltu";
                    case 0b111 -> "bgeu";
                    default -> "branch?";
                };
                return String.format("%-7s %s, %s, %d <0x%x>", op, reg(rs1), reg(rs2), imm_b, pc + imm_b);
            }

            // JAL
            if (opcode == 0x6F) {
                if (rd == 0)
                    return String.format("%-7s %d <0x%x>", "j", imm_j, pc + imm_j);
                return String.format("%-7s %s, %d <0x%x>", "jal", reg(rd), imm_j, pc + imm_j);
            }

            // JALR
            if (opcode == 0x67) {
                if (rd == 0 && rs1 == 1 && imm_i == 0)
                    return "jalr x0, ra, 0";
                return String.format("%-7s %s, %d(%s)", "jalr", reg(rd), imm_i, reg(rs1));
            }

            // LUI
            if (opcode == 0x37) {
                return String.format("%-7s %s, 0x%x", "lui", reg(rd), (imm_u >>> 12));
            }

            // AUIPC
            if (opcode == 0x17) {
                return String.format("%-7s %s, 0x%x", "auipc", reg(rd), (imm_u >>> 12));
            }

            // System (ECALL/EBREAK/CSR)
            if (opcode == 0x73) {
                if (func3 == 0) {
                    if (imm_i == 0)
                        return "ecall";
                    if (imm_i == 1)
                        return "ebreak";
                    if (imm_i == 0x302)
                        return "mret";
                    if (imm_i == 0x102)
                        return "sret";
                    if (imm_i == 0x002)
                        return "uret"; // Rarely used
                    if (imm_i == 0x105)
                        return "wfi";
                } else {
                    // CSR instructions
                    String op = switch (func3) {
                        case 0b001 -> "csrrw";
                        case 0b010 -> "csrrs";
                        case 0b011 -> "csrrc";
                        case 0b101 -> "csrrwi";
                        case 0b110 -> "csrrsi";
                        case 0b111 -> "csrrci";
                        default -> "csr?";
                    };
                    int csr = imm_i & 0xFFF; // CSR address is the "immediate" field 12 bits
                    if (func3 < 4) {
                        return String.format("%-7s %s, 0x%x, %s", op, reg(rd), csr, reg(rs1));
                    } else {
                        return String.format("%-7s %s, 0x%x, %d", op, reg(rd), csr, rs1); // rs1 field is uimm for
                                                                                          // immediate versions
                    }
                }
            }

            // Fence
            if (opcode == 0x0F) {
                return "fence";
            }

            // Atomic (RV32A)
            if (opcode == 0x2F) {
                if (func3 == 2) {
                    int funct5 = func7 >> 2;
                    int aq = (func7 >> 1) & 1;
                    int rl = func7 & 1;
                    String suffix = ".w" + (aq == 1 && rl == 1 ? ".aqrl" : (aq == 1 ? ".aq" : (rl == 1 ? ".rl" : "")));
                    String op = "amo?";
                    switch (funct5) {
                        case 0x02:
                            return String.format("%-7s %s, (%s)", "lr" + suffix, reg(rd), reg(rs1));
                        case 0x03:
                            return String.format("%-7s %s, %s, (%s)", "sc" + suffix, reg(rd), reg(rs2), reg(rs1));
                        case 0x01:
                            op = "amoswap";
                            break;
                        case 0x00:
                            op = "amoadd";
                            break;
                        case 0x04:
                            op = "amoxor";
                            break;
                        case 0x0C:
                            op = "amoand";
                            break;
                        case 0x08:
                            op = "amoor";
                            break;
                        case 0x10:
                            op = "amomin";
                            break;
                        case 0x14:
                            op = "amomax";
                            break;
                        case 0x18:
                            op = "amominu";
                            break;
                        case 0x1C:
                            op = "amomaxu";
                            break;
                    }
                    if (!op.equals("amo?")) {
                        return String.format("%-7s %s, %s, (%s)", op + suffix, reg(rd), reg(rs2), reg(rs1));
                    }
                }
            }

            return String.format("unk 0x%08x", instruction);

        } catch (Exception e) {
            return "de-err";
        }
    }

    private static String reg(int index) {
        if (index >= 0 && index < 32) {
            return REG_NAMES[index];
        }
        return "x" + index;
    }
}
