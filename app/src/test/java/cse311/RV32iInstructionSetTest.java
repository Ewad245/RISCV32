package cse311;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import cse311.Exception.MemoryAccessException;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RV32iInstructionSetTest {

    private static final String ERR_SLT = "SLT comparison failed";
    private static final String ERR_SLTU = "SLTU unsigned comparison failed";

    private RV32Cpu cpu;
    private MemoryManager memory;

    @BeforeEach
    void setUp() {
        SimpleMemory simpleMemory = new SimpleMemory(16 * 1024 * 1024);
        memory = new MemoryManager(simpleMemory);
        cpu = new RV32Cpu(memory);
    }

    @Test
    void testSltSignedComparison() {
        InstructionDecoded slt = new InstructionDecoded();
        slt.setOpcode(0b0110011);
        slt.setRd(1);
        slt.setFunc3(0b010);
        slt.setRs1(2);
        slt.setRs2(3);
        slt.setFunc7(0b0000000);

        cpu.setRegister(2, -5);
        cpu.setRegister(3, 3);
        cpu.executeTest(slt);
        int res1 = cpu.getRegister(1);

        cpu.setRegister(2, 3);
        cpu.setRegister(3, -5);
        cpu.executeTest(slt);
        int res2 = cpu.getRegister(1);

        assertAll("sltResults",
            () -> assertEquals(1, res1, ERR_SLT),
            () -> assertEquals(0, res2, ERR_SLT)
        );
    }

    @Test
    void testSltuUnsignedComparison() {
        InstructionDecoded sltu = new InstructionDecoded();
        sltu.setOpcode(0b0110011);
        sltu.setRd(1);
        sltu.setFunc3(0b011);
        sltu.setRs1(2);
        sltu.setRs2(3);
        sltu.setFunc7(0b0000000);

        cpu.setRegister(2, -1); // 0xFFFFFFFF unsigned
        cpu.setRegister(3, 3);
        cpu.executeTest(sltu);
        int res1 = cpu.getRegister(1);

        cpu.setRegister(2, 3);
        cpu.setRegister(3, -1);
        cpu.executeTest(sltu);
        int res2 = cpu.getRegister(1);

        assertAll("sltuResults",
            () -> assertEquals(0, res1, ERR_SLTU),
            () -> assertEquals(1, res2, ERR_SLTU)
        );
    }

    @Test
    void testBitwiseLogicalRType() {
        InstructionDecoded andInst = new InstructionDecoded();
        andInst.setOpcode(0b0110011);
        andInst.setRd(1);
        andInst.setFunc3(0b111);
        andInst.setRs1(2);
        andInst.setRs2(3);
        andInst.setFunc7(0b0000000);

        cpu.setRegister(2, 0b1100);
        cpu.setRegister(3, 0b1010);
        cpu.executeTest(andInst);
        int andRes = cpu.getRegister(1);

        InstructionDecoded orInst = new InstructionDecoded();
        orInst.setOpcode(0b0110011);
        orInst.setRd(1);
        orInst.setFunc3(0b110);
        orInst.setRs1(2);
        orInst.setRs2(3);
        orInst.setFunc7(0b0000000);

        cpu.executeTest(orInst);
        int orRes = cpu.getRegister(1);

        InstructionDecoded xorInst = new InstructionDecoded();
        xorInst.setOpcode(0b0110011);
        xorInst.setRd(1);
        xorInst.setFunc3(0b100);
        xorInst.setRs1(2);
        xorInst.setRs2(3);
        xorInst.setFunc7(0b0000000);

        cpu.executeTest(xorInst);
        int xorRes = cpu.getRegister(1);

        assertAll("bitwiseResults",
            () -> assertEquals(0b1000, andRes, "AND failed"),
            () -> assertEquals(0b1110, orRes, "OR failed"),
            () -> assertEquals(0b0110, xorRes, "XOR failed")
        );
    }

    @Test
    void testShiftsRType() {
        InstructionDecoded sll = new InstructionDecoded();
        sll.setOpcode(0b0110011);
        sll.setRd(1);
        sll.setFunc3(0b001);
        sll.setRs1(2);
        sll.setRs2(3);
        sll.setFunc7(0b0000000);

        cpu.setRegister(2, 1);
        cpu.setRegister(3, 4);
        cpu.executeTest(sll);
        int sllRes = cpu.getRegister(1);

        InstructionDecoded srl = new InstructionDecoded();
        srl.setOpcode(0b0110011);
        srl.setRd(1);
        srl.setFunc3(0b101);
        srl.setRs1(2);
        srl.setRs2(3);
        srl.setFunc7(0b0000000);

        cpu.setRegister(2, 0xF0000000);
        cpu.setRegister(3, 4);
        cpu.executeTest(srl);
        int srlRes = cpu.getRegister(1);

        InstructionDecoded sra = new InstructionDecoded();
        sra.setOpcode(0b0110011);
        sra.setRd(1);
        sra.setFunc3(0b101);
        sra.setRs1(2);
        sra.setRs2(3);
        sra.setFunc7(0b0100000);

        cpu.setRegister(2, 0xF0000000);
        cpu.setRegister(3, 4);
        cpu.executeTest(sra);
        int sraRes = cpu.getRegister(1);

        assertAll("shiftResults",
            () -> assertEquals(16, sllRes, "SLL failed"),
            () -> assertEquals(0x0F000000, srlRes, "SRL failed"),
            () -> assertEquals(0xFF000000, sraRes, "SRA failed")
        );
    }

    @Test
    void testImmediateShiftsAndLogic() {
        InstructionDecoded slli = new InstructionDecoded();
        slli.setOpcode(0b0010011);
        slli.setRd(1);
        slli.setFunc3(0b001);
        slli.setRs1(2);
        slli.setImm_i(2);

        cpu.setRegister(2, 3);
        cpu.executeTest(slli);
        int slliRes = cpu.getRegister(1);

        InstructionDecoded andi = new InstructionDecoded();
        andi.setOpcode(0b0010011);
        andi.setRd(1);
        andi.setFunc3(0b111);
        andi.setRs1(2);
        andi.setImm_i(0xFF);

        cpu.setRegister(2, 0x1234);
        cpu.executeTest(andi);
        int andiRes = cpu.getRegister(1);

        assertAll("immediateResults",
            () -> assertEquals(12, slliRes, "SLLI failed"),
            () -> assertEquals(0x34, andiRes, "ANDI failed")
        );
    }

    @Test
    void testAuipc() {
        InstructionDecoded auipc = new InstructionDecoded();
        auipc.setOpcode(0b0010111);
        auipc.setRd(1);
        auipc.setImm_u(0x10000);

        cpu.setProgramCounter(0x2004);
        cpu.executeTest(auipc);
        assertEquals(0x12000, cpu.getRegister(1), "AUIPC should compute current PC + imm_u");
    }

    @Test
    void testByteAndHalfwordLoadsAndStores() throws MemoryAccessException {
        int baseAddr = 0x4000;
        cpu.setRegister(2, baseAddr);

        // Store Byte
        InstructionDecoded sb = new InstructionDecoded();
        sb.setOpcode(0b0100011);
        sb.setRs1(2);
        sb.setRs2(3);
        sb.setFunc3(0b000);
        sb.setImm_s(0);

        cpu.setRegister(3, 0x123456AB);
        cpu.executeTest(sb);

        // Load Byte Signed
        InstructionDecoded lb = new InstructionDecoded();
        lb.setOpcode(0b0000011);
        lb.setRd(1);
        lb.setRs1(2);
        lb.setFunc3(0b000);
        lb.setImm_i(0);

        cpu.executeTest(lb);
        int lbRes = cpu.getRegister(1);

        // Load Byte Unsigned
        InstructionDecoded lbu = new InstructionDecoded();
        lbu.setOpcode(0b0000011);
        lbu.setRd(1);
        lbu.setRs1(2);
        lbu.setFunc3(0b100);
        lbu.setImm_i(0);

        cpu.executeTest(lbu);
        int lbuRes = cpu.getRegister(1);

        // Store Halfword
        InstructionDecoded sh = new InstructionDecoded();
        sh.setOpcode(0b0100011);
        sh.setRs1(2);
        sh.setRs2(3);
        sh.setFunc3(0b001);
        sh.setImm_s(2);

        cpu.setRegister(3, 0x89AB);
        cpu.executeTest(sh);

        // Load Halfword Signed
        InstructionDecoded lh = new InstructionDecoded();
        lh.setOpcode(0b0000011);
        lh.setRd(1);
        lh.setRs1(2);
        lh.setFunc3(0b001);
        lh.setImm_i(2);

        cpu.executeTest(lh);
        int lhRes = cpu.getRegister(1);

        // Load Halfword Unsigned
        InstructionDecoded lhu = new InstructionDecoded();
        lhu.setOpcode(0b0000011);
        lhu.setRd(1);
        lhu.setRs1(2);
        lhu.setFunc3(0b101);
        lhu.setImm_i(2);

        cpu.executeTest(lhu);
        int lhuRes = cpu.getRegister(1);

        assertAll("loadStoreExtensions",
            () -> assertEquals(0xFFFFFFAB, lbRes, "LB should sign-extend negative byte"),
            () -> assertEquals(0xAB, lbuRes, "LBU should zero-extend byte"),
            () -> assertEquals(0xFFFF89AB, lhRes, "LH should sign-extend halfword"),
            () -> assertEquals(0x89AB, lhuRes, "LHU should zero-extend halfword")
        );
    }

    @Test
    void testBranchInstructions() {
        InstructionDecoded bne = new InstructionDecoded();
        bne.setOpcode(0b1100011);
        bne.setFunc3(0b001);
        bne.setRs1(1);
        bne.setRs2(2);
        bne.setImm_b(12);

        cpu.setRegister(1, 10);
        cpu.setRegister(2, 20);
        cpu.setProgramCounter(0x1000);
        cpu.executeTest(bne);
        int bnePc = cpu.getProgramCounter() + 4;

        InstructionDecoded blt = new InstructionDecoded();
        blt.setOpcode(0b1100011);
        blt.setFunc3(0b100);
        blt.setRs1(1);
        blt.setRs2(2);
        blt.setImm_b(16);

        cpu.setRegister(1, -5);
        cpu.setRegister(2, 5);
        cpu.setProgramCounter(0x2000);
        cpu.executeTest(blt);
        int bltPc = cpu.getProgramCounter() + 4;

        InstructionDecoded bltu = new InstructionDecoded();
        bltu.setOpcode(0b1100011);
        bltu.setFunc3(0b110);
        bltu.setRs1(1);
        bltu.setRs2(2);
        bltu.setImm_b(20);

        cpu.setRegister(1, 5);
        cpu.setRegister(2, -5);
        cpu.setProgramCounter(0x3000);
        cpu.executeTest(bltu);
        int bltuPc = cpu.getProgramCounter() + 4;

        assertAll("branchPcs",
            () -> assertEquals(0x100C, bnePc, "BNE taken when not equal"),
            () -> assertEquals(0x2010, bltPc, "BLT taken for -5 < 5"),
            () -> assertEquals(0x3014, bltuPc, "BLTU taken for 5 < 0xFFFFFFFB")
        );
    }

    @Test
    void testJalr() {
        InstructionDecoded jalr = new InstructionDecoded();
        jalr.setOpcode(0b1100111);
        jalr.setRd(1);
        jalr.setRs1(2);
        jalr.setFunc3(0b000);
        jalr.setImm_i(8);

        cpu.setRegister(2, 0x5000);
        cpu.setProgramCounter(0x1000);
        cpu.executeTest(jalr);

        assertAll("jalrResults",
            () -> assertEquals(0x1000, cpu.getRegister(1), "JALR return address"),
            () -> assertEquals(0x5008, cpu.getProgramCounter(), "JALR target address")
        );
    }
}
