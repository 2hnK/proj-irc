import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientReceiver extends Thread {
    private final DataInputStream inputStream;
    private final Socket socket;

    public ClientReceiver(Socket socket) throws IOException {
        this.socket = socket;
        this.inputStream = new DataInputStream(socket.getInputStream());
    }

    @Override
    public void run() {
        try {
            while (!socket.isClosed()) {
                String message = inputStream.readUTF();
                processMessage(message);
            }
        } catch (IOException e) {
            System.out.println("Disconnected from server");
        } finally {
            closeResources();
        }
    }

    private void processMessage(String message) {
        String[] command = message.split(" ", 2);
        if ("PONG".equalsIgnoreCase(command[0]) && command.length > 1) {
            long ping = calculatePing(command[1]);
            System.out.println("Ping: " + ping + "ms");
        } else {
            System.out.println(message);
        }
    }

    private long calculatePing(String serverTime) {
        return System.currentTimeMillis() - Long.parseLong(serverTime);
    }

    private void closeResources() {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing resources: " + e.getMessage());
        }
    }
}
