package cse311;

import cse311.kernel.NonContiguous.paging.AddressSpace;
import cse311.kernel.NonContiguous.paging.ClockPolicy;
import cse311.kernel.NonContiguous.paging.DemandPager;
import cse311.kernel.NonContiguous.paging.EagerPager;
import cse311.kernel.NonContiguous.paging.PagedMemoryManager;
import cse311.kernel.NonContiguous.paging.PagingConfiguration;
import cse311.Exception.MemoryAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagedMemoryManagerTest {

    private PagedMemoryManager mm;

    @BeforeEach
    void setUp() {
        // 1MB = 256 frames (4KB each)
        mm = new PagedMemoryManager(1024 * 1024);
    }

    @Test
    void testDemandPagingAndFaultHandling() throws MemoryAccessException {
        DemandPager demandPager = new DemandPager(mm, new ClockPolicy(mm.getTotalFrames()));
        mm.setPager(demandPager);

        AddressSpace as = mm.createAddressSpace(1);
        mm.switchTo(as);

        int testAddr = 0x1000;
        int vpn = AddressSpace.getVPN(testAddr);
        boolean presentBefore = as.isPagePresent(vpn);

        mm.writeByte(testAddr, (byte) 42);

        boolean presentAfter = as.isPagePresent(vpn);
        int frameNum = as.getFrameNumber(vpn);
        byte readVal = mm.readByte(testAddr);

        assertAll("demandPagingChecks",
            () -> assertFalse(presentBefore, "Page should not be present before first access"),
            () -> assertTrue(presentAfter, "Page must be present after demand fault allocation"),
            () -> assertTrue(frameNum >= 0, "Valid frame must be assigned"),
            () -> assertEquals((byte) 42, readVal, "Read byte should match written value")
        );
    }

    @Test
    void testEagerPaging() throws MemoryAccessException {
        EagerPager eagerPager = new EagerPager(mm, new ClockPolicy(mm.getTotalFrames()));
        mm.setPager(eagerPager);

        AddressSpace as = mm.createAddressSpace(2);
        mm.switchTo(as);

        int testAddr = 0x2000;
        mm.writeByte(testAddr, (byte) 123);

        int vpn = AddressSpace.getVPN(testAddr);
        boolean present = as.isPagePresent(vpn);
        byte val = mm.readByte(testAddr);

        assertAll("eagerPagingChecks",
            () -> assertTrue(present, "Page should be present with eager pager"),
            () -> assertEquals((byte) 123, val, "Read value should match written value")
        );
    }

    @Test
    void testAddressSpaceIsolation() throws MemoryAccessException {
        PagingConfiguration.configure(mm, PagingConfiguration.Policy.DEMAND, PagingConfiguration.Policy.CLOCK);

        AddressSpace as1 = mm.createAddressSpace(1);
        AddressSpace as2 = mm.createAddressSpace(2);

        mm.switchTo(as1);
        mm.writeWord(0x3000, 0x11223344);

        mm.switchTo(as2);
        mm.writeWord(0x3000, 0x55667788);
        int val2 = mm.readWord(0x3000);

        mm.switchTo(as1);
        int val1 = mm.readWord(0x3000);

        assertAll("isolationChecks",
            () -> assertEquals(0x55667788, val2, "AS 2 should read 0x55667788"),
            () -> assertEquals(0x11223344, val1, "AS 1 memory must not be overwritten by AS 2")
        );
    }

    @Test
    void testDestroyAddressSpace() throws MemoryAccessException {
        PagingConfiguration.configure(mm, PagingConfiguration.Policy.DEMAND, PagingConfiguration.Policy.CLOCK);

        AddressSpace as = mm.createAddressSpace(10);
        mm.switchTo(as);
        mm.writeByte(0x1000, (byte) 1);
        mm.writeByte(0x2000, (byte) 2);

        AddressSpace beforeDestroy = mm.getAddressSpace(10);
        mm.destroyAddressSpace(10);
        AddressSpace afterDestroy = mm.getAddressSpace(10);

        assertAll("destroyChecks",
            () -> assertNotNull(beforeDestroy, "Address space should exist before destruction"),
            () -> assertNull(afterDestroy, "Address space should be removed after destruction")
        );
    }
}
