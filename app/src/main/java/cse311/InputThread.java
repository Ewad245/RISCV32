package cse311;

import java.io.InputStream;
import java.util.Scanner;

public class InputThread {

    public void getInput(MemoryManager manager) {
        Scanner reader = new Scanner(System.in);
        while (true) {
            String input = reader.nextLine();
            manager.getInput(input + "\n");
        }
    }

}
