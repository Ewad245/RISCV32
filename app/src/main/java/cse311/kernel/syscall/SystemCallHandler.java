package cse311.kernel.syscall;

import cse311.*;
import cse311.kernel.Kernel;

/**
 * Handles system calls from user tasks
 * System call numbers are passed in register a7 (x17)
 * Arguments are passed in registers a0-a6 (x10-x16)
 * Return value is placed in register a0 (x10)
 */
public class SystemCallHandler {
    private final Kernel kernel;
    
    // System call numbers (following Linux RISC-V convention)
    public static final int SYS_EXIT = 93;
    public static final int SYS_WRITE = 64;
    public static final int SYS_READ = 63;
    public static final int SYS_YIELD = 124;
    public static final int SYS_GETPID = 172;
    public static final int SYS_FORK = 220;
    public static final int SYS_WAIT = 260;
    public static final int SYS_EXEC = 221;
    
    // Custom system calls
    public static final int SYS_DEBUG_PRINT = 1000;
    public static final int SYS_GET_TIME = 1001;
    public static final int SYS_SLEEP = 1002;
    
    public SystemCallHandler(Kernel kernel) {
        this.kernel = kernel;
    }
    
    /**
     * Handle a system call from a task
     */
    public void handleSystemCall(Task task, RV32iCpu cpu) {
        int[] registers = cpu.getRegisters();
        int syscallNumber = registers[17]; // a7
        
        // Extract arguments
        int arg0 = registers[10]; // a0
        int arg1 = registers[11]; // a1
        int arg2 = registers[12]; // a2
        int arg3 = registers[13]; // a3
        int arg4 = registers[14]; // a4
        int arg5 = registers[15]; // a5
        
        int result = 0;
        boolean handled = true;
        
        try {
            switch (syscallNumber) {
                case SYS_EXIT:
                    result = handleExit(task, arg0);
                    break;
                    
                case SYS_WRITE:
                    result = handleWrite(task, arg0, arg1, arg2);
                    break;
                    
                case SYS_READ:
                    result = handleRead(task, arg0, arg1, arg2);
                    break;
                    
                case SYS_YIELD:
                    result = handleYield(task);
                    break;
                    
                case SYS_GETPID:
                    result = handleGetPid(task);
                    break;
                    
                case SYS_FORK:
                    result = handleFork(task);
                    break;
                    
                case SYS_WAIT:
                    result = handleWait(task, arg0);
                    break;
                    
                case SYS_EXEC:
                    result = handleExec(task, arg0, arg1);
                    break;
                    
                case SYS_DEBUG_PRINT:
                    if (kernel.getConfig().isEnableDebugSyscalls()) {
                        result = handleDebugPrint(task, arg0, arg1);
                    } else {
                        handled = false;
                    }
                    break;
                    
                case SYS_GET_TIME:
                    result = handleGetTime(task);
                    break;
                    
                case SYS_SLEEP:
                    result = handleSleep(task, arg0);
                    break;
                    
                default:
                    handled = false;
                    System.err.println("Unknown system call: " + syscallNumber + " from task " + task.getId());
                    result = -1; // ENOSYS
            }
            
            if (handled) {
                // Set return value in a0
                cpu.setRegister(10, result); // Set a0 register directly in CPU
                System.out.println("Task " + task.getId() + " syscall " + syscallNumber + " -> " + result);
            }
            
        } catch (Exception e) {
            System.err.println("System call error: " + e.getMessage());
            cpu.setRegister(10, -1); // Return error
        }
    }
    
    private int handleExit(Task task, int exitCode) {
        System.out.println("Task " + task.getId() + " exiting with code " + exitCode);
        task.setState(TaskState.TERMINATED);
        return exitCode;
    }
    
    private int handleWrite(Task task, int fd, int bufferAddr, int count) {
        if (fd == 1 || fd == 2) { // stdout or stderr
            try {
                // Read string from task memory
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < count; i++) {
                    byte b;
                    if (kernel.getMemory() instanceof TaskAwareMemoryManager) {
                        TaskAwareMemoryManager taskMemory = (TaskAwareMemoryManager) kernel.getMemory();
                        b = taskMemory.readByteFromTask(task.getId(), bufferAddr + i);
                    } else {
                        b = kernel.getMemory().readByte(bufferAddr + i);
                    }
                    if (b == 0) break; // Null terminator
                    sb.append((char) b);
                }
                
                String output = sb.toString();
                System.out.print(output);
                return output.length();
                
            } catch (Exception e) {
                System.err.println("Write error: " + e.getMessage());
                return -1;
            }
        }
        return -1; // Unsupported file descriptor
    }
    
    private int handleRead(Task task, int fd, int bufferAddr, int count) {
        if (fd == 0) { // stdin
            try {
                // Check if UART has data
                int status = kernel.getMemory().readByte(MemoryManager.UART_STATUS);
                if (status == 0) {
                    // No data available, block the task
                    task.waitFor(WaitReason.UART_INPUT);
                    return 0; // Will be resumed when data is available
                }
                
                // Read one character from UART
                byte data = kernel.getMemory().readByte(MemoryManager.UART_RX_DATA);
                kernel.getMemory().writeByte(bufferAddr, data);
                return 1;
                
            } catch (Exception e) {
                System.err.println("Read error: " + e.getMessage());
                return -1;
            }
        }
        return -1; // Unsupported file descriptor
    }
    
    private int handleYield(Task task) {
        System.out.println("Task " + task.getId() + " yielded");
        task.setState(TaskState.READY);
        return 0;
    }
    
    private int handleGetPid(Task task) {
        return task.getId();
    }
    
    private int handleFork(Task task) {
        // Fork is complex - for now, return -1 (not implemented)
        System.out.println("Fork not implemented yet");
        return -1;
    }
    
    private int handleWait(Task task, int pidPtr) {
        // Wait for child task - simplified implementation
        System.out.println("Wait not fully implemented yet");
        return -1;
    }
    
    private int handleExec(Task task, int pathPtr, int argvPtr) {
        // Exec is complex - for now, return -1 (not implemented)
        System.out.println("Exec not implemented yet");
        return -1;
    }
    
    private int handleDebugPrint(Task task, int messagePtr, int length) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("[DEBUG PID ").append(task.getId()).append("] ");
            
            for (int i = 0; i < length; i++) {
                byte b = kernel.getMemory().readByte(messagePtr + i);
                if (b == 0) break;
                sb.append((char) b);
            }
            
            System.out.println(sb.toString());
            return length;
            
        } catch (Exception e) {
            return -1;
        }
    }
    
    private int handleGetTime(Task task) {
        // Return current time in milliseconds (truncated to 32-bit)
        return (int) System.currentTimeMillis();
    }
    
    private int handleSleep(Task task, int milliseconds) {
        long wakeupTime = System.currentTimeMillis() + milliseconds;
        task.waitFor(WaitReason.TIMER, wakeupTime);
        System.out.println("Task " + task.getId() + " sleeping for " + milliseconds + "ms");
        return 0;
    }
}