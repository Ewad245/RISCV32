package cse311;

import cse311.*;
import cse311.kernel.*;
import cse311.kernel.scheduler.SchedulerStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Java kernel
 */
public class KernelIntegrationTest {
    
    private MemoryManager memory;
    private RV32iCpu cpu;
    private Kernel kernel;
    
    @BeforeEach
    void setUp() {
        // Use TaskAwareMemoryManager for individual address spaces
        memory = new TaskAwareMemoryManager(64 * 1024 * 1024, 16); // 64MB, max 16 tasks
        cpu = new RV32iCpu(memory);
        kernel = new Kernel(cpu, memory);
    }
    
    @Test
    void testKernelInitialization() {
        assertNotNull(kernel);
        assertNotNull(kernel.getCpu());
        assertNotNull(kernel.getMemory());
        assertNotNull(kernel.getScheduler());
        assertNotNull(kernel.getSystemCallHandler());
        assertNotNull(kernel.getTaskManager());
        assertNotNull(kernel.getKernelMemory());
        assertNotNull(kernel.getConfig());
    }
    
    @Test
    void testTaskCreation() throws Exception {
        byte[] testProgram = createTestProgram();
        Task task = kernel.createTask(testProgram, "test_task");
        
        assertNotNull(task);
        assertEquals("test_task", task.getName());
        assertEquals(TaskState.READY, task.getState());
        assertTrue(task.getId() > 0);
        
        // Check that task was added to kernel
        assertTrue(kernel.getAllTasks().contains(task));
    }
    
    @Test
    void testSchedulerConfiguration() {
        // Test round robin scheduler
        kernel.getConfig().setSchedulerType(KernelConfig.SchedulerType.ROUND_ROBIN);
        kernel.getConfig().setTimeSlice(500);
        
        assertEquals(KernelConfig.SchedulerType.ROUND_ROBIN, kernel.getConfig().getSchedulerType());
        assertEquals(500, kernel.getConfig().getTimeSlice());
        
        // Test cooperative scheduler
        kernel.getConfig().setSchedulerType(KernelConfig.SchedulerType.COOPERATIVE);
        assertEquals(KernelConfig.SchedulerType.COOPERATIVE, kernel.getConfig().getSchedulerType());
        
        // Test priority scheduler
        kernel.getConfig().setSchedulerType(KernelConfig.SchedulerType.PRIORITY);
        assertEquals(KernelConfig.SchedulerType.PRIORITY, kernel.getConfig().getSchedulerType());
    }
    
    @Test
    void testTaskTermination() throws Exception {
        byte[] testProgram = createTestProgram();
        Task task = kernel.createTask(testProgram, "test_task");
        int pid = task.getId();
        
        // Terminate the task
        kernel.terminateTask(pid);
        
        // Check that task is terminated
        assertEquals(TaskState.TERMINATED, task.getState());
        assertNull(kernel.getTask(pid));
    }
    
    @Test
    void testKernelStats() throws Exception {
        // Create some test tasks
        for (int i = 0; i < 3; i++) {
            kernel.createTask(createTestProgram(), "test_task_" + i);
        }
        
        KernelStats stats = kernel.getStats();
        assertEquals(3, stats.totalProcesses);
        assertEquals(3, stats.readyProcesses);
        assertEquals(0, stats.runningProcesses);
        assertEquals(0, stats.waitingProcesses);
        assertEquals(0, stats.terminatedProcesses);
    }
    
    @Test
    void testSchedulerStats() throws Exception {
        // Create a test task
        kernel.createTask(createTestProgram(), "test_task");
        
        SchedulerStats stats = kernel.getScheduler().getStats();
        assertNotNull(stats);
        assertTrue(stats.totalSchedules >= 0);
        assertTrue(stats.contextSwitches >= 0);
        assertTrue(stats.totalSchedulingTime >= 0);
        assertNotNull(stats.algorithmName);
    }
    
    @Test
    void testMemoryAllocation() throws Exception {
        // Test stack allocation
        int stackBase = kernel.getKernelMemory().allocateStack(1, 8192);
        assertTrue(stackBase > 0);
        
        // Test heap allocation
        int heapAddr = kernel.getKernelMemory().allocateHeap(1, 1024);
        assertTrue(heapAddr > 0);
        
        // Test memory stats
        var memStats = kernel.getKernelMemory().getMemoryStats();
        assertEquals(1, memStats.totalStacks);
        assertEquals(8192, memStats.totalStackMemory);
    }
    
    @Test
    void testTaskPriorities() throws Exception {
        Task t1 = kernel.createTask(createTestProgram(), "high_priority");
        Task t2 = kernel.createTask(createTestProgram(), "low_priority");
        
        t1.setPriority(10);
        t2.setPriority(1);
        
        assertEquals(10, t1.getPriority());
        assertEquals(1, t2.getPriority());
    }
    
    @Test
    void testKernelConfiguration() {
        KernelConfig config = kernel.getConfig();
        
        // Test default values
        assertEquals(KernelConfig.SchedulerType.ROUND_ROBIN, config.getSchedulerType());
        assertEquals(1000, config.getTimeSlice());
        assertEquals(64, config.getMaxProcesses());
        assertEquals(8192, config.getStackSize());
        assertTrue(config.isEnableDebugSyscalls());
        assertTrue(config.isEnableFileSyscalls());
        assertEquals(256, config.getUartBufferSize());
        
        // Test configuration changes
        config.setTimeSlice(2000);
        config.setMaxProcesses(32);
        config.setStackSize(4096);
        config.setEnableDebugSyscalls(false);
        
        assertEquals(2000, config.getTimeSlice());
        assertEquals(32, config.getMaxProcesses());
        assertEquals(4096, config.getStackSize());
        assertFalse(config.isEnableDebugSyscalls());
    }
    
    /**
     * Create a simple test program that exits immediately
     */
    private byte[] createTestProgram() {
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