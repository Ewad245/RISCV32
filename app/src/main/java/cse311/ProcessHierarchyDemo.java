package cse311;

import cse311.kernel.process.TaskManager;
import cse311.kernel.Kernel;
import cse311.kernel.memory.KernelMemoryManager;

/**
 * Demonstrates the new process hierarchy features
 */
public class ProcessHierarchyDemo {
    
    public static void main(String[] args) {
        try {
            // Create kernel components
            Kernel kernel = new Kernel();
            KernelMemoryManager kernelMemory = new KernelMemoryManager(kernel.getConfig());
            TaskManager taskManager = new TaskManager(kernel, kernelMemory);
            
            System.out.println("=== Process Hierarchy Demo ===\n");
            
            // Create init process (PID 1)
            Task init = taskManager.createTask(1, "init", "/bin/init");
            System.out.println("Created init process: " + init.getStatusString());
            
            // Create child processes
            Task shell = taskManager.createTask(2, "shell", "/bin/sh", init);
            System.out.println("Created shell process: " + shell.getStatusString());
            
            Task daemon1 = taskManager.createTask(3, "daemon1", "/usr/sbin/sshd", init);
            System.out.println("Created daemon1 process: " + daemon1.getStatusString());
            
            // Create grandchildren
            Task childProcess = taskManager.createTask(4, "child", "/bin/ls", shell);
            System.out.println("Created child process: " + childProcess.getStatusString());
            
            Task grandchild = taskManager.createTask(5, "grandchild", "/bin/cat", childProcess);
            System.out.println("Created grandchild process: " + grandchild.getStatusString());
            
            // Display process tree
            System.out.println("\n=== Process Tree ===");
            displayProcessTree(init, 0);
            
            // Demonstrate hierarchy queries
            System.out.println("\n=== Hierarchy Queries ===");
            System.out.println("Init process has " + init.getChildren().size() + " children");
            System.out.println("Shell process has " + shell.getChildren().size() + " children");
            System.out.println("Shell parent PID: " + shell.getParent().getId());
            
            // Check descendants
            java.util.List<Task> descendants = taskManager.getDescendants(init);
            System.out.println("Init process has " + descendants.size() + " total descendants");
            
            // Check process depth
            System.out.println("Process depths:");
            System.out.println("  init: " + init.getProcessDepth());
            System.out.println("  shell: " + shell.getProcessDepth());
            System.out.println("  child: " + childProcess.getProcessDepth());
            System.out.println("  grandchild: " + grandchild.getProcessDepth());
            
            // Demonstrate cleanup
            System.out.println("\n=== Cleanup Demo ===");
            taskManager.cleanupTask(grandchild);
            System.out.println("After cleanup, shell has " + shell.getChildren().size() + " children");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void displayProcessTree(Task task, int depth) {
        String indent = "  ".repeat(depth);
        System.out.println(indent + task.getStatusString());
        
        for (Task child : task.getChildren()) {
            displayProcessTree(child, depth + 1);
        }
    }
}