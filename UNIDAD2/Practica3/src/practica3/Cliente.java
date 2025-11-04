/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package practica3;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.sound.sampled.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 *
 * @author Alexa
 */

public class Cliente extends JFrame {

    private String username;
    private JTabbedPane chatTabs;

    private MulticastSocket multicastSocket;
    private DatagramSocket unicastSocket;
    private InetAddress group, serverAddress;

    private static final int SERVER_PORT = 8000;
    private static final int MULTICAST_PORT = 8001;
    private static final String MULTICAST_ADDRESS = "230.0.0.0";
    private static final int CHUNK_SIZE = 768;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final Map<String, DefaultListModel<Message>> roomMessageModels = new HashMap<>();
    private final Map<String, DefaultListModel<String>> roomUserModels = new HashMap<>();
    private final Map<String, JList<String>> roomUserLists = new HashMap<>();
    private final Map<String, AudioReassemblyBuffer> audioBuffers = new HashMap<>();
    private final Map<String, JList<Message>> roomChatLists = new HashMap<>();

    private DefaultListModel<String> roomListModel;
    private JTextArea messageInput;
    private JButton recordButton;

    private boolean isRecording = false;
    private TargetDataLine audioLine;
    private ByteArrayOutputStream audioOutStream;

    public Cliente() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        this.username = JOptionPane.showInputDialog(this, "Ingresa tu nombre de usuario:", "Bienvenido al Chat", JOptionPane.PLAIN_MESSAGE);
        if (this.username == null || this.username.trim().isEmpty()) System.exit(0);

