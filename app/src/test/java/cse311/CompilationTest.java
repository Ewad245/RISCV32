package cse311;

import org.junit.jupiter.api.Test;

import cse311.Enum.MemoryMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to verify compilation works
 */
public class CompilationTest {

    @Test
    void testBasicCompilation() {
        // Test that all classes can be instantiated
        RV32Computer computer = new RV32Computer(64 * 1024 * 1024, 4, MemoryMode.PAGING);
        assertNotNull(computer);
        assertNotNull(computer.getCpu());
        assertNotNull(computer.getMemoryManager());
        assertNotNull(computer.getKernel());

        // Test constants are accessible
        assertTrue(VirtualMemoryManager.TEXT_START > 0);
        assertTrue(VirtualMemoryManager.DATA_START > 0);
        assertTrue(VirtualMemoryManager.HEAP_START > 0);
        assertTrue(VirtualMemoryManager.STACK_SIZE > 0);
        assertTrue(VirtualMemoryManager.UART_BASE > 0);
        assertTrue(VirtualMemoryManager.UART_SIZE > 0);

        System.out.println("All classes compile and instantiate correctly");
    }
}