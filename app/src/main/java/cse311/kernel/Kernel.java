package cse311.kernel;

import cse311.*;
import cse311.kernel.scheduler.*;
import cse311.kernel.syscall.*;
import cse311.kernel.process.*;
import cse311.kernel.memory.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main kernel class that coordinates all kernel subsystems
 * Provides a clean interface for managing tasks, scheduling, and system calls
 */
public class Kernel {
    private final RV32iCpu cpu;
    private final MemoryManager memory;
    private final TaskManager taskManager;
    private final Scheduler scheduler;
    private final SystemCallHandler syscallHandler;
    private final KernelMemoryManager kernelMemory;

    // Kernel state
    private boolean running = false;
    private int nextPid = 1;
    private final Map<Integer, Task> tasks = new ConcurrentHashMap<>();

    // Configuration
    private final KernelConfig config;

    public Kernel(RV32iCpu cpu, MemoryManager memory) {
        this.cpu = cpu;
        this.memory = memory;
        this.config = new KernelConfig();

        // Initialize kernel subsystems
        this.kernelMemory = new KernelMemoryManager(memory);
        this.taskManager = new TaskManager(this, kernelMemory);
        this.scheduler = createScheduler();
        this.syscallHandler = new SystemCallHandler(this, cpu);

        System.out.println("RV32IM Java Kernel initialized");
        System.out.println("Scheduler: " + scheduler.getClass().getSimpleName());
    }

    /**
     * Factory method to create scheduler based on configuration
     * This allows easy switching between different scheduling algorithms
     */
    private Scheduler createScheduler() {
        switch (config.getSchedulerType()) {
            case ROUND_ROBIN:
                return new RoundRobinScheduler(config.getTimeSlice());
            case COOPERATIVE:
                return new CooperativeScheduler();
            case PRIORITY:
                return new PriorityScheduler();
            default:
                return new RoundRobinScheduler(config.getTimeSlice());
        }
    }

    /**
     * Start the kernel and begin task execution
     */
    public void start() {
        if (running) {
            throw new IllegalStateException("Kernel is already running");
        }

        running = true;
        System.out.println("Kernel starting...");

        // Main kernel loop
        mainLoop();
    }

    /**
     * Stop the kernel
     */
    public void stop() {
        running = false;
        System.out.println("Kernel stopped");
    }

    /**
     * Main kernel execution loop
     */
    private void mainLoop() {
        while (running) {
            try {
                // Get the next task to run
                Task currentTask = scheduler.schedule(tasks.values());

                if (currentTask == null) {
                    // No runnable tasks, check for waiting tasks
                    if (hasWaitingTasks()) {
                        // Handle I/O or other events that might wake tasks
                        handleWaitingTasks();
                        continue;
                    } else {
                        // No tasks at all, kernel can idle or exit
                        System.out.println("No tasks to run, kernel idling...");
                        break;
                    }
                }

                // Execute the selected task
                executeTask(currentTask);

            } catch (Exception e) {
                System.err.println("Kernel error: " + e.getMessage());
                e.printStackTrace();
                // Continue running unless it's a critical error
            }
        }
    }

    /**
     * Execute a task for its time slice
     */
    private void executeTask(Task task) {
        if (task.getState() != TaskState.READY) {
            return;
        }

        if (task instanceof cse311.JavaTask) {
            // This is a Java-based task
            try {
                task.setState(TaskState.RUNNING);
                ((cse311.JavaTask) task).runLogic(); // Call its Java logic

                // If the task logic didn't block or terminate itself,
                // set it back to READY for the next schedule.
                if (task.getState() == TaskState.RUNNING) {
                    task.setState(TaskState.READY);
                }
            } catch (Exception e) {
                System.err.println("JavaTask " + task.getId() + " error: " + e.getMessage());
                e.printStackTrace();
                task.setState(TaskState.TERMINATED);
            }

        } else {
            // Switch to the task's address space
            switchToTaskAddressSpace(task);

            // Switch to the task
            task.setState(TaskState.RUNNING);
            task.restoreState(cpu);

            int instructionsExecuted = 0;
            int maxInstructions = scheduler.getTimeSlice();

            // Execute instructions until time slice expires or task yields
            while (instructionsExecuted < maxInstructions && task.getState() == TaskState.RUNNING) {
                try {
                    // Execute one instruction
                    cpu.step();
                    instructionsExecuted++;

                    // Check if task made a system call
                    if (cpu.isEcall()) {
                        task.saveState(cpu);
                        handleSystemCall(task);
                        break;
                    }

                    // Check if task hit a breakpoint or exception
                    if (cpu.isException()) {
                        handleException(task);
                        break;
                    }

                } catch (Exception e) {
                    System.err.println("Task " + task.getId() + " error: " + e.getMessage());
                    task.setState(TaskState.TERMINATED);
                    break;
                }
            }

            // Save task state
            task.saveState(cpu);

            // If task is still running, it used up its time slice
            if (task.getState() == TaskState.RUNNING) {
                task.setState(TaskState.READY);
            }
        }
    }

    /**
     * Switch to a task's address space
     */
    private void switchToTaskAddressSpace(Task task) {
        if (memory instanceof cse311.paging.PagedMemoryManager) {
            cse311.paging.PagedMemoryManager pm = (cse311.paging.PagedMemoryManager) memory;
            pm.switchTo(task.getAddressSpace());
        } else if (memory instanceof TaskAwareMemoryManager) {
            TaskAwareMemoryManager taskMemory = (TaskAwareMemoryManager) memory;
            taskMemory.setCurrentTask(task.getId());
        }
    }

