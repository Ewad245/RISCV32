package cse311.kernel.fs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import cse311.Logger.FileLogger;
import cse311.kernel.Kernel;

public class Log {
    private final int MAXOPBLOCKS = 10; // Max blocks written by one system call
    private final int LOGSIZE; // Total size of log in blocks (from SuperBlock.nlog)
    private final int logStart; // Starting block number of the log (from SuperBlock.logstart)

    private int outstanding; // How many system calls are currently in a transaction
    private boolean committing; // True if we are actively committing to disk

    private int[] lh_block; // Log header: the actual block numbers being modified
    private int lh_n; // Number of blocks currently in the log

    private Kernel kernel;
    private BufferCache bcache;

    public Log(Kernel kernel, BufferCache bcache, int logStart, int logSize) {
        this.kernel = kernel;
        this.bcache = bcache;
        this.logStart = logStart;
        this.LOGSIZE = logSize;
        this.lh_block = new int[LOGSIZE];
        this.lh_n = 0;
        this.outstanding = 0;
        this.committing = false;

        recover(); // Replay log on boot if there was a crash
    }

    /**
     * Start a transaction - called at beginning of system call
     */
    public synchronized void beginOp() {
        while (committing || lh_n + MAXOPBLOCKS > LOGSIZE) {
            try {
                wait(); // Wait if the log is committing or full
            } catch (InterruptedException e) {
                FileLogger.log("Log.beginOp: interrupted");
            }
        }
        outstanding++;
    }

    /**
     * End a transaction - called at end of system call
     */
    public synchronized void endOp() {
        outstanding--;
        if (outstanding == 0 && lh_n > 0) {
            committing = true;
            commit();
            committing = false;
            notifyAll(); // Wake up any threads waiting in beginOp
        }
    }

    /**
     * Write a modified block to the log (instead of its actual destination)
     */
    public synchronized void write(Buffer b) {
        if (lh_n >= LOGSIZE) {
            throw new RuntimeException("Log is full!");
        }

        // Record the block number in our header
        int i;
        for (i = 0; i < lh_n; i++) {
            if (lh_block[i] == b.blockNo) {
                break; // Block already in log, just overwrite it in the log area
            }
        }
        lh_block[i] = b.blockNo;
        if (i == lh_n) {
            lh_n++;
        }
        try {
            // Write the data to the log area on disk
            Buffer logBuf = bcache.bread(logStart + i + 1); // +1 to skip log header
            // block
            System.arraycopy(b.data, 0, logBuf.data, 0, DiskDevice.BSIZE);
            bcache.bwrite(logBuf);
            bcache.brelse(logBuf);
        } catch (Exception e) {
            FileLogger.log("Log.write: failed to write to log area");
            FileLogger.log(e);
        }
    }

    /**
     * The Commit sequence
     */
    private void commit() {
        if (lh_n > 0) {
            FileLogger.log(FileLogger.LogLevel.DEBUG, "Log: committing " + lh_n + " blocks");
            writeHeader(); // 1. Write log header to disk (Transaction is now "committed")
            installTrans(); // 2. Copy blocks from log to their actual locations
            lh_n = 0;
            writeHeader(); // 3. Clear the log header on disk
        }
    }

    /**
     * Write the log header to disk
     */
    private void writeHeader() {
        try {
            Buffer buf = bcache.bread(logStart);
            ByteBuffer bb = ByteBuffer.wrap(buf.data).order(ByteOrder.LITTLE_ENDIAN);

            // Write lh_n (number of blocks in log)
            bb.putInt(0, lh_n);

            // Write lh_block array (block numbers being modified)
            for (int i = 0; i < lh_n; i++) {
                bb.putInt(4 + (i * 4), lh_block[i]);
            }

            buf.dirty = true;
            try {
                bcache.bwrite(buf);
            } catch (Exception e) {
                FileLogger.log("Log.writeHeader: failed to write header");
                FileLogger.log(e);
            }
            bcache.brelse(buf);
        } catch (Exception e) {
            FileLogger.log("Log.writeHeader: failed to read header block");
            FileLogger.log(e);
        }
    }

    /**
     * Copy blocks from log to their actual destinations
     */
    private void installTrans() {
        for (int i = 0; i < lh_n; i++) {
            try {
                Buffer logBuf = bcache.bread(logStart + i + 1);
                Buffer dstBuf = bcache.bread(lh_block[i]);
                System.arraycopy(logBuf.data, 0, dstBuf.data, 0, DiskDevice.BSIZE);
                dstBuf.dirty = true;
                try {
                    bcache.bwrite(dstBuf);
                } catch (Exception e) {
                    FileLogger.log("Log.installTrans: failed to write destination block " + lh_block[i]);
                    FileLogger.log(e);
                }
                bcache.brelse(dstBuf);
                bcache.brelse(logBuf);
            } catch (Exception e) {
                FileLogger.log("Log.installTrans: failed to read blocks");
                FileLogger.log(e);
            }
        }
    }

    /**
     * Recover from crash - replay log on boot
     */
    private void recover() {
        try {
            Buffer buf = bcache.bread(logStart);
            ByteBuffer bb = ByteBuffer.wrap(buf.data).order(ByteOrder.LITTLE_ENDIAN);

            lh_n = bb.getInt(0);
            if (lh_n > 0 && lh_n <= LOGSIZE) {
                FileLogger.log("Recovering file system from transaction log (" + lh_n + " blocks)...");

                // Unpack block numbers from header
                for (int i = 0; i < lh_n; i++) {
                    lh_block[i] = bb.getInt(4 + (i * 4));
                }

                installTrans(); // Replay the transaction
                lh_n = 0;
                writeHeader(); // Clear log after recovery
            } else if (lh_n > LOGSIZE) {
                FileLogger.log("Log.recover: invalid log size " + lh_n + ", clearing log");
                lh_n = 0;
                writeHeader();
            }
            bcache.brelse(buf);
        } catch (Exception e) {
            FileLogger.log("Log.recover: failed to recover from log");
            FileLogger.log(e);
        }
    }
}
