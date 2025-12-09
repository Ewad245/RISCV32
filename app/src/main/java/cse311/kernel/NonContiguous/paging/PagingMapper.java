package cse311.kernel.NonContiguous.paging;

import cse311.MemoryManager;
import cse311.Exception.MemoryAccessException;
import cse311.kernel.NonContiguous.NonContiguousMemoryMapper;

public class PagingMapper implements NonContiguousMemoryMapper {
    private final PagedMemoryManager pm;

    public PagingMapper(PagedMemoryManager pm) {
        this.pm = pm;
    }

    @Override
    public void createAddressSpace(int pid) {
        pm.createAddressSpace(pid);
    }

    @Override
    public int mapStack(int pid, int size) throws MemoryAccessException {
        AddressSpace as = pm.getAddressSpace(pid);
        final int STACK_TOP = 0x7FFF_F000;
        final int STACK_BASE = STACK_TOP - size;
        pm.mapRegion(as, STACK_BASE, size, true, true, false);
        return STACK_BASE;
    }

    @Override
    public void switchContext(int pid) {
        pm.switchTo(pm.getAddressSpace(pid));
    }

    @Override
    public void destroyAddressSpace(int pid) {
        pm.destroyAddressSpace(pid);
    }

    @Override
    public void copyAddressSpace(int parentPid, int childPid) throws MemoryAccessException {
        pm.copyAddressSpace(pm.getAddressSpace(parentPid), pm.getAddressSpace(childPid));
    }

    @Override
    public MemoryManager getMemoryInterface() {
        return pm;
    }
}
