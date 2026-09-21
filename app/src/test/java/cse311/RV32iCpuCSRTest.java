package cse311;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RV32iCpuCSRTest {
    private RV32Cpu cpu;

    @BeforeEach
    void setUp() {
        MemoryManager memory = new MemoryManager();
        cpu = new RV32Cpu(memory);
    }

    @Test
    void testCSRReadWrite() {
        // CSRRW x1, mstatus, x0
        int csrrwInstruction = 0b00110000000000000001000011110011;
        cpu.testExecuteInstruction(csrrwInstruction);

        // CSRRW x2, mstatus, x3
        cpu.setRegister(3, 0x1880);
        int csrrwInstruction2 = 0b00110000000000011001000101110011;
        cpu.testExecuteInstruction(csrrwInstruction2);
        int reg2 = cpu.getRegister(2);

        // CSRRS x4, mstatus, x5
        cpu.setRegister(5, 0x8);
        int csrrsInstruction = 0b00110000000000101010001001110011;
        cpu.testExecuteInstruction(csrrsInstruction);
        int reg4 = cpu.getRegister(4);

        // CSRRC x6, mstatus, x7
        cpu.setRegister(7, 0x8);
        int csrrcInstruction = 0b00110000000000111011001101110011;
        cpu.testExecuteInstruction(csrrcInstruction);
        int reg6 = cpu.getRegister(6);

        assertAll("csrReadWriteResults",
            () -> assertEquals(0, reg2, "x2 should contain previous value of mstatus"),
            () -> assertEquals(0x1880, reg4, "x4 should contain previous value of mstatus"),
            () -> assertEquals(0x1888, reg6, "x6 should contain previous value of mstatus")
        );
    }

    @Test
    void testCSRImmediate() {
        // Step 1: CSRRWI x1, mstatus, 10
        int csrrwiInstruction = 0b00110000000001010101000011110011;
        cpu.testExecuteInstruction(csrrwiInstruction);

        // Step 2: CSRRSI x2, mstatus, 8
        int csrrsiInstruction = 0b00110000000001000110000101110011;
        cpu.testExecuteInstruction(csrrsiInstruction);
        int reg2 = cpu.getRegister(2);

        // Step 3: CSRRCI x3, mstatus, 10
        int csrrciInstruction = 0b00110000000001010111000111110011;
        cpu.testExecuteInstruction(csrrciInstruction);
        int reg3 = cpu.getRegister(3);

        assertAll("csrImmediateResults",
            () -> assertEquals(10, reg2, "x2 should contain the previous value of mstatus"),
            () -> assertEquals(10, reg3, "x3 should contain the previous value of mstatus before CSRRCI")
        );
    }

    @Test
    void testPrivilegeModeSwitch() {
        cpu.writeCSRTest(RV32Cpu.MSTATUS, 0x0);
        cpu.writeCSRTest(RV32Cpu.MEPC, 0x1000);

        int mretInstruction = 0b00110000001000000000000001110011;
        cpu.testExecuteInstruction(mretInstruction);

        assertAll("privilegeModeResults",
            () -> assertEquals(RV32Cpu.PRIVILEGE_USER, cpu.getPrivilegeMode(), "CPU should be in U-mode after MRET"),
            () -> assertEquals(0x1000, cpu.getProgramCounter(), "PC should be set to the value in mepc")
        );
    }

    @Test
    void testExceptionHandling() {
        cpu.writeCSRTest(RV32Cpu.MTVEC, 0x2000);
        cpu.setPc(0x1000);

        cpu.handleException(2, 0x1000);

        assertAll("exceptionHandlingResults",
            () -> assertEquals(RV32Cpu.PRIVILEGE_MACHINE, cpu.getPrivilegeMode(), "CPU should be in M-mode after exception"),
            () -> assertEquals(0x2000, cpu.getProgramCounter(), "PC should be set to the value in mtvec"),
            () -> assertEquals(0x1000, cpu.readCSRTest(RV32Cpu.MEPC), "mepc should contain faulting PC"),
            () -> assertEquals(2, cpu.readCSRTest(RV32Cpu.MCAUSE), "mcause should contain exception cause")
        );
    }
}