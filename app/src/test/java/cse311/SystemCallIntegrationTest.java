package cse311;

import cse311.kernel.Kernel;
import cse311.kernel.contiguous.BestFitStrategy;
import cse311.kernel.contiguous.ContiguousMemoryManager;
import cse311.kernel.process.Task;
import cse311.kernel.process.TaskState;
import cse311.kernel.syscall.SystemCallHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemCallIntegrationTest {

    private Kernel kernel;
    private RV32Cpu cpu;

    @BeforeEach
    void setUp() {
        ContiguousMemoryManager cmm = new ContiguousMemoryManager(
                8 * 1024 * 1024,
                new BestFitStrategy());
        this.kernel = new Kernel(cmm);
        this.cpu = kernel.getCpu(0);
    }

    private byte[] getMinimalElf() {
        return new byte[] {
                0x7F, 0x45, 0x4C, 0x46, 0x01, 0x01, 0x01, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x02, 0x00, (byte) 0xF3, 0x00, 0x01, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x01, 0x00, 0x34, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x34, 0x00, 0x20, 0x00, 0x01, 0x00, 0x28, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x01, 0x00, 0x00, 0x00, 0x54, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00,
                0x04, 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00,
                0x05, 0x00, 0x00, 0x00, 0x00, 0x10, 0x00, 0x00,
                0x13, 0x00, 0x00, 0x00
        };
    }

    @Test
    void testEcallDetection() {
        cpu.resetFlags();
        cpu.testExecuteInstruction(0x00000073); // ECALL
        assertTrue(cpu.isEcall(), "CPU should flag ecall on 0x00000073");
    }

    @Test
    void testExceptionDetection() {
        cpu.resetFlags();
        cpu.testExecuteInstruction(0xFE000033);

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
            kernel.getSystemCallHandler().handleSystemCall(task, cpu);
        }

        assertEquals(task.getId(), task.getRegisters()[10], "SYS_GETPID result in a0 should match task ID");
    }

    @Test
    void testKernelSystemCallHandling() throws Exception {
        Task task = kernel.createTask(getMinimalElf(), "kernel_loop_test");

        task.getRegisters()[17] = SystemCallHandler.SYS_GETPID;
        task.restoreState(cpu);

        kernel.getSystemCallHandler().handleSystemCall(task, cpu);

        assertEquals(task.getId(), task.getRegisters()[10], "Handler should write PID back to a0");
    }

    @Test
    void testSysKill() throws Exception {
        Task victim = kernel.createTask(getMinimalElf(), "victim");
        victim.setState(TaskState.READY);

        Task killer = kernel.createTask(getMinimalElf(), "killer");
        killer.getRegisters()[17] = SystemCallHandler.SYS_KILL;
        killer.getRegisters()[10] = victim.getId();
        killer.restoreState(cpu);

        kernel.getSystemCallHandler().handleSystemCall(killer, cpu);

        assertAll("sysKillResults",
            () -> assertTrue(victim.isKilled(), "Victim task should be marked as killed"),
            () -> assertEquals(0, killer.getRegisters()[10], "SYS_KILL should return 0 on success")
        );
    }
}