# RV32IM Java Kernel

A modular, extensible kernel implementation for the RV32IM CPU emulator, written in Java.

## Architecture Overview

The kernel is designed with modularity and extensibility in mind, consisting of several key components:

```
Kernel (Main Coordinator)
├── TaskManager (Task lifecycle)
├── Scheduler (Task scheduling)
│   ├── RoundRobinScheduler
│   ├── CooperativeScheduler
│   └── PriorityScheduler
├── SystemCallHandler (System call processing)
├── KernelMemoryManager (Memory management)
└── KernelConfig (Configuration)
```

## Key Features

### 1. **Modular Scheduler System**
- **Round Robin**: Time-sliced scheduling with configurable time slices
- **Cooperative**: Tasks run until they voluntarily yield
- **Priority**: Priority-based scheduling with preemption

### 2. **Task Management**
- Task creation from ELF files or byte arrays
- Task state management (READY, RUNNING, WAITING, TERMINATED)
- Memory isolation and stack management
- Task cleanup and resource deallocation

### 3. **System Call Interface**
- Standard POSIX-like system calls (read, write, exit, yield, etc.)
- Custom system calls for debugging and kernel interaction
- Extensible system call framework

### 4. **Memory Management**
- Process stack allocation and management
- Heap memory allocation
- Memory protection and validation
- Integration with existing MemoryManager

## Quick Start

### Boot Sequence

The system follows this boot sequence:

1. **Hardware Initialization**
   - 128MB RAM allocation
   - CPU initialization
   - Memory management setup

2. **Kernel Configuration**
   - Scheduler type set to ROUND_ROBIN
   - Time slice of 2000 instructions per task

3. **Process Initialization**
   - The bootloader spawns the Init process (PID 1)
   - The Init process can spawn additional processes (like a shell)

4. **ELF Loading**
   - Optional pre-loading of ELF files at startup
   - ELF files are loaded from the "User_Program_ELF" directory

### Basic Usage

```java
// 1. Create computer with 128MB RAM and memory mode
RV32iComputer computer = new RV32iComputer(128 * 1024 * 1024, 100, MemoryMode.CONTIGUOUS);
Kernel kernel = computer.getKernel();

// 2. Configure scheduler
kernel.getConfig().setSchedulerType(KernelConfig.SchedulerType.ROUND_ROBIN);
kernel.getConfig().setTimeSlice(2000);

// 3. (Optional) Load initial ELF program
kernel.createTask("User_Program_ELF/init.elf");

// 4. Start the kernel (this blocks forever)
kernel.start();
```

### Changing Scheduler Algorithm

```java
// Switch to cooperative scheduling
kernel.getConfig().setSchedulerType(KernelConfig.SchedulerType.COOPERATIVE);

// Switch to priority scheduling
kernel.getConfig().setSchedulerType(KernelConfig.SchedulerType.PRIORITY);
kernel.getConfig().setTimeSlice(500);
```

### Custom Task Priorities

```java
Task highPriorityTask = kernel.createTask(elfData, "important_task");
highPriorityTask.setPriority(10);

Task lowPriorityTask = kernel.createTask(elfData, "background_task");
lowPriorityTask.setPriority(1);
```

## System Calls

The kernel supports both standard and custom system calls:

### Standard System Calls
- `SYS_EXIT (93)`: Terminate process
- `SYS_WRITE (64)`: Write to file descriptor
- `SYS_READ (63)`: Read from file descriptor
- `SYS_YIELD (124)`: Voluntarily yield CPU
- `SYS_GETPID (172)`: Get process ID

### Custom System Calls
- `SYS_DEBUG_PRINT (1000)`: Debug output
- `SYS_GET_TIME (1001)`: Get current time
- `SYS_SLEEP (1002)`: Sleep for specified time

### Adding Custom System Calls

```java
// In SystemCallHandler.java
case SYS_MY_CUSTOM_CALL:
    result = handleMyCustomCall(process, arg0, arg1);
    break;

private int handleMyCustomCall(Process process, int arg0, int arg1) {
    // Your custom system call implementation
    return 0;
}
```

## Configuration Options

The `KernelConfig` class allows extensive customization:

```java
KernelConfig config = kernel.getConfig();

// Scheduler configuration
config.setSchedulerType(KernelConfig.SchedulerType.ROUND_ROBIN);
config.setTimeSlice(1000);

// Process limits
config.setMaxProcesses(64);
config.setStackSize(8192);

// Feature toggles
config.setEnableDebugSyscalls(true);
config.setEnableFileSyscalls(true);

// I/O configuration
config.setUartBufferSize(256);
```

## Extending the Kernel

### Creating a Custom Scheduler

```java
public class MyCustomScheduler extends Scheduler {
    public MyCustomScheduler(int timeSlice) {
        super(timeSlice);
    }
    
    @Override
    public Process schedule(Collection<Process> processes) {
        // Your scheduling algorithm here
        return selectedProcess;
    }
    
    @Override
    public void addProcess(Process process) {
        // Add process to your scheduler's data structures
    }
    
    @Override
    public void removeProcess(Process process) {
        // Remove process from your scheduler's data structures
    }
    
    @Override
    public SchedulerStats getStats() {
        return new SchedulerStats(/* your stats */);
    }
}
```

### Adding the Custom Scheduler

```java
// In Kernel.java createScheduler() method
case CUSTOM:
    return new MyCustomScheduler(config.getTimeSlice());
```

## Task States and Lifecycle

```
[BOOT] → [INIT] → [READY] → [RUNNING] → [TERMINATED]
            |         |         ↓
            |         └─── [WAITING] ←──┘
            |                   ↑
            └─── [SHELL] ───────┘
```

