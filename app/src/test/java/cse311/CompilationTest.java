package cse311;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to verify compilation works
 */
public class CompilationTest {
    
    @Test
    void testBasicCompilation() {
        // Test that all classes can be instantiated
        RV32iComputer computer = new RV32iComputer(64 * 1024 * 1024, 4);
        assertNotNull(computer);
        assertNotNull(computer.getCpu());
        assertNotNull(computer.getMemoryManager());
        assertNotNull(computer.getKernel());
        
        TaskAwareMemoryManager taskMemory = computer.getTaskAwareMemoryManager();
        assertNotNull(taskMemory);
        
        // Test constants are accessible
        assertTrue(VirtualMemoryManager.TEXT_START > 0);
        assertTrue(VirtualMemoryManager.DATA_START > 0);
        assertTrue(VirtualMemoryManager.HEAP_START > 0);
        assertTrue(taskMemory.getStackStart() > 0);
        assertTrue(VirtualMemoryManager.STACK_SIZE > 0);
        assertTrue(VirtualMemoryManager.UART_BASE > 0);
        assertTrue(VirtualMemoryManager.UART_SIZE > 0);
        
        System.out.println("All classes compile and instantiate correctly");
    }
}