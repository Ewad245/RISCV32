package cse311;

import cse311.Exception.MemoryAccessException;

public class MemoryManager {
    private SimpleMemory memory;
    private Uart uart;

    // UART Memory-Mapped Registers
    public static final int UART_BASE = 0x10000000;
    public static final int UART_SIZE = 0x1000;
    public static final int UART_TX_DATA = UART_BASE + 0x0; // Write data to transmit
    public static final int UART_RX_DATA = UART_BASE + 0x4; // Read received data
    public static final int UART_STATUS = UART_BASE + 0x8; // Status register
    public static final int UART_CONTROL = UART_BASE + 0xC;

    public MemoryManager() {
        this.memory = new SimpleMemory();
        this.uart = new Uart();
    }

    public MemoryManager(SimpleMemory memory) {
        this.memory = memory;
        this.uart = new Uart();
    }

    /**
     * Simple no-op free implementation.
     * This is a placeholder for a future implementation of memory deallocation.
     * Currently, memory is not actually freed and will only be reclaimed on
     * reset().
     * * @param address The address to free (ignored in this implementation)
     */
    public void free(int address) {
        // No-op implementation for now
        // In a real implementation, we would track freed blocks and reuse them
    }

    // Memory access methods
    public byte readByte(int address) throws MemoryAccessException {
        if (address >= UART_BASE && address < UART_BASE + 0x1000) {
            return (byte) uart.read(address);
        }
        validateAccess(address);
        return memory.readByte(address);
    }

    public short readHalfWord(int address) throws MemoryAccessException {
        if (address >= UART_BASE && address < UART_BASE + 0x1000) {
            return (short) uart.read(address);
        }
        validateAccess(address);
        validateAccess(address + 1);
        return memory.readHalfWord(address);
    }

    public int readWord(int address) throws MemoryAccessException {
        if (address >= UART_BASE && address < UART_BASE + 0x1000) {
            return (int) uart.read(address);
        }
        validateAccess(address);
        validateAccess(address + 3);
        return memory.readWord(address);
    }

    public void writeByte(int address, byte value) throws MemoryAccessException {
        if (address >= UART_BASE && address < UART_BASE + 0x1000) {
            uart.write(address, value);
            return;
        }
        validateAccess(address);
        validateWriteAccess(address);
        memory.writeByte(address, value);
    }

    public void writeHalfWord(int address, short value) throws MemoryAccessException {
        if (address >= UART_BASE && address < UART_BASE + 0x1000) {
            uart.write(address, value);
            return;
        }
        validateAccess(address);
        validateAccess(address + 1);
        validateWriteAccess(address);
        memory.writeHalfWord(address, value);
    }

    public void writeWord(int address, int value) throws MemoryAccessException {
        if (address >= UART_BASE && address < UART_BASE + 0x1000) {
            uart.write(address, value);
            return;
        }
        validateAccess(address);
        validateAccess(address + 3);
        // validateWriteAccess(address);
        memory.writeWord(address, value);
    }

    // New method for ELF loading to virtual addresses
    public void writeByteToVirtualAddress(int address, byte value) throws MemoryAccessException {
        if (address >= UART_BASE && address < UART_BASE + 0x1000) {
            uart.write(address, value);
            return;
        }

        // For ELF loading, we bypass normal write protection checks
        // but still validate the address is within reasonable bounds
        validateVirtualAddress(address);
        memory.writeByte(address, value);
    }

    public void reset() {
        // No pointers to reset anymore
    }

    // Validation methods
    /**
     * Validate memory access based on address and current CPU privilege mode
     * * @param address The memory address to validate
     * 
     * @param privilegeMode The current CPU privilege mode
     * @throws MemoryAccessException If the access is invalid
     */
    public void validateAccess(int address, int privilegeMode) throws MemoryAccessException {
        // For dynamic ELF loading, we use a more flexible validation approach
        // Convert to unsigned long to handle negative addresses properly
        long unsignedAddr = address & 0xFFFFFFFFL;

        // Check if address is within reasonable bounds for RISC-V
        if (unsignedAddr > 0xFFFFFFFFL) {
            throw new MemoryAccessException("Invalid memory access: " +
                    String.format("0x%08X", address));
        }

        // Check if the address maps to valid physical memory
        try {
            validateVirtualAddress(address);
        } catch (MemoryAccessException e) {
            throw new MemoryAccessException("Invalid memory access: " + e.getMessage());
        }

        // NOTE: Previous "User Mode vs Kernel Mode" checks removed.
        // Protection is now handled by the MMU (ContiguousMemoryManager or
        // PagedMemoryManager)
        // logic, which checks Limit Registers or Page Tables respectively.
    }

