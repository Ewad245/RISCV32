package cse311.kernel.fs;

import cse311.RV32Cpu;
import cse311.kernel.process.Task;

public interface Device {
    int read(Task task, RV32Cpu cpu, int bufferAddr, int count);
    int write(Task task, int bufferAddr, int count);
}
