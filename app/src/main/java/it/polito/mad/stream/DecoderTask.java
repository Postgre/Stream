package it.polito.mad.stream;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;

/**
 * Created by luigi on 21/01/16.
 */
@SuppressWarnings("deprecation")
public class DecoderTask extends AsyncTask<Void, VideoChunksNew.Chunk, Void> {

    public static abstract class Listener {
        void onEncodedDataAvailable(byte[] data){}
    }

    private static final String TAG = "DECODER";
    private static final boolean VERBOSE = false;

    private static final int TIMEOUT_US = 10000;
    private static final String MIME_TYPE = "video/avc";
    private static final int FRAME_RATE = 20;
    private static final int I_FRAME_INTERVAL = 1;
    private static final int BIT_RATE_BPS = 500000;
    private static final long NUM_FRAMES = 100;

    private Listener mListener;
    //private Semaphore mConfigDataReceived = new Semaphore(0);
    private ByteBuffer mConfigBuffer;
    private VideoChunksNew mEncodedFrames = new VideoChunksNew();
    private int mWidth = 320, mHeight = 240;
    private Surface mOutputSurface;

    public void setListener(Listener mListener) {
        this.mListener = mListener;
    }

    public synchronized void setConfigurationBuffer(ByteBuffer csd0){
        mConfigBuffer = csd0;
        notifyAll();
    }

    public void submitEncodedData(VideoChunksNew.Chunk chunk){
        mEncodedFrames.addChunk(chunk);
    }

    public void setSurface(Surface s){
        mOutputSurface = s;
    }

    @Override
    protected Void doInBackground(Void... params) {
        MediaCodec decoder = null;
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        try{
            decoder = MediaCodec.createDecoderByType(MIME_TYPE);
            //now, we must wait for csd-0 configuration bytes by the encoder
            //that is, until setConfigurationBuffer() will be called from another thread
            if (VERBOSE) Log.d(TAG, "Waiting for configuration buffer from the encoder...");
            synchronized (this){
                while (mConfigBuffer == null){
                    wait();
                }
            }
            format.setByteBuffer("csd-0", mConfigBuffer);  //SPS + PPS
            if (VERBOSE) Log.d(TAG, "Configured csd-0 buffer: "+mConfigBuffer.toString());
            decoder.configure(format, mOutputSurface,  null, 0);
            decoder.start();
        }
        catch(IOException e){
            Log.e(TAG, "Unable to create an appropriate codec for " + MIME_TYPE);
            return null;
        }
        catch(InterruptedException e){}
        catch(Throwable t){
            Log.e(TAG, t.toString());
            return null;
        }

        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        ByteBuffer[] decoderOutputBuffers = decoder.getOutputBuffers();
        long encodedSize = 0;
        long counter = 0;

        boolean EOS_Received = false;
        boolean mEnd = true;
        int inputStatus = -1, outputStatus = -1;

        if (VERBOSE) Log.d(TAG, "Decoder starts...");

        while (!Thread.interrupted()){
            if (!EOS_Received) {
                //if (VERBOSE) Log.i(TAG, "Waiting for input buffer");
                inputStatus = decoder.dequeueInputBuffer(TIMEOUT_US);
                if (inputStatus >= 0) {
                    ByteBuffer inputBuf = decoderInputBuffers[inputStatus];
                    inputBuf.clear();

                    if (VERBOSE) Log.d(TAG, "Waiting for new encoded chunk from encoder...");
                    VideoChunksNew.Chunk chunk = mEncodedFrames.getNextChunk();
                    inputBuf.put(chunk.data);
                    EOS_Received = (chunk.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    counter++;

                    decoder.queueInputBuffer(inputStatus, 0, chunk.data.length,
                            chunk.presentationTimestampUs, chunk.flags);
                    if (VERBOSE) Log.d(TAG, "queued array # " + counter + ": "
                            + chunk.data.length + " bytes to decoder");
                }
            }

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            outputStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_US);
            switch (outputStatus){
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    //if (VERBOSE) Log.d(TAG, "no output from decoder available");
                    continue;
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    if (VERBOSE) Log.d(TAG, "decoder output buffers changed");
                    decoderOutputBuffers = decoder.getOutputBuffers();
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    format = decoder.getOutputFormat();
                    if (VERBOSE) Log.d(TAG, "decoder output format changed: " + format);
                    break;
                default:
                    counter++;
                    Log.d(TAG, "DECODER OUTPUT AVAILABLE!!!");
                    ByteBuffer outputFrame = decoderOutputBuffers[outputStatus];
                    outputFrame.position(info.offset);
                    outputFrame.limit(info.offset + info.size);
                    if (info.size == 0) {
                        if (VERBOSE) Log.d(TAG, "got empty frame");
                    }
                    else {
                        byte[] decodedArray = new byte[outputFrame.remaining()];
                        outputFrame.get(decodedArray);
                        VideoChunksNew.Chunk c =
                                new VideoChunksNew.Chunk(decodedArray, info.flags, info.presentationTimeUs);
                        publishProgress(c);
                    }
                    //mEnd = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    if (mEnd) Log.d(TAG, "EOS rechead. Will close");
                    /*EOS_Received = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    if (EOS_Received){
                        if (VERBOSE) Log.d(TAG, "output EOS");
                    }
                    */
                    decoder.releaseOutputBuffer(outputStatus, true /*render*/);
                    break;
            }
        }
        decoder.stop();
        decoder.release();
        Log.i(TAG, "Decoder Released!! Closing");
        return null;
    }
/*
    @Override
    protected void onProgressUpdate(byte[]... values) {
        if (mListener != null){
            byte[] data = values[0];
            mListener.onEncodedDataAvailable(data);
        }
    }
*/

    private static MediaCodecInfo selectCodec(String mimeType) {
        for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (codecInfo.isEncoder()) {
                String[] types = codecInfo.getSupportedTypes();
                for (int j = 0; j < types.length; j++) {
                    if (types[j].equalsIgnoreCase(mimeType)) {
                        return codecInfo;
                    }
                }
            }
        }
        return null;
    }

    private static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            switch (colorFormat) {
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
                case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible:
                    return colorFormat;
            }
        }
        return 0;   // not reached
    }

    private static long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / FRAME_RATE;
    }
}
