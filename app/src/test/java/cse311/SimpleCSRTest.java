package cse311;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SimpleCSRTest {
    private RV32Cpu cpu;
    private MemoryManager memory;

    @BeforeEach
    public void setUp() {
        memory = new MemoryManager();
        cpu = new RV32Cpu(memory);
    }

    @Test
    public void testBasicCSRAccess() {
        // Test direct CSR access first
        System.out.println("Initial MSTATUS: 0x" + Integer.toHexString(cpu.readCSRTest(RV32Cpu.MSTATUS)));

        // Write a value directly
        cpu.writeCSRTest(RV32Cpu.MSTATUS, 0x1234);
        System.out.println("After direct write MSTATUS: 0x" + Integer.toHexString(cpu.readCSRTest(RV32Cpu.MSTATUS)));

        assertEquals(0x1234, cpu.readCSRTest(RV32Cpu.MSTATUS), "Direct CSR write/read failed");
    }

    @Test
    public void testCSRRWInstruction() {
        // Test CSRRW instruction manually
        // CSRRW x1, mstatus, x2
        // Set x2 to a known value
        cpu.setRegister(2, 0x5678);

        // Create instruction manually
        InstructionDecoded inst = new InstructionDecoded();
        inst.setOpcode(0b1110011); // SYSTEM
        inst.setRd(1); // x1
        inst.setFunc3(0b001); // CSRRW
        inst.setRs1(2); // x2
        inst.setImm_i(0x300); // MSTATUS address

        System.out.println("Before CSRRW:");
        System.out.println("  MSTATUS: 0x" + Integer.toHexString(cpu.readCSRTest(RV32Cpu.MSTATUS)));
        System.out.println("  x1: 0x" + Integer.toHexString(cpu.getRegister(1)));
        System.out.println("  x2: 0x" + Integer.toHexString(cpu.getRegister(2)));

        cpu.executeTest(inst);

        System.out.println("After CSRRW:");
        System.out.println("  MSTATUS: 0x" + Integer.toHexString(cpu.readCSRTest(RV32Cpu.MSTATUS)));
        System.out.println("  x1: 0x" + Integer.toHexString(cpu.getRegister(1)));
        System.out.println("  x2: 0x" + Integer.toHexString(cpu.getRegister(2)));

        // x1 should contain the old value of MSTATUS (0x1800)
        assertEquals(0x1800, cpu.getRegister(1), "CSRRW should read old MSTATUS value into x1");
        // MSTATUS should now contain the value from x2 (0x5678)
        assertEquals(0x5678, cpu.readCSRTest(RV32Cpu.MSTATUS), "CSRRW should write x2 value to MSTATUS");
    }
}