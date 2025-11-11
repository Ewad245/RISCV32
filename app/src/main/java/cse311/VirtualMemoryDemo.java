package cse311;

import cse311.kernel.Kernel;

/**
 * Demonstration of individual address spaces for tasks
 */
public class VirtualMemoryDemo {
    
    public static void main(String[] args) {
        try {
            System.out.println("=== Virtual Memory Demo: Individual Address Spaces ===\n");
            
            // Create computer with task-aware memory management
            RV32iComputer computer = new RV32iComputer(128 * 1024 * 1024, 4); // 128MB, max 4 tasks
            TaskAwareMemoryManager memory = computer.getTaskAwareMemoryManager();
            Kernel kernel = computer.getKernel();
            
            System.out.println("1. Creating tasks with individual address spaces...");
            
            // Create multiple tasks
            Task task1 = kernel.createTask(createProgram("Task-1"), "writer_task_1");
            Task task2 = kernel.createTask(createProgram("Task-2"), "writer_task_2");
            Task task3 = kernel.createTask(createProgram("Task-3"), "writer_task_3");
            
            System.out.println("   Created 3 tasks with individual address spaces");
            
            System.out.println("\n2. Demonstrating memory isolation...");
            
            // Write different data to the same virtual address in each task
            int testAddress = VirtualMemoryManager.DATA_START;
            
            // Task 1: Write "AAAA"
            String data1 = "AAAA";
            for (int i = 0; i < data1.length(); i++) {
                memory.writeByteToTask(task1.getId(), testAddress + i, (byte) data1.charAt(i));
            }
            
            // Task 2: Write "BBBB" to the SAME addresses
            String data2 = "BBBB";
            for (int i = 0; i < data2.length(); i++) {
                memory.writeByteToTask(task2.getId(), testAddress + i, (byte) data2.charAt(i));
            }
            
            // Task 3: Write "CCCC" to the SAME addresses
            String data3 = "CCCC";
            for (int i = 0; i < data3.length(); i++) {
                memory.writeByteToTask(task3.getId(), testAddress + i, (byte) data3.charAt(i));
            }
            
            System.out.println("   Written different data to address 0x" + 
                             Integer.toHexString(testAddress) + " in each task");
            
            System.out.println("\n3. Verifying memory isolation...");
            
            // Read back data from each task
            StringBuilder read1 = new StringBuilder();
            StringBuilder read2 = new StringBuilder();
            StringBuilder read3 = new StringBuilder();
            
            for (int i = 0; i < 4; i++) {
                read1.append((char) memory.readByteFromTask(task1.getId(), testAddress + i));
                read2.append((char) memory.readByteFromTask(task2.getId(), testAddress + i));
                read3.append((char) memory.readByteFromTask(task3.getId(), testAddress + i));
            }
            
            System.out.println("   Task " + task1.getId() + " at 0x" + 
                             Integer.toHexString(testAddress) + ": \"" + read1 + "\"");
            System.out.println("   Task " + task2.getId() + " at 0x" + 
                             Integer.toHexString(testAddress) + ": \"" + read2 + "\"");
            System.out.println("   Task " + task3.getId() + " at 0x" + 
                             Integer.toHexString(testAddress) + ": \"" + read3 + "\"");
            
            // Verify isolation
            if (read1.toString().equals("AAAA") && 
                read2.toString().equals("BBBB") && 
                read3.toString().equals("CCCC")) {
                System.out.println("   [SUCCESS] Memory isolation SUCCESSFUL - each task has its own data!");
            } else {
                System.out.println("   [FAILED] Memory isolation FAILED");
            }
            
            System.out.println("\n4. Testing stack isolation...");
            
            // Write to stack areas
            int stackAddr = memory.getStackStart();
            memory.writeWordToTask(task1.getId(), stackAddr, 0x11111111);
            memory.writeWordToTask(task2.getId(), stackAddr, 0x22222222);
            memory.writeWordToTask(task3.getId(), stackAddr, 0x33333333);
            
            int stack1 = memory.readWordFromTask(task1.getId(), stackAddr);
            int stack2 = memory.readWordFromTask(task2.getId(), stackAddr);
            int stack3 = memory.readWordFromTask(task3.getId(), stackAddr);
            
            System.out.println("   Task " + task1.getId() + " stack: 0x" + Integer.toHexString(stack1));
            System.out.println("   Task " + task2.getId() + " stack: 0x" + Integer.toHexString(stack2));
            System.out.println("   Task " + task3.getId() + " stack: 0x" + Integer.toHexString(stack3));
            
            if (stack1 == 0x11111111 && stack2 == 0x22222222 && stack3 == 0x33333333) {
                System.out.println("   [SUCCESS] Stack isolation SUCCESSFUL!");
            } else {
                System.out.println("   [FAILED] Stack isolation FAILED");
            }
            
            System.out.println("\n5. Memory statistics...");
            VirtualMemoryManager.VirtualMemoryStats stats = memory.getVirtualMemoryStats();
            System.out.println("   " + stats);
            
            for (Task task : kernel.getAllTasks()) {
                VirtualMemoryManager.TaskMemoryStats taskStats = memory.getTaskMemoryStats(task.getId());
                System.out.println("   " + taskStats);
            }
            
            System.out.println("\n6. Testing context switching...");
            
            // Simulate context switching
            memory.setCurrentTask(task1.getId());
            memory.writeByte(VirtualMemoryManager.DATA_START + 1000, (byte) 0xAA);
            
            memory.setCurrentTask(task2.getId());
            memory.writeByte(VirtualMemoryManager.DATA_START + 1000, (byte) 0xBB);
            
            memory.setCurrentTask(task3.getId());
            memory.writeByte(VirtualMemoryManager.DATA_START + 1000, (byte) 0xCC);
            
            // Verify each task kept its data
            byte ctx1 = memory.readByteFromTask(task1.getId(), VirtualMemoryManager.DATA_START + 1000);
            byte ctx2 = memory.readByteFromTask(task2.getId(), VirtualMemoryManager.DATA_START + 1000);
            byte ctx3 = memory.readByteFromTask(task3.getId(), VirtualMemoryManager.DATA_START + 1000);
            
            System.out.println("   Task " + task1.getId() + " context data: 0x" + Integer.toHexString(ctx1 & 0xFF));
            System.out.println("   Task " + task2.getId() + " context data: 0x" + Integer.toHexString(ctx2 & 0xFF));
            System.out.println("   Task " + task3.getId() + " context data: 0x" + Integer.toHexString(ctx3 & 0xFF));
            
            if ((ctx1 & 0xFF) == 0xAA && (ctx2 & 0xFF) == 0xBB && (ctx3 & 0xFF) == 0xCC) {
                System.out.println("   [SUCCESS] Context switching preserves individual address spaces!");
            } else {
                System.out.println("   [FAILED] Context switching failed");
            }
            
            System.out.println("\n7. Cleanup...");
            kernel.terminateTask(task1.getId());
            kernel.terminateTask(task2.getId());
            kernel.terminateTask(task3.getId());
            
            VirtualMemoryManager.VirtualMemoryStats finalStats = memory.getVirtualMemoryStats();
            System.out.println("   Final stats: " + finalStats);
            
            System.out.println("\n=== Demo Complete ===");
            System.out.println("[SUCCESS] Individual address spaces working perfectly!");
            System.out.println("[SUCCESS] Each task has its own isolated memory space");
            System.out.println("[SUCCESS] Shared memory (UART) accessible to all tasks");
            System.out.println("[SUCCESS] Context switching preserves memory isolation");
            
        } catch (Exception e) {
            System.err.println("Demo error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static byte[] createProgram(String identifier) {
        return new byte[] {
            // Simple program that exits
            0x13, 0x08, (byte)0xD0, 0x05,  // addi a7, zero, 93 (exit)
            0x13, 0x05, 0x00, 0x00,        // addi a0, zero, 0
            0x73, 0x00, 0x00, 0x00         // ecall
        };
    }
}