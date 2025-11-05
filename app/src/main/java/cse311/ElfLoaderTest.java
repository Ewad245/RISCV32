package cse311;

import java.io.IOException;
import cse311.paging.*;

/**
 * Test class for the dynamic ELF loader with Linux-like memory permissions
 */
public class ElfLoaderTest {

    public static void main(String[] args) {
        testElfLoadingWithPermissions();
    }

    public static void testElfLoadingWithPermissions() {
        System.out.println("🧪 Testing ELF Loading with Linux-like Memory Permissions...");

        try {
            // Create paged memory manager and ELF loader
            PagedMemoryManager memory = new PagedMemoryManager(1024 * 1024); // 1MB
            AddressSpace as = memory.createAddressSpace(1001);
            memory.switchTo(as);
            
            // Set up pager
            DemandPager pager = new DemandPager(memory, new ClockPolicy(memory.getTotalFrames()));
            memory.setPager(pager);
            
            ElfLoader elfLoader = new ElfLoader(memory);

            // Test with a sample ELF file
            String elfFile = "test.elf"; // Replace with actual ELF file path

            try {
                // Load ELF into address space with proper permissions (Linux-like)
                elfLoader.loadElfIntoAddressSpace(as, memory, elfFile);

                int entryPoint = elfLoader.getEntryPoint();
                System.out.println("✅ ELF loaded successfully with memory permissions!");
                System.out.println("🎯 Entry point: 0x" + Integer.toHexString(entryPoint));

                // Display loaded segments with permissions
                System.out.println("\n📋 Loaded Segments:");
                for (ElfLoader.ElfSegment segment : elfLoader.getSegments()) {
                    System.out.println("  " + segment);
                    
                    // Show permission mapping
                    String perms = "";
                    if (segment.readable) perms += "R";
                    if (segment.writable) perms += "W"; 
                    if (segment.executable) perms += "X";
                    
                    System.out.println("    Permissions: " + perms);
                }

                // Test memory access
                int vpn = AddressSpace.getVPN(entryPoint);
                if (as.isPagePresent(vpn)) {
                    int frame = as.getFrameNumber(vpn);
                    System.out.println("\n✅ Page mapped: VPN=" + vpn + " → Frame=" + frame);
                    
                    try {
                        byte data = memory.readByte(entryPoint);
                        System.out.println("✅ Memory access working at entry point");
                    } catch (Exception e) {
                        System.out.println("⚠️ Memory access issue: " + e.getMessage());
                    }
                }

            } catch (IOException e) {
                System.out.println("📁 Could not load ELF file (expected for demo): " + e.getMessage());
                System.out.println("   To test with real ELF: compile a RISC-V program");
                
                // Demonstrate with mock data
                demonstrateMockLoading(as, memory, elfLoader);
            } catch (ElfException e) {
                System.out.println("❌ ELF loading error: " + e.getMessage());
            }

        } catch (Exception e) {
            System.out.println("Test failed with exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void demonstrateMockLoading(AddressSpace as, PagedMemoryManager memory, ElfLoader loader) {
        System.out.println("\n🎭 Demonstrating mock ELF loading with permissions...");
        
        // Simulate loading segments with different permissions
        System.out.println("Simulating Linux-like segment loading:");
        
        // Code segment (RX)
        ElfLoader.ElfSegment codeSegment = new ElfLoader.ElfSegment(
            0x10000,   // virtualAddr
            0x1000,    // fileSize
            0x1000,    // memorySize
            0x0,       // fileOffset
            true,      // readable
            false,     // writable
            true       // executable
        );
        
        // Data segment (RW)
        ElfLoader.ElfSegment dataSegment = new ElfLoader.ElfSegment(
            0x20000,   // virtualAddr
            0x800,     // fileSize
            0x1000,    // memorySize
            0x1000,    // fileOffset
            true,      // readable
            true,      // writable
            false      // executable
        );
        
        // Stack segment (RW, non-executable)
        ElfLoader.ElfSegment stackSegment = new ElfLoader.ElfSegment(
            0x7FFFF000, // virtualAddr (top of user space)
            0x0,        // fileSize (stack is zero-initialized)
            0x1000,     // memorySize (4KB stack)
            0x0,        // fileOffset
            true,       // readable
            true,       // writable
            false       // executable (stack must be non-executable)
        );
        
        // Mock the segments list
        loader.getSegments().add(codeSegment);
        loader.getSegments().add(dataSegment);
        loader.getSegments().add(stackSegment);
        
        System.out.println("\n📋 Mock segments loaded:");
        for (ElfLoader.ElfSegment seg : loader.getSegments()) {
            String perms = (seg.readable ? "r" : "-") + 
                          (seg.writable ? "w" : "-") + 
                          (seg.executable ? "x" : "-");
            System.out.printf("  0x%08x-0x%08x %s\n", 
                seg.virtualAddr, seg.virtualAddr + seg.memorySize, perms);
        }
        
        // Mock entry point
        System.out.println("\n🎯 Mock entry point: 0x10000");
        System.out.println("✅ Mock loading complete!");
    }
    
    private static void testVirtualAddressTranslation(MemoryManager memory) {
        System.out.println("\nTesting virtual address translation:");

        // Test various addresses
        int[] testAddresses = {
                0x00000000, // Low address
                0x80000000, // High address (typical RISC-V kernel load address)
                0x10000000, // UART base
                0x7FFFFFFF // Just below high addresses
        };

        for (int addr : testAddresses) {
            try {
                // Try to write and read a test byte
                memory.writeByteToVirtualAddress(addr, (byte) 0x42);
                byte value = memory.readByte(addr);
                System.out.println("Address 0x" + Integer.toHexString(addr) +
                        ": Write/Read successful, value = 0x" + Integer.toHexString(value & 0xFF));
            } catch (MemoryAccessException e) {
                System.out.println("Address 0x" + Integer.toHexString(addr) +
                        ": " + e.getMessage());
            }
        }
    }
}