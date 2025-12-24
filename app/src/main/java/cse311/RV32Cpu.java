package cse311;

import java.util.Scanner;

import cse311.Exception.MemoryAccessException;

import java.util.HashMap;
import java.util.Map;

public class RV32Cpu {

    private int[] x = new int[32];
    private int lastPC = -1;
    private int lastPCBranch = -1;
    private int loopCountBranch = 0;
    private int loopCount = 0;
    private int pc = 0;
    // private int[] instruction;
    private static final int INSTRUCTION_SIZE = 4; // 32-bit instructions

    // Privilege levels
    public static final int PRIVILEGE_USER = 0; // U-mode
    public static final int PRIVILEGE_SUPERVISOR = 1; // S-mode
    public static final int PRIVILEGE_MACHINE = 3; // M-mode

    // Current privilege level
    private int privilegeMode = PRIVILEGE_MACHINE; // Start in M-mode

    // CSR addresses
    // Machine-level CSRs
    public static final int MSTATUS = 0x300; // Machine status register
    public static final int MISA = 0x301; // Machine ISA register
    public static final int MEDELEG = 0x302; // Machine exception delegation register
    public static final int MIDELEG = 0x303; // Machine interrupt delegation register
    public static final int MIE = 0x304; // Machine interrupt enable register
    public static final int MTVEC = 0x305; // Machine trap handler base address
    public static final int MEPC = 0x341; // Machine exception program counter
    public static final int MCAUSE = 0x342; // Machine trap cause
    public static final int MTVAL = 0x343; // Machine trap value
    public static final int MIP = 0x344; // Machine interrupt pending

    // Supervisor-level CSRs
    public static final int SSTATUS = 0x100; // Supervisor status register
    public static final int SIE = 0x104; // Supervisor interrupt enable register
    public static final int STVEC = 0x105; // Supervisor trap handler base address
    public static final int SEPC = 0x141; // Supervisor exception program counter
    public static final int SCAUSE = 0x142; // Supervisor trap cause
    public static final int STVAL = 0x143; // Supervisor trap value
    public static final int SIP = 0x144; // Supervisor interrupt pending
    public static final int SATP = 0x180; // Supervisor address translation and protection

    // CSR access types
    private static final int CSR_READ_WRITE = 0b01;
    private static final int CSR_READ_SET = 0b10;
    private static final int CSR_READ_CLEAR = 0b11;

    // CSR registers map
    private Map<Integer, Integer> csrRegisters = new HashMap<>();

    private MemoryManager memory;
    private Scanner reader;
    private Thread cpuThread;
    private boolean running = false;
    private static final int LOOP_THRESHOLD = 1000; // Maximum times to execute same instruction
    private InputThread input;

    // Fields to track system calls and exceptions for kernel integration
    private boolean lastInstructionWasEcall = false;
    private boolean exceptionOccurred = false;

    public RV32Cpu(MemoryManager memory) {
        this.memory = memory;
        input = new InputThread();

        // Initialize CSR registers
        initializeCSRs();
    }

    /**
     * Initialize Control and Status Registers (CSRs) with default values
     */
    private void initializeCSRs() {
        // Machine-level CSRs
        csrRegisters.put(MISA, 0x40001108); // RV32I base ISA with M-mode, M-extension, and Zicsr
        csrRegisters.put(MSTATUS, 0x1800); // MPP (Machine Previous Privilege) set to M-mode
        csrRegisters.put(MEDELEG, 0x0); // No exception delegation
        csrRegisters.put(MIDELEG, 0x0); // No interrupt delegation
        csrRegisters.put(MIE, 0x0); // No interrupts enabled
        csrRegisters.put(MTVEC, 0x0); // Trap vector base address
        csrRegisters.put(MEPC, 0x0); // Exception program counter
        csrRegisters.put(MCAUSE, 0x0); // Trap cause
        csrRegisters.put(MTVAL, 0x0); // Trap value
        csrRegisters.put(MIP, 0x0); // No interrupts pending

        // Supervisor-level CSRs
        csrRegisters.put(SSTATUS, 0x0); // Supervisor status
        csrRegisters.put(SIE, 0x0); // No supervisor interrupts enabled
        csrRegisters.put(STVEC, 0x0); // Supervisor trap vector base address
        csrRegisters.put(SEPC, 0x0); // Supervisor exception program counter
        csrRegisters.put(SCAUSE, 0x0); // Supervisor trap cause
        csrRegisters.put(STVAL, 0x0); // Supervisor trap value
        csrRegisters.put(SIP, 0x0); // No supervisor interrupts pending
        csrRegisters.put(SATP, 0x0); // No address translation (bare mode)
    }

