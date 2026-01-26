package cse311;

import cse311.kernel.process.Task;

/**
 * Simple test to verify paging implementation
 */
public class PagingTest {
    public static void main(String[] args) {
        try {
            cse311.Logger.FileLogger.log("=== Testing Paging Implementation ===");

            // Create computer with paging
            RV32Computer computer = new RV32Computer(64 * 1024 * 1024); // 64MB

            // Create a simple program
            byte[] simpleProgram = {
                    // li a7, 93 (exit syscall)
                    0x13, 0x08, (byte) 0xD0, 0x05,
                    // li a0, 0 (exit code)
                    0x13, 0x05, 0x00, 0x00,
                    // ecall
                    0x73, 0x00, 0x00, 0x00
            };

            // Create task
            Task task = computer.createTask(simpleProgram, "test_paging");

            if (task != null) {
                cse311.Logger.FileLogger.log("✓ Successfully created task with paging");
                cse311.Logger.FileLogger.log("Task ID: " + task.getId());
                cse311.Logger.FileLogger.log("Task Name: " + task.getName());
                cse311.Logger.FileLogger.log("Entry Point: 0x" + Integer.toHexString(task.getProgramCounter()));
                cse311.Logger.FileLogger.log("Stack Base: 0x" + Integer.toHexString(task.getStackBase()));

                // Test that address space was created
                if (task.getMemoryContext() != null) {
                    cse311.Logger.FileLogger.log("✓ Address space created successfully");
                } else {
                    cse311.Logger.FileLogger.log("⚠ Address space not found (may be using legacy memory manager)");
                }

            } else {
                cse311.Logger.FileLogger.log("✗ Failed to create task");
            }

            cse311.Logger.FileLogger.log("=== Paging Test Complete ===");

        } catch (Exception e) {
            cse311.Logger.FileLogger.log("Error during paging test: " + e.getMessage());
            cse311.Logger.FileLogger.log(e);
        }
    }
}