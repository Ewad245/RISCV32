package cse311.kernel.memory;

import cse311.MemoryManager;
import cse311.TaskAwareMemoryManager;
import cse311.VirtualMemoryManager;
import cse311.MemoryAccessException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Kernel-level memory management
 * Handles process memory allocation, stack management, etc.
 */
public class KernelMemoryManager {
    private final MemoryManager memory;
    private final Map<Integer, StackInfo> processStacks = new ConcurrentHashMap<>();
    
    // Use the virtual memory manager's stack region
    private int nextStackOffset = 0; // Offset within the stack region
    
    public KernelMemoryManager(MemoryManager memory) {
        this.memory = memory;
    }
    
    /**
     * Allocate a stack for a process
     */
    public int allocateStack(int pid, int stackSize) throws MemoryAccessException {
        // Align stack size to page boundary (4KB)
        int alignedSize = (stackSize + 4095) & ~4095;
        
        if (memory instanceof TaskAwareMemoryManager) {
            TaskAwareMemoryManager taskMemory = (TaskAwareMemoryManager) memory;
            // Ensure task has memory allocated
            if (!taskMemory.hasTaskMemory(pid)) {
                taskMemory.allocateTaskMemory(pid);
            }
            
            // Use the virtual memory manager's stack region
            int stackStartBase = (memory instanceof TaskAwareMemoryManager) ? 
                ((TaskAwareMemoryManager) memory).getStackStart() : 
                0x00800000; // fallback
            int stackStart = stackStartBase + nextStackOffset;
            int stackBase = stackStart + alignedSize; // Top of stack
            
            // Initialize stack memory to zero
            for (int i = 0; i < alignedSize; i++) {
                taskMemory.writeByteToTask(pid, stackStart + i, (byte) 0);
            }
            
            // Store stack information
            StackInfo stackInfo = new StackInfo(stackStart, stackBase, alignedSize);
            processStacks.put(pid, stackInfo);
            
            // Update next stack offset for future allocations
            nextStackOffset += alignedSize + 4096; // Add guard page
            
            System.out.println("Allocated stack for PID " + pid + 
                              ": base=0x" + Integer.toHexString(stackBase) + 
                              ", size=" + alignedSize);
            
            return stackBase;
        } else {
            // Fallback for old memory manager
            throw new MemoryAccessException("Stack allocation requires TaskAwareMemoryManager");
        }
    }
    
    /**
     * Free a process stack
     */
    public void freeStack(int pid, int stackBase, int stackSize) {
        StackInfo stackInfo = processStacks.remove(pid);
        if (stackInfo != null) {
            // In a real implementation, we would mark this memory as free
            // For now, just remove from our tracking
            System.out.println("Freed stack for PID " + pid);
        }
    }
    
    /**
     * Get stack information for a process
     */
    public StackInfo getStackInfo(int pid) {
        return processStacks.get(pid);
    }
    
    /**
     * Allocate heap memory for a process
     */
    public int allocateHeap(int pid, int size) throws MemoryAccessException {
        if (memory instanceof TaskAwareMemoryManager) {
            TaskAwareMemoryManager taskMemory = (TaskAwareMemoryManager) memory;
            // Ensure task has memory allocated
            if (!taskMemory.hasTaskMemory(pid)) {
                taskMemory.allocateTaskMemory(pid);
            }
            // For now, return a simple heap address in the task's address space
            // In a real implementation, this would track heap allocations per task
            return 0x20000000; // Simple heap start address
        } else {
            return memory.allocateHeap(size);
        }
    }
    
    /**
     * Free heap memory for a process
     */
    public void freeHeap(int pid, int address) {
        if (memory instanceof TaskAwareMemoryManager) {
            // In a real implementation, this would free the heap memory
            // For now, just a placeholder
        } else {
            memory.free(address);
        }
    }
    
    /**
     * Check if an address is within a process's stack
     */
    public boolean isInProcessStack(int pid, int address) {
        StackInfo stackInfo = processStacks.get(pid);
        if (stackInfo == null) {
            return false;
        }
        
        return address >= stackInfo.stackStart && address < stackInfo.stackBase;
    }
    
    /**
     * Get memory statistics
     */
    public MemoryStats getMemoryStats() {
        int totalStacks = processStacks.size();
        int totalStackMemory = processStacks.values().stream()
                .mapToInt(s -> s.stackSize)
                .sum();
        
        return new MemoryStats(totalStacks, totalStackMemory);
    }
    
    /**
     * Stack information for a process
     */
    public static class StackInfo {
        public final int stackStart;  // Bottom of stack (lowest address)
        public final int stackBase;   // Top of stack (highest address)
        public final int stackSize;   // Size of stack in bytes
        
        public StackInfo(int stackStart, int stackBase, int stackSize) {
            this.stackStart = stackStart;
            this.stackBase = stackBase;
            this.stackSize = stackSize;
        }
    }
    
    /**
     * Memory statistics
     */
    public static class MemoryStats {
        public final int totalStacks;
        public final int totalStackMemory;
        
        public MemoryStats(int totalStacks, int totalStackMemory) {
            this.totalStacks = totalStacks;
            this.totalStackMemory = totalStackMemory;
        }
    }
}