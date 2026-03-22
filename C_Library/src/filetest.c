//
// Created by Kuno on 3/21/2026.
//
#include "syscall.h"
#include "ulib.h"

int main() {
    print("\n--- File Modes & Directory Test ---\n");
    char buf[64];
    int fd;
    struct stat st;

    // 1. Test O_CREATE and Write
    print("1. Creating test.txt...\n");
    fd = open("test.txt", O_CREATE | O_WRONLY);
    if (fd < 0) { print("Error: Failed to create file\n"); exit(1); }
    write(fd, "Hello", 5);
    close(fd);

    // 2. Test O_APPEND
    print("2. Appending ' World' to test.txt...\n");
    fd = open("test.txt", O_WRONLY | O_APPEND);
    if (fd < 0) { print("Error: Failed to open for append\n"); exit(1); }
    write(fd, " World", 6);
    close(fd);

    // Read back to verify Append
    fd = open("test.txt", O_RDONLY);
    memset(buf, 0, sizeof(buf));
    read(fd, buf, sizeof(buf) - 1);
    close(fd);
    print("   Content: "); print(buf); print("\n");

    // 3. Test O_TRUNC
    print("3. Truncating test.txt...\n");
    fd = open("test.txt", O_WRONLY | O_TRUNC);
    if (fd < 0) { print("Error: Failed to open for truncate\n"); exit(1); }
    write(fd, "Truncated!", 10);
    close(fd);

    // Read back to verify Truncate
    fd = open("test.txt", O_RDONLY);
    memset(buf, 0, sizeof(buf));
    read(fd, buf, sizeof(buf) - 1);
    close(fd);
    print("   Content: "); print(buf); print("\n");

    // 4. Test Directory Link Counts
    print("4. Testing mkdir and link counts...\n");
    if (mkdir("testdir") < 0) {
        print("   Directory 'testdir' already exists or failed.\n");
    }

    fd = open("testdir", O_RDONLY);
    if (fd < 0) { print("Error: Failed to open directory\n"); exit(1); }

    fstat(fd, &st);
    close(fd);

    print("   testdir nlink: ");
    char nlink_buf[16];
    itoa(st.nlink, nlink_buf, 10);
    print(nlink_buf); print("\n");

    if (st.nlink == 2) {
        print("   [SUCCESS] Link count is exactly 2.\n");
    } else {
        print("   [FAILED] Link count is incorrect.\n");
    }

    print("--- Test Complete ---\n");
    exit(0);
    return 0;
}