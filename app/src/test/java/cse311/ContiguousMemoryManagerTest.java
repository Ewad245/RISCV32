package cse311;

import cse311.kernel.contiguous.BestFitStrategy;
import cse311.kernel.contiguous.ContiguousMemoryManager;
import cse311.kernel.contiguous.FirstFitStrategy;
import cse311.Exception.MemoryAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContiguousMemoryManagerTest {

    private static final String ERR_ALLOC_1 = "Alloc 1 should succeed";
    private static final String ERR_ALLOC_2 = "Alloc 2 should succeed";

    private ContiguousMemoryManager mm;

    @BeforeEach
    void setUp() {
        mm = new ContiguousMemoryManager(10000, new FirstFitStrategy());
    }

    @Test
    void testAllocationAndIsolation() throws MemoryAccessException {
        boolean alloc1 = mm.allocateMemory(1, 1000);
        boolean alloc2 = mm.allocateMemory(2, 1000);

        mm.switchContext(1);
        int limit1 = mm.getLimitRegister();
        mm.writeWord(0, 0x12345678);

        mm.switchContext(2);
        int limit2 = mm.getLimitRegister();
        mm.writeWord(0, 0x9ABCDEF0);
        int readPid2 = mm.readWord(0);

        mm.switchContext(1);
        int readPid1 = mm.readWord(0);

        assertAll("isolationState",
            () -> assertTrue(alloc1, ERR_ALLOC_1),
            () -> assertTrue(alloc2, ERR_ALLOC_2),
            () -> assertEquals(1000, limit1, "PID 1 limit should be 1000"),
            () -> assertEquals(1000, limit2, "PID 2 limit should be 1000"),
            () -> assertEquals(0x9ABCDEF0, readPid2, "PID 2 should read its own word"),
            () -> assertEquals(0x12345678, readPid1, "PID 1 memory must not be overwritten by PID 2")
        );
    }

    @Test
    void testSegmentationFaultProtection() {
        boolean allocated = mm.allocateMemory(1, 500);
        mm.switchContext(1);

        assertAll("protectionChecks",
            () -> assertTrue(allocated, "Allocation should succeed"),
            () -> assertDoesNotThrow(() -> mm.writeByte(499, (byte) 1), "Access within limit should succeed"),
            () -> assertThrows(MemoryAccessException.class, () -> mm.writeByte(500, (byte) 2), "Write at limit should throw"),
            () -> assertThrows(MemoryAccessException.class, () -> mm.readWord(500), "Read at limit should throw")
        );
    }

    @Test
    void testFreeAndReallocation() {
        boolean alloc1 = mm.allocateMemory(1, 4000);
        boolean alloc2 = mm.allocateMemory(2, 4000);
        boolean alloc3Fail = mm.allocateMemory(3, 3000);

        mm.freeMemory(1);
        boolean alloc3Success = mm.allocateMemory(3, 3000);

        assertAll("freeReallocChecks",
            () -> assertTrue(alloc1, ERR_ALLOC_1),
            () -> assertTrue(alloc2, ERR_ALLOC_2),
            () -> assertFalse(alloc3Fail, "Alloc 3 should fail before free"),
            () -> assertTrue(alloc3Success, "Alloc 3 should succeed after freeing PID 1")
        );
    }

    @Test
    void testCompactionOnFragmentation() {
        boolean a1 = mm.allocateMemory(1, 3000);
        boolean a2 = mm.allocateMemory(2, 3000);
        boolean a3 = mm.allocateMemory(3, 3000);

        mm.freeMemory(1);
        mm.freeMemory(3);

        boolean a4 = mm.allocateMemory(4, 5000);

        assertAll("compactionChecks",
            () -> assertTrue(a1, ERR_ALLOC_1),
            () -> assertTrue(a2, ERR_ALLOC_2),
            () -> assertTrue(a3, "Alloc 3 should succeed"),
            () -> assertTrue(a4, "Compaction should coalesce fragmented holes to fit 5000")
        );
    }

    @Test
    void testCopyMemoryForFork() throws MemoryAccessException {
        boolean a1 = mm.allocateMemory(1, 1000);
        boolean a2 = mm.allocateMemory(2, 1000);

        mm.switchContext(1);
        mm.writeWord(100, 0xCAFEBABE);

        boolean copied = mm.copyMemory(1, 2);

        mm.switchContext(2);
        int childVal = mm.readWord(100);

        assertAll("copyMemoryChecks",
            () -> assertTrue(a1, ERR_ALLOC_1),
            () -> assertTrue(a2, ERR_ALLOC_2),
            () -> assertTrue(copied, "Copy memory should succeed"),
            () -> assertEquals(0xCAFEBABE, childVal, "Child PID 2 should receive exact copy of parent memory")
        );
    }

    @Test
    void testBestFitStrategy() {
        mm.setAllocationStrategy(new BestFitStrategy());
        boolean a1 = mm.allocateMemory(1, 2000);
        boolean a2 = mm.allocateMemory(2, 2000);
        boolean a3 = mm.allocateMemory(3, 4000);
        boolean a4 = mm.allocateMemory(4, 2000);

        mm.freeMemory(1);
        mm.freeMemory(3);

        boolean a5 = mm.allocateMemory(5, 1500);

        assertAll("bestFitChecks",
            () -> assertTrue(a1, ERR_ALLOC_1),
            () -> assertTrue(a2, ERR_ALLOC_2),
            () -> assertTrue(a3, "Alloc 3 should succeed"),
            () -> assertTrue(a4, "Alloc 4 should succeed"),
            () -> assertTrue(a5, "BestFit should allocate 1500 into 2000 hole")
        );
    }
}
