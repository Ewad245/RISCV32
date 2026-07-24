package cse311.kernel.fs;

import cse311.MemoryManager;
import cse311.TaskAwareMemoryManager;
import cse311.WaitReason;
import cse311.kernel.Kernel;
import cse311.kernel.process.Task;
import cse311.RV32Cpu;

@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class ConsoleDevice implements Device {
    private final Kernel kernel;

    public ConsoleDevice(Kernel kernel) {
        this.kernel = kernel;
    }

    @Override
    public int read(Task task, RV32Cpu cpu, int bufferAddr, int count) {
        try {
            int status = kernel.getMemory().readByte(MemoryManager.UART_STATUS);
            if ((status & 1) == 0) {
                task.waitFor(WaitReason.UART_INPUT);
                int retryPC = cpu.getProgramCounter() - 4;
                cpu.setProgramCounter(retryPC);
                task.setProgramCounter(retryPC);
                return 0;
            }

            byte data = kernel.getMemory().readByte(MemoryManager.UART_RX_DATA);
            if (kernel.getMemory() instanceof TaskAwareMemoryManager) {
                ((TaskAwareMemoryManager) kernel.getMemory()).writeByteToTask(task.getId(), bufferAddr, data);
            } else {
                kernel.getMemory().writeByte(bufferAddr, data);
            }
            return 1;
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public int write(Task task, int bufferAddr, int count) {
        try {
            MemoryManager mem = kernel.getMemory();
            for (int i = 0; i < count; i++) {
                byte b;
                if (mem instanceof TaskAwareMemoryManager) {
                    b = ((TaskAwareMemoryManager) mem).readByteFromTask(task.getId(), bufferAddr + i);
                } else {
                    b = mem.readByte(bufferAddr + i);
                }
                mem.writeByte(MemoryManager.UART_TX_DATA, b);
            }
            return count;
        } catch (Exception e) {
            return -1;
        }
    }
}
