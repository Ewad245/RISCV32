package cse311.kernel.syscall;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import cse311.*;
import cse311.Exception.ElfException;
import cse311.Exception.MemoryAccessException;
import cse311.kernel.Kernel;
import cse311.kernel.NonContiguous.paging.AddressSpace;
import cse311.kernel.NonContiguous.paging.PagedMemoryManager;
import cse311.kernel.fs.FileDescriptor;
import cse311.kernel.fs.Inode;
import cse311.kernel.memory.ProcessMemoryCoordinator;
import cse311.kernel.process.ProgramInfo;
import cse311.kernel.process.Task;
import cse311.kernel.process.TaskState;

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
    public static final int SYS_KILL = 129;

    // Custom system calls
    public static final int SYS_DEBUG_PRINT = 1000;
    public static final int SYS_GET_TIME = 1001;
    public static final int SYS_SLEEP = 1002;

    // File system calls
    public static final int SYS_OPEN = 56; // openat/open
    public static final int SYS_CLOSE = 57;

    public SystemCallHandler(Kernel kernel) {
        this.kernel = kernel;
    }

    /**
     * Handle a system call from a task
     */
    public void handleSystemCall(Task task, RV32Cpu cpu) {
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
                    result = handleRead(cpu, task, arg0, arg1, arg2);
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
                    result = handleWait(cpu, task, arg0);
                    break;

                case SYS_EXEC:
                    result = handleExec(task, arg0, arg1);
                    break;

                case SYS_KILL:
                    result = handleKill(task, arg0);
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

                case SYS_OPEN:
                    result = handleOpen(task, arg0, arg1); // arg0=pathAddr, arg1=flags
                    break;

                case SYS_CLOSE:
                    result = handleClose(task, arg0); // arg0=fd
                    break;

                // Add case to switch(syscallNum)
                case 20: // SYS_SHM_OPEN (a0 = key) -> returns shmid (frame index)
                    // Get parameter from register a0 (register 10) of task
                    int key = (int) task.getRegisters()[10];
                    // Access Memory Manager from Kernel
                    if (kernel.getMemory() instanceof cse311.kernel.NonContiguous.paging.PagedMemoryManager) {
                        PagedMemoryManager pmm = (PagedMemoryManager) kernel.getMemory();

                        int frameId = pmm.openSharedRegion(key);
                        task.getRegisters()[10] = frameId; // Return result to a0
                    } else {
                        task.getRegisters()[10] = -1; // Error: Not Paging mode
                    }
                    break;

                case 21: // SYS_SHM_ATTACH (a0 = shmid, a1 = vaddr)
                    // Get parameters a0 (frameId) and a1 (virtualAddr)
                    int frameToMap = (int) task.getRegisters()[10];
                    int virtualAddr = (int) task.getRegisters()[11];

                    if (kernel.getMemory() instanceof PagedMemoryManager) {
                        PagedMemoryManager pmm = (PagedMemoryManager) kernel.getMemory();

                        // Get AddressSpace of current task (based on PID)
                        AddressSpace currentAS = pmm.getAddressSpace(task.getId());

                        if (currentAS != null) {
                            int vpn = virtualAddr / 4096;
                            // Map frame to address space with Write permission
                            boolean success = pmm.mapSharedPage(currentAS, vpn, frameToMap, true);
                            task.getRegisters()[10] = success ? 0 : -1;
                        } else {
                            task.getRegisters()[10] = -1;
                        }
                    } else {
                        task.getRegisters()[10] = -1;
                    }
                    break;

                default:
                    handled = false;
                    cse311.Logger.FileLogger
                            .log("Unknown system call: " + syscallNumber + " from task " + task.getId());
                    result = -1; // ENOSYS
            }

            // Only update registers if the task is NOT waiting/blocked.
            if (handled && task.getState() != TaskState.WAITING) {
                // 1. Update the CPU (so immediate execution is correct)
                cpu.setRegister(10, result);

                // 2. Update the Task object's register state as well.
                // Since Kernel.java now skips the final saveState() to protect 'exec',
                // we must manually ensure the return value is saved to the Task.
                task.getRegisters()[10] = result;
                // System.out.println("Task " + task.getId() + " syscall " + syscallNumber + "
                // -> " + result);
            }

        } catch (Exception e) {
            cse311.Logger.FileLogger.log("System call error: " + e.getMessage());
            cpu.setRegister(10, -1); // Return error
        }
    }

    private int handleExit(Task task, int exitCode) {
        // System.out.println("Task " + task.getId() + " exiting with code " +
        // exitCode);
        kernel.terminateTask(task.getId());
        task.setExitCode(exitCode);
        return exitCode;
    }

    private int handleWrite(Task task, int fd, int bufferAddr, int count) {
        // 1. Console Output (Stdout/Stderr)
        if (fd == 1 || fd == 2) {
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
                    if (b == 0)
                        break; // Null terminator
                    sb.append((char) b);
                }

                String output = sb.toString();
                cse311.Logger.FileLogger.print(output);
                return output.length();

            } catch (Exception e) {
                cse311.Logger.FileLogger.log("Write error: " + e.getMessage());
                return -1;
            }
        }

        // 2. File Output
        FileDescriptor file = task.getFileDescriptor(fd);
        if (file == null || !file.writable)
            return -1;

        if (file.type == FileDescriptor.FD_INODE) {
            // NOTE: You need to implement writei in FileSystem to support writing!
            // For now, we can return error or implement it.
            // int n = kernel.getFileSystem().writei(file.inode, ...);
            return -1; // Write not yet fully implemented in FS
        }
        return -1;
    }

    private int handleRead(RV32Cpu cpu, Task task, int fd, int bufferAddr, int count) {
        // 1. Console Input (Stdin)
        if (fd == 0) {
            try {
                // Check if UART has data
                int status = kernel.getMemory().readByte(MemoryManager.UART_STATUS);
                if ((status & 1) == 0) {
                    // No data available (RX_READY bit is 0)

                    // Block the task
                    task.waitFor(WaitReason.UART_INPUT);

                    // Rewind PC so we retry the 'read' syscall when we wake up
                    // This ensures we actually get the data when it arrives
                    int retryPC = cpu.getProgramCounter() - 4;
                    cpu.setProgramCounter(retryPC);

                    // Kernel.java will NOT save this change because we are inside a syscall.
                    task.setProgramCounter(retryPC);

                    return 0;
                }

                // Read one character from UART
                byte data = kernel.getMemory().readByte(MemoryManager.UART_RX_DATA);
                kernel.getMemory().writeByte(bufferAddr, data);
                return 1;

            } catch (Exception e) {
                cse311.Logger.FileLogger.log("Read error: " + e.getMessage());
                return -1;
            }
        }

        // 2. File Input
        FileDescriptor file = task.getFileDescriptor(fd);
        if (file == null || !file.readable)
            return -1;

        if (file.type == FileDescriptor.FD_INODE) {
            byte[] tempBuf = new byte[count];

            // Read from Inode using current offset
            int n = kernel.getFileSystem().readi(file.inode, tempBuf, file.offset, count);

            if (n > 0) {
                // Copy data to user memory
                for (int i = 0; i < n; i++) {
                    try {
                        kernel.getMemory().writeByte(bufferAddr + i, tempBuf[i]);
                    } catch (Exception e) {
                        return -1;
                    }
                }
                file.offset += n; // Advance cursor
            }
            return n;
        }
        return -1;
    }

    private int handleYield(Task task) {
        // System.out.println("Task " + task.getId() + " yielded");
        task.setState(TaskState.READY);
        return 0;
    }

    private int handleGetPid(Task task) {
        return task.getId();
    }

    private int handleFork(Task task) {
        // System.out.println("SYS_FORK: Task " + task.getId() + " (" + task.getName() +
        // ") requesting fork.");

        try {
            // The heavy lifting of copying memory and state is done by TaskManager
            Task child = kernel.getTaskManager().forkTask(task);

            // To the PARENT, fork returns the child's PID
            // System.out.println("SYS_FORK: Parent " + task.getId() + " received child PID
            // " + child.getId());
            return child.getId();

        } catch (Exception e) {
            cse311.Logger.FileLogger.log("SYS_FORK: Failed: " + e.getMessage());
            cse311.Logger.FileLogger.log(e);
            return -1; // Return error code to parent
        }
    }

    // Replace your handleWait stub
    private int handleWait(RV32Cpu cpu, Task task, int statusAddr) {

        boolean hasChildren = false;
        for (Task child : task.getChildren()) {
            if (child.getState() != TaskState.TERMINATED) {
                hasChildren = true;
            }

            // Found a ZOMBIE child (it exited, but we haven't cleaned it up yet)
            if (child.getState() == TaskState.TERMINATED) {
                int childPid = child.getId();

                // 1. Retrieve the exit code the child passed to exit()
                // (You need to add a getExitCode() method to your Task class)
                int exitCode = child.getExitCode();

                // 2. If the parent provided a valid pointer (not NULL/0), write the code there
                if (statusAddr != 0) {
                    try {
                        // Get the memory manager
                        MemoryManager manager = kernel.getMemory();

                        // WRITE to the parent's memory space at address 'statusAddr'
                        manager.writeWord(statusAddr, exitCode);
                    } catch (Exception e) {
                        cse311.Logger.FileLogger.log("SYS_WAIT: Failed to write exit code to user memory.");
                        return -1;
                    }
                }

                // 3. Cleanup: Remove child from parent's list and kernel list
                task.removeChild(child);
                kernel.getTaskManager().cleanupTask(child);
                kernel.getAllTasks().remove(child);

                // System.out.println("SYS_WAIT: Cleaned up child " + childPid + " with exit
                // code " + exitCode);
                return childPid; // Return the PID of the child we just cleaned up
            }
        }

        if (!hasChildren) {
            return -1; // Error: No children to wait for
        }

        // Children exist, but none are dead yet. Block the parent.
        task.waitFor(WaitReason.PROCESS_EXIT);

        // Rewind PC by 4 so the 'ecall' instruction is executed again when we wake up.
        int retryPC = cpu.getProgramCounter() - 4;
        cpu.setProgramCounter(retryPC);

        // Manually sync the Task PC for the retry
        task.setProgramCounter(retryPC);

        return 0; // Parent will retry this syscall when it wakes up
    }

    private int handleExec(Task task, int pathPtr, int argvPtr) {
        // System.out.println("SYS_EXEC: Task " + task.getId() + " requesting exec");

        MemoryManager memory = kernel.getMemory();
        ProcessMemoryCoordinator coordinator = kernel.getMemoryCoordinator();

        if (coordinator == null) {
            cse311.Logger.FileLogger.log("SYS_EXEC: Memory Coordinator not initialized.");
            return -1;
        }

        // 1. Read arguments from CURRENT memory
        String path = readStringFromTask(task, pathPtr);
        if (path == null)
            return -1;

        // Read argv (Logic remains same, just using generic memory)
        List<String> argvList = new ArrayList<>();
        int currentArgPtrAddr = argvPtr;
        try {
            while (true) {
                // Read 4 bytes (pointer) from current memory
                int argPtr = memory.readWord(currentArgPtrAddr);
                if (argPtr == 0)
                    break;

                String arg = readStringFromTask(task, argPtr);
                if (arg == null)
                    return -1;

                argvList.add(arg);
                currentArgPtrAddr += 4;
                if (argvList.size() > 64)
                    return -1;
            }
        } catch (MemoryAccessException e) {
            return -1;
        }

        // 2. Load the file bytes - first try fs.img, then fall back to host filesystem
        byte[] elfData = null;

        // Try loading from mounted file system first
        if (kernel.getFileSystem() != null) {
            // Build fs.img path (e.g., "/sh" or "/init")
            String fsPath = "/" + path;
            Inode inode = kernel.getFileSystem().namei(fsPath);

            if (inode != null && inode.type == Inode.T_FILE) {
                elfData = new byte[inode.size];
                int bytesRead = kernel.getFileSystem().readi(inode, elfData, 0, inode.size);
                if (bytesRead != inode.size) {
                    cse311.Logger.FileLogger.log("SYS_EXEC: Partial read from fs.img: " + fsPath);
                    elfData = null; // Fall back to host filesystem
                } else {
                    cse311.Logger.FileLogger.log("SYS_EXEC: Loaded " + bytesRead + " bytes from fs.img: " + fsPath);
                }
            }
        }

        // Fall back to host filesystem if not found in fs.img
        if (elfData == null) {
            String fullPath = ".." + App.file_seperator + "User_Program_ELF" + App.file_seperator + path + ".elf";
            try {
                elfData = Files.readAllBytes(Paths.get(fullPath));
                cse311.Logger.FileLogger.log("SYS_EXEC: Loaded from host filesystem: " + fullPath);
            } catch (Exception e) {
                cse311.Logger.FileLogger.log("SYS_EXEC: Failed to read file: " + fullPath);
                return -1;
            }
        }

        try {
            // 3. ATOMIC SWAP of Memory
            // Free old resources
            coordinator.freeMemory(task.getId());

            int elfEndAddress = 0;
            try {
                elfEndAddress = ElfLoader.calculateRequiredMemory(elfData);
            } catch (ElfException e) {
                cse311.Logger.FileLogger.log("SYS_EXEC: Bad ELF format: " + e.getMessage());
                return -1;
            }

            // Define a reasonable Heap size (e.g., 64KB or config based)
            int minHeapSize = 64 * 1024;
            int stackSize = kernel.getConfig().getStackSize();

            // Total = (End of Code/Data) + (Heap Space) + (Stack Space)
            int requiredSize = elfEndAddress + minHeapSize + stackSize;
            var layout = coordinator.allocateMemory(task.getId(), requiredSize);

            // Load new program
            ProgramInfo newInfo = coordinator.loadProgram(task.getId(), elfData);

            // 4. Setup Stack (Delegated!)
            // This works for Paging AND Contiguous now
            int newSp = coordinator.setupStack(task.getId(), argvList, layout);

            // 5. Update Task
            task.setName(path);
            task.setProgramCounter(newInfo.entryPoint);
            task.setProgramInfo(newInfo);
            task.setStackBase(layout.stackBase);
            task.setStackSize(layout.stackSize);
            task.setAllocatedSize(requiredSize);

            // Update SP (x2)
            task.getRegisters()[2] = newSp;

            // Return argc (Convention: a0 = argc)
            return argvList.size();

        } catch (Exception e) {
            cse311.Logger.FileLogger.log("SYS_EXEC: Failed: " + e.getMessage());
            task.setState(TaskState.TERMINATED);
            return -1;
        }
    }

    private int handleDebugPrint(Task task, int messagePtr, int length) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("[DEBUG PID ").append(task.getId()).append("] ");

            for (int i = 0; i < length; i++) {
                byte b = kernel.getMemory().readByte(messagePtr + i);
                if (b == 0)
                    break;
                sb.append((char) b);
            }

            cse311.Logger.FileLogger.log(sb.toString());
            return length;

        } catch (Exception e) {
            return -1;
        }
    }

    private int handleKill(Task task, int pidToKill) {
        Task target = kernel.getTask(pidToKill);
        if (target != null) {
            target.kill();
            return 0;
        }
        return -1;
    }

    private int handleGetTime(Task task) {
        // Return current time in milliseconds (truncated to 32-bit)
        return (int) System.currentTimeMillis();
    }

    private int handleSleep(Task task, int milliseconds) {
        long wakeupTime = System.currentTimeMillis() + milliseconds;
        task.waitFor(WaitReason.TIMER, wakeupTime);
        cse311.Logger.FileLogger.log("Task " + task.getId() + " sleeping for " + milliseconds + "ms");
        return 0;
    }

    /**
     * Helper to read a null-terminated string from a task's address space.
     */
    private String readStringFromTask(Task task, int va) {
        // This assumes the MemoryManager is already switched to the task's
        // context,
        // which it should be when handleSystemCall is called.
        MemoryManager mem = kernel.getMemory(); // Works for Paging AND Contiguous

        try {
            StringBuilder sb = new StringBuilder();
            while (true) {
                // We must use pm.readByte() which goes through the pager
                byte b = mem.readByte(va); // This is the magic!
                if (b == 0) {
                    break; // End of string
                }
                sb.append((char) b);
                va++;

                // Add a safety break for very long or non-terminated strings
                if (sb.length() > 4096) { // 4KB max path/arg length
                    cse311.Logger.FileLogger.log("readStringFromTask: String too long or not terminated.");
                    return null;
                }
            }
            return sb.toString();
        } catch (MemoryAccessException e) {
            cse311.Logger.FileLogger.log("readStringFromTask: Memory access error at 0x" + Integer.toHexString(va));
            return null;
        }
    }

    // --- File System Handlers ---

    private int handleOpen(Task task, int pathAddr, int mode) {
        // 1. Read path string from user memory
        String path = readStringFromTask(task, pathAddr);
        if (path == null)
            return -1;

        // 2. Resolve path to Inode
        if (kernel.getFileSystem() == null)
            return -1;
        Inode ip = kernel.getFileSystem().namei(path);
        if (ip == null) {
            // Optional: If O_CREATE flag is set, create the file here (allocInode)
            return -1;
        }

        // 3. Create FileDescriptor
        FileDescriptor fd = new FileDescriptor(ip, true, (mode & 1) != 0);

        // 4. Allocate FD in task
        int fdIdx = task.allocFd(fd);
        if (fdIdx < 0) {
            // Table full
            return -1;
        }

        return fdIdx;
    }

    private int handleClose(Task task, int fd) {
        if (fd < 0 || fd >= Task.NOFILE)
            return -1;
        FileDescriptor file = task.getFileDescriptor(fd);
        if (file == null)
            return -1;

        task.closeFd(fd);
        return 0;
    }
}
