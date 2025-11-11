package cse311;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Virtual Memory Manager that provides individual address spaces for each task
 * Uses a 2D array approach where each task has its own memory space
 */
public class VirtualMemoryManager {
    // 2D array: [taskId][address] = byte value
    private final Map<Integer, byte[]> taskMemorySpaces = new ConcurrentHashMap<>();
    
    // Shared memory regions (like UART) that all tasks can access
    private final byte[] sharedMemory;
    
    // Configuration
    private final int memorySize;
    private final int maxTasks;
    
    // Memory layout constants per task
    public final int TASK_MEMORY_SIZE; // Calculated based on total memory and max tasks
    public static final int TEXT_START = 0x00010000;
    public static final int DATA_START = 0x00020000;
    public static final int HEAP_START = 0x00030000;
    public final int STACK_START; // Calculated based on task memory size
    public static final int STACK_SIZE = 0x00100000; // 1MB stack
    
    // Shared memory regions (same for all tasks)
    public static final int UART_BASE = 0x10000000;
    public static final int UART_SIZE = 0x1000;
    
    private Uart uart;
    
    public VirtualMemoryManager(int memorySize, int maxTasks) {
        this.memorySize = memorySize;
        this.maxTasks = maxTasks;
        this.TASK_MEMORY_SIZE = Math.max(memorySize / maxTasks, 1024 * 1024); // At least 1MB per task
        this.STACK_START = Math.max(TASK_MEMORY_SIZE - STACK_SIZE, HEAP_START + 0x10000); // Stack at end of memory
        this.sharedMemory = new byte[UART_SIZE]; // Shared memory for UART
        this.uart = new Uart();
        
        System.out.println("VirtualMemoryManager initialized:");
        System.out.println("  Memory per task: " + (TASK_MEMORY_SIZE / (1024 * 1024)) + "MB");
        System.out.println("  Max tasks: " + maxTasks);
        System.out.println("  Total virtual memory: " + (TASK_MEMORY_SIZE * maxTasks / (1024 * 1024)) + "MB");
    }
    
    /**
     * Get the stack start address for this memory manager
     */
    public int getStackStart() {
        return STACK_START;
    }
    
    /**
     * Allocate memory space for a new task
     */
    public boolean allocateTaskMemory(int taskId) {
        if (taskMemorySpaces.containsKey(taskId)) {
            System.err.println("Task " + taskId + " already has allocated memory");
            return false;
        }
        
        if (taskMemorySpaces.size() >= maxTasks) {
            System.err.println("Maximum number of tasks (" + maxTasks + ") reached");
            return false;
        }
        
        // Allocate memory space for this task
        byte[] taskMemory = new byte[TASK_MEMORY_SIZE];
        taskMemorySpaces.put(taskId, taskMemory);
        
        // Initialize stack area to zero
        initializeTaskMemory(taskId);
        
        System.out.println("Allocated " + (TASK_MEMORY_SIZE / (1024 * 1024)) + "MB memory for task " + taskId);
        return true;
    }
    
    /**
     * Deallocate memory space for a task
     */
    public void deallocateTaskMemory(int taskId) {
        byte[] memory = taskMemorySpaces.remove(taskId);
        if (memory != null) {
            System.out.println("Deallocated memory for task " + taskId);
        }
    }
    
    /**
     * Initialize task memory with default values
     */
    private void initializeTaskMemory(int taskId) {
        byte[] memory = taskMemorySpaces.get(taskId);
        if (memory == null) return;
        
        // Initialize stack area (fill with zeros)
        int stackEnd = STACK_START + STACK_SIZE;
        for (int i = STACK_START; i < stackEnd && i < memory.length; i++) {
            memory[i] = 0;
        }
    }
    
    /**
     * Read a byte from task's virtual address space
     */
    public byte readByte(int taskId, int virtualAddress) throws MemoryAccessException {
        // Check if it's a shared memory region (UART)
        if (isSharedMemoryAddress(virtualAddress)) {
            return readSharedMemory(virtualAddress);
        }
        
        // Read from task's private memory
        byte[] taskMemory = getTaskMemory(taskId);
        validateAddress(virtualAddress, taskMemory.length);
        
        return taskMemory[virtualAddress];
    }
    
