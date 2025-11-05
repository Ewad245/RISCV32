package cse311;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for RV32iComputer with the new kernel architecture
 */
public class RV32iComputerIntegrationTest {
    
    private RV32iComputer computer;
    
    @BeforeEach
    void setUp() {
        computer = new RV32iComputer(64 * 1024 * 1024); // 64MB
    }
    
    @Test
    void testComputerInitialization() {
        assertNotNull(computer);
        assertNotNull(computer.getCpu());
        assertNotNull(computer.getMemoryManager());
        assertNotNull(computer.getKernel());
    }
    
    @Test
    void testTaskCreationWithEntryPoint() {
        // Test creating a task with an entry point
        Task task = computer.createTask(0x1000);
        
        assertNotNull(task);
        assertTrue(task.getId() > 0);
        assertEquals(TaskState.READY, task.getState());
    }
    
    @Test
    void testTaskCreationWithElfData() {
        // Test creating a task with ELF data
        byte[] simpleProgram = createSimpleExitProgram();
        Task task = computer.createTask(simpleProgram, "test_program");
        
        assertNotNull(task);
        assertEquals("test_program", task.getName());
        assertEquals(TaskState.READY, task.getState());
    }
    
    @Test
    void testKernelIntegration() {
        // Verify that the computer properly integrates with the kernel
        Task task1 = computer.createTask(0x1000);
        Task task2 = computer.createTask(createSimpleExitProgram(), "test");
        
        // Both tasks should be managed by the kernel
        assertTrue(computer.getKernel().getAllTasks().contains(task1));
        assertTrue(computer.getKernel().getAllTasks().contains(task2));
        
        // Kernel should have 2 tasks
        assertEquals(2, computer.getKernel().getAllTasks().size());
    }
    
    @Test
    void testComputerToString() {
        String str = computer.toString();
        assertNotNull(str);
        assertTrue(str.contains("RV32iComputer"));
        assertTrue(str.contains("cpu="));
        assertTrue(str.contains("memory="));
        assertTrue(str.contains("kernel="));
    }
    
    private byte[] createSimpleExitProgram() {
        return new byte[] {
            // li a7, 93 (exit syscall)
            0x13, 0x08, (byte)0xD0, 0x05,  // addi a7, zero, 93
            // li a0, 0 (exit code)
            0x13, 0x05, 0x00, 0x00,        // addi a0, zero, 0
            // ecall
            0x73, 0x00, 0x00, 0x00         // ecall
        };
    }
}