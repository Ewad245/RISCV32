package cse311.kernel;

import cse311.*;
import cse311.kernel.scheduler.*;
import cse311.kernel.syscall.*;
import cse311.kernel.process.*;
import cse311.kernel.NonContiguous.NonContiguousMemoryCoordinator;
import cse311.kernel.NonContiguous.paging.PagedMemoryManager;
import cse311.kernel.NonContiguous.paging.PagingMapper;
import cse311.kernel.contiguous.ContiguousMemoryCoordinator;
import cse311.kernel.contiguous.ContiguousMemoryManager;
import cse311.kernel.memory.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Main kernel class that coordinates all kernel subsystems
 * Provides a clean interface for managing tasks, scheduling, and system calls
 */
public class Kernel {
    private final List<RV32Cpu> cpus; // NEW
    private final MemoryManager memory;
    private final TaskManager taskManager;
    private final Scheduler scheduler;
    private final SystemCallHandler syscallHandler;
    private final KernelMemoryManager kernelMemory;
    private ProcessMemoryCoordinator memoryCoordinator;

    // 1. I/O Wait Queue (FIFO)
    private final Queue<Task> ioWaitQueue = new ConcurrentLinkedQueue<>();

    // 2. Interrupt/Timer Wait Queue (Sorted by wakeup time)
    private final PriorityBlockingQueue<Task> sleepWaitQueue = new PriorityBlockingQueue<>(
            11, Comparator.comparingLong(Task::getWakeupTime));

    // 3. Child Termination Wait Queue
    // Map: Child PID -> Parent Task (The parent waiting for that child)
    private final Map<Integer, Task> childTerminationWaitQueue = new ConcurrentHashMap<>();

    // Kernel state
    private boolean running = false;
    private int nextPid = 1;
    // Master list of all tasks (for tracking purposes only, not scheduling)
    private final Map<Integer, Task> tasks = new ConcurrentHashMap<>();

    // Boot coordination
    private volatile boolean started = false;

    // Execution Control
    private volatile boolean paused = false;
    private volatile int executionDelayMs = 0; // 0 = max speed

    // Synchronization
    private final cse311.kernel.lock.Spinlock schedulerLock = new cse311.kernel.lock.Spinlock("scheduler");

    // Configuration
    private final KernelConfig config;

