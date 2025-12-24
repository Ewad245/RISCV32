package cse311;

import cse311.kernel.Kernel;
import cse311.kernel.contiguous.BestFitStrategy;
import cse311.kernel.contiguous.ContiguousMemoryManager;
import cse311.kernel.process.Task;
import cse311.kernel.syscall.SystemCallHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SystemCallIntegrationTest {

    private Kernel kernel;
    private RV32Cpu cpu;
    private MemoryManager memory;

    @BeforeEach
    void setUp() {
        ContiguousMemoryManager cmm = new ContiguousMemoryManager(
                8 * 1024 * 1024,
                new BestFitStrategy());
        this.memory = cmm;
        this.cpu = new RV32Cpu(memory);
        this.kernel = new Kernel(cpu, memory);
    }

    private byte[] getMinimalElf() {
        return new byte[] {
                // ELF Header
                0x7F, 0x45, 0x4C, 0x46, 0x01, 0x01, 0x01, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x02, 0x00, (byte) 0xF3, 0x00, 0x01, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x01, 0x00, 0x34, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x34, 0x00, 0x20, 0x00, 0x01, 0x00, 0x28, 0x00,
                0x00, 0x00, 0x00, 0x00,
                // Program Header
                0x01, 0x00, 0x00, 0x00, 0x54, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00,
                0x04, 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00,
                0x05, 0x00, 0x00, 0x00, 0x00, 0x10, 0x00, 0x00,
                // Code
                0x13, 0x00, 0x00, 0x00
        };
    }

    @Test
    void testEcallDetection() {
        cpu.resetFlags();
        cpu.testExecuteInstruction(0x00000073); // ECALL
        assertTrue(cpu.isEcall());
    }

    @Test
    void testExceptionDetection() {
        cpu.resetFlags();

        // Execute a known illegal instruction to trigger handleException.
        // We use an R-type instruction (opcode 0x33) with an invalid func7.
        // Opcode 0x33 = 0110011
        // Func3 = 000 (ADD)
        // Func7 = 1111111 (Invalid, usually 0x00 or 0x20)
        // Instruction = 0xFE000033
        cpu.testExecuteInstruction(0xFE000033);

        // Check if exception was detected
        assertTrue(cpu.isException(), "CPU should flag an exception for illegal instructions");
    }

    @Test
    void testSystemCallIntegration() throws Exception {
        Task task = kernel.createTask(getMinimalElf(), "syscall_test");

        int[] regs = task.getRegisters();
        regs[17] = SystemCallHandler.SYS_GETPID; // a7

        task.restoreState(cpu);

        cpu.resetFlags();
        cpu.testExecuteInstruction(0x00000073); // ECALL

        if (cpu.isEcall()) {
            kernel.getSystemCallHandler().handleSystemCall(task);
        }

        assertEquals(task.getId(), task.getRegisters()[10]);
    }

    @Test
    void testKernelSystemCallHandling() throws Exception {
        Task task = kernel.createTask(getMinimalElf(), "kernel_loop_test");

        // 1. Set up the system call arguments in the Task
        task.getRegisters()[17] = SystemCallHandler.SYS_GETPID;

        // 2. CRITICAL FIX: Sync Task state to CPU
        // The handler reads registers from the CPU, not the Task object directly.
        task.restoreState(cpu);

        // 3. Manually trigger handler
        kernel.getSystemCallHandler().handleSystemCall(task);

        // 4. Verify result (PID) was written back to register a0 (index 10)
        assertEquals(task.getId(), task.getRegisters()[10]);
    }
}