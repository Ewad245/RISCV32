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

3. **Task Initialization**
   - The bootloader spawns the Init task (PID 1)
   - The Init task can spawn additional tasks (like a shell)

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

// 3. (Optional) Mount filesystem (if needed)
// kernel.mountFileSystem("disk.img");

// 4. (Optional) Load initial ELF program
kernel.createTask("User_Program_ELF/init.elf");

// 5. Start the kernel (this blocks forever)
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
- `SYS_EXIT (93)`: Terminate task
- `SYS_WRITE (64)`: Write to file descriptor
- `SYS_READ (63)`: Read from file descriptor
- `SYS_YIELD (124)`: Voluntarily yield CPU
- `SYS_GETPID (172)`: Get task ID

### Custom System Calls
- `SYS_DEBUG_PRINT (1000)`: Debug output
- `SYS_GET_TIME (1001)`: Get current time
- `SYS_NANOSLEEP (1002)`: Sleep for specified time (replaces deprecated SYS_SLEEP)
- `SYS_CV_WAIT (280)`: Condition variable wait
- `SYS_CV_SIGNAL (281)`: Condition variable signal
- `SYS_CV_BROADCAST (282)`: Condition variable broadcast

### Adding Custom System Calls

```java
// In SystemCallHandler.java
case SYS_MY_CUSTOM_CALL:
    result = handleMyCustomCall(task, cpu, arg0, arg1);
    break;

private int handleMyCustomCall(Task task, RV32Cpu cpu, int arg0, int arg1) {
    // Your custom system call implementation
    // Access task registers via cpu.getRegisters()
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

// Task limits
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
    public Task schedule(Collection<Task> tasks) {
        // Your scheduling algorithm here
        return selectedTask;
    }
    
    @Override
    public void addTask(Task task) {
        // Add task to your scheduler's data structures
    }
    
    @Override
    public void removeTask(Task task) {
        // Remove task from your scheduler's data structures
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

Note: You'll need to add a `CUSTOM` enum value to `KernelConfig.SchedulerType` first.

## Task States and Lifecycle

```
[BOOT] → [INIT] → [READY] → [RUNNING] → [TERMINATED]
            |         |         ↓
            |         └─── [WAITING] ←──┘
            |                   ↑
            └─── [SHELL] ───────┘
```

- **READY**: Task is ready to run and waiting to be scheduled
- **RUNNING**: Task is currently executing on a CPU core
- **WAITING**: Task is blocked (I/O, timer, pipe, condition variable)
- **TERMINATED**: Task has finished execution or was killed

> Note: The kernel uses a multi-core SMP architecture with 5 CPU cores. The Bootstrap Processor (BSP/Core 0) coordinates scheduling, while Application Processors (APs) can be started for parallel execution.

## Initialization Process

The system starts with a special Init task (PID 1) that:
1. Initializes system services
2. Can spawn a shell or other system tasks
3. Manages system startup scripts

The Init task is responsible for:
- Setting up the system environment
- Starting system services
- Launching the default shell
- Managing task cleanup

> **Note**: The kernel boots with only the Bootstrap Processor (BSP/Core 0) active. Application Processors (APs) must be explicitly started by calling `kernel.wakeupApplicationProcessors()` after initialization.

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
[Task 1] [Task 2]  ...  [Task N]  [Free Space]
```

#### Key Features
- **Base/Limit Registers**: Hardware-enforced memory protection
- **Allocation Strategies**: First-Fit, Best-Fit
- **Task Isolation**: Each task has its own memory partition
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
- **Virtual Memory**: Each task has its own 1GB virtual address space
- **Page Replacement**: Configurable paging policies (Demand Paging, Eager Paging)
- **Frame Allocation**: Global frame allocator with reverse mapping

### Memory Protection
- **Contiguous**: Base/Limit registers ensure task isolation
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

### Task Memory Layout (Per Task)

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
  - Each task has its own isolated address space

## Debugging and Monitoring

### Kernel Status

