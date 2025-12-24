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
            System.out.println("=== System Call Integration Demo ===\n");

            // Create memory and CPU
            SimpleMemory simpleMemory = new SimpleMemory(64 * 1024 * 1024);
            MemoryManager memory = new MemoryManager(simpleMemory);
            RV32iCpu cpu = new RV32iCpu(memory);

            // Create kernel
            Kernel kernel = new Kernel(cpu, memory);

            // Configure for cooperative scheduling to see system calls clearly
            kernel.getConfig().setSchedulerType(KernelConfig.SchedulerType.COOPERATIVE);

            System.out.println("1. Testing ECALL detection...");
            testEcallDetection(cpu);

            System.out.println("\n2. Testing system call handling...");
            testSystemCallHandling(kernel, cpu, memory);

            System.out.println("\n3. Testing kernel integration...");
            testKernelIntegration(kernel);

            System.out.println("\n=== Demo Complete ===");

        } catch (Exception e) {
            System.err.println("Demo error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testEcallDetection(RV32iCpu cpu) {
        System.out.println("   - Resetting CPU flags");
        cpu.resetFlags();

        System.out.println("   - Initial ECALL state: " + cpu.isEcall());

        System.out.println("   - Executing ECALL instruction (0x73)");
        cpu.testExecuteInstruction(0x00000073); // ECALL instruction

        System.out.println("   - ECALL detected: " + cpu.isEcall());
        System.out.println("   - ECALL flag after check: " + cpu.isEcall() + " (should be false - auto-reset)");
    }

    private static void testSystemCallHandling(Kernel kernel, RV32iCpu cpu, MemoryManager memory) throws Exception {
        System.out.println("   - Creating test task");
        Task task = new Task(1, "test_task", 0x1000, 4096, 0x7000, null);

        System.out.println("   - Setting up write system call");
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

        System.out.println("   - Executing ECALL");
        cpu.resetFlags();
        cpu.testExecuteInstruction(0x00000073); // ECALL

        if (cpu.isEcall()) {
            System.out.println("   - ECALL detected, handling system call");
            kernel.getSystemCallHandler().handleSystemCall(task);
            System.out.println("   - System call completed, return value: " + task.getRegisters()[10]);
        } else {
            System.out.println("   - ERROR: ECALL not detected!");
        }
    }

    private static void testKernelIntegration(Kernel kernel) throws Exception {
        System.out.println("   - Creating task with exit program");
        byte[] exitProgram = createExitProgram();
        Task task = kernel.createTask(exitProgram, "exit_demo");

        System.out.println("   - Task created: " + task.getName() + " (PID: " + task.getId() + ")");
        System.out.println("   - Task state: " + task.getState());

        // Simulate one execution cycle
        System.out.println("   - Simulating kernel execution cycle...");

        // The kernel would normally handle this in its main loop
        // Here we'll simulate what happens when a task makes a system call
        task.setState(TaskState.RUNNING);
        task.restoreState(kernel.getCpu());

        // Execute one instruction (should be the exit syscall setup)
        try {
            kernel.getCpu().step();
            if (kernel.getCpu().isEcall()) {
                System.out.println("   - System call detected by kernel");
                kernel.getSystemCallHandler().handleSystemCall(task);
                System.out.println("   - Task state after system call: " + task.getState());
            }
        } catch (Exception e) {
            System.out.println("   - Execution completed or encountered issue: " + e.getMessage());
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