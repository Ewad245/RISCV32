package cse311.kernel.process;

import java.util.ArrayList;
import java.util.List;

import cse311.RV32Cpu;
import cse311.WaitReason;

/**
 * Represents a task (process) in the simulated operating system.
 * Each task has its own program counter, register state, and stack.
 * Enhanced with kernel management features and process hierarchy.
 */
public class Task {
    private int id;
    private int pc; // Program counter
    private int[] registers; // Register state
    private int stackBase; // Base address of the task's stack
    private int stackSize; // Size of the stack in bytes
    private boolean active; // Whether the task is currently active

    // Process hierarchy (parent/child relationships)
    private Task parent; // Parent process
    private final List<Task> children; // Child processes
    private int tgid; // Thread group ID (same as PID for single-threaded)

    // Kernel management fields
    private TaskState state;
    private WaitReason waitReason;
    private long wakeupTime;
    private int waitingForPid;
    private String name;
    private int priority;
    private long creationTime;
    private long cpuTime;
    private int exitCode;

    // Process memory information
    private ProgramInfo meminfo;
    private int allocatedSize;

    // Generic context (AddressSpace or SegmentTable)
    private transient Object memoryContext;

    /**
     * Creates a new task with the specified ID and stack size.
     * 
     * @param id         The task ID
     * @param entryPoint The entry point (initial PC value)
     * @param stackSize  The size of the task's stack in bytes
     * @param stackBase  The base address of the task's stack
     */
    public Task(int id, int entryPoint, int stackSize, int stackBase, ProgramInfo info) {
        this.id = id;
        this.pc = entryPoint;
        this.meminfo = info;
        this.registers = new int[32]; // RV32I has 32 registers
        this.stackSize = stackSize;
        this.stackBase = stackBase;
        this.active = false;

        // Initialize process hierarchy
        this.parent = null;
        this.children = new ArrayList<>();
        this.tgid = id; // Thread group ID = PID for single-threaded

        // Initialize kernel management fields
        this.name = "Task-" + id;
        this.state = TaskState.READY;
        this.waitReason = WaitReason.NONE;
        this.priority = 0;
        this.creationTime = System.currentTimeMillis();
        this.cpuTime = 0;
        this.wakeupTime = 0;
        this.waitingForPid = -1;
        this.exitCode = 0;

        // Initialize stack pointer (x2) to the top of the stack
        this.registers[2] = stackBase + stackSize;
    }

    /**
     * Creates a new task with a name
     */
    public Task(int id, String name, int entryPoint, int stackSize, int stackBase, ProgramInfo info) {
        this(id, entryPoint, stackSize, stackBase, info);
        this.name = name;
        this.exitCode = 0;
    }

    /**
     * Saves the current CPU state to this task.
     * 
     * @param cpu The CPU whose state should be saved
     */
    public void saveState(RV32Cpu cpu) {
        // System.out.println("DEBUG: Saving task " + id + " state, PC=" +
        // cpu.getProgramCounter() + ", a0="
        // + cpu.getRegisters()[10]);
        this.pc = cpu.getProgramCounter();
        System.arraycopy(cpu.getRegisters(), 0, this.registers, 0, 32);
    }

