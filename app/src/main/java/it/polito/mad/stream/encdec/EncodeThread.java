package it.polito.mad.stream.encdec;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by luigi on 09/12/15.
 */
@SuppressWarnings("deprecation")
public class EncodeThread implements Runnable {

    private static final String TAG = "ENCODER";
    private static final boolean VERBOSE = true;

    private static final int MEDIA_CODEC_TIMEOUT_US = 10000;
    private static final String MIME_TYPE = "video/avc";
    private static final int FRAME_RATE = 20;
    private static final int I_FRAME_INTERVAL = 1;
    private static final int BIT_RATE_BPS = 500000;
    private static final long NUM_FRAMES = 1000;

    private Thread mWorkerThread;
    private VideoChunks mChunks;
    private MediaPlayer mp = new MediaPlayer();
    private CbkListener mListener;
    private Surface mOutputSurface;

    interface CbkListener {
        void onComplete();
    }

    //private InputStream mInputStream;

    //private WebSocket mWebSocket;

    public EncodeThread(ParcelFileDescriptor fd){
        //mInputStream = new ParcelFileDescriptor.AutoCloseInputStream(fd);
    }

    public EncodeThread(VideoChunks chunks, Surface s){
        //mInputStream = new ParcelFileDescriptor.AutoCloseInputStream(fd);
        mChunks = chunks;
        mOutputSurface = s;
    }

    public void setmListener(CbkListener mListener) {
        this.mListener = mListener;
    }

