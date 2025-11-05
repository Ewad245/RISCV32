package cse311;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify all byte casting issues are resolved
 */
public class ByteCastingTest {
    
    @Test
    void testByteArrayCreation() {
        // Test that all byte arrays can be created without casting errors
        
        // Test simple program
        byte[] program1 = new byte[] {
            0x13, 0x08, (byte)0xD0, 0x05,  // addi a7, zero, 93
            0x13, 0x05, 0x00, 0x00,        // addi a0, zero, 0
            0x73, 0x00, 0x00, 0x00         // ecall
        };
        
        // Test yielding program
        byte[] program2 = new byte[] {
            0x13, 0x02, 0x30, 0x00,        // addi t0, zero, 3
            0x13, 0x08, (byte)0xC0, 0x07,  // addi a7, zero, 124
            0x73, 0x00, 0x00, 0x00,        // ecall
            0x13, (byte)0x82, (byte)0xF2, (byte)0xFF,  // addi t0, t0, -1
            0x63, 0x1C, 0x02, (byte)0xFE,  // bne t0, zero, -8
            0x13, 0x08, (byte)0xD0, 0x05,  // addi a7, zero, 93
            0x13, 0x05, 0x00, 0x00,        // addi a0, zero, 0
            0x73, 0x00, 0x00, 0x00         // ecall
        };
        
        // Test sleep program
        byte[] program3 = new byte[] {
            0x13, 0x08, (byte)0xA0, 0x3E,  // addi a7, zero, 1002
            0x13, 0x05, 0x00, 0x64,        // addi a0, zero, 100
            0x73, 0x00, 0x00, 0x00,        // ecall
            0x13, 0x08, (byte)0xD0, 0x05,  // addi a7, zero, 93
            0x13, 0x05, 0x00, 0x00,        // addi a0, zero, 0
            0x73, 0x00, 0x00, 0x00         // ecall
        };
        
        // Verify arrays were created successfully
        assertNotNull(program1);
        assertNotNull(program2);
        assertNotNull(program3);
        
        // Verify correct lengths
        assertEquals(12, program1.length);
        assertEquals(32, program2.length);
        assertEquals(24, program3.length);
        
        // Verify some specific byte values
        assertEquals((byte)0xD0, program1[2]);  // Exit syscall high byte
        assertEquals((byte)0xC0, program2[6]);  // Yield syscall high byte
        assertEquals((byte)0xA0, program3[2]);  // Sleep syscall high byte
    }
    
    @Test
    void testMemoryOperations() throws Exception {
        SimpleMemory simpleMemory = new SimpleMemory(1024);
        MemoryManager memory = new MemoryManager(simpleMemory);
        
        // Test writing bytes with proper casting
        String testData = "Hello";
        for (int i = 0; i < testData.length(); i++) {
            memory.writeByte(0x100 + i, (byte) testData.charAt(i));
        }
        
        // Verify the data was written correctly
        for (int i = 0; i < testData.length(); i++) {
            byte b = memory.readByte(0x100 + i);
            assertEquals((byte) testData.charAt(i), b);
        }
    }
}