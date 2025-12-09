package cse311;

import java.util.Arrays;

import cse311.Exception.MemoryAccessException;

public class SimpleMemory {
    private byte[] memory;
    private int MEMORY_SIZE = 128 * 1024 * 1024; // 1KB of memory

    // Memory alignment constants
    private static final int HALF_WORD_ALIGN = 2;
    private static final int WORD_ALIGN = 4;

    public SimpleMemory() {
        memory = new byte[MEMORY_SIZE];
    }

    public SimpleMemory(int memSize) {
        MEMORY_SIZE = memSize;
        memory = new byte[MEMORY_SIZE];
    }

    // Add MMIO ranges
    private static final int MMIO_START = 0x10000000;
    private static final int MMIO_END = 0x10001000;

    public byte readByte(int address) throws MemoryAccessException {
        // Check if address is in MMIO range
        if (address >= MMIO_START && address < MMIO_END) {
            // Let MemoryManager handle MMIO
            throw new MemoryAccessException("MMIO_ACCESS:" + address);
        }

        int physicalAddress = translateAddress(address);
        if (physicalAddress < 0 || physicalAddress >= MEMORY_SIZE) {
            throw new MemoryAccessException("Memory access out of bounds: 0x" +
                    Integer.toHexString(address) + " -> 0x" + Integer.toHexString(physicalAddress));
        }
        return memory[physicalAddress];
    }

    public void writeByte(int address, byte value) throws MemoryAccessException {
        // Check if address is in MMIO range
        if (address >= MMIO_START && address < MMIO_END) {
            // Let MemoryManager handle MMIO
            throw new MemoryAccessException("MMIO_ACCESS:" + address);
        }

        int physicalAddress = translateAddress(address);
        if (physicalAddress < 0 || physicalAddress >= MEMORY_SIZE) {
            throw new MemoryAccessException("Memory access out of bounds: 0x" +
                    Integer.toHexString(address) + " -> 0x" + Integer.toHexString(physicalAddress));
        }
        memory[physicalAddress] = value;
    }

    public short readHalfWord(int address) throws MemoryAccessException {
        checkAddress(address, HALF_WORD_ALIGN);
        checkAlignment(address, HALF_WORD_ALIGN);

        int physicalAddress = translateAddress(address);
        return (short) ((memory[physicalAddress + 1] & 0xFF) << 8 |
                (memory[physicalAddress] & 0xFF));
    }

    public int readWord(int address) throws MemoryAccessException {
        checkAddress(address, WORD_ALIGN);
        checkAlignment(address, WORD_ALIGN);

        int physicalAddress = translateAddress(address);
        return (memory[physicalAddress + 3] & 0xFF) << 24 |
                (memory[physicalAddress + 2] & 0xFF) << 16 |
                (memory[physicalAddress + 1] & 0xFF) << 8 |
                (memory[physicalAddress] & 0xFF);
    }

    public void writeHalfWord(int address, short value) throws MemoryAccessException {
        checkAddress(address, HALF_WORD_ALIGN);
        checkAlignment(address, HALF_WORD_ALIGN);

        int physicalAddress = translateAddress(address);
        memory[physicalAddress] = (byte) (value & 0xFF);
        memory[physicalAddress + 1] = (byte) ((value >> 8) & 0xFF);
    }

    public void writeWord(int address, int value) throws MemoryAccessException {
        checkAddress(address, WORD_ALIGN);
        checkAlignment(address, WORD_ALIGN);

        int physicalAddress = translateAddress(address);
        memory[physicalAddress] = (byte) (value & 0xFF);
        memory[physicalAddress + 1] = (byte) ((value >> 8) & 0xFF);
        memory[physicalAddress + 2] = (byte) ((value >> 16) & 0xFF);
        memory[physicalAddress + 3] = (byte) ((value >> 24) & 0xFF);
    }

    // Utility methods
    private void checkAddress(int address, int accessSize) throws MemoryAccessException {
        int physicalAddress = translateAddress(address);
        if (physicalAddress < 0 || physicalAddress + accessSize > MEMORY_SIZE) {
            throw new MemoryAccessException(
                    String.format("Memory access out of bounds: virtual=0x%08X, physical=0x%08X, size=%d",
                            address, physicalAddress, accessSize));
        }
    }

    /**
     * Translate virtual address to physical address
     * Handles the mapping from ELF virtual addresses to our memory array indices
     */
    private int translateAddress(int virtualAddress) {
        // Handle high addresses (like 0x80000000) by converting to unsigned and mapping
        if (virtualAddress < 0) {
            // Convert negative int to unsigned long
            long unsignedAddr = virtualAddress & 0xFFFFFFFFL;

            // Map high addresses (0x80000000+) to lower memory region
            if (unsignedAddr >= 0x80000000L) {
                return (int) (unsignedAddr - 0x80000000L);
            }

            // For other negative addresses, use as-is (shouldn't happen in normal cases)
            return virtualAddress;
        }

        // For positive addresses, use directly
        return virtualAddress;
    }

    private void checkAlignment(int address, int alignment) throws MemoryAccessException {
        if ((address % alignment) != 0) {
            throw new MemoryAccessException(
                    String.format("Misaligned memory access: address=%d, required alignment=%d",
                            address, alignment));
        }
    }

    public void clear() {
        Arrays.fill(memory, (byte) 0);
    }

    // Debug helper methods
    public String dumpMemory(int startAddress, int length) throws MemoryAccessException {
        checkAddress(startAddress, length);
        StringBuilder sb = new StringBuilder();

        int physicalStart = translateAddress(startAddress);
        for (int i = 0; i < length; i += 16) {
            // Print virtual address
            sb.append(String.format("%08x: ", startAddress + i));

            // Print hex values using physical addresses
            for (int j = 0; j < 16 && (i + j) < length; j++) {
                if (j % 4 == 0)
                    sb.append(" ");
                sb.append(String.format("%02x ", memory[physicalStart + i + j]));
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Initialize a region of memory with given bytes
     */
    public void initializeMemory(int startAddress, byte[] data) {
        int physicalAddress = translateAddress(startAddress);
        System.arraycopy(data, 0, memory, physicalAddress, data.length);
    }

    public byte[] getMemory() {
        return memory;
    }
}
