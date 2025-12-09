package cse311;

import cse311.kernel.Kernel;
import cse311.kernel.process.Task;
import cse311.kernel.process.TaskState;

/**
 * An abstract base class for tasks whose logic is written in Java,
 * not RISC-V ELF code.
 */
public abstract class JavaTask extends Task {

    protected Kernel kernel;

    /**
     * Creates a new Java-based task.
     * 
     * @param id     The Process ID for this task.
     * @param name   The name of this task (e.g., "init", "sh").
     * @param kernel A reference to the kernel, used to perform "syscalls".
     * @param parent The parent task (can be null for 'init').
     */
    public JavaTask(int id, String name, Kernel kernel, Task parent) {
        // Call the base 'Task' constructor with dummy values
        // for entryPoint, stackSize, and stackBase, as they aren't used.
        super(id, name, 0, 0, 0);

        this.kernel = kernel;
        this.setState(TaskState.READY); // Start in the ready state

        if (parent != null) {
            this.setParent(parent);
            parent.addChild(this);
        }
    }

    /**
     * This method contains the Java-based logic for the process.
     * The kernel will call this method repeatedly when the task is scheduled.
     * The task must manage its own state (e.g., waiting, exiting).
     */
    public abstract void runLogic();
}
