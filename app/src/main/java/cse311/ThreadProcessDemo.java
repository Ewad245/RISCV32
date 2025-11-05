package cse311;

import cse311.kernel.process.TaskManager;
import java.util.List;

/**
 * Demonstrates unified scheduling of threads and processes
 * Shows how the kernel treats both as Tasks with shared/unshared resources
 */
public class ThreadProcessDemo {
    
    public static void main(String[] args) {
        System.out.println("=== Unified Thread & Process Scheduling Demo ===\n");
        
        TaskManager taskManager = new TaskManager();
        
        // Create a process (thread group leader)
        Task process1 = taskManager.createTask(1, "Process1", "/bin/program1", null);
        System.out.println("Created process: " + process1.getName() + " (PID: " + process1.getId() + ", TGID: " + process1.getTgid() + ")");
        
        // Create threads within the process
        Task thread1 = taskManager.createThread(process1, 2, 0x1000, 8192, 0x7FFF0000);
        Task thread2 = taskManager.createThread(process1, 3, 0x2000, 8192, 0x7FFE0000);
        
        System.out.println("Created threads:");
        System.out.println("  Thread1: " + thread1.getName() + " (PID: " + thread1.getId() + ", TGID: " + thread1.getTgid() + ")");
        System.out.println("  Thread2: " + thread2.getName() + " (PID: " + thread2.getId() + ", TGID: " + thread2.getTgid() + ")");
        
        // Show thread group
        List<Task> threadGroup = taskManager.getThreads(process1);
        System.out.println("\nThread group for Process1 (TGID: " + process1.getTgid() + "):");
        for (Task t : threadGroup) {
            System.out.println("  " + t.getName() + " (PID: " + t.getId() + ")");
        }
        
        // Create another independent process
        Task process2 = taskManager.createTask(4, "Process2", "/bin/program2", null);
        System.out.println("\nCreated independent process: " + process2.getName() + " (PID: " + process2.getId() + ", TGID: " + process2.getTgid() + ")");
        
        // Show process tree
        System.out.println("\nProcess tree:");
        printProcessTree(taskManager, process1, 0);
        printProcessTree(taskManager, process2, 0);
        
        // Demonstrate shared vs unshared resources
        System.out.println("\n=== Resource Sharing Analysis ===");
        
        // Threads share address space
        System.out.println("Thread1 shares address space with Process1: " + 
                          (thread1.getAddressSpace() == process1.getAddressSpace()));
        System.out.println("Thread2 shares address space with Process1: " + 
                          (thread2.getAddressSpace() == process1.getAddressSpace()));
        
        // Processes have separate address spaces
        System.out.println("Process2 shares address space with Process1: " + 
                          (process2.getAddressSpace() == process1.getAddressSpace()));
        
        // Show scheduling perspective
        System.out.println("\n=== Scheduling Perspective ===");
        System.out.println("All tasks (processes and threads) are scheduled as Task objects:");
        System.out.println("- Process1: Task(id=" + process1.getId() + ", state=" + process1.getState() + ")");
        System.out.println("- Thread1: Task(id=" + thread1.getId() + ", state=" + thread1.getState() + ")");
        System.out.println("- Thread2: Task(id=" + thread2.getId() + ", state=" + thread2.getState() + ")");
        System.out.println("- Process2: Task(id=" + process2.getId() + ", state=" + process2.getState() + ")");
        
        // Demonstrate process hierarchy queries
        System.out.println("\n=== Process Hierarchy Queries ===");
        System.out.println("Process1 children: " + process1.getChildren().size());
        System.out.println("Thread1 parent: " + thread1.getParent().getName());
        System.out.println("Thread1 is thread: " + thread1.isThread());
        System.out.println("Process1 is thread group leader: " + process1.isThreadGroupLeader());
        
        // Show how the scheduler sees them
        System.out.println("\n=== Scheduler's View ===");
        System.out.println("Scheduler treats all Tasks identically, regardless of being process or thread:");
        System.out.println("- All have unique PIDs for scheduling");
        System.out.println("- All have states (READY, RUNNING, WAITING, etc.)");
        System.out.println("- All can be prioritized independently");
        System.out.println("- All share CPU time according to scheduling algorithm");
    }
    
    private static void printProcessTree(TaskManager taskManager, Task task, int depth) {
        String indent = "  ".repeat(depth);
        String type = task.isThread() ? "[Thread]" : "[Process]";
        System.out.println(indent + type + " " + task.getName() + " (PID: " + task.getId() + ")");
        
        for (Task child : task.getChildren()) {
            printProcessTree(taskManager, child, depth + 1);
        }
    }
}