package cse311;

import cse311.pipeline.ExecutionTraceBuffer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecutionTraceBufferTest {

    @Test
    void testRecordAndSnapshot() {
        ExecutionTraceBuffer buffer = new ExecutionTraceBuffer();

        buffer.record(0x80001000, 0x00000013, 0, 0, false, false, false, 1, 0, 1);
        buffer.record(0x80001004, 0x00100093, 1, 1, true, false, false, 2, 1, 2);

        List<ExecutionTraceBuffer.TraceEvent> snapshot = buffer.getSnapshot(10);
        ExecutionTraceBuffer.TraceEvent ev0 = snapshot.get(0);
        ExecutionTraceBuffer.TraceEvent ev1 = snapshot.get(1);

        assertAll("recordAndSnapshot",
            () -> assertEquals(2, snapshot.size(), "Snapshot should have 2 events"),
            () -> assertEquals(0x80001000, ev0.pc, "Event 0 PC mismatch"),
            () -> assertEquals(0, ev0.hartId, "Event 0 HartId mismatch"),
            () -> assertEquals(1, ev0.pid, "Event 0 PID mismatch"),
            () -> assertEquals(0x80001004, ev1.pc, "Event 1 PC mismatch"),
            () -> assertEquals(1, ev1.hartId, "Event 1 HartId mismatch"),
            () -> assertEquals(2, ev1.pid, "Event 1 PID mismatch")
        );
    }

    @Test
    void testFilteringByPidAndHart() {
        ExecutionTraceBuffer buffer = new ExecutionTraceBuffer();

        // Record events from different harts and pids
        buffer.record(0x80001000, 0x13, 0, 0, false, false, false, 1, 0, 1);
        buffer.record(0x80002000, 0x13, 0, 0, false, false, false, 2, 1, 2);
        buffer.record(0x80001004, 0x13, 0, 0, false, false, false, 3, 0, 1);
        buffer.record(0x80002004, 0x13, 0, 0, false, false, false, 4, 1, 2);

        List<ExecutionTraceBuffer.TraceEvent> pid1Events = buffer.getSnapshot(10, 1, -1);
        List<ExecutionTraceBuffer.TraceEvent> hart1Events = buffer.getSnapshot(10, -1, 1);
        List<ExecutionTraceBuffer.TraceEvent> bothFiltered = buffer.getSnapshot(10, 2, 1);
        List<ExecutionTraceBuffer.TraceEvent> emptyFiltered = buffer.getSnapshot(10, 1, 1);

        assertAll("filteringByPidAndHart",
            () -> assertEquals(2, pid1Events.size(), "PID 1 filtered count mismatch"),
            () -> assertEquals(0x80001000, pid1Events.get(0).pc, "PID 1 event 0 PC mismatch"),
            () -> assertEquals(0x80001004, pid1Events.get(1).pc, "PID 1 event 1 PC mismatch"),
            () -> assertEquals(2, hart1Events.size(), "Hart 1 filtered count mismatch"),
            () -> assertEquals(0x80002000, hart1Events.get(0).pc, "Hart 1 event 0 PC mismatch"),
            () -> assertEquals(0x80002004, hart1Events.get(1).pc, "Hart 1 event 1 PC mismatch"),
            () -> assertEquals(2, bothFiltered.size(), "PID 2 & Hart 1 filtered count mismatch"),
            () -> assertEquals(0, emptyFiltered.size(), "Non-existent combination should be empty")
        );
    }

    @Test
    void testBufferWrapAround() {
        ExecutionTraceBuffer buffer = new ExecutionTraceBuffer();

        // Record more than BUFFER_SIZE (1024)
        for (int i = 0; i < 1500; i++) {
            buffer.record(0x80000000 + (i * 4), 0x13, i, 1, true, false, false, i, 0, 1);
        }

        List<ExecutionTraceBuffer.TraceEvent> snapshot = buffer.getSnapshot(10);
        ExecutionTraceBuffer.TraceEvent latest = snapshot.get(snapshot.size() - 1);

        assertAll("bufferWrapAround",
            () -> assertEquals(10, snapshot.size(), "Snapshot should contain requested 10 items"),
            () -> assertEquals(0x80000000 + (1499 * 4), latest.pc, "Latest event PC mismatch")
        );
    }
}
