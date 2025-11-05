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

### Basic Usage

```java
// Create memory and CPU
SimpleMemory simpleMemory = new SimpleMemory(128 * 1024 * 1024);
MemoryManager memory = new MemoryManager(simpleMemory);
RV32iCpu cpu = new RV32iCpu(memory);

// Create kernel
Kernel kernel = new Kernel(cpu, memory);

// Configure scheduler
kernel.getConfig().setSchedulerType(KernelConfig.SchedulerType.ROUND_ROBIN);
kernel.getConfig().setTimeSlice(1000);

// Create tasks
Task task1 = kernel.createTask(elfData, "my_program");
Task task2 = kernel.createTask("path/to/program.elf");

// Start kernel
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
[NEW] → [READY] → [RUNNING] → [TERMINATED]
           ↑         ↓
           └─── [WAITING] ←──┘
```

- **READY**: Task is ready to run
- **RUNNING**: Task is currently executing
- **WAITING**: Task is blocked (I/O, sleep, etc.)
- **TERMINATED**: Task has finished or been killed

## Memory Layout

```
0x00000000 - 0x0FFFFFFF: User space
0x10000000 - 0x1FFFFFFF: Device memory (UART, etc.)
0x20000000 - 0x6FFFFFFF: Kernel space
0x70000000 - 0x7FFFFFFF: Task stacks
0x80000000 - 0xFFFFFFFF: High memory
```

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