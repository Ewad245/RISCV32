package cse311;

import cse311.kernel.*;
import cse311.kernel.syscall.SystemCallHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify system call handling between CPU and Kernel
 */
public class SystemCallIntegrationTest {
    
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
    void testEcallDetection() {
        // Reset flags
        cpu.resetFlags();
        
        // Initially no ECALL should be detected
        assertFalse(cpu.isEcall());
        
        // Create a simple ECALL instruction (0x73 = ECALL)
        int ecallInstruction = 0x00000073;
        
        // Execute the ECALL instruction
        cpu.testExecuteInstruction(ecallInstruction);
        
        // Now ECALL should be detected
        assertTrue(cpu.isEcall());
        
        // After checking, flag should be reset
        assertFalse(cpu.isEcall());
    }
    
    @Test
    void testSystemCallIntegration() throws Exception {
        // Create a simple task that makes a system call
        byte[] program = createExitProgram();
        Task task = kernel.createTask(program, "exit_test");
        
        // Set up the task to make an exit system call
        task.getRegisters()[17] = SystemCallHandler.SYS_EXIT; // a7 = exit syscall
        task.getRegisters()[10] = 42; // a0 = exit code
        
        // Load task state into CPU (simulate context switch)
        task.restoreState(cpu);
        
        // Execute one step that should trigger ECALL
        cpu.resetFlags();
        cpu.testExecuteInstruction(0x00000073); // ECALL instruction
        
        // Verify ECALL was detected
        assertTrue(cpu.isEcall());
        
        // Simulate kernel handling the system call
        SystemCallHandler handler = kernel.getSystemCallHandler();
        handler.handleSystemCall(task, cpu);
        
        // Verify the task was terminated
        assertEquals(TaskState.TERMINATED, task.getState());
    }
    
    @Test
    void testExceptionDetection() {
        // Reset flags
        cpu.resetFlags();
        
        // Initially no exception should be detected
        assertFalse(cpu.isException());
        
        // Trigger an exception
        cpu.handleException(2, 0x1000); // Illegal instruction exception
        
        // Now exception should be detected
        assertTrue(cpu.isException());
        
        // After checking, flag should be reset
        assertFalse(cpu.isException());
    }
    
    @Test
    void testKernelSystemCallHandling() throws Exception {
        // Create a task that will make a write system call
        byte[] program = createWriteProgram();
        Task task = kernel.createTask(program, "write_test");
        
        // Set up registers for write system call
        task.getRegisters()[17] = SystemCallHandler.SYS_WRITE; // a7 = write syscall
        task.getRegisters()[10] = 1; // a0 = stdout
        task.getRegisters()[11] = 0x2000; // a1 = buffer address
        task.getRegisters()[12] = 5; // a2 = count
        
        // Write test data to task's memory space
        String testData = "Hello";
        if (memory instanceof TaskAwareMemoryManager) {
            TaskAwareMemoryManager taskMemory = (TaskAwareMemoryManager) memory;
            for (int i = 0; i < testData.length(); i++) {
                taskMemory.writeByteToTask(task.getId(), 0x2000 + i, (byte) testData.charAt(i));
            }
        } else {
            for (int i = 0; i < testData.length(); i++) {
                memory.writeByte(0x2000 + i, (byte) testData.charAt(i));
            }
        }
        
        // Load task state into CPU (simulate context switch)
        task.restoreState(cpu);
        
        // Execute ECALL instruction
        cpu.resetFlags();
        cpu.testExecuteInstruction(0x00000073); // ECALL instruction
        
        // Verify ECALL was detected
        assertTrue(cpu.isEcall());
        
        // Handle the system call through the kernel
        SystemCallHandler handler = kernel.getSystemCallHandler();
        handler.handleSystemCall(task, cpu);
        
        // Save CPU state back to task (simulate context switch)
        task.saveState(cpu);
        
        // Verify the return value was set correctly (should return 5 for 5 bytes written)
        assertEquals(5, task.getRegisters()[10]); // a0 should contain return value
    }
    
    /**
     * Create a simple program that exits
     */
    private byte[] createExitProgram() {
        return new byte[] {
            // li a7, 93 (exit syscall)
            0x13, 0x08, (byte)0xD0, 0x05,  // addi a7, zero, 93
            // li a0, 42 (exit code)
            0x13, 0x05, 0x00, 0x2A,        // addi a0, zero, 42
            // ecall
            0x73, 0x00, 0x00, 0x00         // ecall
        };
    }
    
    /**
     * Create a simple program that writes to stdout
     */
    private byte[] createWriteProgram() {
        return new byte[] {
            // li a7, 64 (write syscall)
            0x13, 0x08, 0x00, 0x04,        // addi a7, zero, 64
            // li a0, 1 (stdout)
            0x13, 0x05, 0x10, 0x00,        // addi a0, zero, 1
            // li a1, 0x2000 (buffer address)
            0x37, 0x05, 0x00, 0x20,        // lui a1, 0x20000
            // li a2, 5 (count)
            0x13, 0x06, 0x50, 0x00,        // addi a2, zero, 5
            // ecall
            0x73, 0x00, 0x00, 0x00         // ecall
        };
    }
}