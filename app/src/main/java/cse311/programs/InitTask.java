package cse311.programs; // You can create a new package

import cse311.*;
import cse311.kernel.Kernel;
import cse311.kernel.process.Task;
import cse311.kernel.process.TaskState;

public class InitTask extends JavaTask {

    private Task shellTask;

    public InitTask(int id, Kernel kernel) {
        super(id, "init", kernel, null); // 'init' has no parent

        // In your TaskManager.java, you should set this task as the
        // `initTask` so orphans can be reparented to it.
        kernel.getTaskManager().setInitTask(this);
    }

    @Override
    public void runLogic() {
        // This method is like init's main "for(;;)" loop.
        // The kernel calls it every time 'init' is scheduled.

        // 1. Check if the shell is running. If not, start it.
        if (shellTask == null || shellTask.getState() == TaskState.TERMINATED) {
            if (shellTask != null) {
                // Shell must have exited, clean it up
                System.out.println("init: Shell exited. Reaping.");
                kernel.getTaskManager().cleanupTaskAndNotify(shellTask);
            }

            // "fork/exec" the shell
            System.out.println("init: Starting sh...");
            try {
                // Create our new Java-based ShellTask
                this.shellTask = new ShellTask(kernel.getNextPid(), kernel, this);
                kernel.addTaskToScheduler(this.shellTask);
            } catch (Exception e) {
                System.err.println("init: Failed to start sh: " + e.getMessage());
                // Wait 5 seconds before retrying
                this.waitFor(WaitReason.TIMER, System.currentTimeMillis() + 5000);
                return;
            }
        }

        // 2. Look for an orphaned child process to reap (like wait())
        Task orphanToReap = null;
        for (Task child : this.getChildren()) {
            if (child != shellTask && child.getState() == TaskState.TERMINATED) {
                orphanToReap = child;
                break;
            }
        }

        if (orphanToReap != null) {
            // Found an orphan to reap
            System.out.println("init: Reaping orphaned process " + orphanToReap.getId());
            kernel.getTaskManager().cleanupTaskAndNotify(orphanToReap);

            // Stay in READY state to immediately check for more orphans
            this.setState(TaskState.READY);
            return;
        }

        // 3. If no orphans to reap, go to sleep.
        // We will be woken up by the kernel when one of our children exits.
        // We "wait" for any child to exit.
        this.waitFor(WaitReason.PROCESS_EXIT, -1);
    }
}