    /**
     * Handle system call from a task
     */
    private void handleSystemCall(Task task) {
        try {
            syscallHandler.handleSystemCall(task);
        } catch (Exception e) {
            System.err.println("System call error for task " + task.getId() + ": " + e.getMessage());
            task.setState(TaskState.TERMINATED);
        }
    }

    /**
     * Handle exception from a task
     */
    private void handleException(Task task) {
        System.err.println("Task " + task.getId() + " caused an exception");
        // For now, terminate the task
        task.setState(TaskState.TERMINATED);
    }

    /**
     * Create a new task from an ELF file
     */
    public Task createTask(String elfPath) throws Exception {
        int pid = nextPid++;
        Task task = taskManager.createTask(pid, elfPath);
        tasks.put(pid, task);
        scheduler.addTask(task);

        System.out.println("Created task " + pid + " from " + elfPath);
        return task;
    }

    /**
     * Create a new task from ELF data
     */
    public Task createTask(byte[] elfData, String name) throws Exception {
        int pid = nextPid++;
        Task task = taskManager.createTask(pid, elfData, name);
        tasks.put(pid, task);
        scheduler.addTask(task);

        System.out.println("Created task " + pid + " (" + name + ")");
        return task;
    }

    /**
     * Terminate a task
     */
    public void terminateTask(int pid) {
        Task task = tasks.get(pid);
        if (task != null) {
            task.setState(TaskState.TERMINATED);
            scheduler.removeTask(task);
            taskManager.cleanupTask(task);
            tasks.remove(pid);
            System.out.println("Terminated task " + pid);
        }
    }

    /**
     * Get a task by PID
     */
    public Task getTask(int pid) {
        return tasks.get(pid);
    }

    /**
     * Get all tasks
     */
    public Collection<Task> getAllTasks() {
        return tasks.values();
    }

    /**
     * Check if there are any waiting tasks
     */
    private boolean hasWaitingTasks() {
        return tasks.values().stream()
                .anyMatch(t -> t.getState() == TaskState.WAITING);
    }

    /**
     * Handle waiting tasks (I/O, timers, etc.)
     */
    private void handleWaitingTasks() {
        // Check for I/O completion, timer events, etc.
        for (Task task : tasks.values()) {
            if (task.getState() == TaskState.WAITING) {
                // Check if the task can be woken up
                if (canWakeTask(task)) {
                    task.setState(TaskState.READY);
                    System.out.println("Woke up task " + task.getId());
                }
            }
        }

        // Small delay to prevent busy waiting
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Check if a waiting task can be woken up
     */
    private boolean canWakeTask(Task task) {
        // Check based on what the task is waiting for
        switch (task.getWaitReason()) {
            case UART_INPUT:
                // Check if UART has data
                try {
                    return memory.readByte(MemoryManager.UART_STATUS) != 0;
                } catch (Exception e) {
                    return false;
                }
            case TIMER:
                // Check if timer has expired (simplified)
                return System.currentTimeMillis() > task.getWakeupTime();
            case PROCESS_EXIT:
                // Check if child task has exited
                return !tasks.containsKey(task.getWaitingForPid());
            default:
                return false;
        }
    }

    // Getters for kernel subsystems
    public RV32iCpu getCpu() {
        return cpu;
    }

    public MemoryManager getMemory() {
        return memory;
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public SystemCallHandler getSystemCallHandler() {
        return syscallHandler;
    }

    public KernelMemoryManager getKernelMemory() {
        return kernelMemory;
    }

    public KernelConfig getConfig() {
        return config;
    }

    /**
     * Get kernel statistics
     */
    public KernelStats getStats() {
        return new KernelStats(
                tasks.size(),
                (int) tasks.values().stream().filter(t -> t.getState() == TaskState.RUNNING).count(),
                (int) tasks.values().stream().filter(t -> t.getState() == TaskState.READY).count(),
                (int) tasks.values().stream().filter(t -> t.getState() == TaskState.WAITING).count(),
                (int) tasks.values().stream().filter(t -> t.getState() == TaskState.TERMINATED).count());
    }

    /**
     * Print kernel status
     */
    public void printStatus() {
        KernelStats stats = getStats();
        System.out.println("=== Kernel Status ===");
        System.out.println("Total tasks: " + stats.totalProcesses);
        System.out.println("Running: " + stats.runningProcesses);
        System.out.println("Ready: " + stats.readyProcesses);
        System.out.println("Waiting: " + stats.waitingProcesses);
        System.out.println("Terminated: " + stats.terminatedProcesses);
        System.out.println("Scheduler: " + scheduler.getClass().getSimpleName());
        System.out.println("====================");
    }

    /**
     * Gets the next available Process ID.
     * NOTE: In a real multithreaded kernel, this would need a lock.
     * 
     * @return A new, unique PID.
     */
    public int getNextPid() {
        // nextPid is already a field in your Kernel class
        return nextPid++;
    }

    /**
     * Adds a newly created task to the kernel's task list and the scheduler.
     * 
     * @param task The task to add.
     */
    public void addTaskToScheduler(Task task) {
        if (task == null)
            return;

        tasks.put(task.getId(), task);
        scheduler.addTask(task);

        System.out.println("Kernel: Added task " + task.getId() + " (" + task.getName() + ") to scheduler.");
    }
}