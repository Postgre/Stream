package it.polito.mad.stream.websocket;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketException;

import java.net.InetAddress;

/**
 * Created by luigi on 02/12/15.
 */
public interface WebSocketClient {

    WebSocket getSocket();

    void connectToWebSocketServer(String serverIP, int port, int timeout);

    void closeConnection();

}