    /**
     * Read a CSR register value
     * 
     * @param csrAddress The CSR address
     * @return The CSR value, or 0 if the CSR is not accessible in the current
     *         privilege mode
     */
    private int readCSR(int csrAddress) {
        // Check if the CSR is accessible in the current privilege mode
        if (!isCSRAccessible(csrAddress)) {
            // For now, just return 0 instead of triggering exception to avoid recursion
            // In a real implementation, this would trigger an illegal instruction exception
            return 0;
        }

        // Return the CSR value, or 0 if not initialized
        return csrRegisters.getOrDefault(csrAddress, 0);
    }

    /**
     * Write a value to a CSR register
     * 
     * @param csrAddress The CSR address
     * @param value      The value to write
     * @param accessType The access type (CSR_READ_WRITE, CSR_READ_SET,
     *                   CSR_READ_CLEAR)
     * @return The previous CSR value, or 0 if the CSR is not accessible in the
     *         current privilege mode
     */
    private int writeCSR(int csrAddress, int value, int accessType) {
        // Check if the CSR is accessible in the current privilege mode
        if (!isCSRAccessible(csrAddress)) {
            // For now, just return 0 instead of triggering exception to avoid recursion
            // In a real implementation, this would trigger an illegal instruction exception
            return 0;
        }

        // Get the current value
        int currentValue = csrRegisters.getOrDefault(csrAddress, 0);

        // Calculate the new value based on the access type
        int newValue;
        switch (accessType) {
            case CSR_READ_WRITE:
                newValue = value;
                break;
            case CSR_READ_SET:
                newValue = currentValue | value;
                break;
            case CSR_READ_CLEAR:
                newValue = currentValue & ~value;
                break;
            default:
                newValue = value;
        }

        // Update the CSR value
        csrRegisters.put(csrAddress, newValue);

        // Handle side effects of writing to certain CSRs
        handleCSRSideEffects(csrAddress, newValue, currentValue);

        return currentValue;
    }

    /**
     * Check if a CSR is accessible in the current privilege mode
     * 
     * @param csrAddress The CSR address
     * @return true if the CSR is accessible, false otherwise
     */
    private boolean isCSRAccessible(int csrAddress) {
        // Extract the privilege level from the CSR address (bits 9:8)
        int csrPrivilege = (csrAddress >> 8) & 0x3;

        // CSR is accessible if the current privilege level is >= the CSR privilege
        // level
        return privilegeMode >= csrPrivilege;
    }

    /**
     * Handle side effects of writing to certain CSRs
     * 
     * @param csrAddress The CSR address
     * @param newValue   The new CSR value
     * @param oldValue   The old CSR value
     */
    private void handleCSRSideEffects(int csrAddress, int newValue, int oldValue) {
        switch (csrAddress) {
            case MSTATUS:
                // Update SSTATUS when MSTATUS changes
                int sstatus = newValue & 0x000C0122; // Only copy relevant bits to SSTATUS
                csrRegisters.put(SSTATUS, sstatus);
                break;
            case SSTATUS:
                // Update MSTATUS when SSTATUS changes
                int mstatus = csrRegisters.getOrDefault(MSTATUS, 0); // Direct access to avoid recursion
                mstatus = (mstatus & ~0x000C0122) | (newValue & 0x000C0122);
                csrRegisters.put(MSTATUS, mstatus);
                break;
            case SATP:
                // Handle address translation mode changes
                // For now, we don't implement address translation
                break;
        }
    }

    public void setProgramCounterEntryPoint(int entryPoint) {
        this.pc = entryPoint;
    }

    // Methods needed by the kernel
    public void step() throws Exception {
        fetchExecuteCycle();
    }

    public boolean isEcall() {
        // Check if the last instruction was an ecall
        boolean wasEcall = lastInstructionWasEcall;
        lastInstructionWasEcall = false; // Reset flag after checking
        return wasEcall;
    }

    public boolean isException() {
        // Check if an exception occurred
        boolean hadException = exceptionOccurred;
        exceptionOccurred = false; // Reset flag after checking
        return hadException;
    }

    /**
     * Reset system call and exception flags (useful for testing or initialization)
     */
    public void resetFlags() {
        lastInstructionWasEcall = false;
        exceptionOccurred = false;
    }

    public int[] getRegisters() {
        return x.clone(); // Return a copy to prevent external modification
    }

    public void setRegisters(int[] registers) {
        if (registers.length == 32) {
            System.arraycopy(registers, 0, this.x, 0, 32);
        }
    }

    public int getProgramCounter() {
        return pc;
    }

    public void setProgramCounter(int pc) {
        this.pc = pc;
    }

    public void turnOn() {
        Runnable task1 = () -> input.getInput(memory);
        /*
         * this.cpuThread = new Thread(new Runnable() {
         * 
         * @Override
         * public void run() {
         * while (RV32iCpu.this.running) {
         * try {
         * // find13And12(memory.getByteMemory());
         * fetchExecuteCycle();
         * } catch (Exception e) {
         * // TODO Auto-generated catch block
         * e.printStackTrace();
         * }
         * }
         * }
         * });
         */
        new Thread(task1).start();
        this.running = true;
        // this.cpuThread.start();
    }

