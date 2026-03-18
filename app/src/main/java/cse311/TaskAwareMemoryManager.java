package cse311;

import cse311.Exception.MemoryAccessException;

/**
 * Task-aware memory manager that provides individual address spaces for each
 * task
 * Implements the MemoryManager interface while providing task isolation
 */
public class TaskAwareMemoryManager extends MemoryManager {
    private final VirtualMemoryManager virtualMemoryManager;
    private int currentTaskId = -1; // Currently active task

    public TaskAwareMemoryManager(int memorySize, int maxTasks) {
        super(); // Initialize parent with minimal memory since we're overriding everything
        this.virtualMemoryManager = new VirtualMemoryManager(memorySize, maxTasks);
    }

    /**
     * Set the currently active task (used by kernel during context switches)
     */
    public void setCurrentTask(int taskId) {
        this.currentTaskId = taskId;
    }

    /**
     * Get the currently active task
     */
    public int getCurrentTask() {
        return currentTaskId;
    }

    /**
     * Allocate memory space for a new task
     */
    public boolean allocateTaskMemory(int taskId) {
        return virtualMemoryManager.allocateTaskMemory(taskId);
    }

    /**
     * Deallocate memory space for a task
     */
    public void deallocateTaskMemory(int taskId) {
        virtualMemoryManager.deallocateTaskMemory(taskId);
    }

    /**
     * Load program into specific task's memory space
     */
    public void loadProgramForTask(int taskId, byte[] programData, int loadAddress) throws MemoryAccessException {
        virtualMemoryManager.loadProgram(taskId, programData, loadAddress);
    }

    // Override MemoryManager methods to use current task's address space

    @Override
    public byte readByte(int address) throws MemoryAccessException {
        if (currentTaskId == -1) {
            throw new MemoryAccessException("No active task set for memory access");
        }
        return virtualMemoryManager.readByte(currentTaskId, address);
    }

    @Override
    public short readHalfWord(int address) throws MemoryAccessException {
        if (currentTaskId == -1) {
            throw new MemoryAccessException("No active task set for memory access");
        }
        return virtualMemoryManager.readHalfWord(currentTaskId, address);
    }

    @Override
    public int readWord(int address) throws MemoryAccessException {
        if (currentTaskId == -1) {
            throw new MemoryAccessException("No active task set for memory access");
        }
        return virtualMemoryManager.readWord(currentTaskId, address);
    }

    @Override
    public void writeByte(int address, byte value) throws MemoryAccessException {
        if (currentTaskId == -1) {
            throw new MemoryAccessException("No active task set for memory access");
        }
        virtualMemoryManager.writeByte(currentTaskId, address, value);
    }

    @Override
    public void writeHalfWord(int address, short value) throws MemoryAccessException {
        if (currentTaskId == -1) {
            throw new MemoryAccessException("No active task set for memory access");
        }
        virtualMemoryManager.writeHalfWord(currentTaskId, address, value);
    }

    @Override
    public void writeWord(int address, int value) throws MemoryAccessException {
        if (currentTaskId == -1) {
            throw new MemoryAccessException("No active task set for memory access");
        }
        virtualMemoryManager.writeWord(currentTaskId, address, value);
    }

    @Override
    public void writeByteToVirtualAddress(int address, byte value) throws MemoryAccessException {
        // For ELF loading, we need to specify which task we're loading into
        if (currentTaskId == -1) {
            throw new MemoryAccessException("No active task set for ELF loading");
        }
        virtualMemoryManager.writeByte(currentTaskId, address, value);
    }

    // Task-specific memory operations

    /**
     * Read byte from specific task's memory (useful for kernel operations)
     */
    public byte readByteFromTask(int taskId, int address) throws MemoryAccessException {
        return virtualMemoryManager.readByte(taskId, address);
    }

    /**
     * Write byte to specific task's memory (useful for kernel operations)
     */
    public void writeByteToTask(int taskId, int address, byte value) throws MemoryAccessException {
        virtualMemoryManager.writeByte(taskId, address, value);
    }

    /**
     * Read word from specific task's memory
     */
    public int readWordFromTask(int taskId, int address) throws MemoryAccessException {
        return virtualMemoryManager.readWord(taskId, address);
    }