    private static List<MediaCodecInfo> selectCodec(String mimeType) {
        List<MediaCodecInfo> res = new LinkedList<>();
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {
                continue;
            }

            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    res.add(codecInfo);
                    //return codecInfo;
                }
            }
        }
        return res;
        //return null;
    }

    public void start(){
        mWorkerThread = new Thread(this);
        mWorkerThread.start();
    }

    public void stop(){
        if (mWorkerThread != null) mWorkerThread.interrupt();
        mWorkerThread = null;
    }

    @Override
    public void run() {
        /*try{
            String serverIP = "192.168.0.2";
            int port = 8080;
            //WebSocketFactory factory = new WebSocketFactory().setConnectionTimeout(timeout);
            String uri = "ws://" + serverIP + ":" + port;
            mWebSocket = new WebSocketFactory().createSocket(uri, 2000);
            mWebSocket.addListener(new MyWSProtocol(null));
            mWebSocket.connect();
        }
        catch(IOException e){
            //TODO
            Log.e("E",e.getMessage());
        }
        catch(WebSocketException e){
            Log.e("E",e.getMessage());
        }
        */

        OutputStream os = null;
        try{
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
                throw new RuntimeException("External storage not mounted");
            }
            File f = new File(Environment.getExternalStorageDirectory().toString() + "/video");
            f.createNewFile();
            os = new FileOutputStream(f);
        }
        catch(IOException ioe){
            throw new RuntimeException(ioe);
        }
        Log.d(TAG, "ostream created");

        List<MediaCodecInfo> infos = selectCodec(MIME_TYPE);
        Log.d(TAG, infos.toString());
        MediaCodecInfo codecInfo = infos.get(0);
        //MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
        if (codecInfo == null) {
            // Don't fail CTS if they don't have an AVC codec (not here, anyway).
            Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return;
        }
        Log.d(TAG, codecInfo.toString());
        int colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
        //int colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
        /*for (int i : codecInfo.getCapabilitiesForType(MIME_TYPE).colorFormats){
            colorFormat = i;
        }
        */
        MediaCodec encoder = null;
        try{
            encoder = MediaCodec.createByCodecName(codecInfo.getName());
        }
        catch(IOException e){
            Log.e(TAG, "Unable to create an appropriate codec for " + MIME_TYPE);
            return;
        }
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, 320, 240);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE_BPS);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

        try{
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        }
        catch(Throwable t){
            Log.e(TAG,t.toString());
        }
        encoder.start();

        // if API level <= 20, get input and output buffer arrays here
        ByteBuffer[] encoderInputBuffers = encoder.getInputBuffers();
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        long framesCounter = 0, encodedSize = 0;

        boolean inputDone = false;
        boolean outputDone = false;

        if (VERBOSE) Log.d(TAG, "Encoder starts...");
        while (!outputDone) {
            if (!inputDone) {
                int inputBufIndex = encoder.dequeueInputBuffer(MEDIA_CODEC_TIMEOUT_US);
                if (Thread.interrupted()){
                    Log.i(TAG, "Interrupt requested");
                    break;
                }
                if (inputBufIndex >= 0) {
                    long ptsUsec = computePresentationTime(framesCounter);
                    if (framesCounter == NUM_FRAMES) {
                        // Send an empty frame with the end-of-stream flags set.  If we set EOS
                        // on a frame with data, that frame data will be ignored, and the
                        // output will be short one frame.
                        encoder.queueInputBuffer(inputBufIndex, 0, 0, ptsUsec,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                        if (VERBOSE) Log.d(TAG, "sent input EOS (with zero-length frame)");
                    } else {
                        byte[] frameData = mChunks.getNextChunk();
                        //byte[] frameData = new byte[1000];
                        ByteBuffer inputBuf = encoderInputBuffers[inputBufIndex];
                        assert inputBuf.capacity() >= frameData.length;
                        inputBuf.clear();
                        inputBuf.put(frameData);
                        encoder.queueInputBuffer(inputBufIndex, 0, frameData.length, ptsUsec, 0);
                        //if (VERBOSE) Log.d(TAG, "submitted frame " + framesCounter + " to enc");
                    }
                    framesCounter++;
                }
                else if (inputBufIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // not available

                }
            }
            // Check for output from the encoder.  If there's no output yet, we either need to
            // provide more input, or we need to wait for the encoder to work its magic.  We
            // can't actually tell which is the case, so if we can't get an output buffer right
            // away we loop around and see if it wants more input.
            //
            // Once we get EOS from the encoder, we don't need to do this anymore.
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            //if (!encoderDone) {
                int encoderStatus = encoder.dequeueOutputBuffer(info, MEDIA_CODEC_TIMEOUT_US);
                if (Thread.interrupted()){
                    Log.i(TAG, "Interrupt requested");
                    break;
                }
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet

                    if (VERBOSE) Log.d(TAG, "no output from encoder available");
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not expected for an encoder
                    encoderOutputBuffers = encoder.getOutputBuffers();
                    if (VERBOSE) Log.d(TAG, "encoder output buffers changed");
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // not expected for an encoder
                    MediaFormat newFormat = encoder.getOutputFormat();
                    ByteBuffer sps = newFormat.getByteBuffer("csd-0");
                    ByteBuffer pps = newFormat.getByteBuffer("csd-1");
                    if (VERBOSE) Log.d(TAG, "encoder output format changed: " + newFormat);
                } else if (encoderStatus < 0) {
                    Log.e(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                } else { // encoderStatus >= 0
                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    if (encodedData == null) {
                        Log.e(TAG, "encoderOutputBuffer " + encoderStatus + " was null");
                        continue;
                    }
                    // It's usually necessary to adjust the ByteBuffer values to match BufferInfo.
                    encodedData.position(info.offset);
                    encodedData.limit(info.offset + info.size);
                    encodedSize += info.size;
                    byte[] ba = new byte[encodedData.remaining()]; //converting bytebuffer to byte array
                    encodedData.get(ba);


                    try {
                        os.write(ba);
                        //mWebSocket.sendBinary(ba);
                    }
                    catch(IOException e){
                        throw new RuntimeException(e);
                    }

                    if (VERBOSE) Log.d(TAG, "Encoded buffer size: "+info.size);
                    if (VERBOSE) Log.d(TAG, "TOTAL Encoded size: "+encodedSize);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        if (VERBOSE) Log.i(TAG, "First coded packet ");
                    } else {
                        outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    }
                    encoder.releaseOutputBuffer(encoderStatus, false);
                }
            //}
        }

        //mWebSocket.sendClose();
        encoder.stop();
        encoder.release();
        Log.i(TAG, "Released!! Closing...");

        try {
            os.close();
            Log.d(TAG, "File closed");
        }catch(IOException e){}
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) mListener.onComplete();
            }
        });
    }


    private static long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / FRAME_RATE;
    }



}