package cse311;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cse311.Exception.MemoryAccessException;

import static org.junit.jupiter.api.Assertions.*;

public class RV32iCpuTest {
    private RV32iCpu cpu;
    private MemoryManager memory;

    @BeforeEach
    void setUp() {
        SimpleMemory simpleMemory = new SimpleMemory(128 * 1024 * 1024);
        memory = new MemoryManager(simpleMemory);
        cpu = new RV32iCpu(memory);
    }

    // R-type instruction tests
    @Test
    void testAdd() {
        // ADD x1, x2, x3
        InstructionDecoded inst = new InstructionDecoded();
        inst.setOpcode(0b0110011);
        inst.setRd(1);
        inst.setFunc3(0b000);
        inst.setRs1(2);
        inst.setRs2(3);
        inst.setFunc7(0b0000000);

        // Set initial register values
        cpu.setRegister(2, 5);
        cpu.setRegister(3, 3);

        cpu.executeTest(inst);
        assertEquals(8, cpu.getRegister(1), "ADD failed");
    }

    @Test
    void testSub() {
        // SUB x1, x2, x3
        InstructionDecoded inst = new InstructionDecoded();
        inst.setOpcode(0b0110011);
        inst.setRd(1);
        inst.setFunc3(0b000);
        inst.setRs1(2);
        inst.setRs2(3);
        inst.setFunc7(0b0100000);

        cpu.setRegister(2, 10);
        cpu.setRegister(3, 3);

        cpu.executeTest(inst);
        assertEquals(7, cpu.getRegister(1), "SUB failed");
    }

    // I-type instruction tests
    @Test
    void testAddi() {
        // ADDI x1, x2, 5
        InstructionDecoded inst = new InstructionDecoded();
        inst.setOpcode(0b0010011);
        inst.setRd(1);
        inst.setFunc3(0b000);
        inst.setRs1(2);
        inst.setImm_i(5);

        cpu.setRegister(2, 10);

        cpu.executeTest(inst);
        assertEquals(15, cpu.getRegister(1), "ADDI failed");
    }

    // Load instruction tests
    @Test
    void testLw() throws MemoryAccessException {
        // LW x1, 0(x2)
        InstructionDecoded inst = new InstructionDecoded();
        inst.setOpcode(0b0000011);
        inst.setRd(1);
        inst.setFunc3(0b010);
        inst.setRs1(2);
        inst.setImm_i(0);

        // Use a simple address that works with our dynamic memory system
        int testAddr = 0x1000;

        // Set base address in register
        cpu.setRegister(2, testAddr);

        // Write test value directly to the address using the memory manager
        memory.writeByteToVirtualAddress(testAddr, (byte) 42);
        memory.writeByteToVirtualAddress(testAddr + 1, (byte) 0);
        memory.writeByteToVirtualAddress(testAddr + 2, (byte) 0);
        memory.writeByteToVirtualAddress(testAddr + 3, (byte) 0);

        cpu.executeTest(inst);
        assertEquals(42, cpu.getRegister(1), "LW failed");
    }

    // Store instruction tests
    @Test
    void testSw() throws MemoryAccessException {
        // SW x1, 0(x2)
        InstructionDecoded inst = new InstructionDecoded();
        inst.setOpcode(0b0100011);
        inst.setRs2(1);
        inst.setFunc3(0b010);
        inst.setRs1(2);
        inst.setImm_s(0);

        // Use a simple address that works with our dynamic memory system
        int testAddr = 0x2000;

        // Set registers
        cpu.setRegister(1, 42); // Value to store
        cpu.setRegister(2, testAddr); // Base address

        cpu.executeTest(inst);

        // Verify stored value at the address
        int storedValue = memory.readWord(testAddr);
        assertEquals(42, storedValue, "SW failed");
    }

    // Branch instruction tests
    @Test
    void testBeq() {
        // BEQ x1, x2, 8
        InstructionDecoded inst = new InstructionDecoded();
        inst.setOpcode(0b1100011);
        inst.setFunc3(0b000);
        inst.setRs1(1);
        inst.setRs2(2);
        inst.setImm_b(8);

        cpu.setRegister(1, 5);
        cpu.setRegister(2, 5);
        int initialPc = cpu.getProgramCounter();

        cpu.executeTest(inst);
        assertEquals(initialPc + 8, cpu.getProgramCounter() + 4, "BEQ failed");
    }

    // Jump instruction tests
    @Test
    void testJal() {
        // JAL x1, 16
        InstructionDecoded inst = new InstructionDecoded();
        inst.setOpcode(0b1101111);
        inst.setRd(1);
        inst.setImm_j(16);

        int initialPc = cpu.getProgramCounter();
        cpu.executeTest(inst);

        assertEquals(initialPc, cpu.getRegister(1), "JAL return address failed");
        assertEquals(initialPc + 16, cpu.getProgramCounter() + 4, "JAL target address failed");
    }

    // Upper immediate instruction tests
    @Test
    void testLui() {
        // LUI x1, 0x12345
        InstructionDecoded inst = new InstructionDecoded();
        inst.setOpcode(0b0110111);
        inst.setRd(1);
        inst.setImm_u(0x12345000);

        cpu.executeTest(inst);
        assertEquals(0x12345000, cpu.getRegister(1), "LUI failed");
    }
}