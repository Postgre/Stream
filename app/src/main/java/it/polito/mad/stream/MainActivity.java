package it.polito.mad.stream;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.graphics.SurfaceTexture;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

import java.io.FileDescriptor;
import java.io.IOException;
import android.hardware.Camera;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import it.polito.mad.stream.websocket.AsyncClientImpl;
import it.polito.mad.stream.websocket.ClientStateListener;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "RECORDER";

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler, mMainHandler;

    private SurfaceView mCameraView;
    private int mRotation;

    protected MediaRecorder mMediaRecorder;
    private Camera mCamera;
    private ParcelFileDescriptor fdWrite, fdRead;
    private Socket mSocket;

    private AsyncClientImpl mWebClient;
    private FramesReaderSocketThread mFramesReader;

    private Button rec, stop, connect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rec = (Button) findViewById(R.id.button_rec);
        stop = (Button) findViewById(R.id.button_stop);
        connect = (Button) findViewById(R.id.button_connect);

        mCameraView = new SurfaceView(this);
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        mRotation = wm.getDefaultDisplay().getRotation();
        mCameraView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                try {
                    mCamera.setPreviewDisplay(holder);
                    mCamera.startPreview();
                } catch (IOException e) {

                }
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
                    mCamera.setPreviewDisplay(holder);
                    mCamera.startPreview();
                } catch (IOException e) {

                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) { }
        });


        mFramesReader = new FramesReaderSocketThread();
        mFramesReader.setCallback(new FramesReaderSocketThread.Callback() {
            @Override
            public void onChunkRead(byte[] buffer, int length) {
                if (mWebClient != null) {
                    mWebClient.sendByteArrayMessage(buffer, length);
                }
                int min = Math.min(10, length);
                String s = "[";
                for (int i = 0; i < min; i++) s += buffer[i] + " ";
                Log.d(TAG, s);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, e.getMessage());
            }
        });
        mFramesReader.start();

        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //String ip = "172.20.89.125";
                //String ip = "192.168.1.63";
                String ip = "192.168.1.129";
                //String ip = editTextAddress.getText().toString();
                //URI serverURI = null;
                int port = 8080;
                mWebClient = new AsyncClientImpl(new ClientStateListener() {
                    @Override
                    public void onConnectionEstablished() {
                        Log.i("ACT", "OK");
                    }

                    @Override
                    public void onConnectionClosed() {
                        Log.i("ACT", "Closed");
                    }

                    @Override
                    public void onServerUnreachable() {
                        Log.i("ACT", "Unreachable");
                    }
                });
                mWebClient.connectToWebSocketServer(ip, port, 2000);
            }
        });

        rec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    configureMediaRecorder();
                    mMediaRecorder.start();
                }
                catch(Exception e){
                    Log.e(TAG, e.getClass().getSimpleName()+": "+e.getMessage());
                }
            }
        });

        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mMediaRecorder.stop();
                }
                catch(Exception e){
                    Log.e(TAG, e.getClass().getSimpleName()+": "+e.getMessage());
                }
            }
        });

        FrameLayout container = (FrameLayout) findViewById(R.id.container);
        container.addView(mCameraView);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    mSocket = new Socket("127.0.0.1", 8000);
                    fdWrite = ParcelFileDescriptor.fromSocket(mSocket);
                }
                catch(IOException e){}
            }
        }).start();
    }





    public void acquireCamera() {
        try {
            mCamera = Camera.open();
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
        mMediaRecorder = new MediaRecorder();
        //startBackgroundThread();
        acquireCamera();
    }

    @Override
    public void onPause() {
        releaseMediaRecorder();
        //stopBackgroundThread();
        if (mFramesReader != null){
            mFramesReader.kill();
        }
        super.onPause();
    }

    public void configureMediaRecorder() {
        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);

        //mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
       /* CamcorderProfile profile = null;
        if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_720P)){
            profile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
        }else if(CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_480P)){
            profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
        }else{ profile = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW); }

        profile.fileFormat = 8;
        mMediaRecorder.setProfile(profile);



        */
        boolean b = false;
        if (b){
            //Solution 1:
            CamcorderProfile profile = CamcorderProfile.get(0, CamcorderProfile.QUALITY_LOW);
            mMediaRecorder.setProfile(profile);
        }
        else{
            // OR Solution 2:
            mMediaRecorder.setOutputFormat(8);
            //mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            //mMediaRecorder.setVideoSize(640, 480);
            //mMediaRecorder.setVideoEncodingBitRate(params.mVideoEncodingBitRate);
            mMediaRecorder.setVideoFrameRate(25);
        }

        //mMediaRecorder.setOutputFile(Environment.getExternalStorageDirectory().toString() + "/video.ts");
        mMediaRecorder.setOutputFile(fdWrite.getFileDescriptor());
        mMediaRecorder.setPreviewDisplay(mCameraView.getHolder().getSurface());
        mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mr, int what, int extra) {
                Log.d("Recorder info", "INFO: codice " + what);
            }
        });

        mMediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mr, int what, int extra) {
                Log.e(TAG, "ERROR: codice " + what);
            }
        });
        switch (mRotation) {
            case Surface.ROTATION_0:
                mMediaRecorder.setOrientationHint(90);
                break;
            case Surface.ROTATION_90:
                break;
            case Surface.ROTATION_180:
                break;
            case Surface.ROTATION_270:
                mMediaRecorder.setOrientationHint(180);
                break;
        }
        try {
            mMediaRecorder.prepare();
        }
        catch(Exception e){
            Log.e(TAG, e.getClass().getSimpleName()+": "+e.getMessage());
        }
    }


    public void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
        }
        mCamera.lock();
        releaseCamera();
    }

    public void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        Log.d(TAG, "Background thread started!!");
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void stopBackgroundThread() {
        //mBackgroundThread.quit();
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
            Log.d(TAG, "Background thread finished!!");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
