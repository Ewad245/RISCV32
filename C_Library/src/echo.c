//
// Created by Kuno on 3/21/2026.
//
#include "syscall.h"
#include "ulib.h"

int main(int argc, char *argv[]) {
    // Start at 1 to skip the program name ("echo")
    for (int i = 1; i < argc; i++) {
        print(argv[i]);
        if (i < argc - 1) {
            print(" ");
        }
    }
    print("\n");

    exit(0);
    return 0;
}