    /**
     * Write a byte to task's virtual address space
     */
    public void writeByte(int taskId, int virtualAddress, byte value) throws MemoryAccessException {
        // Check if it's a shared memory region (UART)
        if (isSharedMemoryAddress(virtualAddress)) {
            writeSharedMemory(virtualAddress, value);
            return;
        }
        
        // Write to task's private memory
        byte[] taskMemory = getTaskMemory(taskId);
        validateAddress(virtualAddress, taskMemory.length);
        validateWriteAccess(virtualAddress);
        
        taskMemory[virtualAddress] = value;
    }
    
    /**
     * Read a half-word (16-bit) from task's virtual address space
     */
    public short readHalfWord(int taskId, int virtualAddress) throws MemoryAccessException {
        if (isSharedMemoryAddress(virtualAddress)) {
            return (short) uart.read(virtualAddress);
        }
        
        byte[] taskMemory = getTaskMemory(taskId);
        validateAddress(virtualAddress + 1, taskMemory.length);
        
        return (short) ((taskMemory[virtualAddress + 1] & 0xFF) << 8 |
                       (taskMemory[virtualAddress] & 0xFF));
    }
    
    /**
     * Write a half-word (16-bit) to task's virtual address space
     */
    public void writeHalfWord(int taskId, int virtualAddress, short value) throws MemoryAccessException {
        if (isSharedMemoryAddress(virtualAddress)) {
            uart.write(virtualAddress, value);
            return;
        }
        
        byte[] taskMemory = getTaskMemory(taskId);
        validateAddress(virtualAddress + 1, taskMemory.length);
        validateWriteAccess(virtualAddress);
        
        taskMemory[virtualAddress] = (byte) (value & 0xFF);
        taskMemory[virtualAddress + 1] = (byte) ((value >> 8) & 0xFF);
    }
    
    /**
     * Read a word (32-bit) from task's virtual address space
     */
    public int readWord(int taskId, int virtualAddress) throws MemoryAccessException {
        if (isSharedMemoryAddress(virtualAddress)) {
            return uart.read(virtualAddress);
        }
        
        byte[] taskMemory = getTaskMemory(taskId);
        validateAddress(virtualAddress + 3, taskMemory.length);
        
        return (taskMemory[virtualAddress + 3] & 0xFF) << 24 |
               (taskMemory[virtualAddress + 2] & 0xFF) << 16 |
               (taskMemory[virtualAddress + 1] & 0xFF) << 8 |
               (taskMemory[virtualAddress] & 0xFF);
    }
    
    /**
     * Write a word (32-bit) to task's virtual address space
     */
    public void writeWord(int taskId, int virtualAddress, int value) throws MemoryAccessException {
        if (isSharedMemoryAddress(virtualAddress)) {
            uart.write(virtualAddress, value);
            return;
        }
        
        byte[] taskMemory = getTaskMemory(taskId);
        validateAddress(virtualAddress + 3, taskMemory.length);
        validateWriteAccess(virtualAddress);
        
        taskMemory[virtualAddress] = (byte) (value & 0xFF);
        taskMemory[virtualAddress + 1] = (byte) ((value >> 8) & 0xFF);
        taskMemory[virtualAddress + 2] = (byte) ((value >> 16) & 0xFF);
        taskMemory[virtualAddress + 3] = (byte) ((value >> 24) & 0xFF);
    }
    
    /**
     * Load program data into task's memory space
     */
    public void loadProgram(int taskId, byte[] programData, int loadAddress) throws MemoryAccessException {
        byte[] taskMemory = getTaskMemory(taskId);
        
        if (loadAddress + programData.length > taskMemory.length) {
            throw new MemoryAccessException("Program too large for task memory space");
        }
        
        System.arraycopy(programData, 0, taskMemory, loadAddress, programData.length);
        System.out.println("Loaded " + programData.length + " bytes into task " + taskId + " at address 0x" + 
                          Integer.toHexString(loadAddress));
    }
    
    /**
     * Get task's memory space
     */
    private byte[] getTaskMemory(int taskId) throws MemoryAccessException {
        byte[] taskMemory = taskMemorySpaces.get(taskId);
        if (taskMemory == null) {
            throw new MemoryAccessException("Task " + taskId + " has no allocated memory space");
        }
        return taskMemory;
    }
    