    private void fetchExecuteCycle() throws Exception {
        // Check for infinite loop
        if (pc == lastPC) {
            loopCount++;
            if (loopCount > LOOP_THRESHOLD) {
                System.out.println("Infinite loop detected at PC: 0x" + Integer.toHexString(pc));
                System.out.println("Program halted after " + LOOP_THRESHOLD + " iterations");
                this.running = false;
                return;
            }
        } else {
            lastPC = pc;
            loopCount = 0;
        }

        try {
            // Fetch the instruction from memory at the address in the pc register
            int instructionFetched = fetch();
            InstructionDecoded instructionDecoded = decode(instructionFetched);
            execute(instructionDecoded);
            // System.out.println(instructionDecoded.toString());
            // displayRegisters();
        } catch (MemoryAccessException e) {
            // Handle memory access exception using the handleException method
            handleException(7, pc - INSTRUCTION_SIZE); // 7 = store/AMO access fault
        } catch (Exception e) {
            // Handle other exceptions using the handleException method
            handleException(2, pc - INSTRUCTION_SIZE); // 2 = illegal instruction
            e.printStackTrace(); // Log the exception for debugging
        }
    }

    /**
     * Expose the handleException method for testing
     * 
     * @param cause The exception cause
     * @param tval  The trap value
     */
    public void handleException(int cause, int tval) {
        // Set exception flag for kernel integration
        exceptionOccurred = true;

        // Directly implement exception handling here to avoid recursive call
        // Check if the exception should be delegated to S-mode
        boolean delegate = false;
        if (privilegeMode < PRIVILEGE_MACHINE) {
            int medeleg = csrRegisters.getOrDefault(MEDELEG, 0); // Direct access to avoid recursion
            delegate = ((medeleg >> cause) & 1) == 1;
        }

        if (delegate && privilegeMode == PRIVILEGE_USER) {
            // Delegate to S-mode
            // Save current PC to SEPC
            csrRegisters.put(SEPC, pc);

            // Set SCAUSE to the exception cause
            csrRegisters.put(SCAUSE, cause);

            // Set STVAL to the trap value
            csrRegisters.put(STVAL, tval);

            // Update SSTATUS SPP field to the current privilege mode
            int sstatus = csrRegisters.getOrDefault(SSTATUS, 0); // Direct access to avoid recursion
            sstatus = (sstatus & ~0x100) | ((privilegeMode & 0x1) << 8);
            csrRegisters.put(SSTATUS, sstatus);

            // Set privilege mode to S-mode
            privilegeMode = PRIVILEGE_SUPERVISOR;

            // Jump to the trap handler address in STVEC
            pc = csrRegisters.getOrDefault(STVEC, 0) & ~0x3; // Clear mode bits
        } else {
            // Handle in M-mode
            // Save current PC to MEPC
            csrRegisters.put(MEPC, pc);

            // Set MCAUSE to the exception cause
            csrRegisters.put(MCAUSE, cause);

            // Set MTVAL to the trap value
            csrRegisters.put(MTVAL, tval);

            // Update MSTATUS MPP field to the current privilege mode
            int mstatus = csrRegisters.getOrDefault(MSTATUS, 0); // Direct access to avoid recursion
            mstatus = (mstatus & ~0x1800) | (privilegeMode << 11);
            csrRegisters.put(MSTATUS, mstatus);

            // Set privilege mode to M-mode
            privilegeMode = PRIVILEGE_MACHINE;

            // Jump to the trap handler address in MTVEC
            pc = csrRegisters.getOrDefault(MTVEC, 0) & ~0x3; // Clear mode bits
        }
    }

