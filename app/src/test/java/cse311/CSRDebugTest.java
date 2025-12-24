package cse311;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CSRDebugTest {
    private RV32Cpu cpu;
    private MemoryManager memory;

    @BeforeEach
    public void setUp() {
        memory = new MemoryManager();
        cpu = new RV32Cpu(memory);
    }

    @Test
    public void debugCSRSI() {
        System.out.println("=== Debug CSRRSI Issue ===");

        // Set MSTATUS to a known value
        cpu.writeCSRTest(RV32Cpu.MSTATUS, 10);
        System.out.println("Initial MSTATUS: " + cpu.readCSRTest(RV32Cpu.MSTATUS));

        // Test CSRRSI x2, mstatus, 8 manually
        InstructionDecoded inst = new InstructionDecoded();
        inst.setOpcode(0b1110011); // SYSTEM
        inst.setRd(2); // x2
        inst.setFunc3(0b110); // CSRRSI
        inst.setRs1(8); // immediate value 8 (in rs1 field for immediate instructions)
        inst.setImm_i(0x300); // MSTATUS address

        System.out.println("Before CSRRSI:");
        System.out.println("  MSTATUS: " + cpu.readCSRTest(RV32Cpu.MSTATUS));
        System.out.println("  x2: " + cpu.getRegister(2));

        cpu.executeTest(inst);

        System.out.println("After CSRRSI:");
        System.out.println("  MSTATUS: " + cpu.readCSRTest(RV32Cpu.MSTATUS) + " (should be " + (10 | 8) + ")");
        System.out.println("  x2: " + cpu.getRegister(2) + " (should be 10)");

        assertEquals(10, cpu.getRegister(2), "x2 should contain old MSTATUS value");
        assertEquals(10, cpu.readCSRTest(RV32Cpu.MSTATUS), "MSTATUS should be 10 | 8 = 10");
    }
}