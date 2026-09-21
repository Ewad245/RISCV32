package cse311.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe, zero-allocation ring buffer for recording committed RISC-V instruction
 * execution events at CPU speed without blocking the simulation or GC allocation.
 */
public class ExecutionTraceBuffer {

    public static final int BUFFER_SIZE = 1024;
    private static final int MASK = BUFFER_SIZE - 1;

    public static class TraceEvent {
        public int pc;
        public int instruction;
        public int aluResult;
        public int rd;
        public boolean regWrite;
        public boolean memRead;
        public boolean memWrite;
        public long cycle;
        public boolean valid;

        public TraceEvent() {
            this.pc = 0;
            this.instruction = 0;
            this.aluResult = 0;
            this.rd = 0;
            this.regWrite = false;
            this.memRead = false;
            this.memWrite = false;
            this.cycle = 0;
            this.valid = false;
        }
    }

    private final TraceEvent[] buffer;
    private final AtomicLong headSequence;

    public ExecutionTraceBuffer() {
        this.buffer = new TraceEvent[BUFFER_SIZE];
        for (int i = 0; i < BUFFER_SIZE; i++) {
            buffer[i] = new TraceEvent();
        }
        this.headSequence = new AtomicLong(0);
    }

    public void record(int pc, int instruction, int aluResult, int rd,
                       boolean regWrite, boolean memRead, boolean memWrite, long cycle) {
        long seq = headSequence.getAndIncrement();
        int idx = (int) (seq & MASK);
        TraceEvent event = buffer[idx];

        event.pc = pc;
        event.instruction = instruction;
        event.aluResult = aluResult;
        event.rd = rd;
        event.regWrite = regWrite;
        event.memRead = memRead;
        event.memWrite = memWrite;
        event.cycle = cycle;
        event.valid = true;
    }

    public List<TraceEvent> getSnapshot(int maxEvents) {
        List<TraceEvent> snapshot = new ArrayList<>();
        long currentHead = headSequence.get();
        int count = (int) Math.min(currentHead, Math.min(maxEvents, BUFFER_SIZE));

        for (int i = count - 1; i >= 0; i--) {
            long seq = currentHead - 1 - i;
            if (seq < 0) continue;
            int idx = (int) (seq & MASK);
            TraceEvent event = buffer[idx];
            if (event.valid) {
                TraceEvent copy = new TraceEvent();
                copy.pc = event.pc;
                copy.instruction = event.instruction;
                copy.aluResult = event.aluResult;
                copy.rd = event.rd;
                copy.regWrite = event.regWrite;
                copy.memRead = event.memRead;
                copy.memWrite = event.memWrite;
                copy.cycle = event.cycle;
                copy.valid = true;
                snapshot.add(copy);
            }
        }
        return snapshot;
    }

    public long getHeadSequence() {
        return headSequence.get();
    }
}
