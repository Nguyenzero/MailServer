package network;

import java.net.*;
import java.util.function.Consumer;

public class MailClient {
    private static final int SERVER_PORT = 9876;
    private static final String SERVER_IP = "10.39.110.214";

    // Persistent notification listener
    private static DatagramSocket notifySocket;
    private static Thread listenerThread;
    private static volatile boolean listening;

    public static synchronized void startListener(Consumer<String> handler) throws Exception {
        if (notifySocket == null || notifySocket.isClosed()) {
            notifySocket = new DatagramSocket(); // bind to any free local port
        }
        if (listenerThread != null && listenerThread.isAlive()) return;

        listening = true;
        listenerThread = new Thread(() -> {
            byte[] buf = new byte[4096];
            while (listening) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    notifySocket.receive(p);
                    String msg = new String(p.getData(), 0, p.getLength());
                    if (handler != null) handler.accept(msg);
                } catch (SocketException se) {
                    break; // socket closed
                } catch (Exception ignore) { }
            }
        }, "mail-notify-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public static int getListenerPort() {
        return (notifySocket != null && !notifySocket.isClosed()) ? notifySocket.getLocalPort() : -1;
    }

    public static synchronized void stopListener() {
        listening = false;
        if (notifySocket != null && !notifySocket.isClosed()) {
            notifySocket.close();
        }
        notifySocket = null;
        listenerThread = null;
    }

    public static String sendCommand(String command) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        byte[] sendData = command.getBytes();
        InetAddress IPAddress = InetAddress.getByName(SERVER_IP);

        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, SERVER_PORT);
        socket.send(sendPacket);

        byte[] receiveData = new byte[4096];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        socket.receive(receivePacket);

        String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
        socket.close();
        return response;
    }
}