    /**
     * Return from an exception/interrupt (MRET or SRET instruction)
     * 
     * @param fromMode The privilege mode returning from (PRIVILEGE_MACHINE or
     *                 PRIVILEGE_SUPERVISOR)
     */
    private void returnFromException(int fromMode) {
        if (fromMode == PRIVILEGE_MACHINE) {
            // MRET instruction
            // Get the previous privilege mode from MSTATUS.MPP
            int mstatus = csrRegisters.getOrDefault(MSTATUS, 0); // Direct access to avoid recursion
            int prevMode = (mstatus >> 11) & 0x3;

            // Set the privilege mode to the previous mode
            privilegeMode = prevMode;

            // Update MSTATUS
            // Clear MPP (set it to U-mode)
            mstatus = mstatus & ~0x1800;
            // Set MIE to the value of MPIE
            mstatus = (mstatus & ~0x8) | (((mstatus >> 7) & 0x1) << 3);
            // Set MPIE to 1
            mstatus = mstatus | 0x80;
            csrRegisters.put(MSTATUS, mstatus);

            // Set PC to the value in MEPC
            pc = csrRegisters.getOrDefault(MEPC, 0); // Direct access to avoid recursion
        } else if (fromMode == PRIVILEGE_SUPERVISOR) {
            // SRET instruction
            // Get the previous privilege mode from SSTATUS.SPP
            int sstatus = csrRegisters.getOrDefault(SSTATUS, 0); // Direct access to avoid recursion
            int prevMode = ((sstatus >> 8) & 0x1) == 1 ? PRIVILEGE_SUPERVISOR : PRIVILEGE_USER;

            // Set the privilege mode to the previous mode
            privilegeMode = prevMode;

            // Update SSTATUS
            // Clear SPP
            sstatus = sstatus & ~0x100;
            // Set SIE to the value of SPIE
            sstatus = (sstatus & ~0x2) | (((sstatus >> 5) & 0x1) << 1);
            // Set SPIE to 1
            sstatus = sstatus | 0x20;
            csrRegisters.put(SSTATUS, sstatus);

            // Set PC to the value in SEPC
            pc = csrRegisters.getOrDefault(SEPC, 0); // Direct access to avoid recursion
        }
    }

    private int fetch() throws MemoryAccessException {
        // Read 32-bit instruction from memory at PC
        int instruction = 0;

        // Read 4 bytes and combine them
        try {
            byte byte0 = memory.readByte(pc);
            byte byte1 = memory.readByte(pc + 1);
            byte byte2 = memory.readByte(pc + 2);
            byte byte3 = memory.readByte(pc + 3);

            // Combine bytes into 32-bit instruction
            instruction = (byte3 & 0xFF) << 24
                    | (byte2 & 0xFF) << 16
                    | (byte1 & 0xFF) << 8
                    | (byte0 & 0xFF);

            // Increment PC by instruction size (4 bytes)
            pc += INSTRUCTION_SIZE;

        } catch (Exception e) {
            throw new MemoryAccessException("Failed to fetch instruction at PC: " + pc);
        }

        return instruction;
    }

    private InstructionDecoded decode(int instructionInt) {
        // Combine the bytes into a 32-bit instruction
        /*
         * int instructionInt = (instruction[3] & 0xFF) << 24
         * | (instruction[2] & 0xFF) << 16
         * | (instruction[1] & 0xFF) << 8
         * | (instruction[0] & 0xFF);
         */
        InstructionDecoded instruction = new InstructionDecoded();

        // Extract instruction fields based on RISC-V RV32I format
        int opcode = instructionInt & 0x7F; // bits 0-6
        instruction.setOpcode(opcode);
        int rd = (instructionInt >> 7) & 0x1F; // bits 7-11
        instruction.setRd(rd);
        int func3 = (instructionInt >> 12) & 0x7; // bits 12-14
        instruction.setFunc3(func3);
        int rs1 = (instructionInt >> 15) & 0x1F; // bits 15-19
        instruction.setRs1(rs1);
        int rs2 = (instructionInt >> 20) & 0x1F; // bits 20-24
        instruction.setRs2(rs2);
        int func7 = (instructionInt >> 25) & 0x7F; // bits 25-31
        instruction.setFunc7(func7);

        // Immediate values for different instruction formats
        // I-type: Sign extended 12-bit immediate
        int imm_i = ((instructionInt >> 20) << 20) >> 20;
        instruction.setImm_i(imm_i);

        // S-type: Sign extended 12-bit immediate
        int imm_s = (((instructionInt >> 25) << 5) | ((instructionInt >> 7) & 0x1F));
        imm_s = (imm_s << 20) >> 20; // Sign extend
        instruction.setImm_s(imm_s);

        // B-type: Sign extended 13-bit immediate
        int imm_b = (((instructionInt >> 31) << 12) // imm[12]
                |
                ((instructionInt >> 7) & 0x1) << 11) // imm[11]
                |
                ((instructionInt >> 25) & 0x3F) << 5 // imm[10:5]
                |
                ((instructionInt >> 8) & 0xF) << 1; // imm[4:1]
        imm_b = (imm_b << 19) >> 19; // Sign extend
        instruction.setImm_b(imm_b);

        // U-type: 20-bit immediate, shifted left by 12
        int imm_u = instructionInt & 0xFFFFF000;
        instruction.setImm_u(imm_u);

        // J-type: Sign extended 21-bit immediate
        int imm_j = (((instructionInt >> 31) << 20) // imm[20]
                |
                ((instructionInt >> 12) & 0xFF) << 12 // imm[19:12]
                |
                ((instructionInt >> 20) & 0x1) << 11 // imm[11]
                |
                ((instructionInt >> 21) & 0x3FF) << 1); // imm[10:1]
        imm_j = (imm_j << 11) >> 11; // Sign extend
        instruction.setImm_j(imm_j);
        return instruction;

    }

