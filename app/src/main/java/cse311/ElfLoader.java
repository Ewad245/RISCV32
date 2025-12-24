package cse311;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import cse311.Exception.ElfException;
import cse311.Exception.MemoryAccessException;
import cse311.kernel.NonContiguous.paging.AddressSpace;
import cse311.kernel.NonContiguous.paging.PagedMemoryManager;
import cse311.kernel.process.ProgramInfo;

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

    /**
     * Load ELF from a file name path
     */
    public void loadElf(String filename) throws IOException, ElfException {
        elfData = Files.readAllBytes(Paths.get(filename));

        if (!validateElfHeader()) {
            throw new ElfException("Invalid ELF file");
        }

        loadProgramSegments();
    }

    /**
     * Load ELF from a byte array (Used by TaskManager).
     */
    public void loadElf(byte[] data) throws ElfException {
        this.elfData = data;
        parseAndLoad();
    }

    /**
     * Shared parsing logic.
     */
    private void parseAndLoad() throws ElfException {
        if (!validateElfHeader()) {
            throw new ElfException("Invalid ELF file or not RISC-V");
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

        // Parse Header Offsets
        int programHeaderOffset = buffer.getInt(28);
        int programHeaderEntrySize = buffer.getShort(42);
        int programHeaderEntryCount = buffer.getShort(44);

        // Process each program header
        for (int i = 0; i < programHeaderEntryCount; i++) {
            int offset = programHeaderOffset + (i * programHeaderEntrySize);

            // Read Segment Type
            int type = buffer.getInt(offset);

            if (type != PT_LOAD) {
                continue;
            }

            // Read Segment Details
            int offset_in_file = buffer.getInt(offset + 4);
            int virtual_addr = buffer.getInt(offset + 8);
            // skip physical addr (offset + 12)
            int size_in_file = buffer.getInt(offset + 16);
            int size_in_mem = buffer.getInt(offset + 20);
            int flags = buffer.getInt(offset + 24);

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
                byte b = elfData[segment.fileOffset + i];
                memory.writeByteToVirtualAddress(loadAddr + i, b);
            }
        }

        // Zero-initialize remaining memory (BSS section)
        for (int i = segment.fileSize; i < segment.memorySize; i++) {
            memory.writeByteToVirtualAddress(loadAddr + i, (byte) 0);
        }
    }

    /**
     * Analyzes loaded segments to determine program layout (Text, Data, Heap).
     */
    public ProgramInfo getProgramInfo() {
        int entry = getEntryPoint();
        int txtStart = Integer.MAX_VALUE;
        int txtEnd = 0;
        int datStart = Integer.MAX_VALUE;
        int datEnd = 0;

        for (ElfSegment seg : segments) {
            int start = seg.virtualAddr;
            int end = start + seg.memorySize;

            if (seg.executable) {
                // Text Segment (Executable)
                if (start < txtStart)
                    txtStart = start;
                if (end > txtEnd)
                    txtEnd = end;
            } else if (seg.writable) {
                // Data Segment (Writable but not Executable)
                if (start < datStart)
                    datStart = start;
                if (end > datEnd)
                    datEnd = end;
            }
        }

        // Handle cases where sections might be missing
        if (txtStart == Integer.MAX_VALUE)
            txtStart = 0;
        if (datStart == Integer.MAX_VALUE) {
            datStart = txtEnd;
            datEnd = txtEnd;
        }

        // Heap starts immediately after the Data segment, aligned to 4 bytes
        int heapStart = (datEnd + 3) & ~3;

        return new ProgramInfo(
                entry,
                txtStart,
                txtEnd - txtStart,
                datStart,
                datEnd - datStart,
                heapStart);
    }

    public int getEntryPoint() {
        ByteBuffer buffer = ByteBuffer.wrap(elfData).order(ByteOrder.LITTLE_ENDIAN);
        return buffer.getInt(24);
    }

    /**
     * Get all loaded segments with their permissions
     */
    public List<ElfSegment> getSegments() {
        return new ArrayList<>(segments);
    }

    /**
     * Static utility to calculate the total memory size required by an ELF file
     * by inspecting its program headers.
     * * @param elfData The ELF file bytes
     * 
     * @return The highest virtual address used by the program (plus alignment)
     */
    public static int calculateRequiredMemory(byte[] elfData) throws ElfException {
        ByteBuffer buffer = ByteBuffer.wrap(elfData).order(ByteOrder.LITTLE_ENDIAN);

        // Basic Header Validation
        if (elfData.length < 52 || buffer.getInt(0) != 0x464C457F) { // 0x7F 'E' 'L' 'F'
            throw new ElfException("Invalid ELF header");
        }

        int programHeaderOffset = buffer.getInt(28);
        int programHeaderEntrySize = buffer.getShort(42);
        int programHeaderEntryCount = buffer.getShort(44);

        int maxVirtualAddress = 0;

        for (int i = 0; i < programHeaderEntryCount; i++) {
            int offset = programHeaderOffset + (i * programHeaderEntrySize);
            int type = buffer.getInt(offset);

            // PT_LOAD = 1
            if (type == 1) {
                int virtualAddr = buffer.getInt(offset + 8);
                int memorySize = buffer.getInt(offset + 20);

                int endAddress = virtualAddr + memorySize;

                if (endAddress > maxVirtualAddress) {
                    maxVirtualAddress = endAddress;
                }
            }
        }

        // Align to 4KB page boundary just to be safe/clean
        return (maxVirtualAddress + 4095) & ~4095;
    }
}