import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

public class ChannelReceiver extends Thread {
    Socket socket;
    DataInputStream inputStream;
    DataOutputStream outputStream;
    ChannelManager channelManager;
    String currentChannel = "null";
    String nickname;

    // 생성자
    ChannelReceiver(Socket socket, ChannelManager channelManager) {
        this.socket = socket;
        this.channelManager = channelManager;
        nickname = "unknown";

        try {
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }

        setupInitialConnection();
    }

    // 클라이언트와 서버 간의 초기 연결을 설정하고 환영 메시지를 전송
    private void setupInitialConnection() {
        sendMessageToClient("=========================================");
        sendMessageToClient(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        sendMessageToClient("Welcome to the IRC Server!");
        displayUserInfo();
        sendMessageToClient("");
        displayCommandList();
    }

    // 클라이언트로부터 메시지를 수신 및 처리
    @Override
    public void run() {
        try {
            while (!socket.isClosed()) {
                String msg = inputStream.readUTF();
                processIncomingMessage(msg);
            }
        } catch (Exception e) {
            handleClientConnectionError();
        } finally {
            cleanupResources();
        }
    }

    // 수신된 메시지 처리
    private void processIncomingMessage(String msg) {
        if (msg == null || msg.trim().isEmpty()) {
            sendMessageToClient("Invalid command");
            return;
        }

        try {
            String[] command = msg.split(" ", 2);
            
            switch (command[0].toUpperCase()) {
                case "JOIN":
                    processJoinChannel(command);
                    break;
                case "NICK":
                    updateNickname(command);
                    break;
                case "PRIVMSG":
                    sendPrivateMessage(command);
                    break;
                case "HELP":
                    displayCommandList();
                    break;
                case "LIST":
                    displayChannelList();
                    break;
                case "PART":
                    leaveCurrentChannel();
                    break;
                case "USER":
                    displayUserInfo();
                    break;
                case "PING":
                    processPingRequest();
                    break;
                case "QUIT":
                    processQuitRequest();
                    break;
                default:
                    broadcastMessage(msg);
                    break;
            }
        } catch (Exception e) {
            handleClientConnectionError();
        }
    }

    // 사용 가능한 IRC 명령어 목록을 클라이언트에게 표시
    private void displayCommandList() {
        sendMessageToClient("===============<Command List>===============");
        sendMessageToClient("0. HELP: Display available commands");
        sendMessageToClient("1. LIST: Display channel list");
        sendMessageToClient("2. JOIN <channel>: Join a channel");
        sendMessageToClient("3. PART: Leave current channel");
        sendMessageToClient("4. QUIT: Disconnect from server");
        sendMessageToClient("5. PING: Test connection");
        sendMessageToClient("6. NICK <nickname>: Set nickname");
        sendMessageToClient("7. PRIVMSG <user> <message>: Send private message");
        sendMessageToClient("8. USER: Display user information");
        sendMessageToClient("=========================================");
    }

    // 채널 참여 명령어 처리
    private void processJoinChannel(String[] command) {
        try {
            if (nickname.equals("unknown")) {
                sendMessageToClient("Please set your nickname before joining a channel. (Command: NICK <nickname>)");
                return;
            }

            if (command.length != 2 || command[1].trim().isEmpty()) {
                sendMessageToClient("Usage: JOIN <channel>");
                return;
            }

            String newChannel = command[1].trim();
            if (newChannel.equals(currentChannel)) {
                sendMessageToClient("Already in channel '" + currentChannel + "'");
                return;
            }

            if (currentChannel != null) {
                channelManager.leaveChannel(currentChannel, outputStream);
            }
            currentChannel = newChannel;
            channelManager.joinChannel(currentChannel, outputStream);
            sendMessageToClient("Joined channel: '" + currentChannel + "'");
            
        } catch (Exception e) {
            sendMessageToClient("Error joining channel: " + e.getMessage());
        }
    }

    // 현재 존재하는 채널 목록을 표시
    private void displayChannelList() {
        try {
            Set<String> channels = channelManager.getChannels();
            sendMessageToClient("<Channel List>");
            
            if (!channels.isEmpty()) {
                int i = 1;
                for (String ch : channels) {
                    sendMessageToClient(i++ + ". " + ch);
                }
            } else {
                sendMessageToClient("No channels available.");
                sendMessageToClient("Create a channel using the 'JOIN <channel>' command.");
            }
        } catch (Exception e) {
            sendMessageToClient("Error retrieving channel list: " + e.getMessage());
        }
    }

    // 사용자 닉네임 설정 처리
    private void updateNickname(String[] command) {
        try {
            if (command.length != 2 || command[1].trim().isEmpty()) {
                sendMessageToClient("Usage: NICK <nickname>");
                return;
            }

            String newNickname = command[1].trim();
            
            if (!validateNickname(newNickname)) {
                sendMessageToClient("Invalid nickname (alphanumeric, 2-12 characters)");
                return;
            }

            if (channelManager.isNicknameAvailable(newNickname)) {
                channelManager.unregisterNickname(nickname);
                channelManager.registerNickname(newNickname, outputStream);
                nickname = newNickname;
                sendMessageToClient("Nickname set to '" + nickname + "'");
            } else {
                sendMessageToClient("Nickname '" + newNickname + "' is already in use");
            }
        } catch (Exception e) {
            sendMessageToClient("Error setting nickname: " + e.getMessage());
        }
    }

    // 현재 채널에서 나가기 처리
    private void leaveCurrentChannel() {
        if (currentChannel != null) {
            channelManager.leaveChannel(currentChannel, outputStream);
            sendMessageToClient("Left channel: " + currentChannel);
            currentChannel = null;
        } else {
            sendMessageToClient("You are not in any channel.");
        }
    }

    // 서버와의 연결 상태 확인을 위한 ping 요청 처리
    private void processPingRequest() {
        try {
            long serverTime = System.currentTimeMillis();
            outputStream.writeUTF("PONG " + serverTime);
        } catch (IOException e) {
            sendMessageToClient("Error processing ping command: " + e.getMessage());
        }
    }

    // 개인 메시지 전송 처리
    private void sendPrivateMessage(String[] command) {
        try {
            if (nickname.equals("unknown")) {
                sendMessageToClient("Please set your nickname before sending private messages");
                return;
            }

            if (command.length != 2) {
                sendMessageToClient("Usage: PRIVMSG <user> <message>");
                return;
            }

            String[] whisper = command[1].split(" ", 2);
            if (whisper.length < 2) {
                sendMessageToClient("Usage: PRIVMSG <user> <message>");
                return;
            }

            channelManager.whisper(nickname, whisper[0], whisper[1], this);
        } catch (Exception e) {
            sendMessageToClient("Error sending private message: " + e.getMessage());
        }
    }

    // 서버 연결 종료 처리
    private void processQuitRequest() {
        try {
            sendMessageToClient("See you later!");
            channelManager.leaveChannel(currentChannel, outputStream);
            channelManager.unregisterNickname(nickname);
        } catch (Exception e) {
            System.err.println("Error during quit process: " + e.getMessage());
        }
    }

    // 일반 채팅 메시지 처리
    private void broadcastMessage(String msg) {
        if (currentChannel != null) {
            String message = "[" + nickname + "] " + msg;
            channelManager.broadcast(currentChannel, message);
        } else {
            sendMessageToClient("You are not in any channel. Please join a channel first.");
        }
    }

    // 클라이언트에게 메시지 전송
    public void sendMessageToClient(String msg) {
        try {
            outputStream.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 현재 사용자 정보(닉네임, 채널) 표시
    private void displayUserInfo() {
        sendMessageToClient("* Current nickname: " + nickname);
        sendMessageToClient("* Current channel: " + currentChannel);
    }

    // 닉네임 유효성 검사 (영숫자, 한글, 2-12자)
    private boolean validateNickname(String nickname) {
        return nickname.matches("^[a-zA-Z0-9가-힣]{2,12}$");
    }

    // 클라이언트 연결 오류 처리
    private void handleClientConnectionError() {
        try {
            System.out.println("Client connection error: " + socket.getInetAddress());
            channelManager.unregisterNickname(nickname);
            if (currentChannel != null) {
                channelManager.leaveChannel(currentChannel, outputStream);
            }
            cleanupResources();
        } catch (Exception e) {
            System.err.println("Error while cleaning up resources: " + e.getMessage());
        }
    }

    // 연결 관련 리소스 정리
    private void cleanupResources() {
        try {
            if (outputStream != null) outputStream.close();
            if (inputStream != null) inputStream.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Error while closing resources: " + e.getMessage());
        }
    }
}
