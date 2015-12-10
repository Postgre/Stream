package it.polito.mad.stream.websocket;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketListener;

import java.net.InetAddress;

/**
 * Created by luigi on 02/12/15.
 */
public abstract class AbstractWSClient implements WebSocketClient {

    protected WebSocketListener mProtocol;
    protected ClientStateListener mStateListener;


}