    /**
     * Check if address is in shared memory region
     */
    private boolean isSharedMemoryAddress(int address) {
        return address >= UART_BASE && address < UART_BASE + UART_SIZE;
    }
    
    /**
     * Read from shared memory (UART)
     */
    private byte readSharedMemory(int address) {
        return (byte) uart.read(address);
    }
    
    /**
     * Write to shared memory (UART)
     */
    private void writeSharedMemory(int address, byte value) {
        uart.write(address, value);
    }
    
    /**
     * Validate memory address
     */
    private void validateAddress(int address, int memorySize) throws MemoryAccessException {
        if (address < 0 || address >= memorySize) {
            throw new MemoryAccessException("Invalid memory address: 0x" + Integer.toHexString(address) + 
                                          " (memory size: 0x" + Integer.toHexString(memorySize) + ")");
        }
    }
    
    /**
     * Validate write access (basic protection)
     */
    private void validateWriteAccess(int address) throws MemoryAccessException {
        // Protect text segment from writes (basic protection)
        if (address >= TEXT_START && address < DATA_START) {
            throw new MemoryAccessException("Cannot write to text segment at address: 0x" + 
                                          Integer.toHexString(address));
        }
    }
    
    /**
     * Get memory statistics for a task
     */
    public TaskMemoryStats getTaskMemoryStats(int taskId) {
        byte[] taskMemory = taskMemorySpaces.get(taskId);
        if (taskMemory == null) {
            return null;
        }
        
        return new TaskMemoryStats(taskId, taskMemory.length, TASK_MEMORY_SIZE);
    }
    
    /**
     * Get overall memory statistics
     */
    public VirtualMemoryStats getMemoryStats() {
        int allocatedTasks = taskMemorySpaces.size();
        long totalAllocated = (long) allocatedTasks * TASK_MEMORY_SIZE;
        long totalVirtual = (long) maxTasks * TASK_MEMORY_SIZE;
        
        return new VirtualMemoryStats(allocatedTasks, maxTasks, totalAllocated, totalVirtual);
    }
    
    /**
     * Get input for UART
     */
    public void getInput(String data) {
        uart.receiveDatas(data.getBytes());
    }
    
    /**
     * Check if task has allocated memory
     */
    public boolean hasTaskMemory(int taskId) {
        return taskMemorySpaces.containsKey(taskId);
    }
    
    /**
     * Get the number of allocated tasks
     */
    public int getAllocatedTaskCount() {
        return taskMemorySpaces.size();
    }
    
    /**
     * Memory statistics for a specific task
     */
    public static class TaskMemoryStats {
        public final int taskId;
        public final int usedMemory;
        public final int totalMemory;
        
        public TaskMemoryStats(int taskId, int usedMemory, int totalMemory) {
            this.taskId = taskId;
            this.usedMemory = usedMemory;
            this.totalMemory = totalMemory;
        }
        
        @Override
        public String toString() {
            return String.format("Task %d: %d/%d bytes (%.1f%% used)", 
                               taskId, usedMemory, totalMemory, 
                               (usedMemory * 100.0) / totalMemory);
        }
    }
    
    /**
     * Overall virtual memory statistics
     */
    public static class VirtualMemoryStats {
        public final int allocatedTasks;
        public final int maxTasks;
        public final long totalAllocatedMemory;
        public final long totalVirtualMemory;
        
        public VirtualMemoryStats(int allocatedTasks, int maxTasks, 
                                long totalAllocatedMemory, long totalVirtualMemory) {
            this.allocatedTasks = allocatedTasks;
            this.maxTasks = maxTasks;
            this.totalAllocatedMemory = totalAllocatedMemory;
            this.totalVirtualMemory = totalVirtualMemory;
        }
        
        @Override
        public String toString() {
            return String.format("Virtual Memory: %d/%d tasks, %dMB/%dMB allocated", 
                               allocatedTasks, maxTasks,
                               totalAllocatedMemory / (1024 * 1024),
                               totalVirtualMemory / (1024 * 1024));
        }
    }
}