    /**
     * Validate memory write access based on address and current CPU privilege mode
     * * @param address The memory address to validate
     * 
     * @param privilegeMode The current CPU privilege mode
     * @throws MemoryAccessException If the write access is invalid
     */
    public void validateWriteAccess(int address, int privilegeMode) throws MemoryAccessException {
        // For dynamic ELF loading, we need more flexible write validation
        // First validate that the address is accessible
        validateAccess(address, privilegeMode);

        // NOTE: Write protection (Read-Only segments) should be handled by
        // Page Tables (Paging) or careful ELF loading (Contiguous).
        // The raw MemoryManager is just the physical storage.
    }

    /**
     * Legacy method for backward compatibility
     */
    public void validateAccess(int address) throws MemoryAccessException { // Changed to public to fix visibility issues
        validateAccess(address, RV32Cpu.PRIVILEGE_MACHINE);
    }

    /**
     * Legacy method for backward compatibility
     */
    private void validateWriteAccess(int address) throws MemoryAccessException {
        validateWriteAccess(address, RV32Cpu.PRIVILEGE_MACHINE);
    }

    /**
     * Validate virtual address for ELF loading
     * More permissive than normal validation to allow loading at ELF-specified
     * addresses
     */
    private void validateVirtualAddress(int address) throws MemoryAccessException {
        // For ELF loading, we need to validate that the virtual address can be
        // translated
        // to a valid physical address within our memory bounds

        // Use the same translation logic as SimpleMemory to get the physical address
        int physicalAddress;
        if (address < 0) {
            // Handle high addresses (like 0x80000000) by converting to unsigned and mapping
            long unsignedAddr = address & 0xFFFFFFFFL;

            // Map high addresses (0x80000000+) to lower memory region
            if (unsignedAddr >= 0x80000000L) {
                physicalAddress = (int) (unsignedAddr - 0x80000000L);
            } else {
                // For other negative addresses, use as-is
                physicalAddress = address;
            }
        } else {
            // For positive addresses, use directly
            physicalAddress = address;
        }

        // Check if the physical address is within our memory bounds
        if (physicalAddress < 0 || physicalAddress >= memory.getMemory().length) {
            throw new MemoryAccessException("Virtual address 0x" + Integer.toHexString(address) +
                    " maps to physical address 0x" + Integer.toHexString(physicalAddress) +
                    " which is beyond memory bounds (0 - 0x" + Integer.toHexString(memory.getMemory().length - 1)
                    + ")");
        }
    }

    public int allocateHeap(int size) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'allocateHeap'");
    }

    // Debug utilities
    public String getMemoryMap() {
        StringBuilder sb = new StringBuilder();
        sb.append("Memory Map (Dynamic ELF Layout):\n");
        sb.append("ELF segments loaded at their virtual addresses\n");
        sb.append(String.format("Memory Size: %d MB\n", memory.getMemory().length / (1024 * 1024)));
        sb.append(String.format("UART:  0x%08X - 0x%08X\n", UART_BASE, UART_BASE + 0x1000 - 1));
        return sb.toString();
    }

    public void dumpMemory(int start, int length) throws MemoryAccessException {
        System.out.println(memory.dumpMemory(start, length));
    }

    public String dumpMemory() throws MemoryAccessException {
        // For dynamic ELF loading, dump a reasonable range around the loaded program
        // Start from address 0 and dump first 64KB to see loaded segments
        return memory.dumpMemory(0, Math.min(65536, memory.getMemory().length));
    }

    public byte[] getByteMemory() {
        return memory.getMemory();
    }

    public void getInput(String data) {
        uart.receiveDatas(data.getBytes());
    }
}