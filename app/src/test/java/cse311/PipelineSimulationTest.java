package cse311;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineSimulationTest {

    private MemoryManager memory;
    private RV32Cpu cpu;

    @BeforeEach
    void setUp() {
        SimpleMemory simpleMemory = new SimpleMemory(128 * 1024 * 1024);
        memory = new MemoryManager(simpleMemory);
        cpu = new RV32Cpu(memory);
    }

    @Test
    void testDataHazardWithForwarding() throws Exception {
        cpu.setExecutionMode(RV32Cpu.ExecutionMode.FIVE_STAGE_PIPELINE);
        cpu.setForwardingEnabled(true);

        int[] regs = new int[32];
        regs[2] = 5;
        regs[3] = 10;
        regs[5] = 20;
        cpu.setRegisters(regs);

        memory.writeWord(0x00, 0x003100B3); // add x1, x2, x3
        memory.writeWord(0x04, 0x00508233); // add x4, x1, x5
        memory.writeWord(0x08, 0x00000013); // nop
        memory.writeWord(0x0C, 0x00000013); // nop
        memory.writeWord(0x10, 0x00000013); // nop

        cpu.setProgramCounter(0x00);

        for (int i = 0; i < 8; i++) {
            cpu.clockTick();
        }

        int[] finalRegs = cpu.getRegisters();

        assertAll("forwardingResults",
            () -> assertEquals(15, finalRegs[1], "x1 should be 15"),
            () -> assertEquals(35, finalRegs[4], "x4 should be 35 (computed via data forwarding)")
        );
    }

    @Test
    void testLoadUseStall() throws Exception {
        cpu.setExecutionMode(RV32Cpu.ExecutionMode.FIVE_STAGE_PIPELINE);
        cpu.setForwardingEnabled(true);

        memory.writeWord(0x100, 42);

        int[] regs = new int[32];
        regs[2] = 0x100;
        regs[4] = 8;
        cpu.setRegisters(regs);

        memory.writeWord(0x00, 0x00012083); // lw x1, 0(x2)
        memory.writeWord(0x04, 0x004081B3); // add x3, x1, x4
        memory.writeWord(0x08, 0x00000013); // nop
        memory.writeWord(0x0C, 0x00000013); // nop
        memory.writeWord(0x10, 0x00000013); // nop

        cpu.setProgramCounter(0x00);

        for (int i = 0; i < 9; i++) {
            cpu.clockTick();
        }

        int[] finalRegs = cpu.getRegisters();

        assertAll("loadUseStallResults",
            () -> assertEquals(42, finalRegs[1], "x1 should be 42"),
            () -> assertEquals(50, finalRegs[3], "x3 should be 50 after load-use stall"),
            () -> assertTrue(cpu.getPipelineStalls() >= 1, "Should have registered at least 1 load-use stall")
        );
    }

    @Test
    void testSingleCycleAndPipelineEquivalence() throws Exception {
        memory.writeWord(0x00, 0x00A00093); // addi x1, x0, 10
        memory.writeWord(0x04, 0x01400113); // addi x2, x0, 20
        memory.writeWord(0x08, 0x002081B3); // add x3, x1, x2
        memory.writeWord(0x0C, 0x00000013); // nop
        memory.writeWord(0x10, 0x00000013); // nop
        memory.writeWord(0x14, 0x00000013); // nop

        RV32Cpu cpuSingle = new RV32Cpu(memory);
        cpuSingle.setExecutionMode(RV32Cpu.ExecutionMode.SINGLE_CYCLE);
        cpuSingle.setProgramCounter(0x00);
        cpuSingle.step();
        cpuSingle.step();
        cpuSingle.step();
        int[] singleRegs = cpuSingle.getRegisters();

        RV32Cpu cpuPipe = new RV32Cpu(memory);
        cpuPipe.setExecutionMode(RV32Cpu.ExecutionMode.FIVE_STAGE_PIPELINE);
        cpuPipe.setProgramCounter(0x00);
        for (int i = 0; i < 8; i++) {
            cpuPipe.clockTick();
        }
        int[] pipeRegs = cpuPipe.getRegisters();

        assertAll("equivalenceResults",
            () -> assertEquals(10, singleRegs[1], "single-cycle x1 should be 10"),
            () -> assertEquals(20, singleRegs[2], "single-cycle x2 should be 20"),
            () -> assertEquals(30, singleRegs[3], "single-cycle x3 should be 30"),
            () -> assertEquals(singleRegs[1], pipeRegs[1], "x1 should match between single-cycle and pipeline"),
            () -> assertEquals(singleRegs[2], pipeRegs[2], "x2 should match between single-cycle and pipeline"),
            () -> assertEquals(singleRegs[3], pipeRegs[3], "x3 should match between single-cycle and pipeline")
        );
    }
}
