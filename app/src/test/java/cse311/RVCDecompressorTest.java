package cse311;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RVCDecompressorTest {

    private static final String RD_X10 = "rd should be x10";
    private static final String RS1_X10 = "rs1 should be x10";
    private static final String OP_IMM = "Opcode should be OP-IMM";
    private static final String OP_R = "Opcode should be OP";
    private static final String OP_JAL = "Opcode should be JAL";

    @Test
    void testIsCompressed() {
        assertAll("isCompressedChecks",
            () -> assertFalse(RVCDecompressor.isCompressed(0x00000013), "NOP is 32-bit (not compressed)"),
            () -> assertFalse(RVCDecompressor.isCompressed(0x00000073), "ECALL is 32-bit (not compressed)"),
            () -> assertTrue(RVCDecompressor.isCompressed(0x0001), "C.NOP is compressed"),
            () -> assertTrue(RVCDecompressor.isCompressed(0x4100), "C.LW is compressed"),
            () -> assertTrue(RVCDecompressor.isCompressed(0x4082), "C.LWSP is compressed")
        );
    }

    @Test
    void testCNopAndCAddi() {
        int nop32 = RVCDecompressor.decompress(0x0001);

        int cAddi = (0b000 << 13) | (0 << 12) | (10 << 7) | (5 << 2) | 0b01;
        int addi32 = RVCDecompressor.decompress(cAddi);

        assertAll("cAddiChecks",
            () -> assertEquals(0x00000013, nop32, "C.NOP should decompress to 32-bit NOP"),
            () -> assertEquals(0b0010011, addi32 & 0x7F, OP_IMM),
            () -> assertEquals(10, (addi32 >> 7) & 0x1F, RD_X10),
            () -> assertEquals(10, (addi32 >> 15) & 0x1F, RS1_X10),
            () -> assertEquals(5, (addi32 >> 20) & 0xFFF, "imm should be 5")
        );
    }

    @Test
    void testCLiAndCLui() {
        int cLi = (0b010 << 13) | (0 << 12) | (10 << 7) | (15 << 2) | 0b01;
        int li32 = RVCDecompressor.decompress(cLi);

        int cLui = (0b011 << 13) | (0 << 12) | (10 << 7) | (1 << 2) | 0b01;
        int lui32 = RVCDecompressor.decompress(cLui);

        assertAll("cLiLuiChecks",
            () -> assertEquals(0b0010011, li32 & 0x7F, OP_IMM),
            () -> assertEquals(10, (li32 >> 7) & 0x1F, RD_X10),
            () -> assertEquals(0, (li32 >> 15) & 0x1F, "rs1 should be x0"),
            () -> assertEquals(15, (li32 >> 20) & 0xFFF, "imm should be 15"),
            () -> assertEquals(0b0110111, lui32 & 0x7F, "Opcode should be LUI"),
            () -> assertEquals(10, (lui32 >> 7) & 0x1F, RD_X10)
        );
    }

    @Test
    void testQuadrant0MemoryOps() {
        int cLw = (0b010 << 13) | (0 << 10) | (0 << 7) | (1 << 6) | (0 << 5) | (1 << 2) | 0b00;
        int lw32 = RVCDecompressor.decompress(cLw);

        int cSw = (0b110 << 13) | (0 << 10) | (0 << 7) | (1 << 6) | (0 << 5) | (1 << 2) | 0b00;
        int sw32 = RVCDecompressor.decompress(cSw);

        assertAll("quadrant0Checks",
            () -> assertEquals(0b0000011, lw32 & 0x7F, "Opcode should be LOAD"),
            () -> assertEquals(9, (lw32 >> 7) & 0x1F, "rd should be x9"),
            () -> assertEquals(8, (lw32 >> 15) & 0x1F, "rs1 should be x8"),
            () -> assertEquals(0b0100011, sw32 & 0x7F, "Opcode should be STORE"),
            () -> assertEquals(8, (sw32 >> 15) & 0x1F, "rs1 should be x8"),
            () -> assertEquals(9, (sw32 >> 20) & 0x1F, "rs2 should be x9")
        );
    }

    @Test
    void testQuadrant2RegisterOps() {
        int cMv = (0b100 << 13) | (0 << 12) | (10 << 7) | (11 << 2) | 0b10;
        int mv32 = RVCDecompressor.decompress(cMv);

        int cAdd = (0b100 << 13) | (1 << 12) | (10 << 7) | (11 << 2) | 0b10;
        int add32 = RVCDecompressor.decompress(cAdd);

        int cEbreak = (0b100 << 13) | (1 << 12) | (0 << 7) | (0 << 2) | 0b10;
        int ebreak32 = RVCDecompressor.decompress(cEbreak);

        assertAll("quadrant2Checks",
            () -> assertEquals(0b0110011, mv32 & 0x7F, OP_R),
            () -> assertEquals(10, (mv32 >> 7) & 0x1F, RD_X10),
            () -> assertEquals(0, (mv32 >> 15) & 0x1F, "rs1 should be x0"),
            () -> assertEquals(11, (mv32 >> 20) & 0x1F, "rs2 should be x11"),
            () -> assertEquals(0b0110011, add32 & 0x7F, OP_R),
            () -> assertEquals(10, (add32 >> 7) & 0x1F, RD_X10),
            () -> assertEquals(10, (add32 >> 15) & 0x1F, RS1_X10),
            () -> assertEquals(11, (add32 >> 20) & 0x1F, "rs2 should be x11"),
            () -> assertEquals(0x00100073, ebreak32, "C.EBREAK should decompress to ebreak")
        );
    }

    @Test
    void testBranchAndJump() {
        int cJ = (0b101 << 13) | 0b01;
        int j32 = RVCDecompressor.decompress(cJ);

        int cJal = (0b001 << 13) | 0b01;
        int jal32 = RVCDecompressor.decompress(cJal);

        assertAll("jumpChecks",
            () -> assertEquals(0b1101111, j32 & 0x7F, OP_JAL),
            () -> assertEquals(0, (j32 >> 7) & 0x1F, "rd should be x0 for C.J"),
            () -> assertEquals(0b1101111, jal32 & 0x7F, OP_JAL),
            () -> assertEquals(1, (jal32 >> 7) & 0x1F, "rd should be x1 for C.JAL")
        );
    }
}