- **BOOT**: System is initializing
- **INIT**: Initial process (PID 1) is starting
- **READY**: Task is ready to run
- **RUNNING**: Task is currently executing
- **WAITING**: Task is blocked (I/O, sleep, etc.)
- **TERMINATED**: Task has finished or been killed
- **SHELL**: Special shell process spawned by init

## Initialization Process

The system starts with a special Init process (PID 1) that:
1. Initializes system services
2. Can spawn a shell or other system processes
3. Manages system startup scripts

The Init process is responsible for:
- Setting up the system environment
- Starting system services
- Launching the default shell
- Managing process cleanup

## Error Handling

The kernel includes comprehensive error handling:
- Invalid ELF files are rejected
- System resources are properly cleaned up on errors
- Detailed error messages are provided for debugging
- The system fails gracefully with clear error messages

## Memory Management

The system supports two memory management approaches:

### 1. Contiguous Memory Management
```
[Process 1] [Process 2]  ...  [Process N]  [Free Space]
```

#### Key Features
- **Base/Limit Registers**: Hardware-enforced memory protection
- **Allocation Strategies**: First-Fit, Best-Fit
- **Process Isolation**: Each process has its own memory partition
- **Fragmentation Handling**: External fragmentation handled via compaction

### 2. Non-Contiguous Memory Management (Paging)
```
+------------------+ 0xFFFFFFFF
|     Stack        | (grows down)
|     ...          |
+------------------+
|     Heap         | (grows up)
|     ...          |
+------------------+
|     Data         |
+------------------+
|     Code         |
+------------------+ 0x00000000
```

#### Key Features
- **Paging**: 4KB pages with 2-level page tables (Sv32-like)
- **Virtual Memory**: Each process has its own 1GB virtual address space
- **Page Replacement**: Configurable paging policies (Demand Paging, Eager Paging)
- **Frame Allocation**: Global frame allocator with reverse mapping

### Memory Protection
- **Contiguous**: Base/Limit registers ensure process isolation
- **Paged**: Page table permissions control access
- **Common**:
  - Memory accesses are validated to prevent out-of-bounds access
  - Each task has its own isolated memory space
  - UART I/O region is handled separately for device communication

### UART Registers (Memory-Mapped I/O)
- `0x10000000`: UART_TX_DATA - Write data to transmit
- `0x10000004`: UART_RX_DATA - Read received data
- `0x10000008`: UART_STATUS - Status register
- `0x1000000C`: UART_CONTROL - Control register

### Process Memory Layout (Per Process)

#### Contiguous Mode
```
+-------------------+ 0x00000000
|      Code         |
|-------------------|
|      Data         |
|-------------------|
|      Heap         | (grows upward)
|                   |
|-------------------|
|                   |
|      Stack        | (grows downward)
+-------------------+ [Base + Limit]
```

#### Paged Mode
```
+------------------+ 0xFFFFFFFF
|     Stack        | (grows down)
|     ...          |
+------------------+
|     Heap         | (grows up)
|     ...          |
+------------------+
|     Data         |
+------------------+
|     Code         |
+------------------+ 0x00000000
```

### Notes
- **Contiguous Mode**:
  - Simpler, lower overhead
  - Suffers from external fragmentation
  - Requires compaction for long-running systems

- **Paged Mode**:
  - Eliminates external fragmentation
  - Supports virtual memory and demand paging
  - Higher overhead due to page tables
  - Configurable page replacement policies

- **Common**:
  - UART region (0x10000000-0x10000FFF) is memory-mapped I/O
  - Memory accesses are validated for protection
  - Each process has its own isolated address space

## Debugging and Monitoring

### Kernel Status

```java
kernel.printStatus();
// Output:
// === Kernel Status ===
// Total processes: 3
// Running: 1
// Ready: 2
// Waiting: 0
// Terminated: 0
// Scheduler: RoundRobinScheduler
// ====================
```

### Task Information

```java
for (Task task : kernel.getAllTasks()) {
    System.out.println(task.getStatusString());
}
```

### Scheduler Statistics

```java
SchedulerStats stats = kernel.getScheduler().getStats();
System.out.println("Schedules: " + stats.totalSchedules);
System.out.println("Context switches: " + stats.contextSwitches);
```

## Examples

See the following example files:
- `KernelExample.java`: Basic kernel usage
- `KernelSchedulerTest.java`: Scheduler comparison and testing
- `SystemCallDemo.java`: Demonstration of system call integration
- `SystemCallIntegrationTest.java`: Integration tests for CPU-kernel system call handling

## Integration with Existing Code

The kernel is designed to work seamlessly with your existing RV32iCpu and MemoryManager classes. It extends the functionality of your `Task.java` class, adding kernel-specific features while maintaining compatibility.

### System Call Integration

The integration between the CPU and kernel for system call handling works as follows:

1. **CPU Detection**: When the CPU executes an `ECALL` instruction, it sets an internal flag
2. **Kernel Polling**: The kernel checks `cpu.isEcall()` after each instruction execution
3. **Handler Invocation**: If an ECALL is detected, the kernel calls `SystemCallHandler.handleSystemCall()`
4. **State Management**: The system call handler can modify task state (e.g., WAITING, TERMINATED)

```java
// In Kernel.executeTask()
cpu.step(); // Execute one instruction
if (cpu.isEcall()) {
    handleSystemCall(task); // Delegates to SystemCallHandler
    break;
}
```

### CPU Modifications

The `RV32iCpu` class has been modified to:
- Track ECALL instructions with `lastInstructionWasEcall` flag
- Track exceptions with `exceptionOccurred` flag  
- Remove built-in system call handling (now handled by kernel)
- Provide `isEcall()` and `isException()` methods for kernel integration

## Future Enhancements

- Virtual memory management
- Inter-process communication (IPC)
- File system integration
- Network stack
- Device driver framework
- Real-time scheduling support