    private void execute(InstructionDecoded instruction) {
        int opcode = instruction.getOpcode();
        int rd = instruction.getRd();
        int rs1 = instruction.getRs1();
        int rs2 = instruction.getRs2();
        int func3 = instruction.getFunc3();
        int func7 = instruction.getFunc7();
        int imm_i = instruction.getImm_i();
        int imm_s = instruction.getImm_s();
        int imm_b = instruction.getImm_b();
        int imm_u = instruction.getImm_u();
        int imm_j = instruction.getImm_j();
        switch (opcode) {
            // R-type instructions
            case 0b0110011: // R-type
                switch (func3) {
                    case 0b000: // ADD/SUB/MUL
                        if (func7 == 0) {
                            x[rd] = x[rs1] + x[rs2]; // ADD
                        } else if (func7 == 0b0100000) {
                            x[rd] = x[rs1] - x[rs2]; // SUB
                        } else if (func7 == 0b0000001) {
                            x[rd] = x[rs1] * x[rs2]; // MUL (M-extension)
                        } else {
                            // Illegal instruction
                            handleException(2, pc - INSTRUCTION_SIZE);
                        }
                        break;
                    case 0b001: // SLL/MULH
                        if (func7 == 0) {
                            x[rd] = x[rs1] << (x[rs2] & 0x1F); // SLL
                        } else if (func7 == 0b0000001) {
                            // MULH (M-extension) - high bits of signed×signed product
                            long a = x[rs1];
                            long b = x[rs2];
                            // Sign extend to 64 bits
                            if ((a & 0x80000000L) != 0)
                                a |= 0xFFFFFFFF00000000L;
                            if ((b & 0x80000000L) != 0)
                                b |= 0xFFFFFFFF00000000L;
                            long result = a * b;
                            x[rd] = (int) (result >> 32); // High 32 bits
                        } else {
                            // Illegal instruction
                            handleException(2, pc - INSTRUCTION_SIZE);
                        }
                        break;
                    case 0b010: // SLT/MULHSU
                        if (func7 == 0) {
                            x[rd] = (x[rs1] < x[rs2]) ? 1 : 0; // SLT
                        } else if (func7 == 0b0000001) {
                            // MULHSU (M-extension) - high bits of signed×unsigned product
                            long a = x[rs1];
                            long b = Integer.toUnsignedLong(x[rs2]);
                            // Sign extend a to 64 bits
                            if ((a & 0x80000000L) != 0)
                                a |= 0xFFFFFFFF00000000L;
                            long result = a * b;
                            x[rd] = (int) (result >> 32); // High 32 bits
                        } else {
                            // Illegal instruction
                            handleException(2, pc - INSTRUCTION_SIZE);
                        }
                        break;
                    case 0b011: // SLTU/MULHU
                        if (func7 == 0) {
                            x[rd] = (Integer.compareUnsigned(x[rs1], x[rs2]) < 0) ? 1 : 0; // SLTU
                        } else if (func7 == 0b0000001) {
                            // MULHU (M-extension) - high bits of unsigned×unsigned product
                            long a = Integer.toUnsignedLong(x[rs1]);
                            long b = Integer.toUnsignedLong(x[rs2]);
                            long result = a * b;
                            x[rd] = (int) (result >> 32); // High 32 bits
                        } else {
                            // Illegal instruction
                            handleException(2, pc - INSTRUCTION_SIZE);
                        }
                        break;
                    case 0b100: // XOR/DIV
                        if (func7 == 0) {
                            x[rd] = x[rs1] ^ x[rs2]; // XOR
                        } else if (func7 == 0b0000001) {
                            // DIV (M-extension) - signed division
                            if (x[rs2] == 0) {
                                // Division by zero
                                x[rd] = -1; // All 1s (0xFFFFFFFF)
                            } else if (x[rs1] == Integer.MIN_VALUE && x[rs2] == -1) {
                                // Signed overflow case
                                x[rd] = Integer.MIN_VALUE;
                            } else {
                                x[rd] = x[rs1] / x[rs2];
                            }
                        } else {
                            // Illegal instruction
                            handleException(2, pc - INSTRUCTION_SIZE);
                        }
                        break;
                    case 0b101: // SRL/SRA/DIVU
                        if (func7 == 0) {
                            x[rd] = x[rs1] >>> (x[rs2] & 0x1F); // SRL
                        } else if (func7 == 0b0100000) {
                            x[rd] = x[rs1] >> (x[rs2] & 0x1F); // SRA
                        } else if (func7 == 0b0000001) {
                            // DIVU (M-extension) - unsigned division
                            if (x[rs2] == 0) {
                                // Division by zero
                                x[rd] = -1; // All 1s (0xFFFFFFFF)
                            } else {
                                x[rd] = Integer.divideUnsigned(x[rs1], x[rs2]);
                            }
                        } else {
                            // Illegal instruction
                            handleException(2, pc - INSTRUCTION_SIZE);
                        }
                        break;
                    case 0b110: // OR/REM
                        if (func7 == 0) {
                            x[rd] = x[rs1] | x[rs2]; // OR
                        } else if (func7 == 0b0000001) {
                            // REM (M-extension) - signed remainder
                            if (x[rs2] == 0) {
                                // Division by zero
                                x[rd] = x[rs1]; // Return dividend
                            } else if (x[rs1] == Integer.MIN_VALUE && x[rs2] == -1) {
                                // Signed overflow case
                                x[rd] = 0;
                            } else {
                                x[rd] = x[rs1] % x[rs2];
                            }
                        } else {
                            // Illegal instruction
                            handleException(2, pc - INSTRUCTION_SIZE);
                        }
                        break;
                    case 0b111: // AND/REMU
                        if (func7 == 0) {
                            x[rd] = x[rs1] & x[rs2]; // AND
                        } else if (func7 == 0b0000001) {
                            // REMU (M-extension) - unsigned remainder
                            if (x[rs2] == 0) {
                                // Division by zero
                                x[rd] = x[rs1]; // Return dividend
                            } else {
                                x[rd] = Integer.remainderUnsigned(x[rs1], x[rs2]);
                            }
                        } else {
                            // Illegal instruction
                            handleException(2, pc - INSTRUCTION_SIZE);
                        }
                        break;
                }
                break;

            // I-type instructions
            case 0b0010011: // I-type ALU
                switch (func3) {
                    case 0b000: // ADDI
                        x[rd] = x[rs1] + imm_i;
                        break;
                    case 0b001: // SLLI
                        x[rd] = x[rs1] << (imm_i & 0x1F);
                        break;
                    case 0b010: // SLTI
                        x[rd] = (x[rs1] < imm_i) ? 1 : 0;
                        break;
                    case 0b011: // SLTIU
                        x[rd] = (Integer.compareUnsigned(x[rs1], imm_i) < 0) ? 1 : 0;
                        break;
                    case 0b100: // XORI
                        x[rd] = x[rs1] ^ imm_i;
                        break;
                    case 0b101: // SRLI/SRAI
                        if ((imm_i & 0xFE0) == 0) {
                            x[rd] = x[rs1] >>> (imm_i & 0x1F); // SRLI
                        } else if ((imm_i & 0xFE0) == 0x400) {
                            x[rd] = x[rs1] >> (imm_i & 0x1F); // SRAI
                        }
                        break;
                    case 0b110: // ORI
                        x[rd] = x[rs1] | imm_i;
                        break;
                    case 0b111: // ANDI
                        x[rd] = x[rs1] & imm_i;
                        break;
                }
                break;

            // Load instructions
            case 0b0000011: // LOAD
                try {
                    int address = mapAddress(x[rs1] + imm_i);
                    switch (func3) {
                        case 0b000: // LB
                            x[rd] = memory.readByte(address);
                            break;
                        case 0b001: // LH
                            x[rd] = memory.readHalfWord(address);
                            break;
                        case 0b010: // LW
                            x[rd] = memory.readWord(address);
                            break;
                        case 0b100: // LBU
                            x[rd] = memory.readByte(address) & 0xFF;
                            break;
                        case 0b101: // LHU
                            x[rd] = memory.readHalfWord(address) & 0xFFFF;
                            break;
                    }
                } catch (MemoryAccessException e) {
                    // Handle load access fault
                    handleException(5, x[rs1] + imm_i); // 5 = load access fault
                }
                break;

            // Store instructions
            case 0b0100011: // STORE
                try {
                    int address = mapAddressForWrite(x[rs1] + imm_s);
                    switch (func3) {
                        case 0b000: // SB
                            memory.writeByte(address, (byte) x[rs2]);
                            break;
                        case 0b001: // SH
                            memory.writeHalfWord(address, (short) x[rs2]);
                            break;
                        case 0b010: // SW
                            memory.writeWord(address, x[rs2]);
                            break;
                    }
                } catch (MemoryAccessException e) {
                    // Handle store access fault
                    handleException(7, x[rs1] + imm_s); // 7 = store/AMO access fault
                }
                break;

            // Branch instructions
            case 0b1100011: // BRANCH
                boolean takeBranch = false;
                switch (func3) {
                    case 0b000: // BEQ
                        takeBranch = (x[rs1] == x[rs2]);
                        break;
                    case 0b001: // BNE
                        takeBranch = (x[rs1] != x[rs2]);
                        break;
                    case 0b100: // BLT
                        takeBranch = (x[rs1] < x[rs2]);
                        break;
                    case 0b101: // BGE
                        takeBranch = (x[rs1] >= x[rs2]);
                        break;
                    case 0b110: // BLTU
                        takeBranch = (Integer.compareUnsigned(x[rs1], x[rs2]) < 0);
                        break;
                    case 0b111: // BGEU
                        takeBranch = (Integer.compareUnsigned(x[rs1], x[rs2]) >= 0);
                        break;
                }
                if (takeBranch) {
                    pc += imm_b - INSTRUCTION_SIZE; // Subtract INSTRUCTION_SIZE because pc was already incremented in
                                                    // fetch
                    /*
                     * if (lastPCBranch == -1) {
                     * lastPCBranch = pc;
                     * } else if (pc == lastPCBranch) {
                     * loopCountBranch++;
                     * } else {
                     * loopCountBranch = 0;
                     * lastPCBranch = -1;
                     * }
                     * if (loopCountBranch > 20) {
                     * loopCountBranch = 0;
                     * lastPCBranch = -1;
                     * System.out.println("Getting input");
                     * memory.getInput(reader.nextLine());
                     * }
                     */
                }
                break;

            // Jump instructions
            case 0b1101111: // JAL
                if (rd != 0) {
                    x[rd] = pc;
                }
                pc += imm_j - INSTRUCTION_SIZE;
                break;

            case 0b1100111: // JALR
                int temp = pc;
                pc = (x[rs1] + imm_i) & ~1;
                if (rd != 0) {
                    x[rd] = temp;
                }
                break;

            // LUI and AUIPC
            case 0b0110111: // LUI
                x[rd] = imm_u;
                break;

            case 0b0010111: // AUIPC
                x[rd] = pc - INSTRUCTION_SIZE + imm_u;
                break;

            case 0b1110011: // SYSTEM
                if (func3 == 0) {
                    if (imm_i == 0) { // ECALL
                        // Mark that an ECALL occurred - let the kernel handle it
                        lastInstructionWasEcall = true;
                        // Don't handle the system call here - let the kernel's SystemCallHandler do it
                        // The kernel will check isEcall() and call the appropriate handler
                    } else if (imm_i == 1) { // EBREAK
                        handleQemuSemihosting();
                    } else if (imm_i == 0x302) { // MRET
                        // Return from M-mode trap
                        if (privilegeMode != PRIVILEGE_MACHINE) {
                            // Illegal instruction exception if executed in lower privilege mode
                            handleException(2, pc - INSTRUCTION_SIZE);
                        } else {
                            returnFromException(PRIVILEGE_MACHINE);
                        }
                    } else if (imm_i == 0x102) { // SRET
                        // Return from S-mode trap
                        if (privilegeMode < PRIVILEGE_SUPERVISOR) {
                            // Illegal instruction exception if executed in U-mode
                            handleException(2, pc - INSTRUCTION_SIZE);
                        } else {
                            returnFromException(PRIVILEGE_SUPERVISOR);
                        }
                    }
                } else {
                    // CSR instructions
                    int csrAddr = imm_i & 0xFFF;
                    int oldCsrValue = readCSR(csrAddr);
                    int newValue;

                    switch (func3) {
                        case 0b001: // CSRRW
                            newValue = x[rs1];
                            break;
                        case 0b010: // CSRRS
                            newValue = oldCsrValue | x[rs1];
                            break;
                        case 0b011: // CSRRC
                            newValue = oldCsrValue & ~x[rs1];
                            break;
                        case 0b101: // CSRRWI
                            newValue = rs1; // Immediate value is in rs1 field
                            break;
                        case 0b110: // CSRRSI
                            newValue = oldCsrValue | rs1; // Immediate value is in rs1 field
                            break;
                        case 0b111: // CSRRCI
                            newValue = oldCsrValue & ~rs1; // Immediate value is in rs1 field
                            break;
                        default:
                            newValue = oldCsrValue; // No change for unknown func3
                            break;
                    }

                    // Update the CSR register directly
                    csrRegisters.put(csrAddr, newValue);

                    // Handle side effects if needed
                    if (csrAddr == MSTATUS || csrAddr == SSTATUS) {
                        handleCSRSideEffects(csrAddr, newValue, oldCsrValue);
                    }

                    if (rd != 0) {
                        x[rd] = oldCsrValue;
                    }
                }
                break;
        }

    }