```java
kernel.printStatus();
// Output:
// === Kernel Status ===
// Total tasks: 3
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

Note: The scheduler uses internal spinlocks for thread-safe operation in multi-core environments.

## Examples

See the following example files in `app/src/main/java/cse311/`:
- [`KernelExample.java`](app/src/main/java/cse311/KernelExample.java): Basic kernel usage
- [`KernelSchedulerTest.java`](app/src/main/java/cse311/KernelSchedulerTest.java): Scheduler comparison and testing
- [`SystemCallDemo.java`](app/src/main/java/cse311/SystemCallDemo.java): Demonstration of system call integration
- Integration tests: `app/src/test/java/cse311/SystemCallIntegrationTest.java`

## Integration with Existing Code

The kernel is designed to work seamlessly with your existing RV32Cpu and MemoryManager classes. It extends the functionality of your `Task.java` class, adding kernel-specific features while maintaining compatibility.

---

## 🔄 Inter-Process Communication (IPC) - Complete Reference

The kernel provides **6 IPC mechanisms** for process coordination and data sharing:

### **Overview Table**

| Mechanism | Type | System Call(s) | Performance | Use Case |
|-----------|------|----------------|-------------|----------|
| **Pipes** | Message Passing | `SYS_PIPE (59)` | Medium | Byte-stream communication |
| **Condition Variables** | Synchronization | `SYS_CV_WAIT/SIGNAL/BROADCAST (280-282)` | Fast | Thread coordination |
| **Shared Memory** | Shared Memory | `SYS_SHM_OPEN/ATTACH (20-21)` | Very Fast | High-throughput data sharing |
| **Thread Creation** | Shared Address Space | `SYS_CLONE (220)` | Fast | Multi-threaded apps |
| **FD Inheritance** | Resource Sharing | Via `clone()` flags | Fast | Process cooperation |
| **User-space Mutexes** | Synchronization | Atomic instructions | Very Fast | Critical sections |

### **1. Pipes** - Byte-stream Communication

**Files:**
- [`Pipe.java`](app/src/main/java/cse311/kernel/fs/Pipe.java) - 4KB circular buffer implementation
- [`SystemCallHandler.handlePipe()`](app/src/main/java/cse311/kernel/syscall/SystemCallHandler.java#L1407) - Pipe creation
- [`SystemCallHandler.handleRead()`](app/src/main/java/cse311/kernel/syscall/SystemCallHandler.java#L514) - Pipe read (blocking)
- [`SystemCallHandler.handleWrite()`](app/src/main/java/cse311/kernel/syscall/SystemCallHandler.java#L376) - Pipe write (blocking)

**Implementation Details:**
```java
// Pipe structure
public class Pipe {
    public static final int PIPESIZE = 4096; // 4KB buffer
    private byte[] buffer = new byte[PIPESIZE];
    private int readIndex, writeIndex;
    private boolean readOpen, writeOpen;
}
```

**Blocking Semantics:**
- **Writers block** when pipe is full (4096 bytes)
- **Readers block** when pipe is empty
- **Automatic wakeup** when data becomes available or space frees up
- **Broken pipe** (-1 return) when reading from pipe with closed write end

**Task Management:**
```java
// In TaskManager
private Map<Pipe, Queue<Task>> pipeWaitQueues; // Tasks blocked on pipes

public void addTaskToPipeWaitQueue(Pipe pipe, Task task)
public void wakeTasksBlockedOnPipe(Pipe pipe)
```

**Usage Example:**
```c
int fds[2];
pipe(fds);  // fds[0] = read, fds[1] = write

