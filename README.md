# RV32IM Simulator Kernel Integrated

A modular, extensible kernel implementation for the RV32IM CPU simulator, written in Java. This project emphasizes a **pluggable architecture**, allowing developers to swap out core OS subsystems (Scheduling, Memory Management) with a single line of configuration code.

## Architecture Overview

The kernel is designed around decoupled interfaces, preventing monolithic dependencies.

```text
[Hardware Layer]      [Kernel Layer]                  [Modules]
RV32iCpu  <---------> Kernel (Coordinator)
Memory    <---------> ├── TaskManager
                      ├── SystemCallHandler
                      │
                      ├── IScheduler  <-------------> RoundRobin / Priority / Cooperative
                      │
                      └── MemoryCoordinator <-------> Contiguous (First-Fit, Best-Fit)
                                             └____--> Paging (Demand, Eager)
````

## Key Features

### 1\. **Modular Memory Management**

The system supports multiple memory models determined at boot time. You can switch between **Contiguous** and **Non-Contiguous** modes instantly.

  - **Contiguous Allocation**: Supports pluggable allocation strategies (First-Fit, Best-Fit, etc.).
  - **Paging (Virtual Memory)**: Supports pluggable paging policies (Demand Paging vs. Eager Paging) and replacement algorithms (Clock, LRU).

### 2\. **Pluggable Scheduler System**

The scheduler is defined by a simple abstract base class.

  - **Round Robin**: Time-sliced scheduling.
  - **Cooperative**: Tasks run until voluntary yield.
  - **Priority**: Preemptive scheduling based on task priority.

### 3\. **Process Hierarchy & Orphan Handling**

  - Full parent-child relationship tracking.
  - **Unix-style Reparenting**: Adopted by `Init` (PID 1) upon parent death.
  - **Zombie Reaping**: Prevents resource leaks via `wait()`/`exit()` semantics.

-----

## Configuration & Quick Start

You can select your kernel modules in `App.java` before booting:

```java
// 1. Choose Memory Mode (PAGING or CONTIGUOUS)
RV32iComputer computer = new RV32iComputer(128 * 1024 * 1024, 16, MemoryMode.PAGING);

// 2. Choose Scheduler
Kernel kernel = computer.getKernel();
kernel.getConfig().setSchedulerType(KernelConfig.SchedulerType.PRIORITY);
kernel.getConfig().setTimeSlice(1000);

// 3. Boot
kernel.start();
```

-----

## Extending the Kernel

Because the kernel uses interfaces and strategy patterns, implementing new algorithms is straightforward.

### 1\. implementing a New Scheduler

To add a new scheduling algorithm (e.g., "Lottery Scheduling"), simply extend the `Scheduler` class.

**Step A: Create the Class**

```java
public class LotteryScheduler extends Scheduler {
    private Random random = new Random();

    public LotteryScheduler(int timeSlice) {
        super(timeSlice);
    }

    @Override
    public Task schedule(Collection<Task> tasks) {
        // Your custom logic to pick a 'winning' ticket
        List<Task> readyTasks = tasks.stream()
            .filter(t -> t.getState() == TaskState.READY)
            .collect(Collectors.toList());
            
        if (readyTasks.isEmpty()) return null;
        return readyTasks.get(random.nextInt(readyTasks.size()));
    }
    
    // Implement addTask/removeTask...
}
```

**Step B: Register it in Kernel.java**

```java
// In Kernel.java -> createScheduler()
case LOTTERY:
    return new LotteryScheduler(config.getTimeSlice());
```

### 2\. Implementing a New Memory Allocator

If running in `CONTIGUOUS` mode, you can change how the kernel finds free memory holes by implementing the `AllocationStrategy` interface.

**Step A: Create the Strategy**

```java
public class WorstFitStrategy implements AllocationStrategy {
    @Override
    public int findRegion(List<MemoryBlock> holes, int requestSize) {
        // Find the LARGEST hole to leave big chunks available
        MemoryBlock worst = null;
        for (MemoryBlock hole : holes) {
            if (hole.size >= requestSize) {
                if (worst == null || hole.size > worst.size)
                    worst = hole;
            }
        }
        return (worst != null) ? worst.start : -1;
    }
}
```

**Step B: Plug it in (RV32iComputer.java)**

```java
// Swap 'BestFitStrategy' with your new class
if (mode == MemoryMode.CONTIGUOUS) {
    AllocationStrategy allocator = new WorstFitStrategy(); // <--- Swapped!
    this.memory = new ContiguousMemoryManager(memSize, allocator);
}
```

### 3\. Implementing a New Paging Policy

If running in `PAGING` mode, you can change how pages are loaded (e.g., implementing `PredictivePager`) by implementing the `Pager` interface.

```java
public class PredictivePager implements Pager {
    @Override
    public int ensureResident(AddressSpace as, int va, VmAccess access) {
        // Logic to bring in page 'va' AND 'va + 1' (prefetching)
        // ...
        return frameNumber;
    }
}
```

-----

## System Calls

The kernel exposes a standard UNIX-like system call interface to user programs:

| Syscall | Code | Description |
| :--- | :--- | :--- |
| `SYS_EXIT` | 93 | Terminate process (become Zombie) |
| `SYS_READ` | 63 | Read from file descriptor/UART |
| `SYS_WRITE` | 64 | Write to file descriptor/UART |
| `SYS_YIELD` | 124 | Voluntarily yield CPU time slice |
| `SYS_FORK` | 220 | Copy current process (parent -\> child) |
| `SYS_EXEC` | 221 | Load new executable image |
| `SYS_WAIT` | 260 | Wait for child to change state |

-----

## Task States Lifecycle

The kernel manages tasks through a rigorous state machine to ensure stability.

```text
[NEW] → [READY] ↔ [RUNNING]
           ↑         ↓
           └─── [WAITING]
                 ↓
            [TERMINATED] (Zombie)
                 ↓
             [REMOVED]
```