    private void handleQemuSemihosting() {
        if (x[17] == 93) { // Exit operation
            this.running = false;
            System.out.println("Program exited with code: " + x[10]);
        }
    }

    private void displayRegisters() {
        String[] regNames = {
                "zero", "ra", "sp", "gp", "tp", "t0", "t1", "t2",
                "s0/fp", "s1", "a0", "a1", "a2", "a3", "a4", "a5",
                "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7",
                "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"
        };

        System.out.println("\nRegister Values:");
        System.out.println("PC: 0x" + String.format("%08X", pc));

        for (int i = 0; i < x.length; i++) {
            if (x[i] != 0) { // Only show non-zero registers to reduce clutter
                System.out.printf("x%d (%s): 0x%08X\n", i, regNames[i], x[i]);
            }
        }
        System.out.println("------------------------");
    }

    /**
     * Maps a virtual address to a physical address and validates access permissions
     * 
     * @param virtualAddr The virtual address to map
     * @return The physical address
     */
    private int mapAddress(int virtualAddr) {
        // Handle UART addresses - these are passed through unchanged
        if (checkUARTAddress(virtualAddr)) {
            // UART access is only allowed in machine mode and supervisor mode
            if (privilegeMode == PRIVILEGE_USER) {
                throw new RuntimeException("User mode cannot access UART at address: 0x" +
                        Integer.toHexString(virtualAddr));
            }
            return virtualAddr;
        }

        // Check if virtual memory translation is enabled in S-mode
        if (privilegeMode <= PRIVILEGE_SUPERVISOR && (readCSR(SATP) & 0x80000000) != 0) {
            // In a real implementation, we would perform page table walks here
            // For now, we'll implement a simple identity mapping with permission checks
            try {
                memory.validateAccess(virtualAddr, privilegeMode);
            } catch (MemoryAccessException e) {
                // Convert to runtime exception for now
                throw new RuntimeException("Memory access violation: " + e.getMessage());
            }
            return virtualAddr;
        }

        // For ELF-loaded programs, use virtual addresses directly
        // The SimpleMemory class will handle the translation to physical addresses
        return virtualAddr;
    }

