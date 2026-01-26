package cse311;

import cse311.kernel.*;
import cse311.kernel.process.Task;
import cse311.kernel.process.TaskState;
import cse311.kernel.syscall.SystemCallHandler;

/**
 * Demonstration of system call integration between CPU and Kernel
 */
public class SystemCallDemo {

    public static void main(String[] args) {
        try {
            cse311.Logger.FileLogger.log("=== System Call Integration Demo ===\n");

            // Create memory
            SimpleMemory simpleMemory = new SimpleMemory(64 * 1024 * 1024);
            MemoryManager memory = new MemoryManager(simpleMemory);

            // Create kernel (which creates CPU)
            Kernel kernel = new Kernel(memory);
            RV32Cpu cpu = kernel.getCpu(); // Use BSP

            // Configure for cooperative scheduling to see system calls clearly
            kernel.getConfig().setSchedulerType(KernelConfig.SchedulerType.COOPERATIVE);

            cse311.Logger.FileLogger.log("1. Testing ECALL detection...");
            testEcallDetection(cpu);

            cse311.Logger.FileLogger.log("\n2. Testing system call handling...");
            testSystemCallHandling(kernel, cpu, memory);

            cse311.Logger.FileLogger.log("\n3. Testing kernel integration...");
            testKernelIntegration(kernel);

            cse311.Logger.FileLogger.log("\n=== Demo Complete ===");

        } catch (Exception e) {
            cse311.Logger.FileLogger.log("Demo error: " + e.getMessage());
            cse311.Logger.FileLogger.log(e);
        }
    }

    private static void testEcallDetection(RV32Cpu cpu) {
        cse311.Logger.FileLogger.log("   - Resetting CPU flags");
        cpu.resetFlags();

        cse311.Logger.FileLogger.log("   - Initial ECALL state: " + cpu.isEcall());

        cse311.Logger.FileLogger.log("   - Executing ECALL instruction (0x73)");
        cpu.testExecuteInstruction(0x00000073); // ECALL instruction

        cse311.Logger.FileLogger.log("   - ECALL detected: " + cpu.isEcall());
        cse311.Logger.FileLogger
                .log("   - ECALL flag after check: " + cpu.isEcall() + " (should be false - auto-reset)");
    }

    private static void testSystemCallHandling(Kernel kernel, RV32Cpu cpu, MemoryManager memory) throws Exception {
        cse311.Logger.FileLogger.log("   - Creating test task");
        Task task = new Task(1, "test_task", 0x1000, 4096, 0x7000, null);

        cse311.Logger.FileLogger.log("   - Setting up write system call");
        // Set up write system call: write("Hello", 5) to stdout
        task.getRegisters()[17] = SystemCallHandler.SYS_WRITE; // a7 = write
        task.getRegisters()[10] = 1; // a0 = stdout
        task.getRegisters()[11] = 0x2000; // a1 = buffer address
        task.getRegisters()[12] = 5; // a2 = count

        // Write test data to memory
        String testData = "Hello";
        for (int i = 0; i < testData.length(); i++) {
            memory.writeByte(0x2000 + i, (byte) testData.charAt(i));
        }

        cse311.Logger.FileLogger.log("   - Executing ECALL");
        cpu.resetFlags();
        cpu.testExecuteInstruction(0x00000073); // ECALL

        if (cpu.isEcall()) {
            cse311.Logger.FileLogger.log("   - ECALL detected, handling system call");
            kernel.getSystemCallHandler().handleSystemCall(task, cpu);
            cse311.Logger.FileLogger.log("   - System call completed, return value: " + task.getRegisters()[10]);
        } else {
            cse311.Logger.FileLogger.log("   - ERROR: ECALL not detected!");
        }
    }

    private static void testKernelIntegration(Kernel kernel) throws Exception {
        cse311.Logger.FileLogger.log("   - Creating task with exit program");
        byte[] exitProgram = createExitProgram();
        Task task = kernel.createTask(exitProgram, "exit_demo");

        cse311.Logger.FileLogger.log("   - Task created: " + task.getName() + " (PID: " + task.getId() + ")");
        cse311.Logger.FileLogger.log("   - Task state: " + task.getState());

        // Simulate one execution cycle
        cse311.Logger.FileLogger.log("   - Simulating kernel execution cycle...");

        // The kernel would normally handle this in its main loop
        // Here we'll simulate what happens when a task makes a system call
        task.setState(TaskState.RUNNING);
        task.restoreState(kernel.getCpu());

        // Execute one instruction (should be the exit syscall setup)
        try {
            kernel.getCpu().step();
            if (kernel.getCpu().isEcall()) {
                cse311.Logger.FileLogger.log("   - System call detected by kernel");
                kernel.getSystemCallHandler().handleSystemCall(task, kernel.getCpu());
                cse311.Logger.FileLogger.log("   - Task state after system call: " + task.getState());
            }
        } catch (Exception e) {
            cse311.Logger.FileLogger.log("   - Execution completed or encountered issue: " + e.getMessage());
        }
    }

    private static byte[] createExitProgram() {
        return new byte[] {
                // li a7, 93 (exit syscall)
                0x13, 0x08, (byte) 0xD0, 0x05, // addi a7, zero, 93
                // li a0, 0 (exit code)
                0x13, 0x05, 0x00, 0x00, // addi a0, zero, 0
                // ecall
                0x73, 0x00, 0x00, 0x00 // ecall
        };
    }
}