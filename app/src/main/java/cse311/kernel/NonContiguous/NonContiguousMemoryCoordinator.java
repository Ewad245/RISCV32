package cse311.kernel.NonContiguous;

import cse311.kernel.memory.ProcessMemoryCoordinator;
import cse311.kernel.process.ProgramInfo;
import cse311.MemoryManager;
import cse311.Exception.MemoryAccessException;
import cse311.kernel.NonContiguous.paging.AddressSpace;
import cse311.kernel.NonContiguous.paging.PagedMemoryManager;

import java.util.ArrayList;
import java.util.List;

import cse311.ElfLoader;

/**
 * Handles ALL non-contiguous techniques (Paging, Segmentation).
 * Delegates the specific mapping logic to a 'mapper'.
 */
public class NonContiguousMemoryCoordinator implements ProcessMemoryCoordinator {

    private final NonContiguousMemoryMapper mapper;
    private final int stackSize;

    public NonContiguousMemoryCoordinator(NonContiguousMemoryMapper mapper, int stackSize) {
        this.mapper = mapper;
        this.stackSize = stackSize;
    }

    @Override
    public MemoryLayout allocateMemory(int pid, int sizeBytes) throws MemoryAccessException {
        // 1. Create the address space (Page Table or Segment Table)
        mapper.createAddressSpace(pid);

        // 2. Map the stack
        // Non-contiguous systems usually put stack at a fixed high virtual address
        int stackBase = mapper.mapStack(pid, stackSize);

        // 3. Set stack bounds on the AddressSpace for access validation
        MemoryManager mem = mapper.getMemoryInterface();
        if (mem instanceof PagedMemoryManager) {
            AddressSpace as = ((PagedMemoryManager) mem).getAddressSpace(pid);
            if (as != null) {
                as.setStackBase(stackBase);
            }
        }

        return new MemoryLayout(stackBase, stackSize);
    }

    @Override
    public ProgramInfo loadProgram(int pid, byte[] elfData) throws Exception {
        // 1. Switch context so we write to the correct tables
        mapper.switchContext(pid);

        // 2. Load ELF
        // We use the mapper's memory interface (which handles the translation)
        ElfLoader loader = new ElfLoader(mapper.getMemoryInterface());
        loader.loadElf(elfData);

        ProgramInfo info = loader.getProgramInfo();

        // Set initial heap limit on the AddressSpace so the pager
        // knows the valid heap boundary from the start.
        // The initial heap limit is heapStart page-aligned upward.
        MemoryManager mem = mapper.getMemoryInterface();
        if (mem instanceof PagedMemoryManager) {
            AddressSpace as = ((PagedMemoryManager) mem).getAddressSpace(pid);
            if (as != null) {
                int initialHeapLimit = (info.heapStart + 4095) & ~4095;
                as.setHeapLimit(initialHeapLimit);
            }
        }

        return info;
    }

    @Override
    public boolean expandHeap(int pid, int currentBreak, int newBreak) throws MemoryAccessException {
        MemoryManager mem = mapper.getMemoryInterface();
        if (!(mem instanceof PagedMemoryManager)) {
            // Non-paged non-contiguous (e.g. segmentation) — assume memory is available
            return true;
        }

        PagedMemoryManager pmm = (PagedMemoryManager) mem;
        AddressSpace as = pmm.getAddressSpace(pid);
        if (as == null) {
            return false;
        }

        // Calculate page-aligned boundaries
        int newPageLimit = (newBreak + 4095) & ~4095;

        // Update the AddressSpace's heap limit so the pager knows
        // these addresses are valid for demand allocation
        as.setHeapLimit(newPageLimit);

        // Demand paging: no frames are allocated here.
        // The DemandPager will allocate physical frames on first access,
        // and isValidAccess() will now permit these addresses.
        return true;
    }

    @Override
    public void freeMemory(int pid) {
        mapper.destroyAddressSpace(pid);
    }

    @Override
    public void copyMemory(int parentPid, int childPid) throws MemoryAccessException {
        mapper.copyAddressSpace(parentPid, childPid);
    }

    @Override
    public void switchContext(int pid) {
        mapper.switchContext(pid);
    }

    @Override
    public int setupStack(int pid, List<String> args, MemoryLayout layout) throws MemoryAccessException {
        // Switch context to ensure we write to the correct address space
        mapper.switchContext(pid);
        MemoryManager memory = mapper.getMemoryInterface();

        // Calculate Top of Stack (Virtual Address)
        // In Non-Contiguous, this is usually fixed at top of memory
        int sp = layout.stackBase + layout.stackSize;

        // Write arguments to stack (Standard RISC-V convention)
        List<Integer> argvPtrs = new ArrayList<>();

        // 1. Push Strings
        for (String arg : args) {
            byte[] bytes = (arg + "\0").getBytes();
            sp -= bytes.length;
            sp &= ~0xF; // 16-byte alignment
            for (int i = 0; i < bytes.length; i++) {
                memory.writeByteToVirtualAddress(sp + i, bytes[i]);
            }
            argvPtrs.add(sp);
        }

        // 2. Push Pointers (argv array)
        int argvBase = sp - (args.size() + 1) * 4;
        argvBase &= ~0xF;
        sp = argvBase;

        for (int i = 0; i < args.size(); i++) {
            memory.writeWord(sp + (i * 4), argvPtrs.get(i));
        }
        memory.writeWord(sp + (args.size() * 4), 0); // Null terminator

        return sp;
    }
}
