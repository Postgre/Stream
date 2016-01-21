package it.polito.mad.stream.encdec;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Created by luigi on 09/12/15.
 */
@SuppressWarnings("deprecation")
public class EncodeDecodeThread extends AsyncTask<Void, byte[], Void> {

    private static final String TAG_ENCODER = "T_ENCODE";
    private static final String TAG_DECODER = "T_DECODE";
    private static final boolean VERBOSE = true;

    private static final int MEDIA_CODEC_TIMEOUT_US = 10000;
    private static final String MIME_TYPE = "video/avc";
    private static final int FRAME_RATE = 20;
    private static final int I_FRAME_INTERVAL = 1;
    private static final int BIT_RATE_BPS = 2000000;
    private static final long NUM_FRAMES = 100;

    private VideoChunks mChunks;
    private CbkListener mListener;
    private Surface mOutputSurface;

    private int mWidth = 320, mHeight = 240;

    interface CbkListener {
        void onComplete();
    }

    //private InputStream mInputStream;

    //private WebSocket mWebSocket;

    public EncodeDecodeThread(ParcelFileDescriptor fd){
        //mInputStream = new ParcelFileDescriptor.AutoCloseInputStream(fd);
    }

    public EncodeDecodeThread(VideoChunks chunks, Surface s){
        //mInputStream = new ParcelFileDescriptor.AutoCloseInputStream(fd);
        mChunks = chunks;
        mOutputSurface = s;
    }

    public void setOutputSurface(Surface surface){
        mOutputSurface = surface;
    }

    public void setmListener(CbkListener mListener) {
        this.mListener = mListener;
    }

    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    public void start(){
    }

    public void stop(){
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
        Log.e(TAG_ENCODER,"couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
        return 0;   // not reached
    }

    protected Void doInBackground(Void... params) {
        OutputStream os = null;
        try{
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
                throw new RuntimeException("External storage not mounted");
            }
            File f = new File(Environment.getExternalStorageDirectory().toString() + "/videoDec");
            f.createNewFile();
            os = new FileOutputStream(f);
        }
        catch(IOException ioe){
            throw new RuntimeException(ioe);
        }
        Log.d(TAG_ENCODER, "ostream created");

        MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
        if (codecInfo == null) {
            // Don't fail CTS if they don't have an AVC codec (not here, anyway).
            Log.e(TAG_ENCODER, "Unable to find an appropriate codec for " + MIME_TYPE);
            return null;
        }
        Log.d(TAG_ENCODER, codecInfo.toString());

        int colorFormat = selectColorFormat(codecInfo, MIME_TYPE);
        MediaCodec encoder, decoder;
        try{
            encoder = MediaCodec.createByCodecName(codecInfo.getName());
            decoder = MediaCodec.createDecoderByType(MIME_TYPE);
        }
        catch(IOException e){
            Log.e(TAG_ENCODER, "Unable to create an appropriate codec for " + MIME_TYPE);
            return null;
        }
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE_BPS);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

        try{
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        }
        catch(Throwable t){
            Log.e(TAG_ENCODER,t.toString());
        }
        encoder.start();