    /**
     * Write word to specific task's memory
     */
    public void writeWordToTask(int taskId, int address, int value) throws MemoryAccessException {
        virtualMemoryManager.writeWord(taskId, address, value);
    }

    // Memory management utilities

    @Override
    public void getInput(String data) {
        virtualMemoryManager.getInput(data);
    }

    /**
     * Get memory statistics for a specific task
     */
    public VirtualMemoryManager.TaskMemoryStats getTaskMemoryStats(int taskId) {
        return virtualMemoryManager.getTaskMemoryStats(taskId);
    }

    /**
     * Get overall virtual memory statistics
     */
    public VirtualMemoryManager.VirtualMemoryStats getVirtualMemoryStats() {
        return virtualMemoryManager.getMemoryStats();
    }

    /**
     * Check if a task has allocated memory
     */
    public boolean hasTaskMemory(int taskId) {
        return virtualMemoryManager.hasTaskMemory(taskId);
    }

    /**
     * Get the number of tasks with allocated memory
     */
    public int getAllocatedTaskCount() {
        return virtualMemoryManager.getAllocatedTaskCount();
    }

    /**
     * Get the stack start address
     */
    public int getStackStart() {
        return virtualMemoryManager.getStackStart();
    }

    @Override
    public String getMemoryMap() {
        StringBuilder sb = new StringBuilder();
        sb.append("Task-Aware Memory Layout:\n");
        sb.append("Each task has individual address space:\n");
        sb.append(String.format("  Text:  0x%08X - 0x%08X\n",
                VirtualMemoryManager.TEXT_START, VirtualMemoryManager.DATA_START - 1));
        sb.append(String.format("  Data:  0x%08X - 0x%08X\n",
                VirtualMemoryManager.DATA_START, VirtualMemoryManager.HEAP_START - 1));
        sb.append(String.format("  Heap:  0x%08X - 0x%08X\n",
                VirtualMemoryManager.HEAP_START, virtualMemoryManager.getStackStart() - 1));
        sb.append(String.format("  Stack: 0x%08X - 0x%08X\n",
                virtualMemoryManager.getStackStart(),
                virtualMemoryManager.getStackStart() + VirtualMemoryManager.STACK_SIZE - 1));
        sb.append(String.format("Shared Memory:\n"));
        sb.append(String.format("  UART:  0x%08X - 0x%08X\n",
                VirtualMemoryManager.UART_BASE,
                VirtualMemoryManager.UART_BASE + VirtualMemoryManager.UART_SIZE - 1));
        sb.append("\n");
        sb.append(getVirtualMemoryStats().toString());
        return sb.toString();
    }

    @Override
    public String dumpMemory() throws MemoryAccessException {
        if (currentTaskId == -1) {
            return "No active task set for memory dump";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Memory dump for task ").append(currentTaskId).append(":\n");

        // Dump first 1KB of each major section
        sb.append("Text section (first 256 bytes):\n");
        sb.append(dumpMemorySection(VirtualMemoryManager.TEXT_START, 256));

        sb.append("\nData section (first 256 bytes):\n");
        sb.append(dumpMemorySection(VirtualMemoryManager.DATA_START, 256));

        sb.append("\nStack section (last 256 bytes):\n");
        sb.append(dumpMemorySection(virtualMemoryManager.getStackStart() + VirtualMemoryManager.STACK_SIZE - 256, 256));

        return sb.toString();
    }

    private String dumpMemorySection(int startAddr, int length) {
        StringBuilder sb = new StringBuilder();
        try {
            for (int i = 0; i < length; i += 16) {
                sb.append(String.format("%08X: ", startAddr + i));

                // Hex bytes
                for (int j = 0; j < 16 && i + j < length; j++) {
                    byte b = readByte(startAddr + i + j);
                    sb.append(String.format("%02X ", b & 0xFF));
                }

                // ASCII representation
                sb.append(" |");
                for (int j = 0; j < 16 && i + j < length; j++) {
                    byte b = readByte(startAddr + i + j);
                    char c = (b >= 32 && b <= 126) ? (char) b : '.';
                    sb.append(c);
                }
                sb.append("|\n");
            }
        } catch (MemoryAccessException e) {
            sb.append("Error reading memory: ").append(e.getMessage()).append("\n");
        }
        return sb.toString();
    }
}