if (fork() == 0) {
    // Child - write
    close(fds[0]);
    write(fds[1], "Hello", 5);
    close(fds[1]);
} else {
    // Parent - read
    close(fds[1]);
    read(fds[0], buffer, 5);
    close(fds[0]);
}
```

---

### **2. Condition Variables** - Thread Synchronization

**Files:**
- [`TaskManager.conditionVariables`](app/src/main/java/cse311/kernel/process/TaskManager.java#L33) - Per-CV wait queues
- [`SystemCallHandler.handleCvWait()`](app/src/main/java/cse311/kernel/syscall/SystemCallHandler.java#L1446) - Wait implementation
- [`SystemCallHandler.handleCvSignal()`](app/src/main/java/cse311/kernel/syscall/SystemCallHandler.java#L1488) - Signal one waiter
- [`SystemCallHandler.handleCvBroadcast()`](app/src/main/java/cse311/kernel/syscall/SystemCallHandler.java#L1501) - Broadcast to all

**Implementation Details:**
```java
// In TaskManager
private Map<Integer, Queue<Task>> conditionVariables; // cvId -> wait queue

public void waitOnCondition(Task task, int cvId)
public boolean signalCondition(int cvId)  // Wakes one
public int broadcastCondition(int cvId)   // Wakes all
```

**Monitor Pattern (with Mutex):**
```c
// C library wrapper
void cv_wait(cv_t *cv, mutex_t *mutex) {
    mutex_unlock(mutex);
    syscall(SYS_CV_WAIT, cv->id, 0);
    mutex_lock(mutex);  // Re-acquire on wakeup
}

void cv_signal(cv_t *cv) {
    syscall(SYS_CV_SIGNAL, cv->id, 0);
}

void cv_broadcast(cv_t *cv) {
    syscall(SYS_CV_BROADCAST, cv->id, 0);
}
```

**Usage Example:**
```c
// Producer-Consumer with bounded buffer
mutex_lock(&mutex);
while (buffer_empty()) {
    cv_wait(&not_empty, &mutex);
}
// Consume item
cv_signal(&not_full);
mutex_unlock(&mutex);
```

---

### **3. Shared Memory** - High-performance Data Sharing

**Files:**
- [`PagedMemoryManager.sharedKeyMap`](app/src/main/java/cse311/kernel/NonContiguous/paging/PagedMemoryManager.java#L38) - Key to frame mapping
- [`PagedMemoryManager.openSharedRegion()`](app/src/main/java/cse311/kernel/NonContiguous/paging/PagedMemoryManager.java#L350) - Create/open shared region
- [`PagedMemoryManager.mapSharedPage()`](app/src/main/java/cse311/kernel/NonContiguous/paging/PagedMemoryManager.java#L372) - Map to address space
- [`SystemCallHandler`](app/src/main/java/cse311/kernel/syscall/SystemCallHandler.java#L230) - SYS_SHM_OPEN handling
- [`SystemCallHandler`](app/src/main/java/cse311/kernel/syscall/SystemCallHandler.java#L244) - SYS_SHM_ATTACH handling

**Implementation Details:**
```java
// In PagedMemoryManager
private Map<Integer, Integer> sharedKeyMap;  // key -> frameId
private int[] frameRefCount;  // Reference counting

public synchronized int openSharedRegion(int key) {
    // 1. Check if key exists
    // 2. If not, allocate new frame
    // 3. Increment ref count
    // 4. Return frameId
}

public synchronized boolean mapSharedPage(AddressSpace as, int vpn, int frame, boolean write) {
    // 1. Map virtual page (vpn) to physical frame
    // 2. Set write permission
    // 3. Update page table
}
```

**Usage Example:**
```c
// Process A - Create shared memory
int shmid = shm_open(0x1234);  // Key = 0x1234
shm_attach(shmid, 0x50000000);  // Map to virtual addr 0x50000000
*(int*)0x50000000 = 42;  // Write to shared memory

