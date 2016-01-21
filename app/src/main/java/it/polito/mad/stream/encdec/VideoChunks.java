package it.polito.mad.stream.encdec;

import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * MULTITHREAD SHARED OBJECT!!
 * The elementary stream coming out of the "video/avc" encoder needs to be fed back into
 * the decoder one chunk at a time.  If we just wrote the data to a file, we would lose
 * the information about chunk boundaries.  This class stores the encoded data in memory,
 * retaining the chunk organization.
 */
public class VideoChunks {

    public class Chunk {
        final byte[] data;
        final int flags;
        final long presentationTimestampUs;
        public Chunk(byte[] data, int flags, long presentationTimestampUs){
            this.data = data;
            this.flags = flags;
            this.presentationTimestampUs = presentationTimestampUs;
        }
    }

    private final int mMaxSize = 100;
    private LinkedList<byte[]> mChunks = new LinkedList<>();
    private LinkedList<Integer> mFlags = new LinkedList<Integer>();
    private LinkedList<Long> mTimes = new LinkedList<Long>();

    public synchronized void addChunk(byte[] data, int flags, long time) {
        if (mChunks.size() == mMaxSize){
            mChunks.removeFirst();
            mFlags.removeFirst();
            mTimes.removeFirst();
        }
        mChunks.addLast(data);
        mFlags.addLast(flags);
        mTimes.addLast(time);
        notifyAll();
    }
    /**
     * Returns the number of chunks currently held.
     */
    public synchronized int getNumChunks() {
        return mChunks.size();
    }
    /**
     * Copies the data from chunk N into "dest".  Advances dest.position.
     */
    public void getChunkData(int chunk, ByteBuffer dest) {
        byte[] data = mChunks.get(chunk);
        dest.put(data);
    }
    /**
     * Returns the flags associated with chunk N.
     */
    public int getChunkFlags(int chunk) {
        return mFlags.get(chunk);
    }
    /**
     * Returns the timestamp associated with chunk N.
     */
    public long getChunkTime(int chunk) {
        return mTimes.get(chunk);
    }

    public synchronized byte[] getNextChunk(){
        while (mChunks.isEmpty()){
            try{
                wait();
            }
            catch (InterruptedException e){}
        }
        byte[] nextChunk = mChunks.removeFirst();
        mFlags.removeFirst();
        mTimes.removeFirst();
        notifyAll();
        return nextChunk;
    }


    public synchronized Chunk get(){
        while (mChunks.isEmpty()){
            try{
                wait();
            }
            catch (InterruptedException e){}
        }
        Chunk c = new Chunk(mChunks.removeFirst(), mFlags.removeFirst(), mTimes.removeFirst());
        notifyAll();
        return c;
    }



}