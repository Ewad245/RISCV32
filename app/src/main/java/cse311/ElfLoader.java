package cse311;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import cse311.paging.AddressSpace;
import cse311.paging.PagedMemoryManager;

public class ElfLoader {
    private byte[] elfData;
    private MemoryManager memory;
    private List<ElfSegment> segments = new ArrayList<>();

    // ELF Header Constants
    private static final byte[] ELF_MAGIC = { 0x7f, 0x45, 0x4c, 0x46 }; // "\177ELF"
    private static final int EI_CLASS_64 = 2;
    private static final int EI_DATA_LE = 1;
    private static final int EM_RISCV = 243;

    // Program Header Types
    private static final int PT_LOAD = 1;

    // Section Header Types
    private static final int SHT_PROGBITS = 1;
    private static final int SHT_NOBITS = 8;

    // Program Header Flags
    private static final int PF_X = 1;
    private static final int PF_W = 2;
    private static final int PF_R = 4;

    /**
     * Represents an ELF segment with memory permissions
     */
    public static class ElfSegment {
        public final int virtualAddr;
        public final int fileSize;
        public final int memorySize;
        public final boolean readable;
        public final boolean writable;
        public final boolean executable;
        public final int fileOffset;

        public ElfSegment(int virtualAddr, int fileSize, int memorySize,
                boolean readable, boolean writable, boolean executable,
                int fileOffset) {
            this.virtualAddr = virtualAddr;
            this.fileSize = fileSize;
            this.memorySize = memorySize;
            this.readable = readable;
            this.writable = writable;
            this.executable = executable;
            this.fileOffset = fileOffset;
        }

        @Override
        public String toString() {
            return String.format("Segment[0x%08x-0x%08x] R=%b W=%b X=%b",
                    virtualAddr, virtualAddr + memorySize, readable, writable, executable);
        }
    }

    public ElfLoader(MemoryManager memory) {
        this.memory = memory;
    }

    public void loadElf(String filename) throws IOException, ElfException {
        elfData = Files.readAllBytes(Paths.get(filename));

        if (!validateElfHeader()) {
            throw new ElfException("Invalid ELF file");
        }

        loadProgramSegments();
    }

    private boolean validateElfHeader() {
        if (elfData.length < 52) { // Minimum size for 32-bit ELF header
            return false;
        }

        // Check magic number
        for (int i = 0; i < ELF_MAGIC.length; i++) {
            if (elfData[i] != ELF_MAGIC[i]) {
                return false;
            }
        }

        // Check ELF class (32-bit)
        if (elfData[4] != 1) {
            return false;
        }

        // Check endianness (little-endian)
        if (elfData[5] != EI_DATA_LE) {
            return false;
        }

        // Check machine type (RISC-V)
        ByteBuffer buffer = ByteBuffer.wrap(elfData).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(18);
        if (buffer.getShort() != EM_RISCV) {
            return false;
        }

        return true;
    }

    private void loadProgramSegments() throws ElfException {
        ByteBuffer buffer = ByteBuffer.wrap(elfData).order(ByteOrder.LITTLE_ENDIAN);

        // Get program header offset and number of entries
        buffer.position(28);
        int programHeaderOffset = buffer.getInt();
        buffer.position(42);
        int programHeaderEntrySize = buffer.getShort();
        int programHeaderEntryCount = buffer.getShort();

        // Process each program header
        for (int i = 0; i < programHeaderEntryCount; i++) {
            int offset = programHeaderOffset + (i * programHeaderEntrySize);
            buffer.position(offset);

            int type = buffer.getInt();
            if (type != PT_LOAD) {
                continue;
            }

            int offset_in_file = buffer.getInt();
            int virtual_addr = buffer.getInt();
            buffer.getInt(); // physical addr (unused)
            int size_in_file = buffer.getInt();
            int size_in_mem = buffer.getInt();
            int flags = buffer.getInt();
            int alignment = buffer.getInt();

            // Extract permissions from flags
            boolean readable = (flags & PF_R) != 0;
            boolean writable = (flags & PF_W) != 0;
            boolean executable = (flags & PF_X) != 0;

            // Create segment info
            ElfSegment segment = new ElfSegment(virtual_addr, size_in_file, size_in_mem,
                    readable, writable, executable, offset_in_file);
            segments.add(segment);

            try {
                loadSegment(segment);
            } catch (MemoryAccessException e) {
                throw new ElfException("Failed to load segment: " + e.getMessage());
            }
        }
    }

