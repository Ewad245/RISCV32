package cse311.pipeline;

/**
 * Hazard Detection and Data Forwarding Unit for the 5-stage RISC-V Pipeline.
 * 
 * Handles:
 * 1. Data Forwarding (Bypassing) for RAW hazards (EX/MEM -> EX, MEM/WB -> EX).
 * 2. Load-Use Hazard Detection (inserts 1-cycle stall bubble).
 * 3. Control Hazard Resolution (flushes speculatively fetched instructions on taken branch/jump).
 */
public class HazardUnit {

    public enum ForwardingSource {
        REGISTER_FILE(0b00),
        FORWARD_EX_MEM(0b10),
        FORWARD_MEM_WB(0b01);

        private final int code;
        ForwardingSource(int code) { this.code = code; }
        public int getCode() { return code; }
    }

    public static class ForwardingResult {
        public ForwardingSource forwardA = ForwardingSource.REGISTER_FILE;
        public ForwardingSource forwardB = ForwardingSource.REGISTER_FILE;
    }

    public static class HazardControlResult {
        public boolean pcStall = false;
        public boolean ifIdStall = false;
        public boolean ifIdFlush = false;
        public boolean idExFlush = false;
        public boolean exMemFlush = false;
    }

    /**
     * Compute Data Forwarding selectors for ALU operands in the EX stage.
     */
    public static ForwardingResult computeForwarding(
            boolean forwardingEnabled,
            PipelineRegisters.ID_EX idEx,
            PipelineRegisters.MEM_WB memWb,
            int wbRd,
            int wbVal) {

        ForwardingResult result = new ForwardingResult();
        if (!forwardingEnabled || !idEx.valid) {
            return result;
        }

        // Operand A Forwarding
        if (idEx.rs1 != 0) {
            // 1. Forward from Stage 4 (currently in memWb)
            if (memWb.valid && memWb.regWrite && memWb.rd == idEx.rs1) {
                result.forwardA = ForwardingSource.FORWARD_EX_MEM;
            }
            // 2. Forward from Stage 5 (wbRd / wbVal)
            else if (wbRd != 0 && wbRd == idEx.rs1) {
                result.forwardA = ForwardingSource.FORWARD_MEM_WB;
            }
        }

        // Operand B Forwarding (For R-type, Branch, and Store data operands)
        if (idEx.rs2 != 0) {
            if (memWb.valid && memWb.regWrite && memWb.rd == idEx.rs2) {
                result.forwardB = ForwardingSource.FORWARD_EX_MEM;
            } else if (wbRd != 0 && wbRd == idEx.rs2) {
                result.forwardB = ForwardingSource.FORWARD_MEM_WB;
            }
        }

        return result;
    }



    /**
     * Detect Load-Use data hazards and control hazards.
     */
    public static HazardControlResult detectHazards(
            boolean forwardingEnabled,
            PipelineRegisters.IF_ID ifId,
            PipelineRegisters.ID_EX idEx,
            PipelineRegisters.EX_MEM exMem,
            boolean branchTaken) {

        HazardControlResult result = new HazardControlResult();

        // 1. Control Hazard: Branch or Jump taken in EX stage -> Flush IF/ID and ID/EX
        if (branchTaken) {
            result.ifIdFlush = true;
            result.idExFlush = true;
            return result;
        }

        // Extract rs1 and rs2 from instruction in IF/ID stage if valid
        if (ifId.valid) {
            int rs1 = (ifId.instruction >> 15) & 0x1F;
            int rs2 = (ifId.instruction >> 20) & 0x1F;

            // 2. Load-Use Data Hazard Detection:
            // An instruction in ID needs a register that is being loaded by a LOAD instruction currently in EX.
            if (idEx.valid && idEx.memRead && idEx.rd != 0) {
                if (idEx.rd == rs1 || idEx.rd == rs2) {
                    result.pcStall = true;     // Freeze PC
                    result.ifIdStall = true;   // Freeze IF/ID latch
                    result.idExFlush = true;   // Insert NOP into ID/EX latch
                    return result;
                }
            }

            // If Forwarding is DISABLED by user setting, any RAW hazard requires a stall until WB
            if (!forwardingEnabled) {
                boolean hazardInEx = idEx.valid && idEx.regWrite && idEx.rd != 0 && (idEx.rd == rs1 || idEx.rd == rs2);
                boolean hazardInMem = exMem.valid && exMem.regWrite && exMem.rd != 0 && (exMem.rd == rs1 || exMem.rd == rs2);
                if (hazardInEx || hazardInMem) {
                    result.pcStall = true;
                    result.ifIdStall = true;
                    result.idExFlush = true;
                    return result;
                }
            }
        }

        return result;
    }
}
