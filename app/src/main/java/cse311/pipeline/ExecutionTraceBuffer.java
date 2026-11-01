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
        public int hartId;
        public int pid;
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
            this.hartId = 0;
            this.pid = 0;
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
        record(pc, instruction, aluResult, rd, regWrite, memRead, memWrite, cycle, 0, 0);
    }

    public void record(int pc, int instruction, int aluResult, int rd,
                       boolean regWrite, boolean memRead, boolean memWrite, long cycle,
                       int hartId, int pid) {
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
        event.hartId = hartId;
        event.pid = pid;
        event.valid = true;
    }

    public List<TraceEvent> getSnapshot(int maxEvents) {
        return getSnapshot(maxEvents, -1, -1);
    }

    public List<TraceEvent> getSnapshot(int maxEvents, int filterPid, int filterHartId) {
        List<TraceEvent> snapshot = new ArrayList<>();
        long currentHead = headSequence.get();
        int available = (int) Math.min(currentHead, BUFFER_SIZE);

        List<TraceEvent> matchingNewestFirst = new ArrayList<>();
        for (int i = 0; i < available && matchingNewestFirst.size() < maxEvents; i++) {
            long seq = currentHead - 1 - i;
            if (seq < 0) break;
            int idx = (int) (seq & MASK);
            TraceEvent event = buffer[idx];
            if (event.valid) {
                if (filterPid != -1 && event.pid != filterPid) {
                    continue;
                }
                if (filterHartId != -1 && event.hartId != filterHartId) {
                    continue;
                }
                TraceEvent copy = new TraceEvent();
                copy.pc = event.pc;
                copy.instruction = event.instruction;
                copy.aluResult = event.aluResult;
                copy.rd = event.rd;
                copy.regWrite = event.regWrite;
                copy.memRead = event.memRead;
                copy.memWrite = event.memWrite;
                copy.cycle = event.cycle;
                copy.hartId = event.hartId;
                copy.pid = event.pid;
                copy.valid = true;
                matchingNewestFirst.add(copy);
            }
        }

        // Reverse so that oldest is first (chronological order)
        for (int i = matchingNewestFirst.size() - 1; i >= 0; i--) {
            snapshot.add(matchingNewestFirst.get(i));
        }
        return snapshot;
    }

    public void clear() {
        headSequence.set(0);
        for (int i = 0; i < BUFFER_SIZE; i++) {
            buffer[i].valid = false;
        }
    }

    public long getHeadSequence() {
        return headSequence.get();
    }
}
