package cse311.programs; // You can create a new package

import cse311.*;
import cse311.kernel.Kernel;
import cse311.kernel.process.Task;
import cse311.kernel.process.TaskState;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class ShellTask extends JavaTask {

    // The shell's state machine
    private enum ShellState {
        READ_CMD, // Printing "$" and waiting for input
        CMD_RUNNING, // Waiting for a child process to finish
    }

    private ShellState state = ShellState.READ_CMD;
    private Task runningChild = null;
    private Scanner hostStdin; // Reads from your computer's terminal

    public ShellTask(int id, Kernel kernel, Task parent) {
        super(id, "sh", kernel, parent);
        // For simplicity, this shell reads from the *host's* System.in.
        // A real shell would use the kernel's read() syscall for the UART.
        this.hostStdin = new Scanner(System.in);
    }

    @Override
    public void runLogic() {
        switch (state) {
            case READ_CMD:
                // Print prompt
                System.out.print("$ ");

                // Read a line of input
                // This is a *blocking* call on the host, which is fine
                // because it just means the *simulation* waits for your input.
                if (!hostStdin.hasNextLine()) {
                    System.out.println("sh: EOF, exiting.");
                    this.setState(TaskState.TERMINATED); // Exit the shell
                    return;
                }
                String cmdLine = hostStdin.nextLine().trim();

                if (cmdLine.isEmpty()) {
                    this.setState(TaskState.READY); // Loop again
                    return;
                }

                // We have a command, execute it
                executeCommand(cmdLine);
                break;

            case CMD_RUNNING:
                // We are waiting for our child process to finish
                if (runningChild == null || runningChild.getState() == TaskState.TERMINATED) {
                    if (runningChild != null) {
                        System.out.println("sh: Reaped child " + runningChild.getId());
                        // "wait()" for the child (cleanup)
                        kernel.getTaskManager().cleanupTaskAndNotify(runningChild);
                        runningChild = null;
                    }

                    // Go back to reading the next command
                    this.state = ShellState.READ_CMD;
                    this.setState(TaskState.READY);
                } else {
                    // Child is still running. Go to sleep and wait for it.
                    this.waitFor(WaitReason.PROCESS_EXIT, runningChild.getId());
                }
                break;
        }
    }

    private void executeCommand(String cmdLine) {
        String[] parts = cmdLine.split("\\s+");
        String cmd = parts[0];

        // Handle 'cd' (not implemented, but shows structure)
        if (cmd.equals("cd")) {
            System.out.println("sh: 'cd' is not implemented in this demo shell.");
            this.setState(TaskState.READY); // Go back to READ_CMD
            return;
        }

        // This is the "fork & exec" step.
        try {
            // Assume the ELF file is on your host filesystem.
            // You'll need to adjust this path to find your ELF files
            // (e.g., "hello_os.elf" from your App.java).
            String elfPath = "app/build/resources/main/" + cmd + ".elf";

            byte[] elfData = Files.readAllBytes(Paths.get(elfPath));

            // This one call simulates fork() and exec()
            Task childTask = kernel.createTask(elfData, cmd);
            childTask.setParent(this); // Set us as the parent
            this.addChild(childTask);

            System.out.println("sh: Executing '" + cmd + "' as PID " + childTask.getId());

            this.runningChild = childTask;
            this.state = ShellState.CMD_RUNNING;

            // "Block" and wait for the child to finish.
            this.waitFor(WaitReason.PROCESS_EXIT, childTask.getId());

        } catch (Exception e) {
            System.err.println("sh: command not found or failed to load: " + cmd);
            System.err.println("   (Searched for: " + "app/build/resources/main/" + cmd + ".elf" + ")");
            System.err.println("   (Error: " + e.getMessage() + ")");

            // Go back to reading the next command
            this.state = ShellState.READ_CMD;
            this.setState(TaskState.READY);
        }
    }
}