package cse311;

import java.io.InputStream;
import java.util.Scanner;

public class InputThread {

    public void getInput(MemoryManager manager) {
        try (Scanner reader = new Scanner(System.in)) {
            while (true) {
                if (reader.hasNextLine()) {
                    String input = reader.nextLine();
                    manager.getInput(input + "\n");
                } else {
                    // EOF reached (e.g., end of non-interactive input)
                    // Sleep to avoid busy-waiting, effectively disabling input
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("InputThread error: " + e.getMessage());
        }
    }

}
