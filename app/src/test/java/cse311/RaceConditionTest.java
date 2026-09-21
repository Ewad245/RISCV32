package cse311;

import cse311.kernel.contiguous.ContiguousMemoryManager;
import cse311.kernel.contiguous.FirstFitStrategy;
import cse311.Exception.MemoryAccessException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RaceConditionTest {

    @Test
    void testConcurrentContextSwitch() throws InterruptedException, MemoryAccessException {
        ContiguousMemoryManager mm = new ContiguousMemoryManager(10000, new FirstFitStrategy());

        mm.allocateMemory(1, 100);
        mm.allocateMemory(2, 100);

        mm.switchContext(1);
        mm.writeByte(0, (byte) 0xAA);

        mm.switchContext(2);
        mm.writeByte(0, (byte) 0xBB);

        mm.switchContext(1);
        byte val1 = mm.readByte(0);
        mm.switchContext(2);
        byte val2 = mm.readByte(0);

        final AtomicBoolean t1Failed = new AtomicBoolean(false);
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        Thread t1 = new Thread(() -> {
            try {
                mm.switchContext(1);
                latch1.countDown();
                latch2.await();

                byte val = mm.readByte(0);
                if (val != (byte) 0xAA) {
                    t1Failed.set(true);
                }
            } catch (InterruptedException | MemoryAccessException e) {
                t1Failed.set(true);
                Thread.currentThread().interrupt();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                latch1.await();
                mm.switchContext(2);
                latch2.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertAll("concurrencyContextChecks",
            () -> assertEquals((byte) 0xAA, val1, "Task 1 initial byte should be 0xAA"),
            () -> assertEquals((byte) 0xBB, val2, "Task 2 initial byte should be 0xBB"),
            () -> assertFalse(t1Failed.get(), "Race condition detected! T1 read Task 2's memory due to shared registers.")
        );
    }
}
