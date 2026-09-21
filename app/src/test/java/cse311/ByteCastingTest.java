package cse311;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ByteCastingTest {

    @Test
    void testByteArrayCreation() {
        byte[] program1 = new byte[] {
            0x13, 0x08, (byte) 0xD0, 0x05,
            0x13, 0x05, 0x00, 0x00,
            0x73, 0x00, 0x00, 0x00
        };

        byte[] program2 = new byte[] {
            0x13, 0x02, 0x30, 0x00,
            0x13, 0x08, (byte) 0xC0, 0x07,
            0x73, 0x00, 0x00, 0x00,
            0x13, (byte) 0x82, (byte) 0xF2, (byte) 0xFF,
            0x63, 0x1C, 0x02, (byte) 0xFE,
            0x13, 0x08, (byte) 0xD0, 0x05,
            0x13, 0x05, 0x00, 0x00,
            0x73, 0x00, 0x00, 0x00
        };

        byte[] program3 = new byte[] {
            0x13, 0x08, (byte) 0xA0, 0x3E,
            0x13, 0x05, 0x00, 0x64,
            0x73, 0x00, 0x00, 0x00,
            0x13, 0x08, (byte) 0xD0, 0x05,
            0x13, 0x05, 0x00, 0x00,
            0x73, 0x00, 0x00, 0x00
        };

        assertAll("byteArraysValidation",
            () -> assertNotNull(program1, "Program 1 should not be null"),
            () -> assertNotNull(program2, "Program 2 should not be null"),
            () -> assertNotNull(program3, "Program 3 should not be null"),
            () -> assertEquals(12, program1.length, "Program 1 length should be 12"),
            () -> assertEquals(32, program2.length, "Program 2 length should be 32"),
            () -> assertEquals(24, program3.length, "Program 3 length should be 24"),
            () -> assertEquals((byte) 0xD0, program1[2], "Exit syscall high byte match"),
            () -> assertEquals((byte) 0xC0, program2[6], "Yield syscall high byte match"),
            () -> assertEquals((byte) 0xA0, program3[2], "Sleep syscall high byte match")
        );
    }

    @Test
    void testMemoryOperations() throws Exception {
        SimpleMemory simpleMemory = new SimpleMemory(1024);
        MemoryManager memory = new MemoryManager(simpleMemory);

        String testData = "Hello";
        for (int i = 0; i < testData.length(); i++) {
            memory.writeByte(0x100 + i, (byte) testData.charAt(i));
        }

        byte b0 = memory.readByte(0x100);
        byte b1 = memory.readByte(0x101);
        byte b2 = memory.readByte(0x102);
        byte b3 = memory.readByte(0x103);
        byte b4 = memory.readByte(0x104);

        assertAll("memoryBytesValidation",
            () -> assertEquals((byte) 'H', b0, "Byte 0 should match 'H'"),
            () -> assertEquals((byte) 'e', b1, "Byte 1 should match 'e'"),
            () -> assertEquals((byte) 'l', b2, "Byte 2 should match 'l'"),
            () -> assertEquals((byte) 'l', b3, "Byte 3 should match 'l'"),
            () -> assertEquals((byte) 'o', b4, "Byte 4 should match 'o'")
        );
    }
}