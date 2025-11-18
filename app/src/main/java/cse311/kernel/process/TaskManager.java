package cse311.kernel.process;

import cse311.*;
import cse311.kernel.Kernel;
import cse311.kernel.memory.KernelMemoryManager;
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
    private Task initTask; // The init process (PID 1)

    public TaskManager(Kernel kernel, KernelMemoryManager kernelMemory) {
        this.kernel = kernel;
        this.kernelMemory = kernelMemory;
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
        // Load ELF file
        ElfLoader elfLoader = new ElfLoader(kernel.getMemory());
        elfLoader.loadElf(elfPath);

        // Get entry point
        int entryPoint = elfLoader.getEntryPoint();

        // Allocate stack
        int stackSize = kernel.getConfig().getStackSize();
        int stackBase = kernelMemory.allocateStack(pid, stackSize);

        // Create task
        Task task = new Task(pid, elfPath, entryPoint, stackSize, stackBase);
        if (parent != null) {
            task.setParent(parent);
            parent.addChild(task);
        }

        // Store memory information
        TaskMemoryInfo memInfo = new TaskMemoryInfo(pid, entryPoint, stackBase, stackSize);
        taskMemory.put(pid, memInfo);

        return task;
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
        if (kernel.getMemory() instanceof cse311.paging.PagedMemoryManager) {
            var pm = (cse311.paging.PagedMemoryManager) kernel.getMemory();

            // 1) Create address space and give it to the Task
            var as = pm.createAddressSpace(pid);

            // 2) Map a user stack (grow-down). 1MB like before (you can tune).
            final int STACK_SIZE = 0x0010_0000;
            final int STACK_TOP = 0x7FFF_F000; // 4KB below 2GB, page-aligned
            final int STACK_BASE = STACK_TOP - STACK_SIZE;
            pm.mapRegion(as, STACK_BASE, STACK_SIZE, /* R= */true, /* W= */true, /* X= */false);

            // 3) Switch to this AS for loading
            pm.switchTo(as);

            // 4) Load ELF using existing loader (it calls writeByteToVirtualAddress)
            MemoryElfLoader elfLoader = new MemoryElfLoader(pm, elfData);
            elfLoader.load();
            int entryPoint = elfLoader.getEntryPoint();

            // 5) Create Task – store AS & stack top
            Task task = new Task(pid, name, entryPoint, STACK_SIZE, STACK_BASE);
            task.setAddressSpace(as);
            if (parent != null) {
                task.setParent(parent);
                parent.addChild(task);
            }

            // 6) Persist memory info for debug views, etc.
            TaskMemoryInfo memInfo = new TaskMemoryInfo(pid, entryPoint, STACK_TOP, STACK_SIZE);
            taskMemory.put(pid, memInfo);

            System.out.println("Created paged task " + pid + " (" + name + ")");
            return task;
        } else if (kernel.getMemory() instanceof TaskAwareMemoryManager) {
            TaskAwareMemoryManager taskAwareMemory = (TaskAwareMemoryManager) kernel.getMemory();

            // Allocate task's individual address space
            if (!taskAwareMemory.allocateTaskMemory(pid)) {
                throw new Exception("Failed to allocate memory space for task " + pid);
            }

            // Load program into task's memory space
            int loadAddress = VirtualMemoryManager.TEXT_START;
            taskAwareMemory.loadProgramForTask(pid, elfData, loadAddress);

            // Set entry point to load address
            int entryPoint = loadAddress;

            // Calculate stack base (top of stack)
            int stackStart = (kernel.getMemory() instanceof TaskAwareMemoryManager)
                    ? ((TaskAwareMemoryManager) kernel.getMemory()).getStackStart()
                    : 0x00800000; // fallback
            int stackBase = stackStart + VirtualMemoryManager.STACK_SIZE;
            int stackSize = VirtualMemoryManager.STACK_SIZE;

            // Create task
            Task task = new Task(pid, name, entryPoint, stackSize, stackBase);
            if (parent != null) {
                task.setParent(parent);
                parent.addChild(task);
            }

            // Store memory information
            TaskMemoryInfo memInfo = new TaskMemoryInfo(pid, entryPoint, stackBase, stackSize);
            taskMemory.put(pid, memInfo);

            System.out.println("Created task " + pid + " (" + name + ") with individual address space");
            return task;

        } else {
            // Fallback to original implementation for backward compatibility
            MemoryElfLoader elfLoader = new MemoryElfLoader(kernel.getMemory(), elfData);
            elfLoader.load();

            int entryPoint = elfLoader.getEntryPoint();
            int stackSize = kernel.getConfig().getStackSize();
            int stackBase = kernelMemory.allocateStack(pid, stackSize);

            Task task = new Task(pid, name, entryPoint, stackSize, stackBase);
            TaskMemoryInfo memInfo = new TaskMemoryInfo(pid, entryPoint, stackBase, stackSize);
            taskMemory.put(pid, memInfo);

            return task;
        }
    }

    /**
     * Clean up task resources
     */
    public void cleanupTask(Task task) {
        int pid = task.getId();

        // Free task memory
        if (kernel.getMemory() instanceof cse311.paging.PagedMemoryManager) {
            cse311.paging.PagedMemoryManager pm = (cse311.paging.PagedMemoryManager) kernel.getMemory();
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
        if (kernel.getMemory() instanceof cse311.paging.PagedMemoryManager) {
            cse311.paging.PagedMemoryManager pm = (cse311.paging.PagedMemoryManager) kernel.getMemory();
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
     * Simple in-memory ELF loader for byte arrays
     */
    private static class MemoryElfLoader {
        private final MemoryManager memory;
        private final byte[] elfData;
        private int entryPoint;

        public MemoryElfLoader(MemoryManager memory, byte[] elfData) {
            this.memory = memory;
            this.elfData = elfData;
        }

        public void load() throws Exception {
            // Simple ELF loading - for now just copy to a fixed address
            // In a real implementation, this would parse ELF headers properly

            // Assume entry point is at the beginning of the loaded code
            int loadAddress = 0x10000; // Fixed load address for simplicity
            entryPoint = loadAddress;

            // Copy ELF data to memory
            for (int i = 0; i < elfData.length; i++) {
                memory.writeByteToVirtualAddress(loadAddress + i, elfData[i]);
            }
        }

        public int getEntryPoint() {
            return entryPoint;
        }
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