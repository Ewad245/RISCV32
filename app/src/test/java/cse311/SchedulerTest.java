package cse311;

import cse311.kernel.process.ProgramInfo;
import cse311.kernel.process.Task;
import cse311.kernel.process.TaskState;
import cse311.kernel.scheduler.CooperativeScheduler;
import cse311.kernel.scheduler.PriorityScheduler;
import cse311.kernel.scheduler.RoundRobinScheduler;
import cse311.kernel.scheduler.Scheduler;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SchedulerTest {

    private Task createTask(int id, int priority) {
        Task t = new Task(id, 0, 1024, 0, new ProgramInfo(0, 0, 0, 0, 0, 0));
        t.setPriority(priority);
        t.setState(TaskState.READY);
        return t;
    }

    @Test
    void testRoundRobinFifoOrder() {
        RoundRobinScheduler rr = new RoundRobinScheduler(100);
        Task t1 = createTask(1, 1);
        Task t2 = createTask(2, 1);
        Task t3 = createTask(3, 1);

        rr.addTask(t1);
        rr.addTask(t2);
        rr.addTask(t3);

        Task s1 = rr.schedule();
        Task s2 = rr.schedule();
        Task s3 = rr.schedule();
        Task s4 = rr.schedule();

        assertAll("roundRobinOrder",
            () -> assertEquals(t1, s1, "First in should be first out"),
            () -> assertEquals(t2, s2, "Second in should be second out"),
            () -> assertEquals(t3, s3, "Third in should be third out"),
            () -> assertNull(s4, "Queue should be empty after all tasks are polled")
        );
    }

    @Test
    void testPrioritySchedulerOrdering() {
        PriorityScheduler ps = new PriorityScheduler(100);
        Task low = createTask(1, 1);
        Task medium = createTask(2, 5);
        Task high = createTask(3, 10);

        ps.addTask(low);
        ps.addTask(high);
        ps.addTask(medium);

        Task s1 = ps.schedule();
        Task s2 = ps.schedule();
        Task s3 = ps.schedule();
        Task s4 = ps.schedule();

        assertAll("priorityOrder",
            () -> assertEquals(high, s1, "Highest priority task should be scheduled first"),
            () -> assertEquals(medium, s2, "Medium priority task should be scheduled second"),
            () -> assertEquals(low, s3, "Lowest priority task should be scheduled last"),
            () -> assertNull(s4, "Scheduler queue should be empty")
        );
    }

    @Test
    void testCooperativeScheduler() {
        CooperativeScheduler cs = new CooperativeScheduler();
        int timeSlice = cs.getTimeSlice();

        Task t1 = createTask(1, 1);
        Task t2 = createTask(2, 1);

        cs.addTask(t1);
        cs.addTask(t2);

        Task s1 = cs.schedule();
        Task s2 = cs.schedule();
        Task s3 = cs.schedule();

        assertAll("cooperativeOrder",
            () -> assertEquals(Integer.MAX_VALUE, timeSlice, "Cooperative scheduler should have infinite timeslice"),
            () -> assertEquals(t1, s1, "First added task scheduled first"),
            () -> assertEquals(t2, s2, "Second added task scheduled second"),
            () -> assertNull(s3, "Queue should be empty")
        );
    }

    @Test
    void testConcurrentTaskAdditionRaceCondition() throws InterruptedException {
        Scheduler[] schedulers = new Scheduler[] {
            new RoundRobinScheduler(100),
            new PriorityScheduler(100),
            new CooperativeScheduler()
        };

        for (Scheduler scheduler : schedulers) {
            Task task = createTask(99, 5);
            int numThreads = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numThreads);
            AtomicInteger errorCount = new AtomicInteger(0);

            for (int i = 0; i < numThreads; i++) {
                new Thread(() -> {
                    try {
                        startLatch.await();
                        scheduler.addTask(task);
                    } catch (InterruptedException e) {
                        errorCount.incrementAndGet();
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                }).start();
            }

            startLatch.countDown();
            doneLatch.await();

            Task scheduled1 = scheduler.schedule();
            Task scheduled2 = scheduler.schedule();

            assertAll("concurrencyResults",
                () -> assertEquals(0, errorCount.get(), "No exceptions during concurrent addTask"),
                () -> assertEquals(task, scheduled1, "Task should be scheduled exactly once"),
                () -> assertNull(scheduled2, "Task must NOT be scheduled twice")
            );
        }
    }
}
