package it.polito.mad.stream.websocket;

/**
 * Created by luigi on 02/12/15.
 */
public interface ClientStateListener {

    void onConnectionEstablished();
    void onConnectionClosed();
    void onServerUnreachable();

}
