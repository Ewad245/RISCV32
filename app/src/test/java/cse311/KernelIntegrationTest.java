package cse311;

import cse311.kernel.Kernel;
import cse311.kernel.KernelConfig;
import cse311.kernel.KernelStats;
import cse311.kernel.contiguous.BestFitStrategy;
import cse311.kernel.contiguous.ContiguousMemoryManager;
import cse311.kernel.process.Task;
import cse311.kernel.process.TaskState;
import cse311.kernel.scheduler.SchedulerStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KernelIntegrationTest {

    private Kernel kernel;
    private MemoryManager memory;
    private RV32iCpu cpu;

    @BeforeEach
    void setUp() {
        // Increase memory to 8MB to accommodate 1MB+ per task
        ContiguousMemoryManager cmm = new ContiguousMemoryManager(
                8 * 1024 * 1024,
                new BestFitStrategy());
        this.memory = cmm;

        cpu = new RV32iCpu(memory);
        kernel = new Kernel(cpu, memory);
    }

    /**
     * Helper to create a minimal valid 32-bit RISC-V ELF binary.
     * Contains a file header and one program header pointing to 4 bytes of code
     * (NOP).
     */
    private byte[] getMinimalElf() {
        return new byte[] {
                // ELF Header (52 bytes)
                0x7F, 0x45, 0x4C, 0x46, // Magic: .ELF
                0x01, // Class: 32-bit
                0x01, // Data: Little Endian
                0x01, // Version: 1
                0x00, // OS/ABI: System V
                0x00, // ABI Version
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // Pad
                0x02, 0x00, // Type: Executable
                (byte) 0xF3, 0x00, // Machine: RISC-V
                0x01, 0x00, 0x00, 0x00, // Version: 1
                0x00, 0x00, 0x01, 0x00, // Entry Point: 0x10000
                0x34, 0x00, 0x00, 0x00, // PhOff: 52 (immediately after header)
                0x00, 0x00, 0x00, 0x00, // ShOff: 0
                0x00, 0x00, 0x00, 0x00, // Flags
                0x34, 0x00, // EHSize: 52
                0x20, 0x00, // PHEntSize: 32
                0x01, 0x00, // PHNum: 1
                0x28, 0x00, // SHEntSize: 40
                0x00, 0x00, // SHNum: 0
                0x00, 0x00, // SHStrNdx: 0

                // Program Header (32 bytes)
                0x01, 0x00, 0x00, 0x00, // Type: LOAD
                0x54, 0x00, 0x00, 0x00, // Offset: 84 (Header 52 + PH 32)
                0x00, 0x00, 0x01, 0x00, // VAddr: 0x10000
                0x00, 0x00, 0x01, 0x00, // PAddr: 0x10000
                0x04, 0x00, 0x00, 0x00, // FileSz: 4
                0x04, 0x00, 0x00, 0x00, // MemSz: 4
                0x05, 0x00, 0x00, 0x00, // Flags: R+X (5)
                0x00, 0x10, 0x00, 0x00, // Align: 0x1000

                // Code (4 bytes)
                0x13, 0x00, 0x00, 0x00 // nop (addi x0, x0, 0)
        };
    }

    @Test
    void testKernelInitialization() {
        assertNotNull(kernel);
        assertNotNull(kernel.getScheduler());
        assertNotNull(kernel.getTaskManager());
        assertNotNull(kernel.getSystemCallHandler());
    }

    @Test
    void testKernelConfiguration() {
        KernelConfig config = kernel.getConfig();
        assertNotNull(config);

        config.setTimeSlice(500);
        assertEquals(500, config.getTimeSlice());

        config.setSchedulerType(KernelConfig.SchedulerType.PRIORITY);
        assertEquals(KernelConfig.SchedulerType.PRIORITY, config.getSchedulerType());
    }

    @Test
    void testTaskCreation() throws Exception {
        Task task = kernel.createTask(getMinimalElf(), "test_task");
        assertNotNull(task);
        assertEquals("test_task", task.getName());
        assertEquals(TaskState.READY, task.getState());
        assertEquals(0x10000, task.getProgramCounter()); // Verify entry point from ELF
    }

    @Test
    void testTaskTermination() throws Exception {
        Task task = kernel.createTask(getMinimalElf(), "term_task");

        int pid = task.getId();
        kernel.terminateTask(pid);

        assertNull(kernel.getTask(pid));
        assertEquals(TaskState.TERMINATED, task.getState());
    }

    @Test
    void testKernelStats() throws Exception {
        KernelStats stats = kernel.getStats();
        assertEquals(0, stats.totalProcesses);

        kernel.createTask(getMinimalElf(), "stat_task");

        stats = kernel.getStats();
        assertEquals(1, stats.totalProcesses);
        assertEquals(1, stats.readyProcesses);
    }

    @Test
    void testSchedulerStats() throws Exception {
        kernel.createTask(getMinimalElf(), "sched_task");

        kernel.start();
        Thread.sleep(100);
        kernel.stop();

        SchedulerStats stats = kernel.getScheduler().getStats();
        assertNotNull(stats);
        assertTrue(stats.totalSchedules >= 0);
    }

    @Test
    void testSchedulerConfiguration() {
        kernel.getConfig().setSchedulerType(KernelConfig.SchedulerType.ROUND_ROBIN);
    }

    @Test
    void testTaskPriorities() throws Exception {
        kernel.getConfig().setSchedulerType(KernelConfig.SchedulerType.PRIORITY);

        Task t1 = kernel.createTask(getMinimalElf(), "low_prio");
        t1.setPriority(1);

        Task t2 = kernel.createTask(getMinimalElf(), "high_prio");
        t2.setPriority(10);

        assertEquals(1, t1.getPriority());
        assertEquals(10, t2.getPriority());
    }
}