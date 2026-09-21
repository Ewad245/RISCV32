package cse311.pipeline;

/**
 * Pipeline Registers (Latches) holding intermediate instruction state
 * between stages in a 5-stage RISC-V processor (IF, ID, EX, MEM, WB).
 */
public class PipelineRegisters {

    public static class IF_ID {
        public int pc;
        public int instruction;
        public int instructionSize = 4;
        public boolean valid = false;

        public void flush() {
            pc = 0;
            instruction = 0b0000000_00000_00000_000_00000_0110011; // NOP (ADD x0, x0, x0)
            instructionSize = 4;
            valid = false;
        }
    }

    public static class ID_EX {
        public int pc;
        public int instruction;
        public int instructionSize = 4;
        public int rs1;
        public int rs2;
        public int rd;
        public int val1;
        public int val2;
        public int imm;
        public int func3;
        public int func7;
        public int opcode;

        // Control Signals
        public boolean regWrite = false;
        public boolean memRead = false;
        public boolean memWrite = false;
        public boolean memToReg = false;
        public boolean aluSrcB = false;
        public boolean branch = false;
        public boolean jump = false;
        public boolean isEcall = false;

        public boolean valid = false;

        public void flush() {
            pc = 0;
            instruction = 0;
            instructionSize = 4;
            rs1 = 0;
            rs2 = 0;
            rd = 0;
            val1 = 0;
            val2 = 0;
            imm = 0;
            func3 = 0;
            func7 = 0;
            opcode = 0;
            regWrite = false;
            memRead = false;
            memWrite = false;
            memToReg = false;
            aluSrcB = false;
            branch = false;
            jump = false;
            isEcall = false;
            valid = false;
        }
    }

    public static class EX_MEM {
        public int pc;
        public int instruction;
        public int aluResult;
        public int writeVal; // For Store operations (rs2 value)
        public int rd;
        public int func3; // Load/Store size (byte, half, word)

        // Control Signals
        public boolean regWrite = false;
        public boolean memRead = false;
        public boolean memWrite = false;
        public boolean memToReg = false;
        public boolean isEcall = false;
        public boolean branchTaken = false;
        public int targetPc = 0;
        public int instructionSize = 4;

        public boolean valid = false;

        public void flush() {
            pc = 0;
            instruction = 0;
            instructionSize = 4;
            aluResult = 0;
            writeVal = 0;
            rd = 0;
            func3 = 0;
            regWrite = false;
            memRead = false;
            memWrite = false;
            memToReg = false;
            isEcall = false;
            branchTaken = false;
            targetPc = 0;
            valid = false;
        }
    }

    public static class MEM_WB {
        public int pc;
        public int instruction;
        public int instructionSize = 4;
        public int memReadData;
        public int aluResult;
        public int rd;

        // Control Signals
        public boolean regWrite = false;
        public boolean memToReg = false;
        public boolean isEcall = false;
        public boolean branchTaken = false;
        public int targetPc = 0;

        public boolean valid = false;

        public void flush() {
            pc = 0;
            instruction = 0;
            instructionSize = 4;
            memReadData = 0;
            aluResult = 0;
            rd = 0;
            regWrite = false;
            memToReg = false;
            isEcall = false;
            branchTaken = false;
            targetPc = 0;
            valid = false;
        }
    }

}