        setTitle("Chat - " + username);
        setSize(900, 620);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        initUI();
        connectToServer();
        startAudioBufferCleanupTask();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(mainPanel);

        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setPreferredSize(new Dimension(180, 0));
        roomListModel = new DefaultListModel<>();
        JList<String> roomList = new JList<>(roomListModel);
        roomList.setBorder(new TitledBorder("Salas Disponibles"));
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomList.setFixedCellHeight(30);
        roomList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selectedRoom = roomList.getSelectedValue();
                    if (selectedRoom != null) joinRoom(selectedRoom);
                }
            }
        });
        JScrollPane roomScroll = new JScrollPane(roomList);
        JPanel roomButtons = new JPanel(new GridLayout(1, 2, 5, 5));
        JButton btnRefreshRooms = new JButton("🔄");
        btnRefreshRooms.setToolTipText("Actualizar lista de salas");
        btnRefreshRooms.addActionListener(e -> sendToServer("<listRooms></listRooms>"));
        JButton btnCreateRoom = new JButton("✚");
        btnCreateRoom.setToolTipText("Crear nueva sala");
        btnCreateRoom.addActionListener(e -> {
            String r = JOptionPane.showInputDialog(Cliente.this, "Nombre de la nueva sala:");
            if (r != null && !r.trim().isEmpty()) {
                sendToServer("<createRoom><sala>" + r.trim() + "</sala></createRoom>");
            }
        });
        roomButtons.add(btnRefreshRooms);
        roomButtons.add(btnCreateRoom);
        leftPanel.add(roomScroll, BorderLayout.CENTER);
        leftPanel.add(roomButtons, BorderLayout.SOUTH);
        mainPanel.add(leftPanel, BorderLayout.WEST);

        chatTabs = new JTabbedPane();
        chatTabs.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = chatTabs.getUI().tabForCoordinate(chatTabs, e.getX(), e.getY());
                    if (index > 0) { // No permitir cerrar "General"
                        String roomToLeave = chatTabs.getTitleAt(index);
                        leaveRoom(roomToLeave);
                    }
                }
            }
        });
        mainPanel.add(chatTabs, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(10, 5));
        messageInput = new JTextArea(3, 20);
        messageInput.setLineWrap(true);
        messageInput.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_ENTER) {
                    e.consume();
                    sendChatMessage();
                }
            }
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        recordButton = new JButton("🎙️ Grabar");
        recordButton.addActionListener(e -> toggleRecording());
        JButton emojiButton = new JButton("😊");
        emojiButton.addActionListener(e -> openEmojiPicker());
        JButton sendButton = new JButton("Enviar");
        sendButton.addActionListener(e -> sendChatMessage());
        JButton btnPrivate = new JButton("→ Privado");
        btnPrivate.addActionListener(e -> sendPrivateMessage());
        actions.add(recordButton);
        actions.add(emojiButton);
        actions.add(btnPrivate);
        actions.add(sendButton);

        bottomPanel.add(new JScrollPane(messageInput), BorderLayout.CENTER);
        bottomPanel.add(actions, BorderLayout.SOUTH);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                sendToServer("<salir><usr>" + username + "</usr></salir>");
                if (multicastSocket != null) multicastSocket.close();
                if (unicastSocket != null) unicastSocket.close();
                executor.shutdownNow();
            }
        });
        chatTabs.addTab("General", createRoomPanel("General"));
        setVisible(true);
    }

    private JPanel createRoomPanel(String roomName) {
        DefaultListModel<Message> messageModel = new DefaultListModel<>();
        DefaultListModel<String> userModel = new DefaultListModel<>();
        roomMessageModels.put(roomName, messageModel);
        roomUserModels.put(roomName, userModel);

        JPanel roomPanel = new JPanel(new BorderLayout(10, 10));
        JList<Message> chatList = new JList<>(messageModel);
        chatList.setCellRenderer(new MessageCellRenderer());
        roomChatLists.put(roomName, chatList);

        JScrollPane chatScrollPane = new JScrollPane(chatList);
        chatScrollPane.setBorder(new TitledBorder("Chat"));
        JList<String> userList = new JList<>(userModel);
        roomUserLists.put(roomName, userList);
        JScrollPane userScrollPane = new JScrollPane(userList);
        userScrollPane.setBorder(new TitledBorder("Usuarios"));
        userScrollPane.setPreferredSize(new Dimension(150, 0));
        roomPanel.add(chatScrollPane, BorderLayout.CENTER);
        roomPanel.add(userScrollPane, BorderLayout.EAST);
        return roomPanel;
    }

    private void joinRoom(String roomName) {
        for (int i = 0; i < chatTabs.getTabCount(); i++) {
            if (chatTabs.getTitleAt(i).equals(roomName)) {
                chatTabs.setSelectedIndex(i);
                return;
            }
        }
        JPanel newRoomPanel = createRoomPanel(roomName);
        chatTabs.addTab(roomName, newRoomPanel);
        chatTabs.setSelectedComponent(newRoomPanel);
        sendToServer("<join><sala>" + roomName + "</sala><usr>" + username + "</usr></join>");
    }

    private void leaveRoom(String roomName) {
        int tabIndex = -1;
        for (int i = 0; i < chatTabs.getTabCount(); i++) {
            if (chatTabs.getTitleAt(i).equals(roomName)) {
                tabIndex = i;
                break;
            }
        }
        if (tabIndex != -1) {
            sendToServer("<leave><sala>" + roomName + "</sala><usr>" + username + "</usr></leave>");
            chatTabs.remove(tabIndex);
            roomMessageModels.remove(roomName);
            roomUserModels.remove(roomName);
            roomUserLists.remove(roomName);
            roomChatLists.remove(roomName);
        }
    }

    private String getCurrentRoom() {
        int selectedIndex = chatTabs.getSelectedIndex();
        return (selectedIndex != -1) ? chatTabs.getTitleAt(selectedIndex) : null;
    }

    private JList<String> getCurrentUserList() {
        String room = getCurrentRoom();
        return (room != null) ? roomUserLists.get(room) : null;
    }

    private void connectToServer() {
        executor.submit(() -> {
            try {
                group = InetAddress.getByName(MULTICAST_ADDRESS);
                serverAddress = InetAddress.getByName("localhost");
                unicastSocket = new DatagramSocket();
                multicastSocket = new MulticastSocket(MULTICAST_PORT);
                multicastSocket.joinGroup(group);
                sendToServer("<join><sala>General</sala><usr>" + username + "</usr></join>");
                listenForMessages();
            } catch (IOException e) {
            }
        });
    }

    private void startAudioBufferCleanupTask() {
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                audioBuffers.entrySet().removeIf(entry -> entry.getValue().isExpired());
            }
        }, 10000, 10000);
    }

    private void listenForMessages() {
        byte[] buffer = new byte[65535];
        while (!multicastSocket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                multicastSocket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength(), "UTF-8").trim();
                SwingUtilities.invokeLater(() -> processIncomingMessage(msg));
            } catch (IOException e) {
            }
        }
    }

    private void processIncomingMessage(String message) {
        if (message.startsWith("<audio_chunk>")) {
            handleAudioChunk(message);
            return;
        }

        String sala = getTag(message, "sala");
        if (sala.isEmpty() && !message.startsWith("<listaSalas>")) return;

        if (message.startsWith("<msg>")) {
            String user = getTag(message, "usr");
            addMessageToRoom(sala, new Message(user, getTag(message, "content"), user.equals(username)));
        } else if (message.startsWith("<listaUsrs>")) {
            DefaultListModel<String> userModel = roomUserModels.get(sala);
            if (userModel == null) return;
            String usersStr = getTag(message, "usrs");
            userModel.clear();
            if (!usersStr.isEmpty()) {
                for (String u : usersStr.split(",")) userModel.addElement(u);
            }
        } else if (message.startsWith("<listaSalas>")) {
            String salas = getTag(message, "salas");
            roomListModel.clear();
            if (!salas.isEmpty()) {
                for (String s : salas.split(",")) roomListModel.addElement(s);
            }
        } else if (message.startsWith("<privado>")) {
            String from = getTag(message, "usr1");
            String to = getTag(message, "usr2");
            if (from.equals(username) || to.equals(username)) {
                String content = getTag(message, "content");
                addMessageToRoom(sala, new Message(from, content, from.equals(username)));
            }
        } else if (message.startsWith("<sys_join>")) {
            addMessageToRoom(sala, new Message(getTag(message, "usr") + " se ha unido al chat."));
        } else if (message.startsWith("<sys_leave>")) {
            addMessageToRoom(sala, new Message(getTag(message, "usr") + " ha salido del chat."));
        }
    }

    private void handleAudioChunk(String message) {
        String audioId = getTag(message, "id");
        String sala = getTag(message, "sala");
        if (audioId.isEmpty() || sala.isEmpty()) return;

        try {
            int chunkNum = Integer.parseInt(getTag(message, "num"));
            int totalChunks = Integer.parseInt(getTag(message, "total"));
            byte[] chunkData = Base64.getDecoder().decode(getTag(message, "data"));

            AudioReassemblyBuffer buffer = audioBuffers.computeIfAbsent(audioId, id -> new AudioReassemblyBuffer(totalChunks));
            buffer.addChunk(chunkNum, chunkData);

            if (buffer.isComplete()) {
                byte[] fullAudioData = buffer.reassemble();
                audioBuffers.remove(audioId);

                String user = getTag(message, "usr");
                boolean isMyOwnAudio = user.equals(username);

                String audioMessageText = isMyOwnAudio ? "Audio enviado" : "Mensaje de voz de " + user;

                if (!isMyOwnAudio) {
                    playAudio(fullAudioData);
                }

                addMessageToRoom(sala, new Message(user, audioMessageText, isMyOwnAudio, fullAudioData));
            }
        } catch (Exception e) {
        }
    }

    private void addMessageToRoom(String roomName, Message message) {
        DefaultListModel<Message> model = roomMessageModels.get(roomName);
        if (model != null) {
            model.addElement(message);
            JList<Message> chatList = roomChatLists.get(roomName);
            if (chatList != null) {
                int lastIndex = model.getSize() - 1;
                if (lastIndex >= 0) {
                    chatList.ensureIndexIsVisible(lastIndex);
                }
            }
        }
    }

    private void sendChatMessage() {
        String content = messageInput.getText().trim();
        if (content.isEmpty()) return;
        String currentRoom = getCurrentRoom();
        if (currentRoom == null) return;

        String msg = "<msg><sala>" + currentRoom + "</sala><usr>" + username + "</usr><content>" + content + "</content></msg>";
        sendToServer(msg);
        messageInput.setText("");
    }

    private void sendPrivateMessage() {
        JList<String> currentUserList = getCurrentUserList();
        if (currentUserList == null) return;

        String target = currentUserList.getSelectedValue();
        String currentRoom = getCurrentRoom();
        if (target == null || target.equals(username)) {
            JOptionPane.showMessageDialog(this, "Selecciona otro usuario para enviar un mensaje privado.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String content = messageInput.getText().trim();
        if (content.isEmpty()) {
            JOptionPane.showMessageDialog(this, "El mensaje no puede estar vacío.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String msg = "<privado><sala>" + currentRoom + "</sala><usr1>" + username + "</usr1><usr2>" + target + "</usr2><content>" + content + "</content></privado>";
        sendToServer(msg);
        messageInput.setText("");
    }

    private void toggleRecording() {
        if (isRecording) {
            stopRecordingAndSend();
        } else {
            startRecording();
        }
    }

    private void stopRecordingAndSend() {
        isRecording = false;
        if (audioLine != null) {
            audioLine.stop();
            audioLine.close();
        }
        SwingUtilities.invokeLater(() -> {
            recordButton.setText("🎙️ Grabar");
            recordButton.setForeground(null);
        });

        byte[] audioData = (audioOutStream != null) ? audioOutStream.toByteArray() : new byte[0];
        if (audioData.length == 0) return;

        String currentRoom = getCurrentRoom();
        if (currentRoom == null) return;

        String audioId = UUID.randomUUID().toString();
        int totalChunks = (int) Math.ceil((double) audioData.length / CHUNK_SIZE);

        executor.submit(() -> {
            for (int i = 0; i < totalChunks; i++) {
                int offset = i * CHUNK_SIZE;
                int length = Math.min(CHUNK_SIZE, audioData.length - offset);
                byte[] chunk = new byte[length];
                System.arraycopy(audioData, offset, chunk, 0, length);
                String b64Chunk = Base64.getEncoder().encodeToString(chunk);

                String xml = String.format(
                    "<audio_chunk><id>%s</id><num>%d</num><total>%d</total><sala>%s</sala><usr>%s</usr><data>%s</data></audio_chunk>",
                    audioId, i, totalChunks, currentRoom, username, b64Chunk
                );
                sendToServer(xml);

                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private void sendToServer(String msg) {
        try {
            byte[] data = msg.getBytes("UTF-8");
            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, SERVER_PORT);
            unicastSocket.send(packet);
        } catch (IOException e) {
        }
    }

    private String getTag(String xml, String tag) {
        try {
            String startTag = "<" + tag + ">";
            String endTag = "</" + tag + ">";
            int a = xml.indexOf(startTag);
            if (a == -1) return "";
            int b = xml.indexOf(endTag, a);
            if (b == -1) return "";
            return xml.substring(a + startTag.length(), b);
        } catch (Exception e) {
            return "";
        }
    }

    private void openEmojiPicker() {
        JDialog emojiDialog = new JDialog(this, "Selecciona un Emoji", true);
        emojiDialog.setLayout(new GridLayout(0, 5, 5, 5));
        String[] emojis = {"😊", "😂", "❤️", "👍", "😭", "🤔", "🔥", "🎉", "🙏", "😮", "😎", "😴", "👋", "👏", "💯", "✅", "❌", "❓", "❗", "🥰"};
        Font emojiFont = new Font("Segoe UI Emoji", Font.PLAIN, 24);
        for (String emoji : emojis) {
            JButton button = new JButton(emoji);
            button.setFont(emojiFont);
            button.addActionListener(e -> {
                messageInput.append(emoji);
                emojiDialog.dispose();
            });
            emojiDialog.add(button);
        }
        emojiDialog.pack();
        emojiDialog.setLocationRelativeTo(this);
        emojiDialog.setVisible(true);
    }

    private AudioFormat getMyAudioFormat() {
        float sampleRate = 8000.0F;
        int sampleSizeInBits = 16;
        int channels = 2;
        boolean signed = true;
        boolean bigEndian = false;
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }

    private void startRecording() {
        executor.submit(() -> {
            try {
                AudioFormat format = getMyAudioFormat();
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

                if (!AudioSystem.isLineSupported(info)) {
                    showError("No se encontró un micrófono compatible. No se puede grabar.");
                    stopRecordingCleanup();
                    return;
                }

                audioLine = (TargetDataLine) AudioSystem.getLine(info);
                audioLine.open(format);
                audioLine.start();
                audioOutStream = new ByteArrayOutputStream();
                isRecording = true;
                SwingUtilities.invokeLater(() -> {
                    recordButton.setText("🛑 Detener");
                    recordButton.setForeground(Color.RED);
                });
                byte[] buffer = new byte[1024];
                while (isRecording) {
                    int read = audioLine.read(buffer, 0, buffer.length);
                    if (read > 0) audioOutStream.write(buffer, 0, read);
                }
            } catch (Exception e) {
                showError("Error al iniciar la grabación: " + e.getMessage());
                stopRecordingCleanup();
            }
        });
    }

    private void stopRecordingCleanup() {
        isRecording = false;
        SwingUtilities.invokeLater(() -> {
            recordButton.setText("🎙️ Grabar");
            recordButton.setForeground(null);
        });
    }

    private void showError(String message) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, message, "Error de Audio", JOptionPane.ERROR_MESSAGE));
    }

    private void playAudio(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return;
        }

        executor.submit(() -> {
            SourceDataLine speakers = null;
            try {
                AudioFormat format = getMyAudioFormat();
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

                if (!AudioSystem.isLineSupported(info)) return;

                speakers = (SourceDataLine) AudioSystem.getLine(info);

                speakers.open(format);
                speakers.start();
                speakers.write(audioData, 0, audioData.length);
                speakers.drain();
            } catch (Exception e) {
            } finally {
                if (speakers != null) {
                    speakers.close();
                }
            }
        });
    }

    enum MessageType {TEXT, AUDIO, SYSTEM}
    static class Message {
        MessageType type; String username, content; boolean isFromMe; byte[] audioData;
        Message(String u, String c, boolean me) { this.type = MessageType.TEXT; this.username = u; this.content = c; this.isFromMe = me; }
        Message(String u, String c, boolean me, byte[] audio) { this.type = MessageType.AUDIO; this.username = u; this.content = c; this.isFromMe = me; this.audioData = audio; }
        Message(String c) { this.type = MessageType.SYSTEM; this.content = c; this.isFromMe = false; this.username = ""; }
    }

    static class AudioReassemblyBuffer {
        private final int totalChunks;
        private final long creationTime;
        private static final long TIMEOUT = 10000;
        private final Map<Integer, byte[]> chunks = new TreeMap<>();

        AudioReassemblyBuffer(int totalChunks) {
            this.totalChunks = totalChunks;
            this.creationTime = System.currentTimeMillis();
        }
        void addChunk(int chunkNum, byte[] data) { synchronized (chunks) { chunks.put(chunkNum, data); } }
        boolean isComplete() { synchronized (chunks) { return chunks.size() == totalChunks; } }
        boolean isExpired() { return (System.currentTimeMillis() - creationTime) > TIMEOUT; }
        byte[] reassemble() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            for (byte[] chunk : chunks.values()) {
                try { outputStream.write(chunk); } catch (IOException e) { /* Ignorar */ }
            }
            return outputStream.toByteArray();
        }
    }

    class MessageCellRenderer extends JPanel implements ListCellRenderer<Message> {
        private final JLabel systemLabel = new JLabel();
        private final JTextArea messageArea = new JTextArea();
        private final JLabel usernameLabel = new JLabel();
        private final JPanel bubble = new JPanel(new BorderLayout(5, 2));

        MessageCellRenderer() {
            super(new BorderLayout());
            setBorder(new EmptyBorder(5, 10, 5, 10));
            bubble.setBorder(new EmptyBorder(5, 10, 5, 10));
            messageArea.setWrapStyleWord(true);
            messageArea.setLineWrap(true);
            messageArea.setEditable(false);
            messageArea.setOpaque(false);
            usernameLabel.setFont(getFont().deriveFont(Font.BOLD));
            systemLabel.setHorizontalAlignment(SwingConstants.CENTER);
            systemLabel.setForeground(Color.GRAY);
            systemLabel.setFont(getFont().deriveFont(Font.ITALIC));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Message> list, Message msg, int idx, boolean isSelected, boolean hasFocus) {
            removeAll();
            setBackground(list.getBackground());

            if (msg.type == MessageType.SYSTEM) {
                systemLabel.setText(msg.content);
                add(systemLabel, BorderLayout.CENTER);
                return this;
            }

            usernameLabel.setText(msg.username);
            bubble.removeAll();
            bubble.add(usernameLabel, BorderLayout.NORTH);

            JPanel contentPanel = new JPanel(new BorderLayout());
            contentPanel.setBackground(Color.WHITE);
            contentPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1),
                new EmptyBorder(5, 8, 5, 8)
            ));

            messageArea.setText(msg.content);
            contentPanel.add(messageArea, BorderLayout.CENTER);

            bubble.add(contentPanel, BorderLayout.CENTER);

            if (msg.isFromMe) {
                bubble.setBackground(new Color(211, 229, 252));
                add(bubble, BorderLayout.EAST);
            } else {
                bubble.setBackground(new Color(241, 241, 241));
                add(bubble, BorderLayout.WEST);
            }

            return this;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Cliente::new);
    }
}