package it.polito.mad.stream;

import android.content.Context;
import android.graphics.ImageFormat;
import android.media.MediaCodec;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.io.IOException;
import java.nio.ByteBuffer;

import android.hardware.Camera;
import android.os.ParcelFileDescriptor;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "PREVIEW";

    private Button rec, stop, connect;
    private SurfaceView mCameraView, mOutputView;
    private int mRotation;

    private ParcelFileDescriptor fdRead, fdWrite;
    //private EncodeThread mReaderThread;

    private byte[] buffer1 = new byte[115200];
    private byte[] buffer2 = new byte[115200];
    private byte[] buffer3 = new byte[115200];
    private int idx = 0;

    private Camera mCamera;

    private EncoderTask mEncoderTask;
    private DecoderTask mDecoderTask;

    private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            Log.d(TAG, "data[" + data.length + "]");
            mEncoderTask.submitAccessUnit(data);
            switch (idx){
                case 0:
                    camera.addCallbackBuffer(buffer1);
                    break;
                case 1:
                    camera.addCallbackBuffer(buffer2);
                    break;
                case 2:
                    camera.addCallbackBuffer(buffer3);
                    break;
            }
            idx = (idx+1) % 3;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rec = (Button) findViewById(R.id.button_rec);
        stop = (Button) findViewById(R.id.button_stop);
        //connect = (Button) findViewById(R.id.button_connect);

        mOutputView = (SurfaceView) findViewById(R.id.output_surface);
        mOutputView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Surface s = holder.getSurface();
                mDecoderTask.setSurface(s);

                        mEncoderTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                mDecoderTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });


        mCameraView = new SurfaceView(this);
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        mRotation = wm.getDefaultDisplay().getRotation();
        mCameraView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                if (holder.getSurface() == null || mCamera == null) {
                    return;
                }
                try {
                    mCamera.stopPreview();
                    switch (mRotation) {
                        case Surface.ROTATION_0:
                            mCamera.setDisplayOrientation(90);
                            break;
                        case Surface.ROTATION_90:
                            break;
                        case Surface.ROTATION_180:
                            break;
                        case Surface.ROTATION_270:
                            mCamera.setDisplayOrientation(180);
                            break;
                    }
                    mCamera.addCallbackBuffer(buffer1);
                    idx++;
                    mCamera.setPreviewCallbackWithBuffer(mPreviewCallback);
                    Camera.Parameters parameters = mCamera.getParameters();
                    parameters.setPreviewFormat(ImageFormat.NV21);
                    parameters.setPreviewSize(320, 240);
                    mCamera.setParameters(parameters);
                    mCamera.setPreviewDisplay(holder);
                    mCamera.startPreview();

                } catch (IOException e) {

                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
            }
        });

        rec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mCamera.stopPreview();
                mCamera.setPreviewCallback(null);

            }
        });

        FrameLayout container = (FrameLayout) findViewById(R.id.container);
        container.addView(mCameraView);

        mEncoderTask = new EncoderTask(){
            @Override
            protected void onProgressUpdate(VideoChunksNew.Chunk... values) {
                VideoChunksNew.Chunk c = values[0];
                if ((c.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0){
                    ByteBuffer bb = ByteBuffer.wrap(c.data);
                    mDecoderTask.setConfigurationBuffer(bb);
                }
                else {
                    mDecoderTask.submitEncodedData(c);
                }
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                mCamera.setPreviewCallback(null);
                //mCamera.stopPreview();
            }
        };

        mDecoderTask = new DecoderTask();


    }





    public void acquireCamera() {
        try {
            mCamera = Camera.open(0);
        } catch (Exception e) {
            Log.e("CAMERA", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void releaseCamera() {
        if (mCamera != null) {
            mCamera.release(); // release the camera for other applications
            mCamera = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        acquireCamera();

    }

    @Override
    public void onPause() {
        releaseCamera();
        super.onPause();
    }
}
