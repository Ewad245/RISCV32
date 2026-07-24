package cse311;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import cse311.Logger.FileLogger;

@SuppressWarnings({"PMD.RelianceOnDefaultCharset", "PMD.AvoidCatchingGenericException"})
public class InputThread {

    private volatile boolean running = true;

    public void stop() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    public void getInput(MemoryManager manager) {
        try (Scanner reader = new Scanner(System.in)) {
            while (running) {
                try {
                    if (System.in.available() > 0 && reader.hasNextLine()) {
                        String input = reader.nextLine();
                        manager.getInput(input + "\n");
                    } else {
                        Thread.sleep(100);
                    }
                } catch (IOException | InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            FileLogger.log(FileLogger.LogLevel.ERROR,
                    "InputThread error: " + e.getMessage());
        }
    }

}
