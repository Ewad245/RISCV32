package cse311;

import cse311.Enum.MemoryMode;
import cse311.kernel.Kernel;
import cse311.kernel.NonContiguous.paging.*;
import cse311.kernel.contiguous.AllocationStrategy;
import cse311.kernel.contiguous.BestFitStrategy;
import cse311.kernel.contiguous.ContiguousMemoryManager;
import cse311.kernel.process.Task;

public class RV32Computer {
    private RV32Cpu cpu;
    private MemoryManager memory;
    private Kernel kernel;

    public RV32Computer(int memSize) {
        this(memSize, 16, MemoryMode.PAGING); // Default to 16 max tasks
    }

    public RV32Computer(int memSize, int maxTasks, MemoryMode mode) {
        System.out.println("--- Booting RV32iComputer in " + mode + " Mode ---");

        // 1. Initialize Memory Hardware & Logic
        if (mode == MemoryMode.PAGING) {
            // --- PAGING SETUP ---
            PagedMemoryManager pmm = new PagedMemoryManager(memSize);

            // Configure Policies (Demand Paging + Clock Replacement)
            PagingConfiguration.configure(pmm,
                    PagingConfiguration.Policy.DEMAND,
                    PagingConfiguration.Policy.CLOCK);
            this.memory = pmm;

        } else {
            // --- CONTIGUOUS SETUP ---
            // 1. Choose an Allocation Strategy (First Fit / Best Fit)
            AllocationStrategy allocator = new BestFitStrategy();
            // AllocationStrategy allocator = new FirstFitStrategy();

            // 2. Create the Memory Manager with this strategy
            this.memory = new ContiguousMemoryManager(memSize, allocator);
        }

        // 2. Initialize CPU (Polymorphic: works with either memory)
        this.cpu = new RV32Cpu(memory);

        // 3. Initialize Kernel (Kernel constructor detects memory type)
        this.kernel = new Kernel(cpu, memory);

        this.cpu.turnOn();

        System.out.println("System Initialized. Memory: " + (memSize / 1024 / 1024) + "MB");
    }

    /**
     * Creates a new task with the specified entry point.
     * Task creation is now handled by the kernel for proper process management.
     * 
     * @param entryPoint The entry point (initial PC value)
     * @return The created Task object, or null if task creation failed
     */
    public Task createTask(int entryPoint) {
        try {
            // Create a simple program that starts at the entry point
            byte[] simpleProgram = createSimpleProgram(entryPoint);
            return kernel.createTask(simpleProgram, "task_" + entryPoint);
        } catch (Exception e) {
            System.err.println("Failed to create task: " + e.getMessage());
            return null;
        }
    }

    /**
     * Creates a task from ELF data
     * 
     * @param elfData The ELF program data
     * @param name    The task name
     * @return The created Task object, or null if task creation failed
     */
    public Task createTask(byte[] elfData, String name) {
        try {
            return kernel.createTask(elfData, name);
        } catch (Exception e) {
            System.err.println("Failed to create task: " + e.getMessage());
            return null;
        }
    }

    /**
     * Creates a simple program that jumps to the specified entry point
     */
    private byte[] createSimpleProgram(int entryPoint) {
        // Create a simple program that sets PC to entry point and then exits
        // This is a basic implementation - in practice you'd load actual ELF data
        return new byte[] {
                // For now, just create a program that exits immediately
                // li a7, 93 (exit syscall)
                0x13, 0x08, (byte) 0xD0, 0x05, // addi a7, zero, 93
                // li a0, 0 (exit code)
                0x13, 0x05, 0x00, 0x00, // addi a0, zero, 0
                // ecall
                0x73, 0x00, 0x00, 0x00 // ecall
        };
    }

    /**
     * Gets the CPU.
     * 
     * @return The CPU
     */
    public RV32Cpu getCpu() {
        return cpu;
    }

    /**
     * Gets the memory manager.
     * 
     * @return The memory manager
     */
    public MemoryManager getMemoryManager() {
        return memory;
    }

    /**
     * Gets the task-aware memory manager (if available).
     * 
     * @return The task-aware memory manager, or null if not using task-aware memory
     */
    public TaskAwareMemoryManager getTaskAwareMemoryManager() {
        return (memory instanceof TaskAwareMemoryManager) ? (TaskAwareMemoryManager) memory : null;
    }

    /**
     * Gets the kernel.
     * 
     * @return The kernel
     */
    public Kernel getKernel() {
        return kernel;
    }

    /**
     * Starts the kernel and begins task execution
     */
    public void start() {
        kernel.start();
    }

    /**
     * Stops the kernel
     */
    public void stop() {
        kernel.stop();
    }

    @Override
    public String toString() {
        return "RV32iComputer [cpu=" + cpu + ", memory=" + memory + ", kernel=" + kernel + "]";
    }
}
