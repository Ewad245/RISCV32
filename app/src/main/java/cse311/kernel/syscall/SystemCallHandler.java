package cse311.kernel.syscall;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import cse311.*;
import cse311.Constants.OSConstants;
import cse311.Exception.ElfException;
import cse311.Exception.MemoryAccessException;
import cse311.Logger.FileLogger;
import cse311.Logger.FileLogger.LogLevel;
import cse311.kernel.Kernel;
import cse311.kernel.NonContiguous.paging.AddressSpace;
import cse311.kernel.NonContiguous.paging.PagedMemoryManager;
import cse311.kernel.fs.FileDescriptor;
import cse311.kernel.fs.Inode;
import cse311.kernel.fs.Pipe;
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
    public static final int SYS_CLONE = 220; // Replaces SYS_FORK; handles both fork and threads
    public static final int SYS_WAIT = 260;
    public static final int SYS_EXEC = 221;
    public static final int SYS_KILL = 129;

    // Linux Clone Flags (Subset relevant to simulator)
    public static final int CLONE_VM = 0x00000100; // Share memory (Thread vs Process)
    public static final int CLONE_FS = 0x00000200; // Share filesystem info
    public static final int CLONE_FILES = 0x00000400; // Share open file descriptors
    public static final int CLONE_SIGHAND = 0x00000800; // Share signal handlers
    public static final int CLONE_THREAD = 0x00010000; // Place in same thread group

    // Standard Linux RISC-V system call
    public static final int SYS_NANOSLEEP = 101;
    public static final int SYS_BRK = 214;

    // Custom system calls
    public static final int SYS_DEBUG_PRINT = 1000;
    public static final int SYS_GET_TIME = 1001;
    // @Deprecated - Use SYS_NANOSLEEP instead
    // public static final int SYS_SLEEP = 1002;

    // Condition Variables
    public static final int SYS_CV_WAIT = 280;
    public static final int SYS_CV_SIGNAL = 281;
    public static final int SYS_CV_BROADCAST = 282;

    // File system calls
    public static final int SYS_DUP = 23;
    public static final int SYS_MKNOD = 33;
    public static final int SYS_MKDIR = 34;
    public static final int SYS_UNLINK = 35;
    public static final int SYS_LINK = 37;
    public static final int SYS_CHDIR = 49;
    public static final int SYS_OPEN = 56; // openat/open
    public static final int SYS_CLOSE = 57;
    public static final int SYS_FSTAT = 80;
    public static final int SYS_PIPE = 59; // Linux RISC-V pipe syscall

    public static final int O_CREATE = 64; // Linux O_CREAT flag

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
                    result = handleWrite(cpu, task, arg0, arg1, arg2);
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

                case SYS_CLONE:
                    // a0 = flags (e.g., CLONE_VM | CLONE_THREAD)
                    // a1 = child_stack (if 0, use parent's stack)
                    int cloneFlags = registers[10];
                    int cloneStack = registers[11];

                    result = handleClone(cpu, task, cloneFlags, cloneStack);
                    // Parent receives Child's PID, Child receives 0 (set in handleClone)
                    break;

                case SYS_WAIT:
                    // Compatibility with older syscall.c:
                    // wait(status) -> args: a0=status
                    // waitpid(pid, st) -> args: a0=pid, a1=status
                    // Since wait() in old syscall.c passes status in a0 and 0 in a1,
                    // we must check if arg0 looks like a pointer (large positive)
                    // or a PID (small positive / -1).
                    // In the simulator, PIDs are small integers, while user pointers are very large
                    // positive numbers (0x40000000+ or heap).
                    int waitPid;
                    int waitStatusAddr;

                    if (arg0 > 10000 || arg0 < -2) {
                        // It's definitely a memory address (status pointer), so this is wait(&status)
                        waitPid = -1; // Wait for ANY child
                        waitStatusAddr = arg0;
                    } else {
                        // It's a PID, so this is waitpid(pid, &status)
                        waitPid = arg0;
                        waitStatusAddr = arg1;
                    }
                    result = handleWait(cpu, task, waitPid, waitStatusAddr);
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

                case SYS_NANOSLEEP:
                    // a0 = pointer to requested time struct
                    // a1 = pointer to remaining time struct (output, can be ignored)
                    result = handleNanoSleep(task, arg0, arg1);
                    break;

                case SYS_OPEN:
                    result = handleOpen(task, arg0, arg1); // arg0=pathAddr, arg1=flags
                    break;

                case SYS_CLOSE:
                    result = handleClose(task, arg0); // arg0=fd
                    break;

                case SYS_DUP:
                    result = handleDup(task, arg0);
                    break;

                case SYS_MKNOD:
                    result = handleMknod(task, arg0, arg1, arg2);
                    break;

                case SYS_FSTAT:
                    result = handleFstat(task, arg0, arg1);
                    break;

                case SYS_LINK:
                    result = handleLink(task, arg0, arg1);
                    break;

                case SYS_UNLINK:
                    result = handleUnlink(task, arg0);
                    break;

                case SYS_MKDIR:
                    result = handleMkdir(task, arg0, arg1);
                    break;

                case SYS_CHDIR:
                    result = handleChdir(task, arg0);
                    break;

                case SYS_PIPE:
                    result = handlePipe(task, arg0);
                    break;

                case SYS_BRK:
                    result = handleBrk(task, arg0);
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

                case SYS_CV_WAIT:
                    result = handleCvWait(cpu, task, arg0, arg1);
                    break;

                case SYS_CV_SIGNAL:
                    result = handleCvSignal(task, arg0);
                    break;

                case SYS_CV_BROADCAST:
                    result = handleCvBroadcast(task, arg0);
                    break;

                default:
                    handled = false;
                    FileLogger
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
            FileLogger.log("System call error: " + e.getMessage());
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

    private int handleWrite(RV32Cpu cpu, Task task, int fd, int bufferAddr, int count) {
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
                FileLogger.print(output);
                return output.length();

            } catch (Exception e) {
                FileLogger.log("Write error: " + e.getMessage());
                return -1;
            }
        }

        // 2. File Output
        FileDescriptor file = task.getFileDescriptor(fd);
        if (file == null || !file.writable)
            return -1;

        if (file.type == FileDescriptor.FD_INODE) {
            if (file.append) {
                file.offset = file.inode.size;
            }

            byte[] tempBuf = new byte[count];
            try {
                for (int i = 0; i < count; i++) {
                    byte b;
                    if (kernel.getMemory() instanceof TaskAwareMemoryManager) {
                        TaskAwareMemoryManager taskMemory = (TaskAwareMemoryManager) kernel
                                .getMemory();
                        b = taskMemory.readByteFromTask(task.getId(), bufferAddr + i);
                    } else {
                        b = kernel.getMemory().readByte(bufferAddr + i);
                    }
                    tempBuf[i] = b;
                }
            } catch (Exception e) {
                return -1;
            }

            int written = kernel.getFileSystem().writei(file.inode, tempBuf, file.offset, count);
            if (written > 0)
                file.offset += written;
            return written;
        }

        // 3. Pipe Output
        if (file.type == FileDescriptor.FD_PIPE) {
            cse311.kernel.fs.Pipe pipe = file.pipe;
            int bytesWritten = 0;

            // If read end is closed, return -1 (broken pipe)
            if (!pipe.isReadOpen()) {
                FileLogger.log(FileLogger.LogLevel.DEBUG,
                        "SYS_WRITE: Broken pipe (read end closed) for task " + task.getId());
                return -1;
            }

            // If pipe is full -> BLOCK the task
            if (pipe.isFull()) {
                FileLogger.log(FileLogger.LogLevel.DEBUG,
                        "SYS_WRITE: Blocking task " + task.getId() + " on pipe (full)");

                task.waitFor(WaitReason.PIPE_WRITE);
                task.setBlockedOnPipe(pipe);
                kernel.getTaskManager().addTaskToPipeWaitQueue(pipe, task);

                // Rewind PC to retry syscall when woken up
                int retryPC = cpu.getProgramCounter() - 4;
                cpu.setProgramCounter(retryPC);
                task.setProgramCounter(retryPC);

                return 0;
            }

            // Read data from user memory and write to pipe
            MemoryManager mem = kernel.getMemory();
            while (!pipe.isFull() && bytesWritten < count) {
                byte data;
                try {
                    if (mem instanceof TaskAwareMemoryManager) {
                        data = ((TaskAwareMemoryManager) mem).readByteFromTask(task.getId(), bufferAddr + bytesWritten);
                    } else {
                        data = mem.readByte(bufferAddr + bytesWritten);
                    }
                } catch (Exception e) {
                    break;
                }

                if (!pipe.writeByte(data))
                    break;
                bytesWritten++;
            }

            // Wake up any readers that were blocked because pipe was empty
            kernel.getTaskManager().wakeTasksBlockedOnPipe(pipe);

            FileLogger.log(LogLevel.DEBUG,
                    "SYS_WRITE: Wrote " + bytesWritten + " bytes to pipe for task " + task.getId() +
                            " (pipe now has " + pipe.availableBytes() + " bytes)");

            return bytesWritten;
        }

        // 4. Device Output
        if (file.type == FileDescriptor.FD_DEVICE) {
            cse311.kernel.fs.Device dev = kernel.getDevice(file.inode.major);
            if (dev != null) {
                return dev.write(task, bufferAddr, count);
            }
            return -1;
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
                if (kernel.getMemory() instanceof TaskAwareMemoryManager) {
                    ((TaskAwareMemoryManager) kernel.getMemory()).writeByteToTask(task.getId(), bufferAddr, data);
                } else {
                    kernel.getMemory().writeByte(bufferAddr, data);
                }
                return 1;

            } catch (Exception e) {
                FileLogger.log("Read error: " + e.getMessage());
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
                        if (kernel.getMemory() instanceof TaskAwareMemoryManager) {
                            ((TaskAwareMemoryManager) kernel.getMemory()).writeByteToTask(task.getId(), bufferAddr + i,
                                    tempBuf[i]);
                        } else {
                            kernel.getMemory().writeByte(bufferAddr + i, tempBuf[i]);
                        }
                    } catch (Exception e) {
                        return -1;
                    }
                }
                file.offset += n; // Advance cursor
            }
            return n;
        }

        // 3. Pipe Input
        if (file.type == FileDescriptor.FD_PIPE) {
            cse311.kernel.fs.Pipe pipe = file.pipe;
            int bytesRead = 0;

            // If pipe is empty and write end is closed, return 0 (EOF)
            if (pipe.isEmpty() && !pipe.isWriteOpen()) {
                FileLogger.log(FileLogger.LogLevel.DEBUG,
                        "SYS_READ: Pipe EOF for task " + task.getId());
                return 0;
            }

            // If pipe is empty but write end is open -> BLOCK the task
            if (pipe.isEmpty()) {
                FileLogger.log(FileLogger.LogLevel.DEBUG,
                        "SYS_READ: Blocking task " + task.getId() + " on pipe (empty, writeOpen=" + pipe.isWriteOpen()
                                + ")");

                task.waitFor(WaitReason.PIPE_READ);
                task.setBlockedOnPipe(pipe);
                kernel.getTaskManager().addTaskToPipeWaitQueue(pipe, task);

                // Rewind PC to retry syscall when woken up
                int retryPC = cpu.getProgramCounter() - 4;
                cpu.setProgramCounter(retryPC);
                task.setProgramCounter(retryPC);

                return 0;
            }

            // Read data from pipe into user memory
            MemoryManager mem = kernel.getMemory();
            FileLogger.log(LogLevel.DEBUG,
                    "SYS_READ: Pipe has " + pipe.availableBytes() + " bytes, reading up to " + count);

            while (!pipe.isEmpty() && bytesRead < count) {
                int data = pipe.readByte();
                if (data < 0)
                    break;
                try {
                    if (mem instanceof TaskAwareMemoryManager) {
                        ((TaskAwareMemoryManager) mem).writeByteToTask(task.getId(), bufferAddr + bytesRead,
                                (byte) data);
                    } else {
                        mem.writeByte(bufferAddr + bytesRead, (byte) data);
                    }
                } catch (MemoryAccessException ex) {
                    FileLogger.log(LogLevel.ERROR, ex);
                    break;
                }

                bytesRead++;
            }

            // Wake up any writers that were blocked because pipe was full
            kernel.getTaskManager().wakeTasksBlockedOnPipe(pipe);

            FileLogger.log(LogLevel.DEBUG,
                    "SYS_READ: Read " + bytesRead + " bytes from pipe for task " + task.getId() +
                            " (pipe now has " + pipe.availableBytes() + " bytes)");

            return bytesRead;
        }

        // 4. Device Input
        if (file.type == FileDescriptor.FD_DEVICE) {
            cse311.kernel.fs.Device dev = kernel.getDevice(file.inode.major);
            if (dev != null) {
                return dev.read(task, cpu, bufferAddr, count);
            }
            return -1;
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

    /**
     * Handles the Linux clone() system call.
     * This is the underlying implementation for both fork() and pthread_create().
     * 
     * @param parent       The parent task
     * @param flags        Clone flags (CLONE_VM, CLONE_THREAD, etc.)
     * @param userStackPtr Stack pointer for the child (0 = use parent's stack)
     * @return Child's PID to parent, 0 to child, or -1 on failure
     */
    private int handleClone(RV32Cpu cpu, Task parent, int flags, int userStackPtr) {
        try {
            boolean shareMemory = (flags & CLONE_VM) != 0;
            boolean shareThread = (flags & CLONE_THREAD) != 0;

            // 1. Create the new Task
            Task child = new Task(
                    kernel.getNextPid(),
                    parent.getName() + (shareMemory ? "_th" : "_fk"),
                    parent.getProgramCounter(), // Child starts at same instruction as parent
                    parent.getStackSize(),
                    parent.getStackBase(),
                    parent.getProgramInfo());

            // 2. Memory Handling (The 'Linux' distinction)
            if (shareMemory) {
                // THREAD: Shared Virtual Memory
                // Both tasks point to the exact same MemoryContext
                child.setMemoryContext(parent.getMemoryContext());

                // Increment AddressSpace refCount to prevent premature destruction
                // when the parent exits before its child threads
                if (kernel.getMemory() instanceof PagedMemoryManager) {
                    PagedMemoryManager pmm = (PagedMemoryManager) kernel.getMemory();
                    AddressSpace as = pmm.getAddressSpace(parent.getId());
                    if (as != null) {
                        as.incrementRefCount();
                        // Register the child PID to the same AddressSpace
                        pmm.registerSharedAddressSpace(child.getId(), as);
                    }
                } else if (kernel.getMemory() instanceof cse311.kernel.contiguous.ContiguousMemoryManager) {
                    // Register child to share parent's Base/Limit memory block
                    cse311.kernel.contiguous.ContiguousMemoryManager cmm = (cse311.kernel.contiguous.ContiguousMemoryManager) kernel
                            .getMemory();
                    cmm.registerSharedBlock(child.getId(), parent.getId());
                }
            } else {
                // PROCESS (FORK): Copy Virtual Memory
                // Use TaskManager's forkTask logic for deep copy
                Task forkedChild = kernel.getTaskManager().forkTask(parent);
                return forkedChild.getId();
            }

            // 3. Register State Copy (for threads)
            System.arraycopy(parent.getRegisters(), 0, child.getRegisters(), 0, 32);

            child.dupFileDescriptors(parent);
            // Inherit the Current Working Directory using strict caching!
            if (parent.cwd != null) {
                child.cwd = kernel.getFileSystem().idup(parent.cwd);
            } else {
                child.cwd = null;
            }

            // Copy heap state
            child.setProgramBreak(parent.getProgramBreak());

            // 4. Set Child Return Value to 0 (POSIX convention)
            // Parent gets PID, Child gets 0
            child.getRegisters()[10] = 0;

            FileLogger.log(FileLogger.LogLevel.DEBUG,
                    "SYS_CLONE: Child PID=" + child.getId() + ", child.a0=0, parent.a0 will be=" + child.getId());

            // 5. Handle Stack Pointer
            // If a custom stack was provided (standard for threads), set it
            if (userStackPtr == 0) {
                // If stack is 0, it means "fork": use the Parent's current SP
                // Register 2 is the Stack Pointer (SP) in RISC-V
                int parentSP = parent.getRegisters()[2];
                child.getRegisters()[2] = parentSP;
            } else {
                // If stack is not 0, it means "thread/clone": use the provided stack
                child.getRegisters()[2] = userStackPtr;
            }

            // 6. PC Adjustment
            int currentPC = parent.getProgramCounter();

            // Wake up at the instruction AFTER ecall (which is already currentPC)
            child.setProgramCounter(currentPC);

            // The scheduler will save the state of the Parent NOW.
            parent.setProgramCounter(currentPC);

            // 7. Thread group relationship
            if (shareThread) {
                child.setTgid(parent.getTgid()); // Same thread group
            }
            parent.addChild(child);

            // 8. Schedule
            child.setState(TaskState.READY);
            kernel.addTaskToScheduler(child);

            FileLogger.log(FileLogger.LogLevel.DEBUG,
                    "SYS_CLONE: Scheduled child " + child.getId() + " (state=" + child.getState() + ")");

            FileLogger.log("Clone: Created " + (shareMemory ? "thread" : "process")
                    + " PID " + child.getId() + " for Parent " + parent.getId());

            return child.getId(); // Return PID to Parent

        } catch (Exception e) {
            FileLogger.log("Clone failed: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Handles the wait4() system call (Linux RISC-V syscall 260).
     *
     * @param cpu        The CPU executing the syscall
     * @param task       The calling (parent) task
     * @param targetPid  -1 = wait for any child, >0 = wait for specific child PID
     * @param statusAddr User-space pointer to write the exit code (0 = ignore)
     * @return PID of the reaped child, 0 if blocking, or -1 on error
     */
    private int handleWait(RV32Cpu cpu, Task task, int targetPid, int statusAddr) {

        boolean hasMatchingChildren = false;

        List<Task> children = task.getChildren();
        FileLogger.log(FileLogger.LogLevel.DEBUG,
                "handleWait: Task " + task.getId() + " has " + children.size() + " children. targetPid=" + targetPid);

        for (Task child : children) {
            FileLogger.log(FileLogger.LogLevel.DEBUG, "  - Checking child " + child.getId()
                    + ", isThread=" + child.isThread() + ", state=" + child.getState());

            // If waiting for a specific PID, skip non-matching children
            if (targetPid > 0 && child.getId() != targetPid) {
                continue;
            }

            // NEW: If waiting for ANY child (-1), DO NOT reap threads!
            // Threads must be specifically joined via waitpid(tid)
            if (targetPid <= 0 && child.isThread()) {
                continue;
            }

            if (child.getState() != TaskState.TERMINATED) {
                hasMatchingChildren = true;
            } else {
                // Found a ZOMBIE child (it exited, but we haven't cleaned it up yet)
                int childPid = child.getId();
                int exitCode = child.getExitCode();

                // Cleanup: Remove child from parent's list and kernel list BEFORE writing
                // memory
                task.removeChild(child);
                kernel.getTaskManager().cleanupTask(child);
                kernel.getAllTasks().remove(child);

                // Write exit code to user memory if a valid pointer was provided
                if (statusAddr != 0) {
                    try {
                        MemoryManager manager = kernel.getMemory();
                        manager.writeWord(statusAddr, exitCode);
                    } catch (Exception e) {
                        FileLogger.log("SYS_WAIT: Failed to write exit code to user memory.");
                        return childPid; // Return the PID anyway, without writing status
                    }
                }

                return childPid; // Return the PID of the child we just cleaned up
            }
        }

        if (!hasMatchingChildren) {
            return -1; // Error: No (matching) children to wait for
        }

        // Children exist, but none are dead yet. Block the parent.
        if (targetPid > 0) {
            // Waiting for a specific PID — uses waitForTask which correctly
            // sets waitingForPid so the kernel's childTerminationWaitQueue works
            task.waitForTask(targetPid);
        } else {
            // Waiting for any child (pid == -1)
            task.waitFor(WaitReason.PROCESS_EXIT);
        }

        // Rewind PC by 4 so the 'ecall' instruction is executed again when we wake up.
        int retryPC = cpu.getProgramCounter() - 4;
        cpu.setProgramCounter(retryPC);
        task.setProgramCounter(retryPC);

        return 0; // Parent will retry this syscall when it wakes up
    }

    private int handleExec(Task task, int pathPtr, int argvPtr) {
        // System.out.println("SYS_EXEC: Task " + task.getId() + " requesting exec");

        MemoryManager memory = kernel.getMemory();
        ProcessMemoryCoordinator coordinator = kernel.getMemoryCoordinator();

        if (coordinator == null) {
            FileLogger.log("SYS_EXEC: Memory Coordinator not initialized.");
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
                    FileLogger.log("SYS_EXEC: Partial read from fs.img: " + fsPath);
                    elfData = null; // Fall back to host filesystem
                } else {
                    FileLogger.log("SYS_EXEC: Loaded " + bytesRead + " bytes from fs.img: " + fsPath);
                }
            }
        }

        // Fall back to host filesystem if not found in fs.img
        if (elfData == null) {
            String fullPath = ".." + OSConstants.file_seperator + "User_Program_ELF" + OSConstants.file_seperator + path
                    + ".elf";
            try {
                elfData = Files.readAllBytes(Paths.get(fullPath));
                FileLogger.log("SYS_EXEC: Loaded from host filesystem: " + fullPath);
            } catch (Exception e) {
                FileLogger.log("SYS_EXEC: Failed to read file: " + fullPath);
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
                FileLogger.log("SYS_EXEC: Bad ELF format: " + e.getMessage());
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

            // Reset program break to new heap start
            task.setProgramBreak(newInfo.heapStart);

            // Update SP (x2)
            task.getRegisters()[2] = newSp;

            // Set a1 (x11) to argv pointer (newSp) for _start -> main(argc, argv)
            task.getRegisters()[11] = newSp;

            // Return argc (Convention: a0 = argc, handled by syscall return)
            return argvList.size();

        } catch (Exception e) {
            FileLogger.log("SYS_EXEC: Failed: " + e.getMessage());
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

            FileLogger.log(sb.toString());
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

    /**
     * Handles the Linux nanosleep() system call.
     * Reads a timespec struct from memory and sleeps for the specified duration.
     * 
     * @param task   The task requesting sleep
     * @param reqPtr Pointer to struct timespec (requested sleep time)
     * @param remPtr Pointer to struct timespec (remaining time if interrupted,
     *               ignored)
     * @return 0 on success, negative error code on failure
     */
    private int handleNanoSleep(Task task, int reqPtr, int remPtr) {
        MemoryManager mem = kernel.getMemory();

        try {
            // 1. Read the `timespec` struct from the address in a0 (reqPtr)
            // Standard Layout: { long tv_sec; long tv_nsec; }

            // Read seconds (offset 0)
            int tv_sec = mem.readWord(reqPtr);

            // Read nanoseconds (offset 4)
            int tv_nsec = mem.readWord(reqPtr + 4);

            // 2. Validate input (nanoseconds must be 0-999999999)
            if (tv_nsec < 0 || tv_nsec >= 1_000_000_000 || tv_sec < 0) {
                return -22; // -EINVAL (Invalid argument in Linux)
            }

            // 3. Convert to milliseconds for Java-based Scheduler
            // (sec * 1000) + (nsec / 1,000,000)
            long durationMs = (tv_sec * 1000L) + (tv_nsec / 1_000_000L);

            // 4. Perform the sleep logic
            long wakeupTime = System.currentTimeMillis() + durationMs;
            task.waitFor(WaitReason.TIMER, wakeupTime);

            FileLogger.log("SYS_NANOSLEEP: Task " + task.getId() +
                    " sleeping for " + durationMs + "ms " +
                    "(Sec: " + tv_sec + ", NSec: " + tv_nsec + ")");

            // 5. Return 0 on success
            return 0;

        } catch (MemoryAccessException e) {
            FileLogger.log("SYS_NANOSLEEP: Failed to read struct from 0x" +
                    Integer.toHexString(reqPtr));
            return -14; // -EFAULT (Bad address in Linux)
        }
    }

    /**
     * Handles the Linux brk() system call (RISC-V syscall 214).
     * 
     * If newBreak is 0, returns the current program break.
     * Otherwise, sets the program break to newBreak if it is valid:
     * - Must be >= heapStart (cannot shrink below data/bss end)
     * - Must be < stackBase (cannot collide with the stack)
     * 
     * @param task     The calling task
     * @param newBreak The requested new program break address (0 = query only)
     * @return The program break after the call (old break if request was refused)
     */
    private int handleBrk(Task task, int newBreak) {
        int currentBreak = task.getProgramBreak();

        // Query-only: if newBreak is 0, just return the current break
        if (newBreak == 0) {
            return currentBreak;
        }

        // Validate: new break must not go below the start of heap
        ProgramInfo info = task.getProgramInfo();
        int heapStart = (info != null) ? info.heapStart : 0;
        if (Integer.compareUnsigned(newBreak, heapStart) < 0) {
            FileLogger.log(FileLogger.LogLevel.DEBUG,
                    "SYS_BRK: Refused shrink below heapStart for PID " + task.getId()
                            + " (requested=0x" + Integer.toHexString(newBreak)
                            + ", heapStart=0x" + Integer.toHexString(heapStart) + ")");
            return currentBreak;
        }

        // Validate: new break must not collide with the stack
        int stackBase = task.getStackBase();
        if (Integer.compareUnsigned(newBreak, stackBase) >= 0) {
            FileLogger.log(FileLogger.LogLevel.DEBUG,
                    "SYS_BRK: Refused expansion into stack for PID " + task.getId()
                            + " (requested=0x" + Integer.toHexString(newBreak)
                            + ", stackBase=0x" + Integer.toHexString(stackBase) + ")");
            return currentBreak;
        }

        // Ask the memory subsystem to physically back the new memory
        // (In paged mode, this updates the AddressSpace's heapLimit so the
        // pager knows these addresses are valid for demand allocation.
        // In contiguous mode, this is a no-op since memory is pre-allocated.)
        try {
            ProcessMemoryCoordinator coordinator = kernel.getMemoryCoordinator();
            if (coordinator != null) {
                boolean success = coordinator.expandHeap(task.getId(), currentBreak, newBreak);
                if (!success) {
                    FileLogger.log(FileLogger.LogLevel.DEBUG,
                            "SYS_BRK: Out of memory for PID " + task.getId());
                    return currentBreak;
                }
            }
        } catch (Exception e) {
            FileLogger.log(FileLogger.LogLevel.ERROR,
                    "SYS_BRK: expandHeap failed for PID " + task.getId() + ": " + e.getMessage());
            return currentBreak;
        }

        // Accept the new program break
        task.setProgramBreak(newBreak);

        FileLogger.log(FileLogger.LogLevel.DEBUG,
                "SYS_BRK: PID " + task.getId() + " break moved 0x"
                        + Integer.toHexString(currentBreak) + " -> 0x"
                        + Integer.toHexString(newBreak));

        return newBreak;
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
                byte b;
                if (mem instanceof TaskAwareMemoryManager) {
                    b = ((TaskAwareMemoryManager) mem).readByteFromTask(task.getId(), va);
                } else {
                    b = mem.readByte(va);
                }
                if (b == 0) {
                    break; // End of string
                }
                sb.append((char) b);
                va++;

                // Add a safety break for very long or non-terminated strings
                if (sb.length() > 4096) { // 4KB max path/arg length
                    FileLogger.log("readStringFromTask: String too long or not terminated.");
                    return null;
                }
            }
            return sb.toString();
        } catch (MemoryAccessException e) {
            FileLogger.log("readStringFromTask: Memory access error at 0x" + Integer.toHexString(va));
            return null;
        }
    }

    // --- File System Handlers ---

    private int handleDup(Task task, int oldFd) {
        return task.dupFd(oldFd);
    }

    private int handleChdir(Task task, int pathAddr) {
        String path = readStringFromTask(task, pathAddr);
        if (path == null)
            return -1;

        Inode ip = kernel.getFileSystem().namei(task, path);

        if (ip == null || ip.type != Inode.T_DIR) {
            if (ip != null)
                kernel.getFileSystem().iput(ip); // Cleanup on failure
            return -1;
        }

        if (task.cwd != null) {
            kernel.getFileSystem().iput(task.cwd); // Release the old directory
        }
        task.cwd = ip; // Keep the new one
        return 0;
    }

    private int handleFstat(Task task, int fd, int statAddr) {
        FileDescriptor file = task.getFileDescriptor(fd);
        if (file == null || file.type != FileDescriptor.FD_INODE)
            return -1;

        Inode ip = file.inode;
        MemoryManager mem = kernel.getMemory();

        try {
            mem.writeWord(statAddr, ip.inum); // ino
            mem.writeWord(statAddr + 4, ip.type); // mode/type
            mem.writeWord(statAddr + 8, ip.nlink); // nlink
            mem.writeWord(statAddr + 12, ip.size); // size
            return 0;
        } catch (MemoryAccessException e) {
            return -1;
        }
    }

    private int handleMkdir(Task task, int pathAddr, int mode) {
        String path = readStringFromTask(task, pathAddr);
        FileLogger.log("SYS_MKDIR: path=" + path);
        if (path == null)
            return -1;

        StringBuilder nameBuilder = new StringBuilder();
        Inode dp = kernel.getFileSystem().nameiparent(task, path, nameBuilder);
        FileLogger
                .log("SYS_MKDIR: dp=" + (dp != null ? dp.inum : "null") + " name=" + nameBuilder.toString());
        if (dp == null || nameBuilder.length() == 0) {
            FileLogger.log("SYS_MKDIR: nameiparent failed");
            return -1;
        }

        String name = nameBuilder.toString();
        if (kernel.getFileSystem().dirlookup(dp, name) != null) {
            FileLogger.log("SYS_MKDIR: dir already exists");
            return -1;
        }

        Inode ip;
        try {
            ip = kernel.getFileSystem().ialloc(Inode.T_DIR);
        } catch (Exception e) {
            FileLogger.log("SYS_MKDIR: ialloc threw exception: " + e.getMessage());
            return -1;
        }

        FileLogger.log("SYS_MKDIR: allocated inode " + (ip != null ? ip.inum : "null"));
        if (ip == null)
            return -1;

        ip.nlink = 2;
        kernel.getFileSystem().updateInode(ip);

        FileLogger.log("SYS_MKDIR: linking . and ..");
        kernel.getFileSystem().dirlink(ip, ".", ip.inum);
        kernel.getFileSystem().dirlink(ip, "..", dp.inum);

        dp.nlink++;
        kernel.getFileSystem().updateInode(dp);

        FileLogger.log("SYS_MKDIR: linking into parent");
        if (kernel.getFileSystem().dirlink(dp, name, ip.inum) < 0) {
            FileLogger.log("SYS_MKDIR: dirlink into parent failed");
            kernel.getFileSystem().iput(ip);
            kernel.getFileSystem().iput(dp);
            return -1;
        }

        FileLogger.log("SYS_MKDIR: success!");
        kernel.getFileSystem().iput(ip);
        kernel.getFileSystem().iput(dp);
        return 0;
    }

    private int handleMknod(Task task, int pathPtr, int major, int minor) {
        String path = readStringFromTask(task, pathPtr);
        if (path == null)
            return -1;

        if (kernel.getFileSystem().namei(task, path) != null)
            return -1;

        StringBuilder name = new StringBuilder();
        Inode dp = kernel.getFileSystem().nameiparent(task, path, name);
        if (dp == null)
            return -1;

        try {
            Inode ip = kernel.getFileSystem().ialloc(Inode.T_DEV);
            ip.major = (short) major;
            ip.minor = (short) minor;
            ip.nlink = 1;
            kernel.getFileSystem().updateInode(ip);

            if (kernel.getFileSystem().dirlink(dp, name.toString(), ip.inum) < 0) {
                return -1;
            }
        } catch (Exception e) {
            return -1;
        }
        return 0;
    }

    private int handleLink(Task task, int oldPathAddr, int newPathAddr) {
        String oldPath = readStringFromTask(task, oldPathAddr);
        String newPath = readStringFromTask(task, newPathAddr);
        if (oldPath == null || newPath == null)
            return -1;

        Inode ip = kernel.getFileSystem().namei(task, oldPath);
        if (ip == null || ip.type == Inode.T_DIR)
            return -1;

        StringBuilder nameBuilder = new StringBuilder();
        Inode dp = kernel.getFileSystem().nameiparent(task, newPath, nameBuilder);
        if (dp == null || nameBuilder.length() == 0)
            return -1;

        String name = nameBuilder.toString();
        if (kernel.getFileSystem().dirlink(dp, name, ip.inum) < 0) {
            kernel.getFileSystem().iput(ip);
            kernel.getFileSystem().iput(dp);
            return -1;
        }

        ip.nlink++;
        kernel.getFileSystem().updateInode(ip);

        kernel.getFileSystem().iput(ip);
        kernel.getFileSystem().iput(dp);

        return 0;
    }

    private int handleUnlink(Task task, int pathAddr) {
        String path = readStringFromTask(task, pathAddr);
        if (path == null)
            return -1;

        StringBuilder nameBuilder = new StringBuilder();
        Inode dp = kernel.getFileSystem().nameiparent(task, path, nameBuilder);
        if (dp == null || nameBuilder.length() == 0)
            return -1;

        String name = nameBuilder.toString();
        if (name.equals(".") || name.equals(".."))
            return -1;

        Inode ip = kernel.getFileSystem().dirlookup(dp, name);
        if (ip == null)
            return -1;

        byte[] buf = new byte[cse311.kernel.fs.DirectoryEntry.SIZE];
        for (int off = 0; off < dp.size; off += cse311.kernel.fs.DirectoryEntry.SIZE) {
            kernel.getFileSystem().readi(dp, buf, off, cse311.kernel.fs.DirectoryEntry.SIZE);
            cse311.kernel.fs.DirectoryEntry de = cse311.kernel.fs.DirectoryEntry.fromBytes(buf);
            if (de.inum == ip.inum && de.name.equals(name)) {
                de.inum = 0;
                kernel.getFileSystem().writei(dp, de.toBytes(), off, cse311.kernel.fs.DirectoryEntry.SIZE);
                break;
            }
        }

        if (ip.type == Inode.T_DIR) {
            dp.nlink--;
            kernel.getFileSystem().updateInode(dp);
        }

        ip.nlink--;
        kernel.getFileSystem().updateInode(ip);

        kernel.getFileSystem().iput(ip);
        kernel.getFileSystem().iput(dp);

        return 0;
    }

    private int handleOpen(Task task, int pathAddr, int mode) {
        String path = readStringFromTask(task, pathAddr);
        if (path == null)
            return -1;

        if (kernel.getFileSystem() == null)
            return -1;
        Inode ip = kernel.getFileSystem().namei(task, path);
        if (ip == null) {
            if ((mode & O_CREATE) != 0) {
                StringBuilder nameBuilder = new StringBuilder();
                Inode dp = kernel.getFileSystem().nameiparent(task, path, nameBuilder);
                if (dp == null || nameBuilder.length() == 0)
                    return -1;

                ip = kernel.getFileSystem().ialloc(Inode.T_FILE);
                if (ip == null)
                    return -1;

                ip.nlink = 1;
                kernel.getFileSystem().updateInode(ip);

                if (kernel.getFileSystem().dirlink(dp, nameBuilder.toString(), ip.inum) < 0) {
                    kernel.getFileSystem().iput(ip);
                    kernel.getFileSystem().iput(dp);
                    return -1;
                }
                kernel.getFileSystem().iput(dp);
            } else {
                return -1;
            }
        }

        boolean isTruncate = (mode & 0x200) != 0;
        boolean isAppend = (mode & 0x400) != 0;

        if (isTruncate && ip.type == Inode.T_FILE) {
            kernel.getFileSystem().truncate(ip);
        }

        FileDescriptor fd = new FileDescriptor(ip, true, (mode & 1) != 0 || (mode & 2) != 0);
        fd.append = isAppend;

        if (ip.type == Inode.T_DEV) {
            fd.type = FileDescriptor.FD_DEVICE;
        }

        int fdIdx = task.allocFd(fd);
        if (fdIdx < 0) {
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

        // If this is a pipe, we need to wake up tasks blocked on it before closing
        if (file.type == FileDescriptor.FD_PIPE && file.pipe != null) {
            Pipe pipe = file.pipe;
            FileLogger.log(FileLogger.LogLevel.DEBUG,
                    "SYS_CLOSE: Closing pipe fd " + fd + " for task " + task.getId());

            // Close the file descriptor (which marks read/write ends as closed)
            task.closeFd(fd, kernel.getFileSystem());

            // Wake up any tasks blocked on this pipe
            kernel.getTaskManager().wakeTasksBlockedOnPipe(pipe);
        } else {
            task.closeFd(fd, kernel.getFileSystem());
        }

        return 0;
    }

    private int handlePipe(Task task, int fdArrayAddr) {
        try {
            Pipe pipe = new Pipe();
            FileDescriptor readFd = new FileDescriptor(pipe, true, false);
            FileDescriptor writeFd = new FileDescriptor(pipe, false, true);

            int fd0 = task.allocFd(readFd);
            int fd1 = task.allocFd(writeFd);

            if (fd0 < 0 || fd1 < 0) {
                if (fd0 >= 0)
                    task.closeFd(fd0, kernel.getFileSystem());
                if (fd1 >= 0)
                    task.closeFd(fd1, kernel.getFileSystem());
                return -1;
            }

            MemoryManager mem = kernel.getMemory();
            try {
                mem.writeWord(fdArrayAddr, fd0);
                mem.writeWord(fdArrayAddr + 4, fd1);
            } catch (MemoryAccessException e) {
                FileLogger.log("SYS_PIPE: Memory write failed: " + e.getMessage());
                task.closeFd(fd0, kernel.getFileSystem());
                task.closeFd(fd1, kernel.getFileSystem());
                return -1;
            }

            FileLogger.log(FileLogger.LogLevel.DEBUG,
                    "SYS_PIPE: Created pipe with fds [" + fd0 + ", " + fd1 + "] for task " + task.getId());

            return 0;
        } catch (Exception e) {
            FileLogger.log("SYS_PIPE failed: " + e.getMessage());
            FileLogger.log(e);
            return -1;
        }
    }

    /**
     * Handles the condition variable wait system call.
     * 
     * @param cpu       The CPU executing the syscall
     * @param task      The calling task
     * @param cvId      The ID of the condition variable
     * @param mutexAddr The user-space address of the mutex to unlock
     * @return 0 on success, -1 on failure
     */
    private int handleCvWait(RV32Cpu cpu, Task task, int cvId, int mutexAddr) {
        try {
            // 1. Atomically release the user-space mutex
            // The user-space mutex is typically a struct where 'locked' is an int.
            MemoryManager mem = kernel.getMemory();
            if (mem instanceof TaskAwareMemoryManager) {
                ((TaskAwareMemoryManager) mem).writeWordToTask(task.getId(), mutexAddr, 0);
            } else {
                mem.writeWord(mutexAddr, 0); // Unlock = 0
            }

            // 2. Register the task in the CV's wait queue
            kernel.getTaskManager().waitOnCondition(task, cvId);

            // 3. Force the return value NOW.
            // Because the task is going to sleep, the standard mechanism at the end
            // of handleSystemCall (which checks if the task is WAITING) will SKIP it.
            cpu.setRegister(10, 0);
            task.getRegisters()[10] = 0;

            // 4. Important: Do NOT rewind the PC.
            // When the task wakes up, it should proceed to the immediate next instruction,
            // which in the C library wrapper will be a call to re-acquire the mutex.

            return 0;

        } catch (Exception e) {
            FileLogger.log("CV_WAIT failed for task " + task.getId() + ": " + e.getMessage());
            return -1;
        }
    }

    /**
     * Handles the condition variable signal system call (wakes up 1).
     * 
     * @param task The calling task
     * @param cvId The ID of the condition variable
     * @return 0 on success
     */
    private int handleCvSignal(Task task, int cvId) {
        kernel.getTaskManager().signalCondition(cvId);
        return 0; // Success even if nobody was waiting
    }

    /**
     * Handles the condition variable broadcast system call (wakes up all).
     * 
     * @param task The calling task
     * @param cvId The ID of the condition variable
     * @return 0 on success
     */
    private int handleCvBroadcast(Task task, int cvId) {
        kernel.getTaskManager().broadcastCondition(cvId);
        return 0; // Success even if nobody was waiting
    }
}