        ByteBuffer[] encoderInputBuffers = encoder.getInputBuffers();
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        ByteBuffer[] decoderInputBuffers = null;
        ByteBuffer[] decoderOutputBuffers = null;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        MediaFormat decoderOutputFormat = null;
        int frameCounter = 0;
        int checkIndex = 0;
        int badFrames = 0;
        boolean decoderConfigured = false;
        //final OutputSurface outputSurface = new OutputSurface(mWidth, mHeight, stexture);
        // The size of a frame of video data, in the formats we handle, is stride*sliceHeight
        // for Y, and (stride/2)*(sliceHeight/2) for each of the Cb and Cr channels.  Application
        // of algebra and assuming that stride==width and sliceHeight==height yields:
        //byte[] frameData = new byte[mWidth * mHeight * 3 / 2];
        // Just out of curiosity.
        long rawSize = 0;
        long encodedSize = 0;
        boolean inputDone = false;
        boolean encoderDone = false;
        boolean outputDone = false;
        while (!outputDone) {
            if (!inputDone) {
                int inputBufIndex = encoder.dequeueInputBuffer(MEDIA_CODEC_TIMEOUT_US);
                if (VERBOSE) Log.d(TAG_ENCODER, "inputBufIndex=" + inputBufIndex);
                if (inputBufIndex >= 0) {
                    long ptsUsec = computePresentationTime(frameCounter);
                    if (frameCounter == NUM_FRAMES) {
                        // Send an empty frame with the end-of-stream flags set.  If we set EOS
                        // on a frame with data, that frame data will be ignored, and the
                        // output will be short one frame.
                        encoder.queueInputBuffer(inputBufIndex, 0, 0, ptsUsec,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                        if (VERBOSE) Log.d(TAG_ENCODER, "sent input EOS (with zero-length frame)");
                    } else {
                        byte[] frameData = mChunks.getNextChunk();

                        ByteBuffer inputBuf = encoderInputBuffers[inputBufIndex];
                        // the buffer should be sized to hold one full frame
                        //assertTrue(inputBuf.capacity() >= frameData.length);
                        inputBuf.clear();
                        inputBuf.put(frameData);

                        encoder.queueInputBuffer(inputBufIndex, 0, frameData.length, ptsUsec, 0);
                        if (VERBOSE) Log.d(TAG_ENCODER, "submitted frame " + frameCounter + " to enc");
                    }
                    frameCounter++;
                } else {
                    // either all in use, or we timed out during initial setup
                    if (VERBOSE) Log.d(TAG_ENCODER, "input buffer not available");
                }
            }
            // Check for output from the encoder.  If there's no output yet, we either need to
            // provide more input, or we need to wait for the encoder to work its magic.  We
            // can't actually tell which is the case, so if we can't get an output buffer right
            // away we loop around and see if it wants more input.
            //
            // Once we get EOS from the encoder, we don't need to do this anymore.
            if (!encoderDone) {
                int encoderStatus = encoder.dequeueOutputBuffer(info, MEDIA_CODEC_TIMEOUT_US);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (VERBOSE) Log.d(TAG_ENCODER, "no output from encoder available");
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not expected for an encoder
                    encoderOutputBuffers = encoder.getOutputBuffers();
                    if (VERBOSE) Log.d(TAG_ENCODER, "encoder output buffers changed");
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // not expected for an encoder
                    MediaFormat newFormat = encoder.getOutputFormat();
                    if (VERBOSE) Log.d(TAG_ENCODER, "encoder output format changed: " + newFormat);
                } else if (encoderStatus < 0) {
                    //fail("unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                } else { // encoderStatus >= 0
                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    if (encodedData == null) {
                        Log.e(TAG_ENCODER, "encoderOutputBuffer " + encoderStatus + " was null");
                        continue;
                    }
                    // It's usually necessary to adjust the ByteBuffer values to match BufferInfo.
                    encodedData.position(info.offset);
                    encodedData.limit(info.offset + info.size);
                    encodedSize += info.size;
                    byte[] ba = new byte[encodedData.remaining()]; //converting bytebuffer to byte array
                    encodedData.get(ba);
                    if (VERBOSE) Log.d(TAG_ENCODER, ba.length+" bytes of encoded data!!!!!");

                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // Codec config info.  Only expected on first packet.  One way to
                        // handle this is to manually stuff the data into the MediaFormat
                        // and pass that to configure().  We do that here to exercise the API.
                        //assertFalse(decoderConfigured);
                        MediaFormat decoderFormat =
                                MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
                        decoderFormat.setByteBuffer("csd-0", encodedData);  //SPS + PPS
                        decoder.configure(decoderFormat, null,  null, 0);
                        decoder.start();
                        decoderInputBuffers = decoder.getInputBuffers();
                        decoderOutputBuffers = decoder.getOutputBuffers();
                        decoderConfigured = true;
                        if (VERBOSE) Log.d(TAG_ENCODER, "decoder configured (" + info.size + " bytes)");
                    } else {
                        // Get a decoder input buffer, blocking until it's available.
                        //assertTrue(decoderConfigured);
                        int inputBufIndex = decoder.dequeueInputBuffer(MEDIA_CODEC_TIMEOUT_US);
                        if (inputBufIndex >= 0) {
                            if (VERBOSE)
                                Log.d(TAG_DECODER, "Dequeued input buffer # " + inputBufIndex);
                            ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                            inputBuf.clear();
                            inputBuf.put(encodedData);
                            Log.d(TAG_DECODER, "Queuing "+info.size+" bytes");
                            decoder.queueInputBuffer(inputBufIndex, 0, info.size,
                                    info.presentationTimeUs, info.flags);
                            encoderDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                            if (VERBOSE)
                                Log.d(TAG_ENCODER, "passed " + info.size + " bytes to decoder"
                                        + (encoderDone ? " (EOS)" : ""));
                        }
                    }
                    encoder.releaseOutputBuffer(encoderStatus, false);
                }
            }

