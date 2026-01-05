package cse311;

import cse311.kernel.process.ProgramInfo;
import cse311.kernel.process.Task;
import cse311.kernel.process.TaskState;
import cse311.kernel.scheduler.RoundRobinScheduler;
import cse311.kernel.scheduler.Scheduler;
import cse311.kernel.scheduler.PriorityScheduler;
import cse311.kernel.scheduler.CooperativeScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test to reproduce and verify fix for scheduler race condition.
 * Simulates multiple threads (CPUs/ISRs) adding the same task simultaneously.
 */
public class SchedulerRaceTest {

    public static void main(String[] args) {
        System.out.println("=== Scheduler Race Condition Test ===\n");

        boolean passed = true;
        passed &= testScheduler(new RoundRobinScheduler(100), "RoundRobinScheduler");
        passed &= testScheduler(new PriorityScheduler(100), "PriorityScheduler");
        passed &= testScheduler(new CooperativeScheduler(), "CooperativeScheduler");

        if (passed) {
            System.out.println("\nAll tests PASSED.");
            System.exit(0);
        } else {
            System.out.println("\nSome tests FAILED.");
            System.exit(1);
        }
    }

    private static boolean testScheduler(Scheduler scheduler, String name) {
        System.out.println("Testing " + name + "...");

        // Create a dummy task
        // int id, int entryPoint, int stackSize, int stackBase, ProgramInfo info
        Task task = new Task(1, 0, 1024, 0, new ProgramInfo(0, 0, 0, 0, 0, 0));
        task.setState(TaskState.READY);

        int numThreads = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger excCount = new AtomicInteger(0);

        // Threads simply try to add the same task
        for (int i = 0; i < numThreads; i++) {
            new Thread(() -> {
                try {
                    startLatch.await(); // Wait for signal
                    scheduler.addTask(task);
                } catch (Exception e) {
                    excCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        // Boom! Start all threads effectively at once
        startLatch.countDown();

        try {
            doneLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (excCount.get() > 0) {
            System.out.println("  [FAIL] Exceptions occurred during addTask.");
            return false;
        }

        // Verify:
        // 1. Task should be in the scheduler
        // 2. Task should be scheduled exactly ONCE

        Task t1 = scheduler.schedule();
        Task t2 = scheduler.schedule();

        if (t1 == task && t2 == null) {
            System.out.println("  [PASS] Task scheduled exactly once.");
            return true;
        } else {
            System.out.println("  [FAIL] Race condition detected!");
            if (t1 == null) {
                System.out.println("    First schedule() returned null (Task not added?)");
            } else {
                System.out.println("    First schedule() returned task: " + t1);
            }
            if (t2 != null) {
                System.out.println("    Second schedule() returned task: " + t2 + " (Duplicate!)");
            }
            return false;
        }
    }
}