// Process B - Access same shared memory
int shmid = shm_open(0x1234);  // Same key
shm_attach(shmid, 0x50000000);
int value = *(int*)0x50000000;  // Read: 42
```

**Limitations:**
- ✅ Works in **Paging mode only**
- ❌ Not available in Contiguous mode
- Page-aligned (4KB granularity)

---

### **4. Thread Creation (CLONE_VM)** - Shared Address Space

**Files:**
- [`SystemCallHandler.handleClone()`](app/src/main/java/cse311/kernel/syscall/SystemCallHandler.java#L601) - Clone implementation
- [`TaskManager.clone()`](app/src/main/java/cse311/kernel/process/TaskManager.java#L267) - Task duplication
- [`AddressSpace.refCount`](app/src/main/java/cse311/kernel/NonContiguous/paging/AddressSpace.java#L55) - Shared address space ref counting

**Clone Flags:**
```java
public static final int CLONE_VM      = 0x00000100; // Share memory
public static final int CLONE_FILES   = 0x00000400; // Share file descriptors
public static final int CLONE_SIGHAND = 0x00000800; // Share signal handlers
```

**Implementation Details:**
```java
// In SystemCallHandler
private int handleClone(RV32Cpu cpu, Task parent, int flags, int stack) {
    boolean shareMemory = (flags & CLONE_VM) != 0;
    boolean shareFiles = (flags & CLONE_FILES) != 0;
    
    // Create child task
    Task child = kernel.getTaskManager().clone(parent, shareMemory, shareFiles);
    
    if (shareMemory) {
        // Share address space (increment ref count)
        child.setAddressSpace(parent.getAddressSpace());
    }
    
    // Set child stack if provided
    if (stack != 0) {
        child.setStackPointer(stack);
    }
    
    return child.getId();  // Return child PID to parent
}
```

**Usage Example:**
```c
// Create thread (share memory)
pid_t tid = clone(CLONE_VM | CLONE_FILES, thread_stack);

if (tid == 0) {
    // Child thread - shares memory with parent
    global_var = 42;  // Visible to all threads
    return 0;
} else {
    // Parent thread
    waitpid(tid, NULL, 0);
}

// Create process (separate memory)
pid_t pid = clone(0, process_stack);
```

---

### **5. File Descriptor Inheritance** - Implicit IPC

**Mechanism:** Automatic via `clone()` with `CLONE_FILES` flag

**Implementation:**
```java
// In TaskManager.clone()
if (shareFiles) {
    // Copy file descriptor table (shallow copy - same FileDescriptor objects)
    child.openFiles = Arrays.copyOf(parent.openFiles, NOFILE);
}
```

**Usage:**
```c
// Create pipe
int fds[2];
pipe(fds);

// Clone with CLONE_FILES
pid_t child = clone(CLONE_VM | CLONE_FILES, stack);

if (child == 0) {
    // Child inherits fds[0] and fds[1]
    write(fds[1], "data", 4);  // Can use inherited pipe
} else {
    read(fds[0], buffer, 4);  // Parent uses same pipe
}
```

---

### **6. User-space Mutexes** - Atomic Synchronization

**Files:**
- `C_Library/lib/libthread.c` - Mutex implementation
- Uses RV32A atomic instructions (LR/SC, AMO*)

**Implementation:**
```c
// Spinlock using atomic instructions
void mutex_lock(mutex_t *m) {
    while (atomic_exchange(&m->locked, 1) != 0) {
        // Spin until lock acquired
    }
}

