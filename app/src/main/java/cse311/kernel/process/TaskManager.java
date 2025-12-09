package cse311.kernel.process;

import cse311.*;
import cse311.Exception.MemoryAccessException;
import cse311.kernel.Kernel;
import cse311.kernel.NonContiguous.paging.AddressSpace;
import cse311.kernel.NonContiguous.paging.PagedMemoryManager;
import cse311.kernel.contiguous.AllocationStrategy;
import cse311.kernel.contiguous.ContiguousMemoryCoordinator;
import cse311.kernel.contiguous.ContiguousMemoryManager;
import cse311.kernel.memory.KernelMemoryManager;
import cse311.kernel.memory.ProcessMemoryCoordinator;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * Manages task creation, destruction, and lifecycle
 */
public class TaskManager {
    private final Kernel kernel;
    private final KernelMemoryManager kernelMemory;
    private final Map<Integer, TaskMemoryInfo> taskMemory = new ConcurrentHashMap<>();
    private ProcessMemoryCoordinator memoryCoordinator;
    private Task initTask; // The init process (PID 1)

    public TaskManager(Kernel kernel, KernelMemoryManager kernelMemory) {
        this.kernel = kernel;
        this.kernelMemory = kernelMemory;
    }

    public void setMemoryCoordinator(ProcessMemoryCoordinator mem) {
        this.memoryCoordinator = mem;
    }

    /**
     * Create a new process from an ELF file
     */
    public Task createTask(int pid, String elfPath) throws Exception {
        return createTask(pid, elfPath, null); // No parent for root tasks
    }

    /**
     * Create a new process from an ELF file with a parent
     */
    public Task createTask(int pid, String elfPath, Task parent) throws Exception {
        // 1. Read the file bytes (Simulating File System read)
        byte[] elfData;
        try {
            elfData = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(elfPath));
        } catch (java.io.IOException e) {
            throw new Exception("Failed to read ELF file: " + elfPath);
        }

