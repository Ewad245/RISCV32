package cse311.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import cse311.App;

public class FileLogger {
    private static Path path = Paths.get(".." + App.file_seperator + "LogFiles" + App.file_seperator + "log.txt");
    private static Path exceptionPath = Paths
            .get(".." + App.file_seperator + "LogFiles" + App.file_seperator + "exception_log.txt");
    private static OutputStream logOutputStream;
    private static OutputStream exceptionOutputStream;
    public static FileLogger instance;

    private FileLogger() {
        initializeLogFile(path, false);
        initializeLogFile(exceptionPath, true);
    }

    private void initializeLogFile(Path p, boolean isException) {
        if (!Files.exists(p)) {
            try {
                if (p.getParent() != null) {
                    Files.createDirectories(p.getParent());
                }
                Files.createFile(p);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            File file = p.toFile();
            file.delete();
            try {
                Files.createFile(p);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            OutputStream os = Files.newOutputStream(p);
            if (isException) {
                exceptionOutputStream = os;
            } else {
                logOutputStream = os;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized FileLogger getInstance() {
        if (instance == null) {
            instance = new FileLogger();
        }
        return instance;
    }

    private static void ensureInitialized() {
        if (instance == null) {
            getInstance();
        }
    }

    public enum LogLevel {
        DEBUG, INFO, ERROR
    }

    public static void log(LogLevel level, Object message) {
        ensureInitialized();
        if (logOutputStream == null)
            return;

        try {
            String timestamp = "[" + level + "] "; // meaningful prefix
            String msgContent = (message == null ? "null" : message.toString()) + "\n";
            String fileMsg = timestamp + msgContent;

            // Always write to file
            logOutputStream.write(fileMsg.getBytes());
            logOutputStream.flush();

            // Only print to console if INFO or ERROR (or if we want to config this later)
            if (level != LogLevel.DEBUG) {
                System.out.print(msgContent); // Console doesn't necessarily need the [INFO] prefix if clutter is a
                                              // concern, but typically it helps.
                // Keeping it simpler for console to match previous behavior: just the message
                // for now,
                // or maybe we WANT the prefix? The user asked to hide "debug text".
                // Let's just print the message content to console to mimic previous behavior
                // exact, just filtered.
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void log(Object message) {
        // Default to INFO
        log(LogLevel.INFO, message);
    }

    public static void log(Throwable e) {
        ensureInitialized();
        if (exceptionOutputStream == null)
            return;
        try {
            String msg = e.toString() + "\n";
            exceptionOutputStream.write(msg.getBytes());
            // Also print to console as ERROR
            System.out.print(msg);

            for (StackTraceElement element : e.getStackTrace()) {
                String elemMsg = "\t" + element.toString() + "\n";
                exceptionOutputStream.write(elemMsg.getBytes());
                System.out.print(elemMsg);
            }
            exceptionOutputStream.flush();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public static void print(Object message) {
        ensureInitialized();
        // Bypass log.txt for user I/O to avoid polluting the system log
        // Just print directly to console (System.out)
        String msg = (message == null ? "null" : message.toString());
        System.out.print(msg);
    }
}