    /**
     * Restores this task's state to the CPU.
     * 
     * @param cpu The CPU to restore the state to
     */
    public void restoreState(RV32Cpu cpu) {
        // System.out.println("DEBUG: Restoring task " + id + " state, PC=" + this.pc +
        // ", a0=" + this.registers[10]);
        cpu.setProgramCounter(this.pc);
        cpu.setRegisters(this.registers);
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public int getProgramCounter() {
        return pc;
    }

    public void setProgramCounter(int pc) {
        this.pc = pc;
    }

    public int[] getRegisters() {
        return registers;
    }

    public void setRegisters(int[] registers) {
        this.registers = registers;
    }

    public int getStackBase() {
        return stackBase;
    }

    public void setStackBase(int stackBase) {
        this.stackBase = stackBase;
    }

    public int getStackSize() {
        return stackSize;
    }

    public void setStackSize(int stackSize) {
        this.stackSize = stackSize;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    // Kernel management methods

    // State management
    public TaskState getState() {
        return state;
    }

    public void setState(TaskState state) {
        this.state = state;
    }

    // Wait management
    public WaitReason getWaitReason() {
        return waitReason;
    }

    public void setWaitReason(WaitReason waitReason) {
        this.waitReason = waitReason;
    }

    public long getWakeupTime() {
        return wakeupTime;
    }

    public void setWakeupTime(long wakeupTime) {
        this.wakeupTime = wakeupTime;
    }

    public int getWaitingForPid() {
        return waitingForPid;
    }

    public void setWaitingForPid(int waitingForPid) {
        this.waitingForPid = waitingForPid;
    }

    // Task information
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public long getCpuTime() {
        return cpuTime;
    }

    public void addCpuTime(long time) {
        this.cpuTime += time;
    }

    public void setMemoryContext(Object context) {
        this.memoryContext = context;
    }

    public Object getMemoryContext() {
        return this.memoryContext;
    }

    // Process hierarchy methods
    public Task getParent() {
        return parent;
    }

    public void setParent(Task parent) {
        this.parent = parent;
    }

    public List<Task> getChildren() {
        return new ArrayList<>(children);
    }

    public void addChild(Task child) {
        children.add(child);
        child.setParent(this);
    }

    public void removeChild(Task child) {
        children.remove(child);
        child.setParent(null);
    }

    public int getTgid() {
        return tgid;
    }

    public void setTgid(int tgid) {
        this.tgid = tgid;
    }

    public boolean isThread() {
        return tgid != id;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public int getAllocatedSize() {
        return allocatedSize;
    }

    public void setAllocatedSize(int allocatedSize) {
        this.allocatedSize = allocatedSize;
    }

    public ProgramInfo getProgramInfo() {
        return meminfo;
    }

    public void setProgramInfo(ProgramInfo meminfo) {
        this.meminfo = meminfo;
    }

    /**
     * Creates a new thread within this process's thread group
     * Threads share the same address space and TGID but have separate stacks
     */
    public Task createThread(int threadId, int entryPoint, int stackSize, int stackBase) {
        Task thread = new Task(threadId, entryPoint, stackSize, stackBase, this.meminfo);
        thread.setParent(this);
        thread.setTgid(this.tgid); // Same thread group
        thread.setMemoryContext(this.memoryContext); // Share address space
        thread.setName(this.name + ":" + threadId);
        return thread;
    }

    /**
     * Gets all threads in this thread group (including self if leader)
     */
    public List<Task> getThreadGroup() {
        List<Task> threads = new ArrayList<>();
        if (isThreadGroupLeader()) {
            // This is the leader, find all threads with same TGID
            threads.add(this);
            for (Task child : children) {
                if (child.getTgid() == this.tgid) {
                    threads.add(child);
                }
            }
        } else {
            // This is a thread, find the leader and all threads
            Task leader = getGroupLeader();
            if (leader != null) {
                threads.addAll(leader.getThreadGroup());
            }
        }
        return threads;
    }

    /**
     * Checks if this task is a thread group leader (main process)
     */
    public boolean isThreadGroupLeader() {
        return tgid == id;
    }

    /**
     * Get process group leader (self for single-threaded)
     */
    public Task getGroupLeader() {
        return isThread() ? getParent() : this;
    }

    /**
     * Check if this task is a descendant of another task
     */
    public boolean isDescendantOf(Task ancestor) {
        Task current = this;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Get the process tree depth (0 for init process)
     */
    public int getProcessDepth() {
        int depth = 0;
        Task current = this.getParent();
        while (current != null) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }

    /**
     * Wait for a specific condition
     */
    public void waitFor(WaitReason reason) {
        this.state = TaskState.WAITING;
        this.waitReason = reason;
    }

    /**
     * Wait for a specific time
     */
    public void waitFor(WaitReason reason, long wakeupTime) {
        this.state = TaskState.WAITING;
        this.waitReason = reason;
        this.wakeupTime = wakeupTime;
    }

    /**
     * Wait for another task to exit
     */
    public void waitForTask(int pid) {
        this.state = TaskState.WAITING;
        this.waitReason = WaitReason.PROCESS_EXIT;
        this.waitingForPid = pid;
    }

    /**
     * Wake up the task
     */
    public void wakeup() {
        if (state == TaskState.WAITING) {
            state = TaskState.READY;
            waitReason = WaitReason.NONE;
            wakeupTime = 0;
            waitingForPid = -1;
        }
    }

    /**
     * Check if task can be scheduled
     */
    public boolean isSchedulable() {
        return state == TaskState.READY;
    }

    /**
     * Get task status string
     */
    public String getStatusString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PID: ").append(getId());
        sb.append(", TGID: ").append(tgid);
        sb.append(", Name: ").append(name);
        sb.append(", State: ").append(state);
        if (state == TaskState.WAITING) {
            sb.append(" (").append(waitReason).append(")");
        }
        sb.append(", Priority: ").append(priority);
        sb.append(", CPU Time: ").append(cpuTime).append("ms");

        // Process hierarchy info
        if (parent != null) {
            sb.append(", Parent: ").append(parent.getId());
        }
        sb.append(", Children: ").append(children.size());
        sb.append(", Depth: ").append(getProcessDepth());

        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("Task[pid=%d, name=%s, state=%s]", getId(), name, state);
    }
}