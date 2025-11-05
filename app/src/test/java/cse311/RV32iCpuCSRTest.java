package cse311;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RV32iCpuCSRTest {
    private RV32iCpu cpu;
    private MemoryManager memory;
    
    @BeforeEach
    public void setUp() {
        memory = new MemoryManager();
        cpu = new RV32iCpu(memory);
    }
    
    @Test
    public void testCSRReadWrite() throws Exception {
        // Test CSRRW instruction (CSR Read and Write)
        // CSRRW x1, mstatus, x0 (Read mstatus into x1, write 0 to mstatus)
        int csrrwInstruction = 0b00110000000000000001000011110011;
        cpu.testExecuteInstruction(csrrwInstruction);
        
        // The value of mstatus should now be 0
        // CSRRW x2, mstatus, x3 (Read mstatus into x2, write x3 to mstatus)
        // First set x3 to a known value
        cpu.setRegister(3, 0x1880); // Set MPP to M-mode (11) and MPIE to 1
        int csrrwInstruction2 = 0b00110000000000011001000101110011;
        cpu.testExecuteInstruction(csrrwInstruction2);
        
        // x2 should now contain 0 (the previous value of mstatus)
        assertEquals(0, cpu.getRegister(2), "x2 should contain the previous value of mstatus");
        
        // Test CSRRS instruction (CSR Read and Set)
        // CSRRS x4, mstatus, x5 (Read mstatus into x4, set bits from x5 in mstatus)
        // First set x5 to a known value
        cpu.setRegister(5, 0x8); // Set MIE bit
        // Correct instruction: CSRRS x4, mstatus(0x300), x5
        // 0x300 = 001100000000, rs1=5, func3=010, rd=4, opcode=1110011
        int csrrsInstruction = 0b00110000000000101010001001110011;
        cpu.testExecuteInstruction(csrrsInstruction);
        
        // x4 should now contain 0x1880 (the previous value of mstatus)
        System.out.println("x4 register value: 0x" + Integer.toHexString(cpu.getRegister(4)));
        System.out.println("Expected: 0x1880");
        assertEquals(0x1880, cpu.getRegister(4), "x4 should contain the previous value of mstatus");
        
        // Test CSRRC instruction (CSR Read and Clear)
        // CSRRC x6, mstatus, x7 (Read mstatus into x6, clear bits from x7 in mstatus)
        // First set x7 to a known value
        cpu.setRegister(7, 0x8); // Clear MIE bit
        int csrrcInstruction = 0b00110000000000111011001101110011;
        cpu.testExecuteInstruction(csrrcInstruction);
        
        // x6 should now contain 0x1888 (the previous value of mstatus)
        assertEquals(0x1888, cpu.getRegister(6), "x6 should contain the previous value of mstatus");
    }
    
    @Test
    public void testCSRImmediate() throws Exception {
        System.out.println("=== CSR Immediate Test Debug ===");
        
        // Test CSRRWI instruction (CSR Read and Write Immediate)
        // CSRRWI x1, mstatus, 10 (Read mstatus into x1, write immediate value 10 to mstatus)
        System.out.println("Step 1: CSRRWI x1, mstatus, 10");
        System.out.println("  Before: MSTATUS = 0x" + Integer.toHexString(cpu.readCSRTest(RV32iCpu.MSTATUS)));
        int csrrwiInstruction = 0b00110000000001010101000011110011;
        cpu.testExecuteInstruction(csrrwiInstruction);
        System.out.println("  After: MSTATUS = 0x" + Integer.toHexString(cpu.readCSRTest(RV32iCpu.MSTATUS)));
        System.out.println("  x1 = " + cpu.getRegister(1) + " (should be 0x1800 = " + 0x1800 + ")");
        
        // Test CSRRSI instruction (CSR Read and Set Immediate)
        // CSRRSI x2, mstatus, 8 (Read mstatus into x2, set bits from immediate value 8 in mstatus)
        System.out.println("Step 2: CSRRSI x2, mstatus, 8");
        System.out.println("  Before: MSTATUS = 0x" + Integer.toHexString(cpu.readCSRTest(RV32iCpu.MSTATUS)));
        int csrrsiInstruction = 0b00110000000001000110000101110011;
        cpu.testExecuteInstruction(csrrsiInstruction);
        System.out.println("  After: MSTATUS = 0x" + Integer.toHexString(cpu.readCSRTest(RV32iCpu.MSTATUS)));
        System.out.println("  x2 = " + cpu.getRegister(2) + " (should be 10)");
        
        // x2 should now contain 10 (the previous value of mstatus)
        assertEquals(10, cpu.getRegister(2), "x2 should contain the previous value of mstatus");
        
        // Test CSRRCI instruction (CSR Read and Clear Immediate)
        // CSRRCI x3, mstatus, 10 (Read mstatus into x3, clear bits from immediate value 10 in mstatus)
        System.out.println("Step 3: CSRRCI x3, mstatus, 10");
        System.out.println("  Before: MSTATUS = 0x" + Integer.toHexString(cpu.readCSRTest(RV32iCpu.MSTATUS)));
        int csrrciInstruction = 0b00110000000001010111000111110011;
        cpu.testExecuteInstruction(csrrciInstruction);
        System.out.println("  After: MSTATUS = 0x" + Integer.toHexString(cpu.readCSRTest(RV32iCpu.MSTATUS)));
        System.out.println("  x3 = " + cpu.getRegister(3) + " (should be 18)");
        
        // x3 should now contain 10 (the previous value of mstatus before CSRRCI)
        // Note: After CSRRSI, MSTATUS was 10|8=10, so x3 should contain 10
        assertEquals(10, cpu.getRegister(3), "x3 should contain the previous value of mstatus");
    }
    
    @Test
    public void testPrivilegeModeSwitch() throws Exception {
        // Set up mstatus with MPP = 0 (U-mode)
        cpu.writeCSRTest(RV32iCpu.MSTATUS, 0x0);
        // Set up mepc with a return address
        cpu.writeCSRTest(RV32iCpu.MEPC, 0x1000);
        
        // Execute MRET instruction
        int mretInstruction = 0b00110000001000000000000001110011;
        cpu.testExecuteInstruction(mretInstruction);
        
        // Check that we're now in U-mode
        assertEquals(RV32iCpu.PRIVILEGE_USER, cpu.getPrivilegeMode(), "CPU should be in U-mode after MRET");
        // Check that PC is now the value from mepc
        assertEquals(0x1000, cpu.getProgramCounter(), "PC should be set to the value in mepc");
    }
    
    @Test
    public void testExceptionHandling() throws Exception {
        // Set up mtvec to point to the trap handler
        cpu.writeCSRTest(RV32iCpu.MTVEC, 0x2000);
        
        // Set the PC to a known value
        cpu.setPc(0x1000);
        
        // Trigger an illegal instruction exception
        cpu.handleException(2, 0x1000);
        
        // Check that we're now in M-mode
        assertEquals(RV32iCpu.PRIVILEGE_MACHINE, cpu.getPrivilegeMode(), "CPU should be in M-mode after exception");
        
        // Check that PC is now the value from mtvec
        assertEquals(0x2000, cpu.getProgramCounter(), "PC should be set to the value in mtvec");
        
        // Check that mepc contains the address of the faulting instruction
        assertEquals(0x1000, cpu.readCSRTest(RV32iCpu.MEPC), "mepc should contain the address of the faulting instruction");
        
        // Check that mcause contains the exception cause
        assertEquals(2, cpu.readCSRTest(RV32iCpu.MCAUSE), "mcause should contain the exception cause");
    }
}