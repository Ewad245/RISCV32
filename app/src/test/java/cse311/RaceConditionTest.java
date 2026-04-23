package cse311;

import cse311.kernel.contiguous.ContiguousMemoryManager;
import cse311.kernel.contiguous.FirstFitStrategy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import cse311.Exception.MemoryAccessException;

public class RaceConditionTest {

    @Test
    public void testConcurrentContextSwitch() throws InterruptedException, MemoryAccessException {
        // Setup Memory Manager
        ContiguousMemoryManager mm = new ContiguousMemoryManager(10000, new FirstFitStrategy());

        // Allocate Task 1 (Pid 1) -> Start 0, Size 100
        mm.allocateMemory(1, 100);
        // Allocate Task 2 (Pid 2) -> Start 1000, Size 100
        // We force allocation at 1000 by allocating a filler block first if needed,
        // but FirstFit will put Pid 1 at 0.
        // Pid 2 will go to 100 (size 100).
        // Let's just use what we get.

        // Pid 1 is at 0.
        // Pid 2 is at 100.
        mm.allocateMemory(2, 100);

        // Verify positions (internal knowledge or just check logic)
        // We can write unique values to their starts.

        // Write to Physical Address 0 (which corresponds to Task 1 start)
        // Access via context switch to be sure, or cheat via mm.getByteMemory() if
        // accessible?
        // ContiguousMemoryManager extends MemoryManager which has protected memory
        // access.
        // We can just use switchContext to write safely first.

        // Init Task 1 Memory
        mm.switchContext(1);
        mm.writeByte(0, (byte) 0xAA); // Task 1, offset 0 -> Phy 0

        // Init Task 2 Memory
        mm.switchContext(2);
        mm.writeByte(0, (byte) 0xBB); // Task 2, offset 0 -> Phy 100 (assuming allocated there)

        // Sanity Check
        mm.switchContext(1);
        assertEquals((byte) 0xAA, mm.readByte(0));
        mm.switchContext(2);
        assertEquals((byte) 0xBB, mm.readByte(0));

        // THE RACE
        // T1 wants to read Task 1 (Expect 0xAA)
        // T2 switches to Task 2 (Sets base to 100)

        final AtomicBoolean t1Failed = new AtomicBoolean(false);
        final CountDownLatch latch1 = new CountDownLatch(1); // T1 ready to read
        final CountDownLatch latch2 = new CountDownLatch(1); // T2 switched

        Thread t1 = new Thread(() -> {
            try {
                mm.switchContext(1);
                // Pause to let T2 intrude
                latch1.countDown();
                latch2.await(); // Wait until T2 has switched context

                byte val = mm.readByte(0);
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
                mm.switchContext(2); // Overwrite context to 2
                latch2.countDown(); // Tell T1 to proceed
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertFalse(t1Failed.get(), "Race condition detected! T1 read Task 2's memory due to shared registers.");
    }
}
