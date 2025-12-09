package cse311.kernel.contiguous;

import java.util.ArrayList;
import java.util.List;

import cse311.ElfLoader;
import cse311.MemoryManager;
import cse311.Exception.MemoryAccessException;
import cse311.kernel.memory.ProcessMemoryCoordinator;

public class ContiguousMemoryCoordinator implements ProcessMemoryCoordinator {

    private final ContiguousMemoryManager manager;
    private final int defaultStackSize;

    public ContiguousMemoryCoordinator(ContiguousMemoryManager manager, int stackSize) {
        this.manager = manager;
        this.defaultStackSize = stackSize;
    }

    @Override
    public MemoryLayout allocateMemory(int pid, int sizeBytes) throws MemoryAccessException {
        // 1. Ask the MMU to find a hole (First-Fit, Best-Fit, etc.)
        // We need space for ELF + Stack + potentially Heap
        boolean success = manager.allocateMemory(pid, sizeBytes);

        if (!success) {
            throw new MemoryAccessException(
                    "Contiguous Allocation Failed: Not enough memory or fragmentation too high.");
        }

        // 2. Define Stack Location (For contiguous, usually at the end of the
        // partition)
        // Virtual Address logic: Base is 0 relative to the partition
        int stackBase = sizeBytes - defaultStackSize;

        return new MemoryLayout(stackBase, defaultStackSize);
    }

    @Override
    public int loadProgram(int pid, byte[] elfData) throws Exception {
        // 1. Set Hardware Context (Base/Limit)
        manager.switchContext(pid);

        // 2. It writes to "Virtual Addresses" (e.g., 0x10000),
        // and ContiguousMemoryManager adds the Base Register automatically.
        ElfLoader loader = new ElfLoader(manager);
        loader.loadElf(elfData);

        // Note: For contiguous, the "Virtual" entry point is what we want
        // (e.g. 0x10000), because PC is relative to Base.
        return loader.getEntryPoint();
    }

    @Override
    public void freeMemory(int pid) {
        manager.freeMemory(pid);
    }

    @Override
    public void copyMemory(int parentPid, int childPid) throws MemoryAccessException {
        // Delegate to the manager to physically copy bytes from Parent -> Child
        // partition
        boolean success = manager.copyMemory(parentPid, childPid);

        if (!success) {
            throw new MemoryAccessException(
                    "Fork failed: Could not copy contiguous memory from PID " + parentPid + " to " + childPid);
        }
    }

    @Override
    public void switchContext(int pid) {
        manager.switchContext(pid);
    }

    @Override
    public int setupStack(int pid, List<String> args, MemoryLayout layout) throws MemoryAccessException {
        manager.switchContext(pid);

        // Calculate Top of Stack relative to Base
        // For contiguous, we need to know the partition size to find the top.
        // Assuming we allocated what was requested + buffer, or we can look it up.
        // For simplicity, let's assume the stack sits at a high relative address.
        // Ideally, allocateMemory should store the limit in a map this class can
        // access.
        // Workaround: We'll assume a fixed relative top for this demo,
        // or re-calculate based on what we passed to allocateMemory.

        // Let's use the MemoryLayout convention: stackBase + stackSize = top
        // But we don't have the layout here.
        // FIX: The caller (handleExec) should probably pass the layout or we store it.
        // However, standard Linux stack grows down from top of User Space.
        // In this sim, let's assume relative 0x00100000 (1MB) is the top for now.
        int sp = layout.stackBase + layout.stackSize;

        // Reuse the exact same logic as NonContiguous, because
        // physMem.writeByteToVirtualAddress handles the Base Register addition!

        List<Integer> argvPtrs = new ArrayList<>();
        for (String arg : args) {
            byte[] bytes = (arg + "\0").getBytes();
            sp -= bytes.length;
            sp &= ~0xF;
            for (int i = 0; i < bytes.length; i++) {
                manager.writeByteToVirtualAddress(sp + i, bytes[i]);
            }
            argvPtrs.add(sp);
        }

        int argvBase = sp - (args.size() + 1) * 4;
        argvBase &= ~0xF;
        sp = argvBase;

        for (int i = 0; i < args.size(); i++) {
            manager.writeWord(sp + (i * 4), argvPtrs.get(i));
        }
        manager.writeWord(sp + (args.size() * 4), 0);

        return sp;
    }
}