            if (decoderConfigured) {
                int decoderStatus = decoder.dequeueOutputBuffer(info, MEDIA_CODEC_TIMEOUT_US);
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (VERBOSE) Log.d(TAG_DECODER, "no output from decoder available");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // The storage associated with the direct ByteBuffer may already be unmapped,
                    // so attempting to access data through the old output buffer array could
                    // lead to a native crash.
                    if (VERBOSE) Log.d(TAG_DECODER, "decoder output buffers changed");
                    decoderOutputBuffers = decoder.getOutputBuffers();
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // this happens before the first frame is returned
                    decoderOutputFormat = decoder.getOutputFormat();
                    if (VERBOSE) Log.d(TAG_DECODER, "decoder output format changed: " +
                            decoderOutputFormat);
                } else if (decoderStatus < 0) {
                    //fail("unexpected result from deocder.dequeueOutputBuffer: " + decoderStatus);
                } else {  // decoderStatus >= 0
                    //if (!toSurface) {
                        Log.d(TAG_DECODER, "DECODER OUTPUT AVAILABLE!!!");
                        ByteBuffer outputFrame = decoderOutputBuffers[decoderStatus];
                        outputFrame.position(info.offset);
                        outputFrame.limit(info.offset + info.size);
                        rawSize += info.size;
                        if (info.size == 0) {
                            if (VERBOSE) Log.d(TAG_DECODER, "got empty frame");
                        } else {
                            if (VERBOSE) Log.d(TAG_DECODER, "decoded, checking frame " + checkIndex);
                            //assertEquals("Wrong time stamp", computePresentationTime(checkIndex),
                            //      info.presentationTimeUs);
                            byte[] ba = new byte[outputFrame.remaining()];
                            outputFrame.get(ba);
                            try {
                                os.write(ba);
                            }
                            catch(IOException e){
                                throw new RuntimeException(e);
                            }

                        }
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            if (VERBOSE) Log.d(TAG_DECODER, "output EOS");
                            outputDone = true;
                        }
                        decoder.releaseOutputBuffer(decoderStatus, false /*render*/);
                    /*} else {
                        if (VERBOSE) Log.d(TAG_ENCODER, "surface decoder given buffer " + decoderStatus +
                                " (size=" + info.size + ")");
                        rawSize += info.size;
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            if (VERBOSE) Log.d(TAG_ENCODER, "output EOS");
                            outputDone = true;
                        }
                        boolean doRender = (info.size != 0);
                        // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                        // to SurfaceTexture to convert to a texture.  The API doesn't guarantee
                        // that the texture will be available before the call returns, so we
                        // need to wait for the onFrameAvailable callback to fire.
                        decoder.releaseOutputBuffer(decoderStatus, doRender);
                        /*if (doRender) {
                            if (VERBOSE) Log.d(TAG_ENCODER, "awaiting frame " + checkIndex);
                            //assertEquals("Wrong time stamp", computePresentationTime(checkIndex),
                            //      info.presentationTimeUs);
                            outputSurface.awaitNewImage();
                            outputSurface.drawImage();

                        }
                    }*/
                }
            }
        }
        if (os != null) {
            try {
                os.close();
                Log.d(TAG_ENCODER, "File closed");
            } catch (IOException ioe) {
                Log.w(TAG_ENCODER, "failed closing debug file");
                throw new RuntimeException(ioe);
            }
        }

        if (mOutputSurface != null) mOutputSurface.release();
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        Log.d(TAG_ENCODER, "TERMINATED!!!!");
    }

    private static long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / FRAME_RATE;
    }



}