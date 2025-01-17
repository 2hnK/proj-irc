import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class ClientSender extends Thread {
    private final DataOutputStream outputStream;
    private final Socket socket;

    public ClientSender(Socket socket) throws IOException {
        this.socket = socket;
        this.outputStream = new DataOutputStream(socket.getOutputStream());
    }

    @Override
    public void run() {
        try (Scanner scanner = new Scanner(System.in)) {
            while (!socket.isClosed()) {
                String message = scanner.nextLine();
                sendMessage(message);

                if ("QUIT".equalsIgnoreCase(message)) {
                    break;
                }
            }
        } finally {
            closeResources();
        }
    }

    private void sendMessage(String message) {
        try {
            outputStream.writeUTF(message);
        } catch (IOException e) {
            System.err.println("Error sending message: " + e.getMessage());
        }
    }

    private void closeResources() {
        try {
            if (outputStream != null) {
                outputStream.close();
            }
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing resources: " + e.getMessage());
        }
    }
}
