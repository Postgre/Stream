package it.polito.mad.stream;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by luigi on 09/12/15.
 */
public class FramesReaderSocketThread implements Runnable {

    public interface Callback {
        void onChunkRead(byte[] buffer, int length);
        void onError(Exception e);
    }

    private static int BUFFER_SIZE = 8*1024;

    private ServerSocket mServerSocket;
    private Socket mSocket;
    private Thread mWorkerThread;

    private Callback mCallback;
    private InputStream mInputStream;

    public FramesReaderSocketThread(){

    }

    public void start() {
        if (mWorkerThread == null) {
            mWorkerThread = new Thread(this);
            mWorkerThread.start();
        }
    }

    public void kill() {
        try{
            if (mInputStream != null) mInputStream.close();
            mWorkerThread.join();
        }
        catch(Exception ignore){}
        mWorkerThread = null;
    }

    public static void setBufferSize(int bufferSize){
        BUFFER_SIZE = bufferSize;
    }

    public void setCallback(Callback callback){
        mCallback = callback;
    }

    //private AtomicBoolean mInterruptReq = new AtomicBoolean(false);

    @Override
    public synchronized void run() {
        try{
            mServerSocket = new ServerSocket(8000);
            mSocket = mServerSocket.accept();

            mInputStream = mSocket.getInputStream();
            int bytesRead;
            byte[] buffer = new byte[BUFFER_SIZE];
            while (mSocket.isConnected() && (bytesRead = mInputStream.read(buffer)) != -1) {
                Log.d("READER THREAD", "read " + bytesRead + " bytes");
                if (mCallback != null){
                    mCallback.onChunkRead(buffer, bytesRead);
                }
            }
        }
        catch(IOException e){

        }
        finally {
            try{
                if (mInputStream != null) mInputStream.close();
                if (mSocket != null) mSocket.close();
                if (mServerSocket != null) mServerSocket.close();
            }
            catch(IOException ignore){}
        }
        Log.d("READER THREAD", "Exit!!");
    }

}