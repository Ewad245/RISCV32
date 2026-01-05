package cse311;

import cse311.kernel.NonContiguous.paging.AddressSpace;
import cse311.kernel.NonContiguous.paging.PagedMemoryManager;
import cse311.kernel.NonContiguous.paging.EagerPager;
import cse311.Exception.MemoryAccessException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import cse311.kernel.NonContiguous.paging.ClockPolicy;

public class PagingRaceConditionTest {

    @Test
    public void testConcurrentContextSwitchPaging() throws InterruptedException, MemoryAccessException {
        // Setup Paged Memory Manager (Small memory: 128KB = 32 frames)
        PagedMemoryManager mm = new PagedMemoryManager(128 * 1024);
        mm.setPager(new EagerPager(mm, new ClockPolicy(32)));

        // Create AS 1
        AddressSpace as1 = mm.createAddressSpace(1);
        mm.switchTo(as1);
        mm.mapRegion(as1, 0x1000, 4096, true, true, false); // Map 0x1000
        mm.writeByte(0x1000, (byte) 0xAA);

        // Create AS 2
        AddressSpace as2 = mm.createAddressSpace(2);
        mm.switchTo(as2);
        mm.mapRegion(as2, 0x1000, 4096, true, true, false); // Map 0x1000 to DIFFERENT frame
        mm.writeByte(0x1000, (byte) 0xBB);

        // Sanity Check
        mm.switchTo(as1);
        assertEquals((byte) 0xAA, mm.readByte(0x1000));
        mm.switchTo(as2);
        assertEquals((byte) 0xBB, mm.readByte(0x1000));

        // THE RACE
        // T1 wants to read AS 1 (Expect 0xAA)
        // T2 switches to AS 2 (Sets current AS to 2)

        final AtomicBoolean t1Failed = new AtomicBoolean(false);
        final CountDownLatch latch1 = new CountDownLatch(1); // T1 ready to read
        final CountDownLatch latch2 = new CountDownLatch(1); // T2 switched

        Thread t1 = new Thread(() -> {
            try {
                mm.switchTo(as1);
                // Pause to let T2 intrude
                latch1.countDown();
                latch2.await(); // Wait until T2 has switched context

                // Should read from AS1, but if race exists, might read from AS2
                byte val = mm.readByte(0x1000);
                if (val != (byte) 0xAA) {
                    System.out.println("T1 Read Wrong Value: 0x" + Integer.toHexString(val & 0xFF));
                    t1Failed.set(true);
                }
            } catch (Exception e) {
                e.printStackTrace();
                t1Failed.set(true);
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                latch1.await(); // Wait for T1 to set context 1
                mm.switchTo(as2); // Overwrite context to 2
                latch2.countDown(); // Tell T1 to proceed
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertFalse(t1Failed.get(), "Race condition detected! T1 read AS 2's memory due to shared context.");
    }
}
