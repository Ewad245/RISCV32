package cse311;

import cse311.kernel.*;
import cse311.kernel.process.Task;

/**
 * Example demonstrating how to use the Java kernel with RV32IM CPU emulator
 */
public class KernelExample {

    public static void main(String[] args) {
        try {
            // Create memory and CPU
            SimpleMemory simpleMemory = new SimpleMemory(128 * 1024 * 1024); // 128MB
            MemoryManager memory = new MemoryManager(simpleMemory);
            RV32Cpu cpu = new RV32Cpu(memory);

            // Create kernel
            Kernel kernel = new Kernel(cpu, memory);

            // Configure kernel for cooperative scheduling
            kernel.getConfig().setSchedulerType(KernelConfig.SchedulerType.COOPERATIVE);
            kernel.getConfig().setTimeSlice(500);

            // Create some example tasks
            createExampleTasks(kernel);

            // Print initial status
            kernel.printStatus();

            // Start the kernel (this will run until all processes finish)
            System.out.println("Starting kernel...");
            kernel.start();

            // Print final status
            System.out.println("Kernel finished");
            kernel.printStatus();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createExampleTasks(Kernel kernel) throws Exception {
        // Example 1: Simple program that prints and exits
        byte[] program1 = createSimpleProgram("Hello from Task 1!");
        Task task1 = kernel.createTask(program1, "hello1");

        // Example 2: Program that yields and prints
        byte[] program2 = createYieldingProgram("Task 2 yielding");
        Task task2 = kernel.createTask(program2, "yielder");

        // Example 3: Program that sleeps
        byte[] program3 = createSleepingProgram("Task 3 sleeping", 1000);
        Task task3 = kernel.createTask(program3, "sleeper");

        System.out.println("Created " + kernel.getAllTasks().size() + " example tasks");
    }

    /**
     * Create a simple program that prints a message and exits
     */
    private static byte[] createSimpleProgram(String message) {
        // This is a simplified example - in reality you'd compile RISC-V assembly
        // For now, we'll create a minimal program structure

        // Simple RISC-V program:
        // 1. Load system call number for write (64) into a7
        // 2. Load file descriptor (1 = stdout) into a0
        // 3. Load message address into a1
        // 4. Load message length into a2
        // 5. Call ecall
        // 6. Load system call number for exit (93) into a7
        // 7. Load exit code (0) into a0
        // 8. Call ecall

        return new byte[] {
                // li a7, 64 (write syscall)
                0x13, 0x08, 0x00, 0x04, // addi a7, zero, 64
                // li a0, 1 (stdout)
                0x13, 0x05, 0x10, 0x00, // addi a0, zero, 1
                // li a1, message_addr (simplified - would be actual address)
                0x13, 0x05, 0x00, 0x10, // addi a1, zero, 0x1000
                // li a2, message_len
                0x13, 0x06, 0x00, 0x0A, // addi a2, zero, 10
                // ecall
                0x73, 0x00, 0x00, 0x00, // ecall
                // li a7, 93 (exit syscall)
                0x13, 0x08, (byte) 0xD0, 0x05, // addi a7, zero, 93
                // li a0, 0 (exit code)
                0x13, 0x05, 0x00, 0x00, // addi a0, zero, 0
                // ecall
                0x73, 0x00, 0x00, 0x00 // ecall
        };
    }

    /**
     * Create a program that yields periodically
     */
    private static byte[] createYieldingProgram(String message) {
        // Program that yields 3 times then exits
        return new byte[] {
                // Loop 3 times
                // li t0, 3
                0x13, 0x02, 0x30, 0x00, // addi t0, zero, 3
                // loop:
                // li a7, 124 (yield syscall)
                0x13, 0x08, (byte) 0xC0, 0x07, // addi a7, zero, 124
                // ecall
                0x73, 0x00, 0x00, 0x00, // ecall
                // addi t0, t0, -1
                0x13, (byte) 0x82, (byte) 0xF2, (byte) 0xFF, // addi t0, t0, -1
                // bne t0, zero, loop
                0x63, 0x1C, 0x02, (byte) 0xFE, // bne t0, zero, -8
                // exit
                0x13, 0x08, (byte) 0xD0, 0x05, // addi a7, zero, 93
                0x13, 0x05, 0x00, 0x00, // addi a0, zero, 0
                0x73, 0x00, 0x00, 0x00 // ecall
        };
    }

    /**
     * Create a program that sleeps for a specified time
     */
    private static byte[] createSleepingProgram(String message, int sleepMs) {
        return new byte[] {
                // li a7, 1002 (sleep syscall)
                0x13, 0x08, (byte) 0xA0, 0x3E, // addi a7, zero, 1002
                // li a0, sleepMs (simplified)
                0x13, 0x05, 0x00, 0x64, // addi a0, zero, 100
                // ecall
                0x73, 0x00, 0x00, 0x00, // ecall
                // exit
                0x13, 0x08, (byte) 0xD0, 0x05, // addi a7, zero, 93
                0x13, 0x05, 0x00, 0x00, // addi a0, zero, 0
                0x73, 0x00, 0x00, 0x00 // ecall
        };
    }
}