package cse311;

import cse311.kernel.process.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RV32iComputerIntegrationTest {

    private RV32Computer computer;

    @BeforeEach
    void setUp() {
        computer = new RV32Computer(8 * 1024 * 1024, 16, cse311.Constants.MemoryMode.CONTIGUOUS);
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
    void testComputerInitialization() {
        assertAll("computerComponents",
            () -> assertNotNull(computer, "Computer should not be null"),
            () -> assertNotNull(computer.getMemoryManager(), "Memory manager should not be null"),
            () -> assertNotNull(computer.getKernel(), "Kernel should not be null")
        );
    }

    @Test
    void testTaskCreationWithElfData() {
        Task task = computer.createTask(getMinimalElf(), "elf_task");

        assertAll("taskProperties",
            () -> assertNotNull(task, "Task should not be null"),
            () -> assertEquals(0x10000, task.getProgramCounter(), "Entry point should be 0x10000")
        );
    }

    @Test
    void testKernelIntegration() {
        Task task = computer.createTask(getMinimalElf(), "kernel_task");

        assertAll("kernelTaskIntegration",
            () -> assertNotNull(task, "Task should not be null"),
            () -> assertTrue(computer.getKernel().getAllTasks().contains(task), "Task should be in kernel task list")
        );
    }

    @Test
    void testComputerExecutionLifecycle() {
        Task task = computer.createTask(getMinimalElf(), "lifecycle_task");
        int taskCount = computer.getKernel().getAllTasks().size();

        computer.start();
        Object startStats = computer.getKernel().getStats();
        computer.stop();
        Object stopStats = computer.getKernel().getStats();

        assertAll("executionLifecycle",
            () -> assertNotNull(task, "Task should not be null"),
            () -> assertEquals(1, taskCount, "Task count should be 1"),
            () -> assertNotNull(startStats, "Stats should not be null after start"),
            () -> assertNotNull(stopStats, "Stats should not be null after stop")
        );
    }
}