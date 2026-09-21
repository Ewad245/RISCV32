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

class ComprehensiveSystemCallTest {

    private Kernel kernel;
    private RV32Cpu cpu;
    private ContiguousMemoryManager memory;

    @BeforeEach
    void setUp() {
        memory = new ContiguousMemoryManager(8 * 1024 * 1024, new BestFitStrategy());
        kernel = new Kernel(memory);
        cpu = kernel.getCpu(0);
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
            // Code (nop)
            0x13, 0x00, 0x00, 0x00
        };
    }

    private void invokeSyscall(Task task, int syscallNum, int arg0, int arg1, int arg2) {
        task.getRegisters()[17] = syscallNum;
        task.getRegisters()[10] = arg0;
        task.getRegisters()[11] = arg1;
        task.getRegisters()[12] = arg2;
        task.restoreState(cpu);

        kernel.getSystemCallHandler().handleSystemCall(task, cpu);
    }

    @Test
    void testSysGetPid() throws Exception {
        Task task = kernel.createTask(getMinimalElf(), "getpid_task");
        invokeSyscall(task, SystemCallHandler.SYS_GETPID, 0, 0, 0);

        assertEquals(task.getId(), task.getRegisters()[10], "SYS_GETPID should return task ID in a0");
    }

    @Test
    void testSysExit() throws Exception {
        Task task = kernel.createTask(getMinimalElf(), "exit_task");
        task.setState(TaskState.RUNNING);

        invokeSyscall(task, SystemCallHandler.SYS_EXIT, 42, 0, 0);

        assertAll("exitStatus",
            () -> assertEquals(TaskState.TERMINATED, task.getState(), "SYS_EXIT should set task state to TERMINATED"),
            () -> assertEquals(42, task.getExitCode(), "SYS_EXIT should record exit code")
        );
    }

    @Test
    void testSysYield() throws Exception {
        Task task = kernel.createTask(getMinimalElf(), "yield_task");
        task.setState(TaskState.RUNNING);

        invokeSyscall(task, SystemCallHandler.SYS_YIELD, 0, 0, 0);

        assertAll("yieldStatus",
            () -> assertEquals(TaskState.READY, task.getState(), "SYS_YIELD should transition task from RUNNING to READY"),
            () -> assertEquals(0, task.getRegisters()[10], "SYS_YIELD should return 0 on success")
        );
    }

    @Test
    void testSysBrk() throws Exception {
        Task task = kernel.createTask(getMinimalElf(), "brk_task");
        int initialBrk = task.getProgramBreak();

        invokeSyscall(task, SystemCallHandler.SYS_BRK, 0, 0, 0);
        int queriedBrk = task.getRegisters()[10];

        int newBrk = initialBrk + 4096;
        invokeSyscall(task, SystemCallHandler.SYS_BRK, newBrk, 0, 0);
        int setBrk = task.getRegisters()[10];
        int actualBrk = task.getProgramBreak();

        assertAll("brkStatus",
            () -> assertEquals(initialBrk, queriedBrk, "brk(0) should return current program break"),
            () -> assertEquals(newBrk, setBrk, "brk(new) should update and return new program break"),
            () -> assertEquals(newBrk, actualBrk, "task program break field should be updated")
        );
    }

    @Test
    void testSysGetTime() throws Exception {
        Task task = kernel.createTask(getMinimalElf(), "time_task");
        invokeSyscall(task, SystemCallHandler.SYS_GET_TIME, 0, 0, 0);

        assertTrue(task.getRegisters()[10] > 0, "SYS_GET_TIME should return a positive timestamp");
    }
}
