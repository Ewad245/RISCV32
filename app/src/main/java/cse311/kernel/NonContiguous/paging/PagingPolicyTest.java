package cse311.kernel.NonContiguous.paging;

import cse311.Exception.MemoryAccessException;

/**
 * Test class to verify paging policy abstraction works correctly
 * with the new 2-level page table structure.
 */
public class PagingPolicyTest {
    public static void main(String[] args) {
        try {
            testPolicyAbstraction();
            System.out.println("All policy abstraction tests passed!");
        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testPolicyAbstraction() throws MemoryAccessException {
        PagedMemoryManager mm = new PagedMemoryManager(1024 * 1024); // 1MB

        // Test Demand Pager
        DemandPager demandPager = new DemandPager(mm, new ClockPolicy(mm.getTotalFrames()));
        mm.setPager(demandPager);

        AddressSpace as = mm.createAddressSpace(1);
        mm.switchTo(as);

        // Test that policies work without knowing page table internals
        int testAddr = 0x1000; // Page 1

        // This should trigger page fault and allocation
        mm.writeByte(testAddr, (byte) 42);

        // Verify page was allocated
        int vpn = AddressSpace.getVPN(testAddr);
        if (!as.isPagePresent(vpn)) {
            throw new RuntimeException("Page should be present after write");
        }

        int frame = as.getFrameNumber(vpn);
        if (frame < 0) {
            throw new RuntimeException("Should have valid frame number");
        }

        // Test Eager Pager
        EagerPager eagerPager = new EagerPager(mm, new ClockPolicy(mm.getTotalFrames()));
        mm.setPager(eagerPager);

        AddressSpace as2 = mm.createAddressSpace(2);
        mm.switchTo(as2);

        // Test with eager pager
        int testAddr2 = 0x2000;
        mm.writeByte(testAddr2, (byte) 123);

        int vpn2 = AddressSpace.getVPN(testAddr2);
        if (!as2.isPagePresent(vpn2)) {
            throw new RuntimeException("Page should be present with eager pager");
        }

        System.out.println("Policy abstraction working correctly with 2-level page tables");
    }
}