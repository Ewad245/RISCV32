package cse311.kernel.fs;

@SuppressWarnings("PMD.CompareObjectsWithEquals")
public class BufferCache {
    public static final int NBUF = 30;
    private final Buffer head;
    private final DiskDevice disk;

    public BufferCache(DiskDevice disk) {
        this.disk = disk;
        this.head = new Buffer();
        this.head.prev = this.head;
        this.head.next = this.head;

        for (int i = 0; i < NBUF; i++) {
            Buffer b = new Buffer();
            b.next = head.next;
            b.prev = head;
            head.next.prev = b;
            head.next = b;
        }
    }

    private Buffer bget(int blockNo) throws InterruptedException {
        synchronized (this) {
            for (Buffer b = head.next; b != head; b = b.next) {
                if (b.blockNo == blockNo) {
                    b.refCount++;
                    return b;
                }
            }

            for (Buffer b = head.prev; b != head; b = b.prev) {
                if (b.refCount == 0) {
                    if (b.dirty) {
                        throw new RuntimeException("bget: reclaiming dirty buffer");
                    }
                    b.blockNo = blockNo;
                    b.valid = false;
                    b.refCount = 1;
                    return b;
                }
            }
            throw new RuntimeException("bcache: no free buffers (panic)");
        }
    }

    public Buffer bread(int blockNo) throws Exception {
        Buffer b = bget(blockNo);
        b.acquire();

        if (!b.valid) {
            disk.read(blockNo, b.data);
            b.valid = true;
        }
        return b;
    }

    public void bwrite(Buffer b) throws Exception {
        if (!b.locked) {
            throw new RuntimeException("bwrite: buffer not locked");
        }
        disk.write(b.blockNo, b.data);
        b.dirty = false;
    }

    public void brelse(Buffer b) {
        if (!b.locked) {
            throw new RuntimeException("brelse: buffer not locked");
        }

        b.release();

        synchronized (this) {
            b.refCount--;
            if (b.refCount == 0) {
                b.next.prev = b.prev;
                b.prev.next = b.next;

                b.next = head.next;
                b.prev = head;
                head.next.prev = b;
                head.next = b;
            }
        }
    }
}
