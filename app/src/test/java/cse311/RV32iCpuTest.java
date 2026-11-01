package cse311;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import cse311.Exception.MemoryAccessException;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RV32iCpuTest {
    private RV32Cpu cpu;
    private MemoryManager memory;

    @BeforeEach
    void setUp() {
        SimpleMemory simpleMemory = new SimpleMemory(128 * 1024 * 1024);
        memory = new MemoryManager(simpleMemory);
        cpu = new RV32Cpu(memory);
    }

    @Test
    void testAdd() {
        InstructionDecoded inst = new InstructionDecoded();
        inst.setOpcode(0b0110011);
        inst.setRd(1);
        inst.setFunc3(0b000);
        inst.setRs1(2);
        inst.setRs2(3);
        inst.setFunc7(0b0000000);

        cpu.setRegister(2, 5);
        cpu.setRegister(3, 3);
        cpu.executeTest(inst);

        assertEquals(8, cpu.getRegister(1), "ADD failed");
    }

    @Test
    void testSub() {
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

    @Test
    void testAddi() {
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

    @Test
    void testLw() throws MemoryAccessException {
        InstructionDecoded inst = new InstructionDecoded();
        inst.setOpcode(0b0000011);
        inst.setRd(1);
        inst.setFunc3(0b010);
        inst.setRs1(2);
        inst.setImm_i(0);

        int testAddr = 0x1000;
        cpu.setRegister(2, testAddr);

        memory.writeByteToVirtualAddress(testAddr, (byte) 42);
        memory.writeByteToVirtualAddress(testAddr + 1, (byte) 0);
        memory.writeByteToVirtualAddress(testAddr + 2, (byte) 0);
        memory.writeByteToVirtualAddress(testAddr + 3, (byte) 0);

        cpu.executeTest(inst);

        assertEquals(42, cpu.getRegister(1), "LW failed");
    }

    @Test
    void testSw() throws MemoryAccessException {
        InstructionDecoded inst = new InstructionDecoded();
        inst.setOpcode(0b0100011);
        inst.setRs2(1);
        inst.setFunc3(0b010);
        inst.setRs1(2);
        inst.setImm_s(0);

        int testAddr = 0x2000;
        cpu.setRegister(1, 42);
        cpu.setRegister(2, testAddr);

        cpu.executeTest(inst);

        int storedValue = memory.readWord(testAddr);
        assertEquals(42, storedValue, "SW failed");
    }

    @Test
    void testBeq() {
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

    @Test
    void testJal() {
        InstructionDecoded inst = new InstructionDecoded();
        inst.setOpcode(0b1101111);
        inst.setRd(1);
        inst.setImm_j(16);

        int initialPc = cpu.getProgramCounter();
        cpu.executeTest(inst);

        assertAll("jalState",
            () -> assertEquals(initialPc, cpu.getRegister(1), "JAL return address failed"),
            () -> assertEquals(initialPc + 16, cpu.getProgramCounter() + 4, "JAL target address failed")
        );
    }

    @Test
    void testLui() {
        InstructionDecoded inst = new InstructionDecoded();
        inst.setOpcode(0b0110111);
        inst.setRd(1);
        inst.setImm_u(0x12345000);

        cpu.executeTest(inst);

        assertEquals(0x12345000, cpu.getRegister(1), "LUI failed");
    }

    @Test
    void testCpuTracingRecord() throws Exception {
        cpu.setId(3);
        cpu.setTracingEnabled(true);
        int pc = cpu.getProgramCounter();
        // ADDI x1, x0, 42 -> 0x02a00093
        memory.writeWord(pc, 0x02a00093);

        cpu.runBatch(1);

        var snapshot = cpu.getTraceBuffer().getSnapshot(5);
        var ev = !snapshot.isEmpty() ? snapshot.get(snapshot.size() - 1) : null;

        assertAll("cpuTracingRecord",
            () -> org.junit.jupiter.api.Assertions.assertFalse(snapshot.isEmpty(), "Trace snapshot should not be empty"),
            () -> assertEquals(pc, ev != null ? ev.pc : 0, "PC should match executed instruction"),
            () -> assertEquals(3, ev != null ? ev.hartId : 0, "HartId should match CPU ID"),
            () -> assertEquals(0x02a00093, ev != null ? ev.instruction : 0, "Instruction should match ADDI"),
            () -> assertEquals(1, ev != null ? ev.rd : 0, "Destination register should be x1"),
            () -> assertEquals(42, ev != null ? ev.aluResult : 0, "Result value should be 42")
        );
    }
}