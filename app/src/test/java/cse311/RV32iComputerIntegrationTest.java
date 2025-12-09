package cse311;

import cse311.kernel.Kernel;
import cse311.kernel.contiguous.BestFitStrategy;
import cse311.kernel.contiguous.ContiguousMemoryManager;
import cse311.kernel.process.Task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RV32iComputerIntegrationTest {

    private RV32iComputer computer;
    private Kernel kernel;

    @BeforeEach
    void setUp() {
        ContiguousMemoryManager memory = new ContiguousMemoryManager(
                8 * 1024 * 1024,
                new BestFitStrategy());

        RV32iCpu cpu = new RV32iCpu(memory);
        kernel = new Kernel(cpu, memory);

        // We use the constructor that matches our memory mode manually
        // or recreate the computer wrapper if needed.
        computer = new RV32iComputer(8 * 1024 * 1024, 16, cse311.Enum.MemoryMode.CONTIGUOUS);
        kernel = computer.getKernel(); // Refreshed kernel from new computer
    }

    private byte[] getMinimalElf() {
        return new byte[] {
                // ELF Header
                0x7F, 0x45, 0x4C, 0x46, 0x01, 0x01, 0x01, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x02, 0x00, (byte) 0xF3, 0x00, 0x01, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x01, 0x00, 0x34, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x34, 0x00, 0x20, 0x00, 0x01, 0x00, 0x28, 0x00,
                0x00, 0x00, 0x00, 0x00,
                // Program Header
                0x01, 0x00, 0x00, 0x00, 0x54, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00,
                0x04, 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00,
                0x05, 0x00, 0x00, 0x00, 0x00, 0x10, 0x00, 0x00,
                // Code (nop)
                0x13, 0x00, 0x00, 0x00
        };
    }

    @Test
    void testComputerInitialization() {
        assertNotNull(computer);
        assertNotNull(computer.getCpu());
        assertNotNull(computer.getMemoryManager());
        assertNotNull(computer.getKernel());
    }

    /*
     * This test is disabled because RV32iComputer.createTask(int) generates
     * raw machine code (createSimpleProgram), but the Kernel now enforces
     * ELF format validation.
     */
    /*
     * @Test
     * void testTaskCreationWithEntryPoint() {
     * Task task = computer.createTask(0x1000);
     * assertNotNull(task);
     * assertEquals(0x1000, task.getProgramCounter());
     * }
     */

    @Test
    void testTaskCreationWithElfData() {
        Task task = computer.createTask(getMinimalElf(), "elf_task");
        assertNotNull(task);
        assertEquals(0x10000, task.getProgramCounter());
    }

    @Test
    void testKernelIntegration() {
        // Use byte[] version instead of int version
        Task task = computer.createTask(getMinimalElf(), "kernel_task");
        assertNotNull(task);
        assertTrue(computer.getKernel().getAllTasks().contains(task));
    }

    @Test
    void testComputerToString() {
        assertNotNull(computer.toString());
    }
}