    /**
     * Maps a virtual address to a physical address for write access and validates
     * permissions
     * 
     * @param virtualAddr The virtual address to map
     * @return The physical address
     */
    private int mapAddressForWrite(int virtualAddr) {
        int physicalAddr = mapAddress(virtualAddr);
        // Additional write permission check could be added here
        return physicalAddr;
    }

    // Test-purpose only methods
    public void setRegister(int index, int value) {
        if (index >= 0 && index < x.length) {
            x[index] = value;
        }
    }

    public int getRegister(int index) {
        if (index >= 0 && index < x.length) {
            return x[index];
        }
        return 0;
    }

    /**
     * Get the current privilege mode (for testing)
     * 
     * @return The current privilege mode
     */
    public int getPrivilegeMode() {
        return privilegeMode;
    }

    /**
     * Set the program counter (for testing)
     * 
     * @param value The new PC value
     */
    public void setPc(int value) {
        pc = value;
    }

    /**
     * Write to a CSR register (for testing)
     * 
     * @param csrAddr The CSR address
     * @param value   The value to write
     */
    public void writeCSRTest(int csrAddr, int value) {
        csrRegisters.put(csrAddr, value);
    }

    /**
     * Read from a CSR register (for testing)
     * 
     * @param csrAddr The CSR address
     * @return The value of the CSR register
     */
    public int readCSRTest(int csrAddr) {
        return csrRegisters.getOrDefault(csrAddr, 0);
    }

