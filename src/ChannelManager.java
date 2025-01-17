import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ChannelManager {
    private final Map<String, Set<DataOutputStream>> channels;  // channel name, outputStream
    private final Map<String, DataOutputStream> users;  // nickname, outputStream

    ChannelManager() {
        channels = Collections.synchronizedMap(new HashMap<>());
        users = Collections.synchronizedMap(new HashMap<>());
    }

    public synchronized void joinChannel(String channelName, DataOutputStream outputStream) {
        channels.computeIfAbsent(channelName, k -> new HashSet<>()).add(outputStream);
    }

    public synchronized void leaveChannel(String channelName, DataOutputStream outputStream) {
        if (channelName == null || !channels.containsKey(channelName)) {
            return;
        }

        Set<DataOutputStream> channelUsers = channels.get(channelName);
        channelUsers.remove(outputStream);

        if (channelUsers.isEmpty()) {
            channels.remove(channelName);
        }
    }

    public synchronized Set<String> getChannels() {
        return new HashSet<>(channels.keySet());
    }

    public synchronized void broadcast(String channelName, String message) {
        if (!channels.containsKey(channelName)) {
            return;
        }

        channels.get(channelName).forEach(outputStream -> {
            try {
                outputStream.writeUTF(message);
            } catch (IOException e) {
                System.err.println("Error broadcasting message: " + e.getMessage());
            }
        });
    }

    public synchronized void registerNickname(String nickname, DataOutputStream outputStream) {
        users.put(nickname, outputStream);
    }

    public synchronized void unregisterNickname(String nickname) {
        users.remove(nickname);
    }

    public synchronized boolean isNicknameAvailable(String nickname) {
        return !users.containsKey(nickname);
    }

    public synchronized void whisper(String from, String to, String content, ChannelReceiver receiver) {
        if (!users.containsKey(from)) {
            receiver.sendMessageToClient("Error: User '" + from + "' not found");
            return;
        }

        if (!users.containsKey(to)) {
            receiver.sendMessageToClient("Error: User '" + to + "' not found");
            return;
        }

        try {
            users.get(to).writeUTF(String.format("[Whisper from %s] %s", from, content));
            receiver.sendMessageToClient("Message sent to " + to);
        } catch (IOException e) {
            receiver.sendMessageToClient("Error: Failed to send message - " + e.getMessage());
        }
    }
}

