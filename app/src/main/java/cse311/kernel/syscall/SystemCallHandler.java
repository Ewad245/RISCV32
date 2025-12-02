package cse311.kernel.syscall;

import java.util.ArrayList;
import java.util.List;

import cse311.*;
import cse311.kernel.Kernel;
import cse311.paging.PagedMemoryManager;

/**
 * Handles system calls from user tasks
 * System call numbers are passed in register a7 (x17)
 * Arguments are passed in registers a0-a6 (x10-x16)
 * Return value is placed in register a0 (x10)
 */
public class SystemCallHandler {
    private final Kernel kernel;
    private RV32iCpu cpu;

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

    public SystemCallHandler(Kernel kernel, RV32iCpu cpu) {
        this.kernel = kernel;
        this.cpu = cpu;
    }

    /**
     * Handle a system call from a task
     */
    public void handleSystemCall(Task task) {
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

            // Only update registers if the task is NOT waiting/blocked.
            if (handled && task.getState() != TaskState.WAITING) {
                // 1. Update the CPU (so immediate execution is correct)
                cpu.setRegister(10, result);

                // 2. CRITICAL: Update the Task object's register state as well.
                // Since Kernel.java now skips the final saveState() to protect 'exec',
                // we must manually ensure the return value is saved to the Task.
                task.getRegisters()[10] = result;
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
                    if (b == 0)
                        break; // Null terminator
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
                if ((status & 1) == 0) {
                    // No data available (RX_READY bit is 0)

                    // Block the task
                    task.waitFor(WaitReason.UART_INPUT);

                    // Rewind PC so we retry the 'read' syscall when we wake up
                    // This ensures we actually get the data when it arrives
                    cpu.setProgramCounter(cpu.getProgramCounter() - 4);

                    return 0;
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
        System.out.println("SYS_FORK: Task " + task.getId() + " (" + task.getName() + ") requesting fork.");

        try {
            // The heavy lifting of copying memory and state is done by TaskManager
            Task child = kernel.getTaskManager().forkTask(task);

            // To the PARENT, fork returns the child's PID
            System.out.println("SYS_FORK: Parent " + task.getId() + " received child PID " + child.getId());
            return child.getId();

        } catch (Exception e) {
            System.err.println("SYS_FORK: Failed: " + e.getMessage());
            e.printStackTrace();
            return -1; // Return error code to parent
        }
    }

    // Replace your handleWait stub
    private int handleWait(Task task, int statusAddr) {

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
                        if (kernel.getMemory() instanceof PagedMemoryManager) {
                            PagedMemoryManager pm = (PagedMemoryManager) kernel.getMemory();

                            // WRITE to the parent's memory space at address 'statusAddr'
                            pm.writeWord(statusAddr, exitCode);
                        }
                    } catch (Exception e) {
                        System.err.println("SYS_WAIT: Failed to write exit code to user memory.");
                        return -1;
                    }
                }

                // 3. Cleanup: Remove child from parent's list and kernel list
                task.removeChild(child);
                kernel.getTaskManager().cleanupTask(child);
                kernel.getAllTasks().remove(child);

                System.out.println("SYS_WAIT: Cleaned up child " + childPid + " with exit code " + exitCode);
                return childPid; // Return the PID of the child we just cleaned up
            }
        }

        if (!hasChildren) {
            return -1; // Error: No children to wait for
        }

        // Children exist, but none are dead yet. Block the parent.
        task.waitFor(WaitReason.PROCESS_EXIT);

        // Rewind PC by 4 so the 'ecall' instruction is executed again when we wake up.
        cpu.setProgramCounter(cpu.getProgramCounter() - 4);

        return 0; // Parent will retry this syscall when it wakes up
    }

    private int handleExec(Task task, int pathPtr, int argvPtr) {
        // This is for optional path subfolders
        StringBuilder pathBuilder = new StringBuilder();
        pathBuilder.append("User_Program_ELF\\");

        System.out.println("SYS_EXEC: Task " + task.getId() + " requesting exec");

        // We must be using the PagedMemoryManager for exec to work
        if (!(kernel.getMemory() instanceof cse311.paging.PagedMemoryManager)) {
            System.err.println("SYS_EXEC: PagedMemoryManager is required for exec.");
            return -1;
        }
        cse311.paging.PagedMemoryManager pm = (cse311.paging.PagedMemoryManager) kernel.getMemory();

        // --- 1. Read arguments (path and argv) from old address space ---
        String path = readStringFromTask(task, pathPtr);
        if (path == null) {
            System.err.println("SYS_EXEC: Invalid path pointer 0x" + Integer.toHexString(pathPtr));
            return -1;
        }
        pathBuilder.append(path);
        pathBuilder.append(".elf");

        List<String> argvList = new ArrayList<>();
        int currentArgPtrAddr = argvPtr;
        try {
            while (true) {
                // Read the next pointer (which is a 4-byte int address)
                int argPtr = pm.readWord(currentArgPtrAddr);
                if (argPtr == 0) { // Null terminator for the array
                    break;
                }

                // Read the string at that pointer
                String arg = readStringFromTask(task, argPtr);
                if (arg == null) {
                    System.err.println("SYS_EXEC: Invalid argv string pointer at 0x" + Integer.toHexString(argPtr));
                    return -1;
                }
                argvList.add(arg);
                currentArgPtrAddr += 4; // Move to the next pointer in the array

                if (argvList.size() > 64) { // Safety break
                    System.err.println("SYS_EXEC: Too many arguments.");
                    return -1;
                }
            }
        } catch (MemoryAccessException e) {
            System.err.println("SYS_EXEC: Error reading argv array: " + e.getMessage());
            return -1;
        }
        int argc = argvList.size();

        // --- 2. Create new address space and load ELF ---
        // NOTE: This assumes 'path' is a path on the *HOST* file system,
        // as your ElfLoader.java implementation reads from the host.
        // A true xv6 `kexec` would read from its own emulated file system.

        cse311.paging.AddressSpace newAS = pm.createAddressSpace(task.getId());
        ElfLoader elfLoader = new ElfLoader(pm);
        int newEntryPoint;

        try {
            // Temporarily switch to the new AddressSpace for loading
            pm.switchTo(newAS);

            // loadElf will parse the file and map segments into the *current*
            // address space (which we just set to newAS).
            // We use loadElf, not loadElfIntoAddressSpace, as the latter
            // seems to be for a different purpose in your test files.
            elfLoader.loadElf(pathBuilder.toString());
            newEntryPoint = elfLoader.getEntryPoint();

        } catch (Exception e) {
            System.err.println("SYS_EXEC: Failed to load ELF file '" + pathBuilder + "': " + e.getMessage());
            pm.switchTo(task.getAddressSpace()); // Switch back to old AS
            pm.destroyAddressSpace(newAS.getPid()); // Clean up the failed AS
            return -1; // Return error
        }

        // --- 3. Create new stack and copy arguments ---
        // We are still switched to newAS
        int newStackPointer;
        int argvArrayAddr;
        final int STACK_SIZE = kernel.getConfig().getStackSize(); // e.g., 8192 bytes
        // Based on your TaskManager, stack is at a high address
        final int STACK_TOP = 0x7FFF_F000;
        final int STACK_BASE = STACK_TOP - STACK_SIZE;

        try {
            // Map the stack region
            pm.mapRegion(newAS, STACK_BASE, STACK_SIZE, true, true, false);

            int sp = STACK_TOP; // Stack pointer starts at the top

            // Store string data
            List<Integer> argvPtrsOnNewStack = new ArrayList<>();
            for (String arg : argvList) {
                byte[] argBytes = (arg + '\0').getBytes(); // Add null terminator
                sp -= argBytes.length; // Make space

                // Align stack pointer (16-byte alignment is good practice)
                sp = sp & ~0xF;

                if (sp < STACK_BASE) {
                    throw new MemoryAccessException("Stack overflow during argv copy");
                }

                // Copy string data to new stack
                for (int j = 0; j < argBytes.length; j++) {
                    pm.writeByte(sp + j, argBytes[j]);
                }
                argvPtrsOnNewStack.add(sp); // Store the pointer
            }

            // Now copy the array of pointers (argv)
            int argvArraySize = (argc + 1) * 4; // 4 bytes per pointer, +1 for null terminator
            sp -= argvArraySize;
            sp = sp & ~0xF; // Align again

            if (sp < STACK_BASE) {
                throw new MemoryAccessException("Stack overflow during argv pointer copy");
            }

            argvArrayAddr = sp; // This is the address that `a1` will point to

            // Write the pointers
            for (int i = 0; i < argc; i++) {
                pm.writeWord(argvArrayAddr + (i * 4), argvPtrsOnNewStack.get(i));
            }
            // Write the null terminator pointer
            pm.writeWord(argvArrayAddr + (argc * 4), 0);

            newStackPointer = sp; // This is the final SP

        } catch (Exception e) {
            System.err.println("SYS_EXEC: Failed to create new stack: " + e.getMessage());
            pm.switchTo(task.getAddressSpace()); // Switch back to old AS
            pm.destroyAddressSpace(newAS.getPid()); // Clean up
            return -1;
        }

        // --- 4. Atomic Swap and Cleanup ---

        // Get old address space *before* overwriting it
        cse311.paging.AddressSpace oldAS = task.getAddressSpace();

        // Update the task object
        task.setAddressSpace(newAS);
        task.setProgramCounter(newEntryPoint);
        task.setStackBase(STACK_BASE); // Update stack info
        task.setStackSize(STACK_SIZE);
        task.setName(path); // Set name to program path

        // Update trapframe registers for return to user
        int[] registers = task.getRegisters();
        registers[2] = newStackPointer; // sp (x2)
        registers[11] = argvArrayAddr; // a1 (x11) -> argv
        // a0 (x10) -> argc, will be set by the return value of this function
        task.setRegisters(registers);

        // Clean up the old address space
        if (oldAS != null) {
            // We must destroy the old space, using its PID (which is the same as the
            // task's)
            pm.destroyAddressSpace(oldAS.getPid());
        }

        // Switch the pager's context back to the (new) active address space
        pm.switchTo(newAS);

        // --- 5. Return argc ---
        // This value will be placed in a0 by handleSystemCall
        return argc;
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

    /**
     * Helper to read a null-terminated string from a task's address space.
     */
    private String readStringFromTask(Task task, int va) {
        // This assumes the PagedMemoryManager is already switched to the task's
        // context,
        // which it should be when handleSystemCall is called.
        if (!(kernel.getMemory() instanceof PagedMemoryManager)) {
            System.err.println("readStringFromTask: PagedMemoryManager is required.");
            return null;
        }
        PagedMemoryManager pm = (PagedMemoryManager) kernel.getMemory();

        try {
            StringBuilder sb = new StringBuilder();
            while (true) {
                // We must use pm.readByte() which goes through the pager
                byte b = pm.readByte(va);
                if (b == 0) {
                    break; // End of string
                }
                sb.append((char) b);
                va++;

                // Add a safety break for very long or non-terminated strings
                if (sb.length() > 4096) { // 4KB max path/arg length
                    System.err.println("readStringFromTask: String too long or not terminated.");
                    return null;
                }
            }
            return sb.toString();
        } catch (MemoryAccessException e) {
            System.err.println("readStringFromTask: Memory access error at 0x" + Integer.toHexString(va));
            return null;
        }
    }
}