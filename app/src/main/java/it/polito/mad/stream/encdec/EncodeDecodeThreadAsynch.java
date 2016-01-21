package it.polito.mad.stream.encdec;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by luigi on 09/12/15.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@SuppressWarnings("deprecation")
public class EncodeDecodeThreadAsynch extends AsyncTask<Void, byte[], Void> {

    private static final String TAG_ENCODER = "T_ENCODE";
    private static final String TAG_DECODER = "T_DECODE";
    private static final boolean VERBOSE = true;

    private static final int MEDIA_CODEC_TIMEOUT_US = 10000;
    private static final String MIME_TYPE = "video/avc";
    private static final int FRAME_RATE = 20;
    private static final int I_FRAME_INTERVAL = 1;
    private static final int BIT_RATE_BPS = 2000000;
    private static final long NUM_FRAMES = 100;

    private VideoChunks mChunks, mEncodedChunks = new VideoChunks();
    private Surface mOutputSurface;

    private MediaCodec encoder, decoder;
    private MediaFormat mEncoderFormat, mDecoderFormat;
    private int chunkCounter = 0;
    private int encodedSize = 0;

    private int mWidth = 320, mHeight = 240;

    public EncodeDecodeThreadAsynch(VideoChunks chunks, Surface s){
        mChunks = chunks;
        mOutputSurface = s;
    }

    public void setOutputSurface(Surface surface){
        mOutputSurface = surface;
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
        MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
        if (codecInfo == null) {
            // Don't fail CTS if they don't have an AVC codec (not here, anyway).
            Log.e(TAG_ENCODER, "Unable to find an appropriate codec for " + MIME_TYPE);
            return null;
        }
        Log.d(TAG_ENCODER, codecInfo.toString());

        int colorFormat = selectColorFormat(codecInfo, MIME_TYPE);
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

        encoder.setCallback(mEncoderCallback);

        try{
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        }
        catch(Throwable t){
            Log.e(TAG_ENCODER,t.toString());
        }
        mEncoderFormat = encoder.getOutputFormat();
        encoder.start();
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        Log.d(TAG_ENCODER, "TERMINATED!!!!");
    }

    private static long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / FRAME_RATE;
    }

    private MediaCodec.Callback mEncoderCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(MediaCodec mc, int inputBufferId) {
            ByteBuffer inputBuffer = mc.getInputBuffer(inputBufferId);
            long ptsUsec = computePresentationTime(chunkCounter);
            if (chunkCounter == NUM_FRAMES) {
                // Send an empty frame with the end-of-stream flags set.  If we set EOS
                // on a frame with data, that frame data will be ignored, and the
                // output will be short one frame.
                encoder.queueInputBuffer(inputBufferId, 0, 0, ptsUsec, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                if (VERBOSE) Log.d(TAG_ENCODER, "sent input EOS (with zero-length frame)");
            } else {
                byte[] frameData = mChunks.getNextChunk();
                inputBuffer.clear();
                inputBuffer.put(frameData);

                encoder.queueInputBuffer(inputBufferId, 0, frameData.length, ptsUsec, 0);
                if (VERBOSE) Log.d(TAG_ENCODER, "submitted frame " + chunkCounter + " to enc");
            }
            chunkCounter++;
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec mc, int outputBufferId, MediaCodec.BufferInfo info) {
            ByteBuffer encodedData = mc.getOutputBuffer(outputBufferId);

            encodedData.position(info.offset);
            encodedData.limit(info.offset + info.size);
            encodedSize += info.size;
            byte[] ba = new byte[encodedData.remaining()]; //converting bytebuffer to byte array
            encodedData.get(ba);
            if (VERBOSE) Log.d(TAG_ENCODER, ba.length+" bytes of encoded data!!!!!");

            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                // Codec config info.  Only expected on first packet.  One way to
                // handle this is to manually stuff the data into the MediaFormat
                // and pass that to configure()
                MediaFormat decoderFormat =
                        MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
                decoderFormat.setByteBuffer("csd-0", encodedData);  //SPS + PPS
                decoder.setCallback(mDecoderCallback);
                decoder.configure(decoderFormat, null, null, 0);
                decoder.start();
                if (VERBOSE) Log.d(TAG_ENCODER, "decoder configured (" + info.size + " bytes)");
            }
            mEncodedChunks.addChunk(ba, info.flags, info.presentationTimeUs);
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            mEncoderFormat = format;
            if (VERBOSE) Log.d(TAG_ENCODER, "encoder output format changed: " + format);
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {

        }
    };


    private MediaCodec.Callback mDecoderCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
            if (VERBOSE) Log.d(TAG_DECODER, "Dequeued input buffer # " + index);
            ByteBuffer inputBuf = codec.getInputBuffer(index);
            VideoChunks.Chunk c = mEncodedChunks.get();
            byte[] encodedChunk = c.data;
            int flag = c.flags;
            long timestamp = c.presentationTimestampUs;
            ByteBuffer encodedData = ByteBuffer.wrap(encodedChunk);
            inputBuf.clear();
            inputBuf.put(encodedData);
            decoder.queueInputBuffer(index, 0, encodedChunk.length, timestamp, flag);
            //encoderDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            if (VERBOSE)
                Log.d(TAG_ENCODER, "passed " + encodedChunk.length + " bytes to decoder");
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
            Log.d(TAG_DECODER, "DECODER OUTPUT AVAILABLE!!!");
            ByteBuffer outputFrame = codec.getOutputBuffer(index);
            outputFrame.position(info.offset);
            outputFrame.limit(info.offset + info.size);
            if (info.size == 0) {
                if (VERBOSE) Log.d(TAG_DECODER, "got empty frame");
            } else {
                byte[] ba = new byte[outputFrame.remaining()];

            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                if (VERBOSE) Log.d(TAG_DECODER, "output EOS");
            }
            decoder.releaseOutputBuffer(index, false /*render*/);
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {

        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            mDecoderFormat = codec.getOutputFormat();
            if (VERBOSE) Log.d(TAG_DECODER, "decoder output format changed: " + format);
        }
    };


}