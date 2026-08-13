import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class IrcServer {

    private final int port;
    private final ChannelManager channelManager;
    private volatile boolean isRunning;
    private volatile ServerSocket serverSocket;

    public IrcServer(int port) {
        this.port = port;
        this.channelManager = new ChannelManager();
    }

    public static void main(String[] args) {
        IrcServer server = new IrcServer(9910);

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop)); // JVM 종료 시 서버 종료 처리

        server.start();
    }

    public void start() {
        isRunning = true;

        try {
            serverSocket = new ServerSocket(port);

            System.out.println("IRC Server started on port " + port);

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();

                    System.out.println("New client connected: " + clientSocket.getInetAddress());

                    startClientThread(clientSocket);
                } catch (IOException e) {
                    if (isRunning) {
                        System.err.println("Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Server startup failed: " + e.getMessage());
        } finally {
            stop();
        }
    }

    public void stop() {
        isRunning = false;

        if (serverSocket == null || serverSocket.isClosed()) {
            return;
        }

        try {
            serverSocket.close();
            System.out.println("IRC Server stopped.");
        } catch (IOException e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }

    }

    private void startClientThread(Socket clientSocket) {
        try {
            ChannelReceiver receiver = new ChannelReceiver(clientSocket, channelManager);
            receiver.start();
        } catch (Exception e) {
            System.err.println("Error starting client thread: " + e.getMessage());
            closeClientSocket(clientSocket);
        }
    }

    private void closeClientSocket(Socket clientSocket) {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing client socket: " + e.getMessage());
        }
    }
}