    /**
     * Gets the current program counter value.
     * 
     * @return The current program counter
     */
    /*
     * public int getProgramCounter() {
     * return pc;
     * }
     */

    /**
     * Sets the program counter to a specific value.
     * 
     * @param pc The new program counter value
     */
    /*
     * public void setProgramCounter(int pc) {
     * this.pc = pc;
     * }
     */

    /**
     * Gets the current register values.
     * 
     * @return The current register values
     */
    /*
     * public int[] getRegisters() {
     * return x;
     * }
     */

    /**
     * Sets the register values.
     * 
     * @param registers The new register values
     */
    /*
     * public void setRegisters(int[] registers) {
     * if (registers.length == 32) {
     * System.arraycopy(registers, 0, this.x, 0, 32);
     * }
     * }
     */

    public void executeTest(InstructionDecoded inst) {
        execute(inst);
    }

    /**
     * Test method to execute a raw instruction
     * 
     * @param instruction The raw 32-bit instruction to execute
     */
    public void testExecuteInstruction(int instruction) {
        InstructionDecoded decoded = decode(instruction);
        execute(decoded);
    }

    public int mapAddressTest(int i) {
        return mapAddress(i);
    }

    public void find13And12(byte[] arr) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == 13 || arr[i] == 12) {
                if (arr[i] == 13) {
                    System.out.println("Found 13 at index " + i);
                } else {
                    System.out.println("Found 12 at index " + i);
                }
            }
        }
    }

    public boolean checkUARTAddress(int virtualAddr) {
        if (virtualAddr >= MemoryManager.UART_BASE &&
                virtualAddr < MemoryManager.UART_BASE + 0x1000) {
            return true;
        }
        return false;
    }

    // Task management is now handled by the kernel, not the CPU
    // These methods have been removed to maintain proper separation of concerns
}
