package cse311;

import org.junit.jupiter.api.Test;
import cse311.Constants.MemoryMode;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompilationTest {

    @Test
    void testBasicCompilation() {
        RV32Computer computer = new RV32Computer(64 * 1024 * 1024, 4, MemoryMode.PAGING);

        assertAll("compilationAndConstants",
            () -> assertNotNull(computer, "Computer should be instantiated"),
            () -> assertNotNull(computer.getMemoryManager(), "Memory manager should be instantiated"),
            () -> assertNotNull(computer.getKernel(), "Kernel should be instantiated"),
            () -> assertTrue(VirtualMemoryManager.TEXT_START > 0, "TEXT_START should be positive"),
            () -> assertTrue(VirtualMemoryManager.DATA_START > 0, "DATA_START should be positive"),
            () -> assertTrue(VirtualMemoryManager.HEAP_START > 0, "HEAP_START should be positive"),
            () -> assertTrue(VirtualMemoryManager.STACK_SIZE > 0, "STACK_SIZE should be positive"),
            () -> assertTrue(VirtualMemoryManager.UART_BASE > 0, "UART_BASE should be positive"),
            () -> assertTrue(VirtualMemoryManager.UART_SIZE > 0, "UART_SIZE should be positive"),
            () -> assertNotNull(cse311.Constants.OSConstants.file_separator, "file_separator must not be null"),
            () -> assertNotNull(cse311.Constants.OSConstants.file_seperator, "file_seperator must not be null")
        );
    }
}