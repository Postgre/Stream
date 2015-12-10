package it.polito.mad.stream.websocket;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;

import java.io.IOException;

/**
 * Manages a {@link WebSocket} inside a background thread
 * Created by luigi on 02/12/15.
 */
public class AsyncClientImpl extends AbstractWSClient {

    private static final String TAG = "AsynchWebSocketClient";

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler, mMainHandler;

    //private WSClientImpl mClient;
    private WebSocket mWebSocket;

    public AsyncClientImpl(final ClientStateListener listener) {
        startBackgroundThread();
        mStateListener = listener;
        mProtocol = new MyWSProtocol(listener);
    }

    @Override
    public WebSocket getSocket() {
        return mWebSocket;
    }

    @Override
    public void connectToWebSocketServer(final String serverIP, final int port, final int timeout) {
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                try{
                    //WebSocketFactory factory = new WebSocketFactory().setConnectionTimeout(timeout);
                    String uri = "ws://" + serverIP + ":" + port;
                    mWebSocket = new WebSocketFactory().createSocket(uri, timeout);
                    mWebSocket.addListener(mProtocol);
                    mWebSocket.connect();
                }
                catch(IOException e){
                    //TODO
                    Log.e("E",e.getMessage());
                }
                catch(WebSocketException e){
                    Log.e("E",e.getMessage());
                }
            }
        });
    }

    @Override
    public void closeConnection() {
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                mWebSocket.sendClose();
            }
        });
        stopBackgroundThread();
    }

    public void sendByteArrayMessage(final byte[] data, final int len){
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                byte[] toSend = data;
                if (len < data.length){
                    toSend = new byte[len];
                    System.arraycopy(data, 0, toSend, 0, len);
                }
                mWebSocket.sendBinary(toSend);
            }
        });
    }





    /**
     * Starts a background thread and its {@link Handler}.
     */
    public void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
            Log.d(TAG, "Web socket Client TERMINATED!");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
