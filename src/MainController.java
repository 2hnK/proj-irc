import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;

public class MainController {
    private static final int SERVER_PORT = 9910;

    public static void main(String[] args) {
        ChannelManager channelManager = new ChannelManager();
        
        try (ServerSocket serverSocket = new ServerSocket(SERVER_PORT)) {
            System.out.println("IRC Server started on port " + SERVER_PORT);
            
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket.getInetAddress());
                    
                    startClientThread(clientSocket, channelManager);
                } catch (IOException e) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Server startup failed: " + e.getMessage());
        }
    }

    private static void startClientThread(Socket clientSocket, ChannelManager channelManager) {
        try {
            ChannelReceiver receiver = new ChannelReceiver(clientSocket, channelManager);
            receiver.start();
        } catch (Exception e) {
            System.err.println("Error starting client thread: " + e.getMessage());
            closeClientSocket(clientSocket);
        }
    }

    private static void closeClientSocket(Socket clientSocket) {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing client socket: " + e.getMessage());
        }
    }
}