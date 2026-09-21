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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelIntegrationTest {

    private Kernel kernel;

    @BeforeEach
    void setUp() {
        ContiguousMemoryManager cmm = new ContiguousMemoryManager(
                8 * 1024 * 1024,
                new BestFitStrategy());
        kernel = new Kernel(cmm);
    }

    private byte[] getMinimalElf() {
        return new byte[] {
                // ELF Header (52 bytes)
                0x7F, 0x45, 0x4C, 0x46,
                0x01, 0x01, 0x01, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x02, 0x00, (byte) 0xF3, 0x00, 0x01, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x01, 0x00, 0x34, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x34, 0x00, 0x20, 0x00, 0x01, 0x00, 0x28, 0x00,
                0x00, 0x00, 0x00, 0x00,
                // Program Header (32 bytes)
                0x01, 0x00, 0x00, 0x00, 0x54, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00,
                0x04, 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00,
                0x05, 0x00, 0x00, 0x00, 0x00, 0x10, 0x00, 0x00,
                // Code (4 bytes)
                0x13, 0x00, 0x00, 0x00
        };
    }

    @Test
    void testKernelInitialization() {
        assertAll("kernelSubsystems",
            () -> assertNotNull(kernel, "Kernel should not be null"),
            () -> assertNotNull(kernel.getScheduler(), "Scheduler should not be null"),
            () -> assertNotNull(kernel.getTaskManager(), "TaskManager should not be null"),
            () -> assertNotNull(kernel.getSystemCallHandler(), "SystemCallHandler should not be null")
        );
    }

    @Test
    void testKernelConfiguration() {
        KernelConfig config = kernel.getConfig();
        config.setTimeSlice(500);
        config.setSchedulerType(KernelConfig.SchedulerType.PRIORITY);

        assertAll("kernelConfig",
            () -> assertNotNull(config, "Config should not be null"),
            () -> assertEquals(500, config.getTimeSlice(), "Time slice should be 500"),
            () -> assertEquals(KernelConfig.SchedulerType.PRIORITY, config.getSchedulerType(), "Scheduler type should be PRIORITY")
        );
    }

    @Test
    void testTaskCreation() throws Exception {
        Task task = kernel.createTask(getMinimalElf(), "test_task");

        assertAll("taskProperties",
            () -> assertNotNull(task, "Task should not be null"),
            () -> assertEquals("test_task", task.getName(), "Task name should match"),
            () -> assertEquals(TaskState.READY, task.getState(), "Task initial state should be READY"),
            () -> assertEquals(0x10000, task.getProgramCounter(), "Entry point should match ELF")
        );
    }

    @Test
    void testTaskTermination() throws Exception {
        Task task = kernel.createTask(getMinimalElf(), "term_task");
        int pid = task.getId();
        kernel.terminateTask(pid);

        assertAll("taskTerminationState",
            () -> assertNotNull(kernel.getTask(pid), "Task should remain in map as a zombie until reaped"),
            () -> assertEquals(TaskState.TERMINATED, task.getState(), "Task state should be TERMINATED")
        );
    }

    @Test
    void testKernelStats() throws Exception {
        KernelStats initialStats = kernel.getStats();
        kernel.createTask(getMinimalElf(), "stat_task");
        KernelStats updatedStats = kernel.getStats();

        assertAll("kernelStatsValues",
            () -> assertEquals(0, initialStats.totalProcesses, "Initial total processes should be 0"),
            () -> assertEquals(1, updatedStats.totalProcesses, "Updated total processes should be 1"),
            () -> assertEquals(1, updatedStats.readyProcesses, "Ready processes should be 1")
        );
    }

    @Test
    void testSchedulerStats() throws Exception {
        kernel.createTask(getMinimalElf(), "sched_task");

        kernel.start();
        Thread.sleep(100);
        kernel.stop();

        SchedulerStats stats = kernel.getScheduler().getStats();

        assertAll("schedulerStatsValues",
            () -> assertNotNull(stats, "Scheduler stats should not be null"),
            () -> assertNotNull(stats.algorithmName, "Scheduler algorithm name should be set"),
            () -> assertTrue(stats.totalSchedules >= 0, "Total schedules should be non-negative"),
            () -> assertTrue(stats.contextSwitches >= 0, "Context switches should be non-negative")
        );
    }

    @Test
    void testSchedulerConfiguration() {
        kernel.getConfig().setSchedulerType(KernelConfig.SchedulerType.ROUND_ROBIN);
        assertEquals(KernelConfig.SchedulerType.ROUND_ROBIN, kernel.getConfig().getSchedulerType(),
                "Scheduler type should be set to ROUND_ROBIN");
    }

    @Test
    void testTaskPriorities() throws Exception {
        kernel.getConfig().setSchedulerType(KernelConfig.SchedulerType.PRIORITY);

        Task t1 = kernel.createTask(getMinimalElf(), "low_prio");
        t1.setPriority(1);

        Task t2 = kernel.createTask(getMinimalElf(), "high_prio");
        t2.setPriority(10);

        assertAll("taskPrioritiesValues",
            () -> assertEquals(1, t1.getPriority(), "t1 priority should be 1"),
            () -> assertEquals(10, t2.getPriority(), "t2 priority should be 10")
        );
    }
}