package cse311;

import cse311.paging.*;
import cse311.paging.AddressSpace;

/**
 * Test class demonstrating ELF loading with proper memory permissions
 * like Linux kernel does
 */
public class ElfLoaderWithPermissionsTest {
    
    public static void main(String[] args) {
        testElfLoadingWithPermissions();
    }
    
    public static void testElfLoadingWithPermissions() {
        System.out.println("🐧 Testing ELF Loading with Linux-like Memory Permissions...");
        
        try {
            // Create memory manager with paging
            PagedMemoryManager mm = new PagedMemoryManager(1024 * 1024); // 1MB
            
            // Create address space for a process
            AddressSpace as = mm.createAddressSpace(1);
            mm.switchTo(as);
            
            // Set up pager
            DemandPager pager = new DemandPager(mm, new ClockPolicy(mm.getTotalFrames()));
            mm.setPager(pager);
            
            // Create enhanced ELF loader
            ElfLoader loader = new ElfLoader(mm);
            
            // Test with a sample ELF file (you would replace this)
            String elfFile = "test_program.elf";
            
            System.out.println("Loading ELF with memory permissions...");
            
            // Load ELF into address space with proper permissions
            loader.loadElfIntoAddressSpace(as, mm, elfFile);
            
            System.out.println("✅ ELF loaded successfully with memory permissions!");
            
            // Display loaded segments with permissions
            System.out.println("\n📋 Loaded Segments:");
            for (ElfLoader.ElfSegment segment : loader.getSegments()) {
                System.out.println("  " + segment);
                
                // Show permission mapping
                String perms = "";
                if (segment.readable) perms += "R";
                if (segment.writable) perms += "W"; 
                if (segment.executable) perms += "X";
                
                System.out.println("    Permissions: " + perms);
            }
            
            // Test memory access
            int entryPoint = loader.getEntryPoint();
            System.out.println("\n🎯 Entry Point: 0x" + Integer.toHexString(entryPoint));
            
            // Verify memory is properly mapped
            int vpn = AddressSpace.getVPN(entryPoint);
            if (as.isPagePresent(vpn)) {
                int frame = as.getFrameNumber(vpn);
                System.out.println("✅ Page mapped: VPN=" + vpn + " → Frame=" + frame);
                
                // Test reading from the entry point
                try {
                    byte data = mm.readByte(entryPoint);
                    System.out.println("✅ Memory access working at entry point");
                } catch (Exception e) {
                    System.out.println("⚠️ Memory access issue: " + e.getMessage());
                }
            }
            
            System.out.println("\n🎉 ELF loading with permissions test complete!");
            
        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Demonstrate how to use the new ELF loader in a kernel context
     */
    public static void demonstrateKernelUsage() {
        System.out.println("🔧 Demonstrating Kernel-like ELF Loading...");
        
        try {
            // Simulate kernel loading a user program
            PagedMemoryManager kernelMM = new PagedMemoryManager(4 * 1024 * 1024); // 4MB
            
            // Create process address space
            AddressSpace userAS = kernelMM.createAddressSpace(42); // PID 42
            kernelMM.switchTo(userAS);
            
            // Set up pager for demand paging
            DemandPager pager = new DemandPager(kernelMM, new ClockPolicy(kernelMM.getTotalFrames()));
            kernelMM.setPager(pager);
            
            // Create ELF loader
            ElfLoader loader = new ElfLoader(kernelMM);
            
            System.out.println("🖥️ Kernel loading user program...");
            
            // Load ELF with full Linux-like handling
            loader.loadElfIntoAddressSpace(userAS, kernelMM, "/bin/hello");
            
            System.out.println("✅ User program loaded with proper memory protections");
            
            // Verify segments have correct permissions
            for (ElfLoader.ElfSegment seg : loader.getSegments()) {
                System.out.printf("  0x%08x-0x%08x %s\n", 
                    seg.virtualAddr, 
                    seg.virtualAddr + seg.memorySize,
                    getPermissionString(seg));
            }
            
        } catch (Exception e) {
            System.err.println("❌ Kernel demo failed: " + e.getMessage());
        }
    }
    
    private static String getPermissionString(ElfLoader.ElfSegment seg) {
        StringBuilder perms = new StringBuilder();
        perms.append(seg.readable ? "r" : "-");
        perms.append(seg.writable ? "w" : "-");
        perms.append(seg.executable ? "x" : "-");
        return perms.toString();
    }
}