    public Kernel(MemoryManager memory) {
        cpus = new ArrayList<>();
        this.memory = memory;
        this.config = new KernelConfig();

        for (int i = 0; i < config.getCoreCount(); i++) {
            RV32Cpu core = new RV32Cpu(memory); // All cores share the same physical RAM
            core.setId(i);
            this.cpus.add(core);
        }

        // Initialize kernel subsystems
        this.kernelMemory = new KernelMemoryManager(memory);
        this.taskManager = new TaskManager(this, kernelMemory);
        this.scheduler = createScheduler();
        this.syscallHandler = new SystemCallHandler(this);

        // --------------------------------------------------------
        // 1. FACTORY: Initialize the correct Memory Coordinator
        // --------------------------------------------------------
        if (memory instanceof PagedMemoryManager) {
            // --- PAGING MODE ---
            System.out.println("Kernel: Detected Paging Mode.");

            PagedMemoryManager pm = (PagedMemoryManager) memory;
            PagingMapper mapper = new PagingMapper(pm);

            this.memoryCoordinator = new NonContiguousMemoryCoordinator(
                    mapper,
                    config.getStackSize());

        } else if (memory instanceof ContiguousMemoryManager) {
            // --- CONTIGUOUS MODE ---
            System.out.println("Kernel: Detected Contiguous Mode.");

            ContiguousMemoryManager cmm = (ContiguousMemoryManager) memory;

            this.memoryCoordinator = new ContiguousMemoryCoordinator(
                    cmm, // The MMU for allocation/translation
                    config.getStackSize());

        } else {
            // --- LEGACY MODE ---
            System.out.println("Kernel: Warning - Legacy Memory Mode (No Coordinator).");
            this.memoryCoordinator = null;
        }
        taskManager.setMemoryCoordinator(this.memoryCoordinator);

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
     * Start the kernel and eventually the APs
     */
    public void start() {
        if (running) {
            throw new IllegalStateException("Kernel is already running");
        }

        running = true;
        System.out.println("Kernel starting...");

        // 1. Launch Maintenance Thread (Handles Interrupts/Timers)
        new Thread(this::maintenanceLoop, "Kernel-Maintenance").start();

        // 2. Launch Bootstrap Processor (BSP) - Core 0
        RV32Cpu bsp = cpus.get(0);
        new Thread(() -> {
            System.out.println("BSP (Core 0) booting...");
            cpuRunLoop(bsp);
        }, "CPU-Core-0").start();

        // Note: Application Processors (APs) are NOT started here.
        // They will be started by the BSP calling startOthers().
        // For simulation purposes, we will trigger this automatically after a short
        // delay
        // to mimic the BSP finishing its initialization.
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Simulate BSP initialization time
                startOthers();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Wake up Application Processors (APs)
     * In xv6, this is done by the BSP in main() calling startothers()
     */
    private void startOthers() {
        System.out.println("BSP: Starting Application Processors (APs)...");
        // Set the flag to allow APs to proceed
        started = true;

        // In simulation, we need to actually start the threads if they aren't already.
        // But to better match the 'spin wait' logic, we could have started them earlier
        // and let them spin.
        // For now, adhering to the plan: we launch them here, and they check the flag.
        for (int i = 1; i < cpus.size(); i++) {
            RV32Cpu ap = cpus.get(i);
            new Thread(() -> cpuRunLoop(ap), "CPU-Core-" + ap.getId()).start();
        }
    }

    /**
     * Stop the kernel
     */
    public void stop() {
        running = false;
        System.out.println("Kernel stopped");
    }

    /**
     * [Thread 1..N] The Main Execution Loop for each CPU Core
     */
    private void cpuRunLoop(RV32Cpu cpu) {
        System.out.println("Core " + cpu.getId() + " online.");
        // Only start the keyboard input thread on the BSP (Core 0)
        // This prevents multiple threads from contending for System.in
        if (cpu.getId() == 0) {
            cpu.turnOn();
        }

        // Boot Coordination: APs spin-wait until BSP says 'started'
        if (cpu.getId() != 0) {
            while (!started) {
                Thread.yield(); // Spin
            }
        }

        while (running) {
            try {
                // ------------------------------------------------------------
                // 1. EXECUTION CONTROL (Pause / Speed)
                // ------------------------------------------------------------
                while (paused) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                if (executionDelayMs > 0) {
                    try {
                        Thread.sleep(executionDelayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                Task currentTask;

                // Synchronize scheduler access using our Spinlock
                schedulerLock.acquire();
                try {
                    currentTask = scheduler.schedule();
                } finally {
                    schedulerLock.release();
                }

                if (currentTask == null) {
                    cpu.setCurrentTask(null); // Explicitly mark as idle
                    idle();
                    continue;
                }

                // DOUBLE-SCHEDULE CHECK
                if (!currentTask.tryAcquireCpu(cpu.getId())) {
                    int otherHart = currentTask.getActiveHartId();
                    String error = "DOUBLE SCHEDULE DETECTED! Task " + currentTask.getId()
                            + " is already running on Hart " + otherHart
                            + " but Hart " + cpu.getId() + " tried to run it!";
                    System.err.println(error);
                    throw new RuntimeException(error);
                }

                try {
                    // Execute the selected task on THIS cpu
                    executeTask(currentTask, cpu);
                } finally {
                    // Release ownership
                    currentTask.releaseCpu();
                }

                // Decide where the task goes next (Ready, Wait, or Terminated)
                dispatchTask(currentTask);

                // Note: We do NOT clear currentTask here to avoid UI flickering.
                // It will be updated in the next 'executeTask' call
                // or cleared in the 'if (currentTask == null)' block above.

            } catch (Exception e) {
                System.err.println("Core " + cpu.getId() + " error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * [Thread 0] Maintenance Loop
     * Checks hardware status and moves tasks from Wait Queues to Ready Queue.
     */
    private void maintenanceLoop() {
        while (running) {
            try {
                checkDeviceInterrupts();
                Thread.sleep(10); // Prevent burning host CPU
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void idle() {
        // Simple idle loop: sleep briefly to save host CPU
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
    }

    /**
     * Execute a task for its time slice
     */
    private void executeTask(Task task, RV32Cpu cpu) {
        if (task.getState() != TaskState.READY) {
            return;
        }

        cpu.setCurrentTask(task); // Notify CPU about the task it is running

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
            boolean stateSavedBySyscall = false; // Flag to track if syscall saved state

            // Execute instructions until time slice expires or task yields
            while (instructionsExecuted < maxInstructions && task.getState() == TaskState.RUNNING) {
                try {
                    // Execute one instruction
                    cpu.step();
                    instructionsExecuted++;

                    // Check if task made a system call
                    if (cpu.isEcall()) {
                        // 1. Save state BEFORE handling syscall (Required for fork/wait to work)
                        task.saveState(cpu);

                        // 2. Handle the syscall (which might change Task state, like exec)
                        handleSystemCall(task, cpu);

                        // 3. Mark that we have already saved/handled the state.
                        // This prevents the code below the loop from overwriting
                        // changes made by 'exec' (like the new PC).
                        stateSavedBySyscall = true;
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

            if (!stateSavedBySyscall) {
                task.saveState(cpu);
            }

            // If task is still running, it used up its time slice
            if (task.getState() == TaskState.RUNNING) {
                task.setState(TaskState.READY);
            }
        }
    }

    /**
     * Checks hardware status and moves tasks from Wait Queues to Ready Queue.
     * This corresponds to the "Interrupt Occurs" or "I/O Completion" arrows.
     */
    private void checkDeviceInterrupts() {
        // A. Check UART (I/O)
        // If UART has data, wake up ALL tasks waiting for UART input
        // (Real OS might be more selective, but this matches your logic)
        try {
            if ((memory.readByte(MemoryManager.UART_STATUS) & 1) != 0) {
                Iterator<Task> it = ioWaitQueue.iterator();
                while (it.hasNext()) {
                    Task t = it.next();
                    if (t.getWaitReason() == WaitReason.UART_INPUT) {
                        t.wakeup(); // Set state to READY
                        scheduler.addTask(t); // Move to Ready Queue
                        it.remove(); // Remove from I/O Wait Queue
                        System.out.println("Kernel: Woke up Task " + t.getId() + " (UART)");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Kernel: UART check error: " + e.getMessage());
            e.printStackTrace();
        }

        // B. Check Timer (Interrupt Wait Queue)
        long now = System.currentTimeMillis();
        while (!sleepWaitQueue.isEmpty() && sleepWaitQueue.peek().getWakeupTime() <= now) {
            Task t = sleepWaitQueue.poll(); // Remove from Interrupt Wait Queue
            t.wakeup();
            scheduler.addTask(t); // Move to Ready Queue
            System.out.println("Kernel: Woke up Task " + t.getId() + " (Timer)");
        }
    }

    /**
     * Decides where to put the task after it stops running on the CPU.
     * This implements the arrows leaving the CPU node.
     */
    private void dispatchTask(Task task) {
        switch (task.getState()) {
            case RUNNING:
                // Time slice expired, still runnable
                task.setState(TaskState.READY);
                scheduler.addTask(task); // Back to Ready Queue
                break;

            case WAITING:
                // Blocked (Syscall or I/O)
                handleWaitRequest(task);
                break;

            case TERMINATED:
                // Task finished
                handleTermination(task);
                break;

            case READY:
                // Should not happen immediately after execution, but treat as re-queue
                scheduler.addTask(task);
                break;
        }
    }

    private void handleWaitRequest(Task task) {
        switch (task.getWaitReason()) {
            case UART_INPUT:
                ioWaitQueue.add(task); // -> I/O Wait Queue
                break;

            case TIMER:
                sleepWaitQueue.add(task); // -> Interrupt Wait Queue
                break;

            case PROCESS_EXIT:
                // The task is waiting for a specific child PID
                int childPid = task.getWaitingForPid();
                if (childPid != -1) {
                    childTerminationWaitQueue.put(childPid, task); // -> Child Wait Queue
                } else {
                    // Waiting for ANY child (simplified: just put in sleep queue or check
                    // immediately)
                    // For strict diagram adherence, we'd add to a generic wait list.
                    // Here we fall back to a slow polling list for "Wait Any"
                    ioWaitQueue.add(task);
                }
                break;

            default:
                // Generic wait
                ioWaitQueue.add(task);
                break;
        }
    }

    private void handleTermination(Task task) {
        // 1. Remove from Scheduler so it never runs again
        scheduler.removeTask(task);

        // 2. Notify waiting parents
        int pid = task.getId();

        // Check if a parent was waiting specifically for THIS child
        Task parent = childTerminationWaitQueue.remove(pid);

        // Check for parents waiting for ANY child (WaitReason.PROCESS_EXIT with pid -1)
        if (parent == null && task.getParent() != null) {
            Task p = task.getParent();
            if (p.getState() == TaskState.WAITING &&
                    p.getWaitReason() == WaitReason.PROCESS_EXIT &&
                    p.getWaitingForPid() == -1) {
                parent = p;
                // Remove from generic wait queue if it was stored there
                ioWaitQueue.remove(parent);
            }
        }

        if (parent != null) {
            // Wake up the parent.
            // When the parent runs, it will re-execute the 'wait' instruction,
            // find this ZOMBIE (TERMINATED) task, read its exit code,
            // and perform the actual cleanup.
            parent.wakeup();
            scheduler.addTask(parent);
            System.out.println("Kernel: Zombie Task " + pid + " woke up Parent " + parent.getId());
        } else {
            System.out.println("Kernel: Task " + pid + " became a Zombie (Parent not waiting).");
        }

        // We must leave the task in memory as a ZOMBIE so the parent can read the exit
        // code.
    }

    /**
     * Switch to a task's address space
     */
    private void switchToTaskAddressSpace(Task task) {
        if (memoryCoordinator != null) {
            // The coordinator knows whether to swap Page Tables or Base Registers
            memoryCoordinator.switchContext(task.getId());
        }
    }

    /**
     * Handle system call from a task
     */
    private void handleSystemCall(Task task, RV32Cpu cpu) {
        try {
            syscallHandler.handleSystemCall(task, cpu);
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
                    return (memory.readByte(MemoryManager.UART_STATUS) & 1) != 0;
                } catch (Exception e) {
                    return false;
                }
            case TIMER:
                // Check if timer has expired (simplified)
                return System.currentTimeMillis() > task.getWakeupTime();
            case PROCESS_EXIT:
                // Check if child task has exited
                int pid = task.getWaitingForPid();
                if (pid == -1) {
                    // Waiting for ANY child to terminate: Look for a ZOMBIE child
                    for (Task child : task.getChildren()) {
                        if (child.getState() == TaskState.TERMINATED) {
                            return true;
                        }
                    }
                    return false; // No zombie children found yet
                } else {
                    // Waiting for a specific child
                    Task child = tasks.get(pid);
                    return child == null || child.getState() == TaskState.TERMINATED;
                }
            default:
                return false;
        }
    }

    public RV32Cpu getCpu(int id) {
        if (id < 0 || id >= cpus.size()) {
            return null;
        }
        return cpus.get(id);
    }

    /**
     * Get the Bootstrap Processor (Core 0)
     * Added for backward compatibility
     */
    public RV32Cpu getCpu() {
        return getCpu(0);
    }

    // --- Execution Control Methods ---
    public void pause() {
        this.paused = true;
        System.out.println("Kernel: Execution Paused.");
    }

    public void resume() {
        this.paused = false;
        System.out.println("Kernel: Execution Resumed.");
    }

    public void setExecutionSpeed(int delayMs) {
        this.executionDelayMs = delayMs;
        System.out.println("Kernel: Speed set to " + delayMs + "ms delay.");
    }

    public boolean isPaused() {
        return paused;
    }

    // --- Observability Methods (For GUI) ---
    public Collection<Task> getIoWaitQueue() {
        return Collections.unmodifiableCollection(ioWaitQueue);
    }

    public Collection<Task> getSleepWaitQueue() {
        return Collections.unmodifiableCollection(sleepWaitQueue);
    }

    public Collection<Task> getReadyQueue() {
        return scheduler.getReadyTasks(); // Needs to be implemented in Scheduler
    }

    // Getters for kernel subsystems
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

        // System.out.println("Kernel: Added task " + task.getId() + " (" +
        // task.getName() + ") to scheduler.");
    }

    public ProcessMemoryCoordinator getMemoryCoordinator() {
        return memoryCoordinator;
    }

    public void setMemoryCoordinator(ProcessMemoryCoordinator memoryCoordinator) {
        this.memoryCoordinator = memoryCoordinator;
        this.taskManager.setMemoryCoordinator(memoryCoordinator);
    }
}