        // 2. Delegate to the main creation logic
        // The name of the task defaults to the filename
        String taskName = new java.io.File(elfPath).getName();
        return createTask(pid, elfData, taskName, parent);
    }

    /**
     * Create a new task from ELF data in memory
     */
    public Task createTask(int pid, byte[] elfData, String name) throws Exception {
        return createTask(pid, elfData, name, null); // No parent for root tasks
    }

    /**
     * Create a new task from ELF data in memory with a parent
     */
    public Task createTask(int pid, byte[] elfData, String name, Task parent) throws Exception {
        // 1. Calculate Requirements
        int elfEndAddress = ElfLoader.calculateRequiredMemory(elfData);
        int stackSize = kernel.getConfig().getStackSize();
        int minHeapSize = 64 * 1024; // 64KB Heap buffer

        int requiredSize = elfEndAddress + minHeapSize + stackSize;

        // 2. Delegate Allocation
        var layout = memoryCoordinator.allocateMemory(pid, requiredSize);

        // 3. Delegate Loading
        int entryPoint = memoryCoordinator.loadProgram(pid, elfData);

        // 4. Create Task Object
        Task task = new Task(pid, name, entryPoint, layout.stackSize, layout.stackBase);

        task.setAllocatedSize(requiredSize);

        // REMOVED: if (instanceof PagedMemoryManager) task.setAddressSpace(...)
        // Reason: The coordinator now handles context switching using PID lookup only.
        // We do not need to attach the AddressSpace object to the Task anymore.

        if (parent != null) {
            task.setParent(parent);
            parent.addChild(task);
        }

        TaskMemoryInfo memInfo = new TaskMemoryInfo(pid, entryPoint, layout.stackBase, layout.stackSize);
        taskMemory.put(pid, memInfo);

        return task;
    }

    /**
     * Clean up task resources
     */
    public void cleanupTask(Task task) {
        int pid = task.getId();

        reparentChildrenToInit(task);
        memoryCoordinator.freeMemory(task.getId());

        TaskMemoryInfo memInfo = taskMemory.get(pid);
        if (memInfo != null) {
            kernelMemory.freeStack(pid, memInfo.stackBase, memInfo.stackSize);
            taskMemory.remove(pid);
        }

        System.out.println("Cleaned up task " + pid + " resources");
    }

    /**
     * Moves all children of the dying task to the Init process (PID 1).
     * This prevents orphans from being lost or becoming zombies forever.
     */
    public void reparentChildrenToInit(Task dyingTask) {
        // 1. Find the Init Process (Always PID 1)
        Task initTask = kernel.getTask(1);

        if (initTask == null) {
            System.err.println("CRITICAL: Init task (PID 1) not found during reparenting!");
            return;
        }

        if (dyingTask.getId() == 1) {
            System.err.println("CRITICAL: Init task is dying! System halt imminent.");
            return;
        }

        // 2. Iterate safely over a copy of the children list
        // (We use a copy because we will be modifying the original list inside the
        // loop)
        List<Task> children = new ArrayList<>(dyingTask.getChildren());

        for (Task child : children) {
            System.out.println("Reparenting child PID " + child.getId() +
                    " from " + dyingTask.getId() + " to Init (PID 1)");

            // A. Remove from dying parent
            dyingTask.removeChild(child);

            // B. Set new parent to Init
            child.setParent(initTask);

            // C. Add to Init's children list
            initTask.addChild(child);

            // D. Important: If the child was already ZOMBIE (TERMINATED),
            // Init needs to know so it can reap it immediately.
            // In a real OS, we might send a SIGCHLD signal here.
            // For this simulator, if the child is already TERMINATED, Init will
            // pick it up in its next wait() call naturally.
        }
    }

    /**
     * Get task memory information
     */
    public TaskMemoryInfo getTaskMemoryInfo(int pid) {
        return taskMemory.get(pid);
    }

    /**
     * Create the init process (PID 1) (ELF Version)
     */
    public Task createInitTask(String elfPath) throws Exception {
        if (initTask != null) {
            throw new IllegalStateException("Init task already created");
        }
        initTask = createTask(1, elfPath);
        initTask.setName("init");
        return initTask;
    }

    /**
     * Set the init process
     */
    public void setInitTask(Task initTask) {
        this.initTask = initTask;
    }

    /**
     * Get the init process
     */
    public Task getInitTask() {
        return initTask;
    }

    /**
     * Forks a task to create a new process
     */
    public Task forkTask(int childPid, Task parent, String name) throws Exception {
        // Create new task as copy of parent
        Task child = new Task(childPid, name,
                parent.getProgramCounter(),
                parent.getStackSize(),
                parent.getStackBase());

        // Copy registers
        child.setProgramCounter(parent.getProgramCounter());
        System.arraycopy(parent.getRegisters(), 0, child.getRegisters(), 0, 32);

        // Set up parent relationship
        child.setParent(parent);
        parent.addChild(child);

        // Copy memory layout
        child.setTextStart(parent.getTextStart());
        child.setTextSize(parent.getTextSize());
        child.setDataStart(parent.getDataStart());
        child.setDataSize(parent.getDataSize());
        child.setHeapStart(parent.getHeapStart());
        child.setHeapSize(parent.getHeapSize());

        return child;
    }

    /**
     * Forks a task to create a new process (child).
     * This acts as a wrapper that generates the PID and Name automatically.
     */
    public Task forkTask(Task parent) throws Exception {
        if (memoryCoordinator == null) {
            throw new Exception("Fork requires a valid Memory Coordinator.");
        }

        // 1. Generate Identity
        int childPid = kernel.getNextPid();
        String childName = parent.getName() + "_child";

        // 2. Allocate Memory for Child
        // We assume the child needs the same memory footprint as the parent
        // In paging, this is just a quota. In contiguous, this finds a hole of the same
        // size.
        int childMemorySize = parent.getAllocatedSize();

        // Fallback for tasks created before this fix or special cases
        if (childMemorySize == 0) {
            childMemorySize = parent.getStackSize() + (1024 * 1024);
        }

        // Strategy Pattern: Allocate based on mode (Paging or Contiguous)
        ProcessMemoryCoordinator.MemoryLayout layout = memoryCoordinator.allocateMemory(childPid, childMemorySize);

        // 3. Copy Memory Content
        // Strategy Pattern: Copy Page Tables OR Copy Physical Bytes
        try {
            memoryCoordinator.copyMemory(parent.getId(), childPid);
        } catch (MemoryAccessException e) {
            // Cleanup if copy fails
            memoryCoordinator.freeMemory(childPid);
            throw new Exception("Fork failed during memory copy: " + e.getMessage());
        }

        // 4. Create Task Object
        Task child = new Task(childPid, childName,
                parent.getProgramCounter(),
                parent.getStackSize(),
                parent.getStackBase());

        // 5. Copy CPU State
        child.setRegisters(parent.getRegisters().clone());

        // 6. Set Return Value (0 for child)
        child.getRegisters()[10] = 0; // a0 = 0

        // 7. Hierarchy & Scheduler
        child.setAllocatedSize(childMemorySize);
        child.setParent(parent);
        parent.addChild(child);
        child.setState(TaskState.READY);

        kernel.addTaskToScheduler(child);

        System.out.println("TaskManager: Forked task " + parent.getId() + " -> " + childPid);
        return child;
    }

    /**
     * Creates a new thread within an existing process
     * Threads share the same address space and TGID
     */
    public Task createThread(Task process, int threadId, int entryPoint, int stackSize, int stackBase) {
        if (!process.isThreadGroupLeader()) {
            throw new IllegalArgumentException("Process must be a thread group leader");
        }

        Task thread = process.createThread(threadId, entryPoint, stackSize, stackBase);

        // Register the thread with the kernel
        // process.put(threadId, thread); (unknown)

        return thread;
    }

    /**
     * Gets all threads in a process's thread group
     */
    public List<Task> getThreads(Task process) {
        return process.getThreadGroup();
    }

    /**
     * Checks if a task is part of a thread group (either leader or thread)
     */
    public boolean isInThreadGroup(Task task, int tgid) {
        return task.getTgid() == tgid;
    }

    /**
     * Get all descendant processes of a task
     */
    public List<Task> getDescendants(Task task) {
        List<Task> descendants = new ArrayList<>();
        addDescendantsRecursive(task, descendants);
        return descendants;
    }

    private void addDescendantsRecursive(Task task, List<Task> descendants) {
        for (Task child : task.getChildren()) {
            descendants.add(child);
            addDescendantsRecursive(child, descendants);
        }
    }

    /**
     * Clean up task resources and notify parent
     */
    public void cleanupTaskAndNotify(Task task) {
        int pid = task.getId();

        // Notify parent if exists
        Task parent = task.getParent();
        if (parent != null) {
            parent.removeChild(task);
            // Wake up parent if waiting for this child
            if (parent.getState() == TaskState.WAITING &&
                    parent.getWaitReason() == WaitReason.PROCESS_EXIT &&
                    parent.getWaitingForPid() == pid) {
                parent.wakeup();
            }
        }

        // Clean up children first
        for (Task child : new ArrayList<>(task.getChildren())) {
            cleanupTask(child);
        }

        // Free task memory
        if (kernel.getMemory() instanceof cse311.kernel.NonContiguous.paging.PagedMemoryManager) {
            cse311.kernel.NonContiguous.paging.PagedMemoryManager pm = (cse311.kernel.NonContiguous.paging.PagedMemoryManager) kernel
                    .getMemory();
            pm.destroyAddressSpace(pid);
        } else if (kernel.getMemory() instanceof TaskAwareMemoryManager) {
            TaskAwareMemoryManager taskAwareMemory = (TaskAwareMemoryManager) kernel.getMemory();
            taskAwareMemory.deallocateTaskMemory(pid);
        }

        TaskMemoryInfo memInfo = taskMemory.get(pid);
        if (memInfo != null) {
            kernelMemory.freeStack(pid, memInfo.stackBase, memInfo.stackSize);
            taskMemory.remove(pid);
        }

        System.out.println("Cleaned up task " + pid + " resources");
    }

    /**
     * Task memory information
     */
    public static class TaskMemoryInfo {
        public final int pid;
        public final int entryPoint;
        public final int stackBase;
        public final int stackSize;

        public TaskMemoryInfo(int pid, int entryPoint, int stackBase, int stackSize) {
            this.pid = pid;
            this.entryPoint = entryPoint;
            this.stackBase = stackBase;
            this.stackSize = stackSize;
        }
    }
}