package cse311;

import cse311.kernel.process.Task;

/**
 * Simple test to verify paging implementation
 */
public class PagingTest {
    public static void main(String[] args) {
        try {
            System.out.println("=== Testing Paging Implementation ===");

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
                System.out.println("✓ Successfully created task with paging");
                System.out.println("Task ID: " + task.getId());
                System.out.println("Task Name: " + task.getName());
                System.out.println("Entry Point: 0x" + Integer.toHexString(task.getProgramCounter()));
                System.out.println("Stack Base: 0x" + Integer.toHexString(task.getStackBase()));

                // Test that address space was created
                if (task.getMemoryContext() != null) {
                    System.out.println("✓ Address space created successfully");
                } else {
                    System.out.println("⚠ Address space not found (may be using legacy memory manager)");
                }

            } else {
                System.out.println("✗ Failed to create task");
            }

            System.out.println("=== Paging Test Complete ===");

        } catch (Exception e) {
            System.err.println("Error during paging test: " + e.getMessage());
            e.printStackTrace();
        }
    }
}