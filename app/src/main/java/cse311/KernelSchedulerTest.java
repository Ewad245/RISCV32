package cse311;

import cse311.kernel.*;
import cse311.kernel.process.Task;
import cse311.kernel.scheduler.*;

/**
 * Test different scheduling algorithms
 */
public class KernelSchedulerTest {

    public static void main(String[] args) {
        System.out.println("=== Kernel Scheduler Comparison ===\n");

        try {
            testRoundRobinScheduler();
            System.out.println();
            testCooperativeScheduler();
            System.out.println();
            testPriorityScheduler();

        } catch (Exception e) {
            System.err.println("Test error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testRoundRobinScheduler() throws Exception {
        System.out.println("--- Round Robin Scheduler Test ---");

        // Create kernel with round robin scheduler
        SimpleMemory memory = new SimpleMemory(64 * 1024 * 1024);
        MemoryManager memManager = new MemoryManager(memory);
        Kernel kernel = new Kernel(memManager);

        kernel.getConfig().setSchedulerType(KernelConfig.SchedulerType.ROUND_ROBIN);
        kernel.getConfig().setTimeSlice(100); // Small time slice for demonstration

        // Create test tasks
        createTestTasks(kernel, "RR");

        // Print scheduler info
        RoundRobinScheduler scheduler = (RoundRobinScheduler) kernel.getScheduler();
        System.out.println("Ready queue size: " + scheduler.getReadyQueueSize());

        kernel.printStatus();

        // Run for a short time
        runKernelBriefly(kernel, 1000);

        SchedulerStats stats = scheduler.getStats();
        System.out.println("Scheduler stats: " + stats.totalSchedules + " schedules, " +
                stats.contextSwitches + " context switches");
    }

    private static void testCooperativeScheduler() throws Exception {
        System.out.println("--- Cooperative Scheduler Test ---");

        SimpleMemory memory = new SimpleMemory(64 * 1024 * 1024);
        MemoryManager memManager = new MemoryManager(memory);
        Kernel kernel = new Kernel(memManager);

        kernel.getConfig().setSchedulerType(KernelConfig.SchedulerType.COOPERATIVE);

        createTestTasks(kernel, "COOP");

        CooperativeScheduler scheduler = (CooperativeScheduler) kernel.getScheduler();
        System.out.println("Task count: " + scheduler.getTaskCount());

        kernel.printStatus();

        runKernelBriefly(kernel, 1000);

        SchedulerStats stats = scheduler.getStats();
        System.out.println("Scheduler stats: " + stats.totalSchedules + " schedules, " +
                stats.contextSwitches + " context switches");
    }

    private static void testPriorityScheduler() throws Exception {
        System.out.println("--- Priority Scheduler Test ---");

        SimpleMemory memory = new SimpleMemory(64 * 1024 * 1024);
        MemoryManager memManager = new MemoryManager(memory);
        Kernel kernel = new Kernel(memManager);

        kernel.getConfig().setSchedulerType(KernelConfig.SchedulerType.PRIORITY);
        kernel.getConfig().setTimeSlice(200);

        // Create tasks with different priorities
        Task t1 = kernel.createTask(createTestProgram(), "HIGH_PRIORITY");
        t1.setPriority(10);

        Task t2 = kernel.createTask(createTestProgram(), "MEDIUM_PRIORITY");
        t2.setPriority(5);

        Task t3 = kernel.createTask(createTestProgram(), "LOW_PRIORITY");
        t3.setPriority(1);

        PriorityScheduler scheduler = (PriorityScheduler) kernel.getScheduler();
        System.out.println("Highest priority: " + scheduler.getHighestPriority());
        System.out.println("Ready queue size: " + scheduler.getReadyQueueSize());

        kernel.printStatus();

        runKernelBriefly(kernel, 1000);

        SchedulerStats stats = scheduler.getStats();
        System.out.println("Scheduler stats: " + stats.totalSchedules + " schedules, " +
                stats.contextSwitches + " context switches");
    }

    private static void createTestTasks(Kernel kernel, String prefix) throws Exception {
        for (int i = 1; i <= 3; i++) {
            Task task = kernel.createTask(createTestProgram(), prefix + "_TASK_" + i);
            System.out.println("Created task: " + task.getName());
        }
    }

    private static void runKernelBriefly(Kernel kernel, int maxIterations) {
        // Run kernel in a separate thread with timeout
        Thread kernelThread = new Thread(() -> kernel.start());
        kernelThread.setDaemon(true);
        kernelThread.start();

        // Let it run briefly
        try {
            Thread.sleep(100); // 100ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        kernel.stop();

        try {
            kernelThread.join(1000); // Wait up to 1 second for kernel to stop
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static byte[] createTestProgram() {
        // Simple program that yields a few times then exits
        return new byte[] {
                // li t0, 5 (loop counter)
                0x13, 0x02, 0x50, 0x00, // addi t0, zero, 5
                // loop:
                // li a7, 124 (yield syscall)
                0x13, 0x08, (byte) 0xC0, 0x07, // addi a7, zero, 124
                // ecall
                0x73, 0x00, 0x00, 0x00, // ecall
                // addi t0, t0, -1
                0x13, (byte) 0x82, (byte) 0xF2, (byte) 0xFF, // addi t0, t0, -1
                // bne t0, zero, loop
                0x63, 0x1C, 0x02, (byte) 0xFE, // bne t0, zero, -8
                // exit
                0x13, 0x08, (byte) 0xD0, 0x05, // addi a7, zero, 93 (exit)
                0x13, 0x05, 0x00, 0x00, // addi a0, zero, 0
                0x73, 0x00, 0x00, 0x00 // ecall
        };
    }
}