package cse311;

/**
 * RISC-V Disassembler
 * Converts 32-bit machine code into human-readable assembly.
 * Supports RV32I + M-extension.
 */
public class Disassembler {

    private static final String[] REG_NAMES = {
            "zero", "ra", "sp", "gp", "tp", "t0", "t1", "t2",
            "s0", "s1", "a0", "a1", "a2", "a3", "a4", "a5",
            "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7",
            "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"
    };

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
                    return "ret";
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
