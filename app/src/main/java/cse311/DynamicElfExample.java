package cse311;

import java.io.IOException;

import cse311.Exception.ElfException;
import cse311.Logger.FileLogger;

/**
 * Example demonstrating how to use the new dynamic ELF loading system
 */
@SuppressWarnings({"PMD.AvoidCatchingGenericException", "PMD.RelianceOnDefaultCharset"})
public class DynamicElfExample {

    public static void main(String[] args) {
        /*
         * if (args.length < 1) {
         * System.out.println("Usage: java DynamicElfExample <elf-file>");
         * System.out.println("Example: java DynamicElfExample hello.elf");
         * return;
         * }
         */
        String elfFile = "app/build/resources/main/program.elf";
        runElfProgram(elfFile);
    }

    public static void runElfProgram(String elfFile) {
        try {
            FileLogger.log("Loading ELF program: " + elfFile);

            // Create memory manager with larger memory for ELF programs
            MemoryManager memory = new MemoryManager();

            // Create ELF loader
            ElfLoader elfLoader = new ElfLoader(memory);

            // Load the ELF file
            elfLoader.loadElf(elfFile);
            int entryPoint = elfLoader.getEntryPoint();

            FileLogger.log("ELF loaded successfully!");
            FileLogger.log(FileLogger.LogLevel.DEBUG,
                    "Entry point: 0x" + Integer.toHexString(entryPoint));

            // Create and configure CPU
            RV32Cpu cpu = new RV32Cpu(memory);
            cpu.setProgramCounterEntryPoint(entryPoint);

            // Initialize stack pointer to a reasonable value
            // For RISC-V programs, stack typically starts high in memory
            cpu.setRegister(2, 0x7C00000); // sp register (x2)

            FileLogger.log("Starting program execution...");
            FileLogger.log(FileLogger.LogLevel.DEBUG, "Memory layout:");
            FileLogger.log(FileLogger.LogLevel.DEBUG, memory.getMemoryMap());

            // Start CPU execution
            cpu.turnOn();

            // Keep the main thread alive while CPU runs
            try {
                Thread.sleep(1000); // Run for 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

        } catch (IOException e) {
            FileLogger.log(FileLogger.LogLevel.ERROR,
                    "Failed to read ELF file: " + e.getMessage());
        } catch (ElfException e) {
            FileLogger.log(FileLogger.LogLevel.ERROR,
                    "Invalid ELF file: " + e.getMessage());
        } catch (Exception e) {
            FileLogger.log(FileLogger.LogLevel.ERROR,
                    "Execution failed: " + e.getMessage());
            FileLogger.log(e);
        }
    }

    /**
     * Create a simple test ELF-like program in memory for demonstration
     */
    public static void createTestProgram() {
        try {
            MemoryManager memory = new MemoryManager();

            // Create a simple RISC-V program that prints "Hello World"
            // This would normally come from an ELF file
            byte[] program = {
                    // Simple program that calls write syscall
                    0x13, 0x05, 0x10, 0x00, // addi a0, zero, 1 (stdout)
                    0x37, 0x15, 0x00, 0x00, // lui a1, 0x1000 (buffer address)
                    0x13, 0x06, 0x10, 0x00, // addi a2, zero, 16 (count)
                    0x13, 0x08, 0x00, 0x04, // addi a7, zero, 64 (write syscall)
                    0x73, 0x00, 0x00, 0x00, // ecall
                    0x13, 0x08, (byte) 0xD0, 0x05, // addi a7, zero, 93 (exit syscall)
                    0x13, 0x05, 0x00, 0x00, // addi a0, zero, 0 (exit code)
                    0x73, 0x00, 0x00, 0x00 // ecall
            };

            // Load program at address 0x80000000 (typical RISC-V kernel address)
            int loadAddress = 0x80000000;
            for (int i = 0; i < program.length; i++) {
                memory.writeByteToVirtualAddress(loadAddress + i, program[i]);
            }

            // Load "Hello World!" string at 0x1000
            String message = "Hello World!\n";
            byte[] messageBytes = message.getBytes();
            for (int i = 0; i < messageBytes.length; i++) {
                memory.writeByteToVirtualAddress(0x1000 + i, messageBytes[i]);
            }

            // Create CPU and run
            RV32Cpu cpu = new RV32Cpu(memory);
            cpu.setProgramCounterEntryPoint(loadAddress);
            cpu.setRegister(2, 0x7C00000); // Set stack pointer

            FileLogger.log("Running test program...");
            cpu.turnOn();

            Thread.sleep(100); // Let it run briefly

        } catch (Exception e) {
            FileLogger.log(FileLogger.LogLevel.ERROR,
                    "Test program failed: " + e.getMessage());
            FileLogger.log(e);
        }
    }
}