package it.polito.mad.stream.websocket;

import android.os.Build;
import android.util.Log;

import com.neovisionaries.ws.client.*;

import java.util.List;
import java.util.Map;

/**
 * Defines the web socket protocol with the server
 * Created by luigi on 02/12/15.
 */
public class MyWSProtocol implements WebSocketListener {

    private static final String TAG = "WEB_SOCKET_PROTOCOL";

    private ClientStateListener mStateListener;

    public MyWSProtocol(ClientStateListener listener){
        mStateListener = listener;
    }

    @Override
    public void onStateChanged(WebSocket websocket, WebSocketState newState) throws Exception {

    }

    @Override
    public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {
        websocket.sendText("Hello from " + Build.MANUFACTURER + " " + Build.MODEL);
        /*notify
        mCallerHandler.post(new Runnable() {
            @Override
            public void run() {

            }
        });
        */
        if (mStateListener != null){
            mStateListener.onConnectionEstablished();
        }
    }

    @Override
    public void onConnectError(WebSocket websocket, WebSocketException cause) throws Exception {
        Log.i(TAG, cause.getClass().getSimpleName() + ": " + cause.getMessage());
    }

    @Override
    public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {
        /*notify
        mCallerHandler.post(new Runnable() {
            @Override
            public void run() {

            }
        });*/
        if (mStateListener != null){
            mStateListener.onConnectionClosed();
        }
    }

    @Override
    public void onFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {

    }

    @Override
    public void onContinuationFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {

    }

    @Override
    public void onTextFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {

    }

    @Override
    public void onBinaryFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {

    }

    @Override
    public void onCloseFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {

    }

    @Override
    public void onPingFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {

    }

    @Override
    public void onPongFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {

    }

    @Override
    public void onTextMessage(WebSocket websocket, String text) throws Exception {

    }

    @Override
    public void onBinaryMessage(WebSocket websocket, byte[] binary) throws Exception {

    }

    @Override
    public void onFrameSent(WebSocket websocket, WebSocketFrame frame) throws Exception {

    }

    @Override
    public void onFrameUnsent(WebSocket websocket, WebSocketFrame frame) throws Exception {

    }

    @Override
    public void onError(WebSocket websocket, WebSocketException cause) throws Exception {
        Log.i(TAG, cause.getClass().getSimpleName() + ": " + cause.getMessage());
    }

    @Override
    public void onFrameError(WebSocket websocket, WebSocketException cause, WebSocketFrame frame) throws Exception {
        Log.i(TAG, cause.getClass().getSimpleName() + ": " + cause.getMessage());
    }

    @Override
    public void onMessageError(WebSocket websocket, WebSocketException cause, List<WebSocketFrame> frames) throws Exception {
        Log.i(TAG, cause.getClass().getSimpleName() + ": " + cause.getMessage());
    }

    @Override
    public void onTextMessageError(WebSocket websocket, WebSocketException cause, byte[] data) throws Exception {
        Log.i(TAG, cause.getClass().getSimpleName() + ": " + cause.getMessage());
    }

    @Override
    public void onSendError(WebSocket websocket, WebSocketException cause, WebSocketFrame frame) throws Exception {
        Log.i(TAG, cause.getClass().getSimpleName() + ": " + cause.getMessage());
    }

    @Override
    public void onUnexpectedError(WebSocket websocket, WebSocketException cause) throws Exception {
        Log.i(TAG, cause.getClass().getSimpleName() + ": " + cause.getMessage());
    }

    @Override
    public void handleCallbackError(WebSocket websocket, Throwable cause) throws Exception {
        Log.i(TAG, cause.getClass().getSimpleName() + ": " + cause.getMessage());
    }

}
