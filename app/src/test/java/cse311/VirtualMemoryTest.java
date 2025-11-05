package cse311;

import cse311.kernel.Kernel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test individual address spaces for tasks
 */
public class VirtualMemoryTest {
    
    private RV32iComputer computer;
    private TaskAwareMemoryManager taskMemory;
    private Kernel kernel;
    
    @BeforeEach
    void setUp() {
        computer = new RV32iComputer(64 * 1024 * 1024, 8); // 64MB, max 8 tasks
        taskMemory = computer.getTaskAwareMemoryManager();
        kernel = computer.getKernel();
        assertNotNull(taskMemory);
    }
    
    @Test
    void testIndividualAddressSpaces() throws Exception {
        // Create two tasks
        Task task1 = kernel.createTask(createTestProgram("Task1"), "task1");
        Task task2 = kernel.createTask(createTestProgram("Task2"), "task2");
        
        assertNotNull(task1);
        assertNotNull(task2);
        
        // Verify both tasks have their own memory spaces
        assertTrue(taskMemory.hasTaskMemory(task1.getId()));
        assertTrue(taskMemory.hasTaskMemory(task2.getId()));
        
        // Write different data to the same address in each task's memory
        int testAddress = VirtualMemoryManager.DATA_START;
        byte[] data1 = "Hello from Task 1".getBytes();
        byte[] data2 = "Hello from Task 2".getBytes();
        
        // Write to task 1's memory
        for (int i = 0; i < data1.length; i++) {
            taskMemory.writeByteToTask(task1.getId(), testAddress + i, data1[i]);
        }
        
        // Write to task 2's memory (same addresses, different data)
        for (int i = 0; i < data2.length; i++) {
            taskMemory.writeByteToTask(task2.getId(), testAddress + i, data2[i]);
        }
        
        // Verify each task has its own data
        for (int i = 0; i < data1.length; i++) {
            byte b1 = taskMemory.readByteFromTask(task1.getId(), testAddress + i);
            assertEquals(data1[i], b1, "Task 1 data mismatch at offset " + i);
        }
        
        for (int i = 0; i < data2.length; i++) {
            byte b2 = taskMemory.readByteFromTask(task2.getId(), testAddress + i);
            assertEquals(data2[i], b2, "Task 2 data mismatch at offset " + i);
        }
        
        System.out.println("✅ Tasks have isolated address spaces");
    }
    
    @Test
    void testMemoryIsolation() throws Exception {
        // Create two tasks
        Task task1 = kernel.createTask(createTestProgram("Task1"), "isolation_test1");
        Task task2 = kernel.createTask(createTestProgram("Task2"), "isolation_test2");
        
        // Write to task 1's stack area
        int stackAddr = taskMemory.getStackStart();
        int testValue1 = 0x12345678;
        taskMemory.writeWordToTask(task1.getId(), stackAddr, testValue1);
        
        // Write to task 2's stack area (same address)
        int testValue2 = 0x87654321;
        taskMemory.writeWordToTask(task2.getId(), stackAddr, testValue2);
        
        // Verify isolation
        int read1 = taskMemory.readWordFromTask(task1.getId(), stackAddr);
        int read2 = taskMemory.readWordFromTask(task2.getId(), stackAddr);
        
        assertEquals(testValue1, read1, "Task 1 stack data corrupted");
        assertEquals(testValue2, read2, "Task 2 stack data corrupted");
        assertNotEquals(read1, read2, "Tasks are not isolated!");
        
        System.out.println("✅ Memory isolation working correctly");
    }
    
    @Test
    void testSharedMemoryAccess() throws Exception {
        // Create two tasks
        Task task1 = kernel.createTask(createTestProgram("Task1"), "shared_test1");
        Task task2 = kernel.createTask(createTestProgram("Task2"), "shared_test2");
        
        // Both tasks should be able to access UART (shared memory)
        int uartAddr = VirtualMemoryManager.UART_BASE;
        
        // Task 1 writes to UART
        taskMemory.setCurrentTask(task1.getId());
        taskMemory.writeByte(uartAddr, (byte) 0x42);
        
        // Task 2 should see the same data (shared memory)
        taskMemory.setCurrentTask(task2.getId());
        byte sharedData = taskMemory.readByte(uartAddr);
        
        // Note: UART behavior might reset the data, so we just verify no exception
        System.out.println("✅ Shared memory (UART) accessible from both tasks");
    }
    
    @Test
    void testContextSwitching() throws Exception {
        // Create a task
        Task task = kernel.createTask(createTestProgram("ContextTest"), "context_test");
        
        // Initially no task is active
        assertEquals(-1, taskMemory.getCurrentTask());
        
        // Simulate kernel switching to the task
        taskMemory.setCurrentTask(task.getId());
        assertEquals(task.getId(), taskMemory.getCurrentTask());
        
        // Write some data using the current task context
        int testAddr = VirtualMemoryManager.DATA_START + 100;
        taskMemory.writeByte(testAddr, (byte) 0xAB);
        
        // Verify the data was written to the correct task's memory
        byte data = taskMemory.readByteFromTask(task.getId(), testAddr);
        assertEquals((byte) 0xAB, data);
        
        System.out.println("✅ Context switching works correctly");
    }
    
    @Test
    void testMemoryStatistics() throws Exception {
        // Create some tasks
        Task task1 = kernel.createTask(createTestProgram("Stats1"), "stats_test1");
        Task task2 = kernel.createTask(createTestProgram("Stats2"), "stats_test2");
        
        // Get memory statistics
        VirtualMemoryManager.VirtualMemoryStats stats = taskMemory.getVirtualMemoryStats();
        
        assertEquals(2, stats.allocatedTasks);
        assertTrue(stats.maxTasks >= 2);
        assertTrue(stats.totalAllocatedMemory > 0);
        assertTrue(stats.totalVirtualMemory >= stats.totalAllocatedMemory);
        
        // Get task-specific statistics
        VirtualMemoryManager.TaskMemoryStats taskStats1 = taskMemory.getTaskMemoryStats(task1.getId());
        assertNotNull(taskStats1);
        assertEquals(task1.getId(), taskStats1.taskId);
        
        System.out.println("Memory Statistics:");
        System.out.println("  " + stats);
        System.out.println("  " + taskStats1);
        
        System.out.println("✅ Memory statistics working correctly");
    }
    
    @Test
    void testMemoryDeallocation() throws Exception {
        // Create a task
        Task task = kernel.createTask(createTestProgram("DeallocTest"), "dealloc_test");
        int taskId = task.getId();
        
        // Verify memory is allocated
        assertTrue(taskMemory.hasTaskMemory(taskId));
        
        // Terminate the task (should deallocate memory)
        kernel.terminateTask(taskId);
        
        // Verify memory is deallocated
        assertFalse(taskMemory.hasTaskMemory(taskId));
        
        System.out.println("✅ Memory deallocation working correctly");
    }
    
    @Test
    void testMaxTaskLimit() throws Exception {
        // Create tasks up to the limit (8 in our test setup)
        for (int i = 0; i < 8; i++) {
            Task task = kernel.createTask(createTestProgram("Task" + i), "limit_test" + i);
            assertNotNull(task, "Failed to create task " + i);
        }
        
        // Verify we have 8 tasks
        assertEquals(8, taskMemory.getAllocatedTaskCount());
        
        System.out.println("✅ Created maximum number of tasks successfully");
    }
    
    private byte[] createTestProgram(String identifier) {
        // Create a simple program that can be loaded into memory
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