    private void loadSegment(ElfSegment segment) throws MemoryAccessException {
        // Use the virtual address directly from the ELF program header
        int loadAddr = segment.virtualAddr;

        if (segment.fileSize > 0) {
            byte[] segmentData = new byte[segment.fileSize];
            System.arraycopy(elfData, segment.fileOffset, segmentData, 0, segment.fileSize);

            // Load segment data to the specified virtual address
            for (int i = 0; i < segment.fileSize; i++) {
                memory.writeByteToVirtualAddress(loadAddr + i, segmentData[i]);
            }
        }

        // Zero-initialize remaining memory (BSS section)
        for (int i = segment.fileSize; i < segment.memorySize; i++) {
            memory.writeByteToVirtualAddress(loadAddr + i, (byte) 0);
        }
    }

    public int getEntryPoint() {
        ByteBuffer buffer = ByteBuffer.wrap(elfData).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(24);
        int entryPoint = buffer.getInt();
        // Return the entry point address directly from ELF header
        return entryPoint;
    }

    /**
     * Get all loaded segments with their permissions
     */
    public List<ElfSegment> getSegments() {
        return new ArrayList<>(segments);
    }

    /**
     * Load ELF segments into an AddressSpace with proper memory permissions
     * like Linux does
     */
    public void loadElfIntoAddressSpace(AddressSpace as, PagedMemoryManager mm, String elfFile)
            throws ElfException, MemoryAccessException, IOException {
        loadElf(elfFile);

        // Map segments into address space with proper permissions
        for (ElfSegment segment : segments) {
            mapSegmentWithPermissions(as, segment, mm);
        }
    }

    /**
     * Map a segment into address space with Linux-like permission handling
     */
    private void mapSegmentWithPermissions(AddressSpace as, ElfSegment segment, PagedMemoryManager mm)
            throws MemoryAccessException {
        int startAddr = segment.virtualAddr;
        int endAddr = segment.virtualAddr + segment.memorySize;

        // Align to page boundaries (Linux behavior)
        int pageSize = PagedMemoryManager.PAGE_SIZE;
        int startPage = startAddr / pageSize;
        int endPage = (endAddr + pageSize - 1) / pageSize;

        // Map each page with appropriate permissions
        for (int page = startPage; page < endPage; page++) {
            int vpn = page;
            int pageAddr = page * pageSize;

            // Determine permissions for this page based on segment permissions
            boolean read = segment.readable;
            boolean write = segment.writable;
            boolean exec = segment.executable;

            // Linux security: stack should not be executable
            if (pageAddr >= 0x7FFFFFF0) { // High address for stack
                exec = false;
            }

            // Map the page with extracted permissions
            if (!as.isPagePresent(vpn)) {
                // Allocate frame and map with permissions
                int frame = mm.allocateFrame();
                if (frame >= 0) {
                    as.mapPage(vpn, frame, write, exec);

                    // Zero-initialize pages (Linux behavior)
                    int frameStart = frame * pageSize;
                    for (int i = 0; i < pageSize; i++) {
                        mm.writeByte(frameStart + i, (byte) 0);
                    }
                }
            }
        }

        // Copy segment data into mapped pages
        copySegmentDataToPages(as, segment, mm);
    }

    /**
     * Copy segment data into properly mapped pages
     */
    private void copySegmentDataToPages(AddressSpace as, ElfSegment segment, PagedMemoryManager mm)
            throws MemoryAccessException {
        int pageSize = PagedMemoryManager.PAGE_SIZE;

        for (int i = 0; i < segment.fileSize; i++) {
            int addr = segment.virtualAddr + i;
            int vpn = addr / pageSize;
            int offset = addr % pageSize;

            if (as.isPagePresent(vpn)) {
                int frame = as.getFrameNumber(vpn);
                if (frame >= 0) {
                    int physicalAddr = frame * pageSize + offset;
                    byte data = elfData[segment.fileOffset + i];
                    mm.writeByte(physicalAddr, data);
                }
            }
        }
    }
}

class ElfException extends Exception {
    public ElfException(String message) {
        super(message);
    }
}