package cse311;

import cse311.kernel.NonContiguous.paging.AddressSpace;
import cse311.kernel.NonContiguous.paging.ClockPolicy;
import cse311.kernel.NonContiguous.paging.EagerPager;
import cse311.kernel.NonContiguous.paging.PagedMemoryManager;
import cse311.Exception.MemoryAccessException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PagingRaceConditionTest {

    @Test
    void testConcurrentContextSwitchPaging() throws InterruptedException, MemoryAccessException {
        PagedMemoryManager mm = new PagedMemoryManager(128 * 1024);
        mm.setPager(new EagerPager(mm, new ClockPolicy(32)));

        AddressSpace as1 = mm.createAddressSpace(1);
        mm.switchTo(as1);
        mm.mapRegion(as1, 0x1000, 4096, true, true, false);
        mm.writeByte(0x1000, (byte) 0xAA);

        AddressSpace as2 = mm.createAddressSpace(2);
        mm.switchTo(as2);
        mm.mapRegion(as2, 0x1000, 4096, true, true, false);
        mm.writeByte(0x1000, (byte) 0xBB);

        mm.switchTo(as1);
        byte val1 = mm.readByte(0x1000);
        mm.switchTo(as2);
        byte val2 = mm.readByte(0x1000);

        final AtomicBoolean t1Failed = new AtomicBoolean(false);
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        Thread t1 = new Thread(() -> {
            try {
                mm.switchTo(as1);
                latch1.countDown();
                latch2.await();

                byte val = mm.readByte(0x1000);
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
                mm.switchTo(as2);
                latch2.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertAll("pagingConcurrencyChecks",
            () -> assertEquals((byte) 0xAA, val1, "AS 1 initial value should be 0xAA"),
            () -> assertEquals((byte) 0xBB, val2, "AS 2 initial value should be 0xBB"),
            () -> assertFalse(t1Failed.get(), "Race condition detected! T1 read AS 2's memory due to shared context.")
        );
    }
}