void mutex_unlock(mutex_t *m) {
    atomic_store(&m->locked, 0);
}
```

**Atomic Instructions Used:**
- `LR.W` (Load Reserved) - Atomic load with reservation
- `SC.W` (Store Conditional) - Conditional store
- `AMOSWAP.W` - Atomic swap
- `AMOADD.W` - Atomic add

**Thread Safety:**
- ✅ Works across multiple CPU cores
- ✅ Proper memory ordering
- ✅ Integration with condition variables

---

## IPC Performance Comparison

| Mechanism | Latency | Throughput | Best For |
|-----------|---------|------------|----------|
| **Shared Memory** | ~1 cycle | GB/s | Large data, frequent access |
| **User-space Mutex** | ~10-100 cycles | N/A | Critical sections |
| **Condition Variable** | ~100-1000 cycles | N/A | Thread coordination |
| **Pipe** | ~1000-10000 cycles | MB/s | Byte streams, unrelated processes |
| **Thread (CLONE_VM)** | ~1000 cycles | N/A | Parallel tasks |

---

## IPC Selection Guide

**Choose based on your needs:**

1. **Need to share large data structures?**
   - ✅ **Shared Memory** (fastest, but paging mode only)
   - ✅ **CLONE_VM threads** (share entire address space)

2. **Need synchronization?**
   - ✅ **User-space Mutex** (fast, for critical sections)
   - ✅ **Condition Variables** (for waiting on conditions)

3. **Need message passing?**
   - ✅ **Pipes** (byte-stream, producer-consumer)
   - ✅ **File descriptors** (inheritance between related processes)

4. **Need parallel execution?**
   - ✅ **CLONE_VM threads** (shared memory, low overhead)
   - ✅ **Separate processes** (isolation, stability)

---

## Synchronization Patterns

### **Producer-Consumer with Pipe**
```c
int fds[2];
pipe(fds);

// Producer
write(fds[1], &data, sizeof(data));

// Consumer
read(fds[0], &data, sizeof(data));
```

### **Monitor Pattern with Mutex + CV**
```c
mutex_lock(&m);
while (condition_not_met) {
    cv_wait(&cv, &m);
}
// Critical section
cv_signal(&cv);
mutex_unlock(&m);
```

### **Shared Memory with Mutex**
```c
// Thread A
mutex_lock(&m);
shared_data = 42;
mutex_unlock(&m);

// Thread B
mutex_lock(&m);
value = shared_data;  // Reads 42
mutex_unlock(&m);
```

### System Call Integration

The integration between the CPU and kernel for system call handling works as follows:

1. **CPU Detection**: When the CPU executes an `ECALL` instruction, it sets `lastInstructionWasEcall = true`
2. **Kernel Polling**: The kernel checks `cpu.isEcall()` after each instruction execution
3. **Handler Invocation**: If an ECALL is detected, the kernel calls `SystemCallHandler.handleSystemCall()`
4. **State Management**: The system call handler can modify task state (e.g., WAITING, TERMINATED)
5. **Exception Handling**: If an exception occurs during execution, `cpu.isException()` is checked and handled

```java
// In Kernel.executeTask()
cpu.step(); // Execute one instruction
if (cpu.isEcall()) {
    handleSystemCall(task); // Delegates to SystemCallHandler
    break;
}
if (cpu.isException()) {
    handleException(task); // Handle exceptions
    break;
}
```

### CPU Modifications

The `RV32Cpu` class has been modified to:
- Track ECALL instructions with `lastInstructionWasEcall` flag
- Track exceptions with `exceptionOccurred` flag  
- Remove built-in system call handling (now handled by kernel)
- Provide `isEcall()` and `isException()` methods for kernel integration
- Support multi-core execution with per-core CPU instances

### Memory Management Integration

The kernel uses a strategy pattern for memory management:

```java
// ProcessMemoryCoordinator interface abstracts memory management
public interface ProcessMemoryCoordinator {
    MemoryLayout allocateMemory(int pid, int sizeBytes);
    ProgramInfo loadProgram(int pid, byte[] elfData);
    void freeMemory(int pid);
    void copyMemory(int parentPid, int childPid);
    void switchContext(int pid);
    boolean expandHeap(int pid, int currentBreak, int newBreak);
}
```

Two implementations are available:
- **ContiguousMemoryCoordinator**: Traditional contiguous memory allocation
- **NonContiguousMemoryCoordinator**: Paging-based virtual memory (Sv32-like)

## Future Enhancements

- [ ] Network stack implementation
- [ ] Device driver framework expansion
- [ ] Advanced paging features (copy-on-write fork)
- [ ] Real-time scheduling support
- [ ] User-space threading library support
- [ ] POSIX signals (SIGCHLD, SIGTERM, etc.)
- [ ] Message queues (msgget, msgsnd, msgrcv)
- [ ] Named semaphores
- [ ] Unix domain sockets