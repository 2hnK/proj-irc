import java.io.IOException;
import java.net.Socket;

public class Client {
    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int SERVER_PORT = 9910;

    public static void main(String[] args) {
        try {
            Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            System.out.println("Connected to server on port " + SERVER_PORT);

            startClient(socket);
        } catch (IOException e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }

    private static void startClient(Socket socket) {
        try {
            ClientSender sender = new ClientSender(socket);
            ClientReceiver receiver = new ClientReceiver(socket);

            sender.start();
            receiver.start();
        } catch (Exception e) {
            System.err.println("Error starting client: " + e.getMessage());
            closeSocket(socket);
        }
    }

    private static void closeSocket(Socket socket) {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing socket: " + e.getMessage());
        }
    }
}
