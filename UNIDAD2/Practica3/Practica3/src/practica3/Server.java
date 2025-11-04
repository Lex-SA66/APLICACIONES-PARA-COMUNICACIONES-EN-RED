/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package practica3;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 *
 * @author Alexa
 */

public class Server extends JFrame {
    private final JTextArea logArea;
    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

    private static final int SERVER_PORT = 8000;
    private static final int MULTICAST_PORT = 8001;
    private static final String MULTICAST_ADDRESS = "230.0.0.0";

    private DatagramSocket unicastSocket;
    private MulticastSocket multicastSocket;
    private InetAddress multicastGroup;
    private final Map<String, LinkedHashSet<String>> rooms = new HashMap<>();

    public Server() {
        setTitle("Servidor de Chat - Log de Actividad");
        setSize(620, 420);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(new EmptyBorder(10, 10, 10, 10));
        add(scrollPane);

        rooms.put("General", new LinkedHashSet<>());

        new Thread(this::startServer).start();
    }

    private void logEvent(String event) {
        SwingUtilities.invokeLater(() -> logArea.append("[" + sdf.format(new Date()) + "] " + event + "\n"));
    }

    private void startServer() {
        try {
            multicastGroup = InetAddress.getByName(MULTICAST_ADDRESS);
            unicastSocket = new DatagramSocket(SERVER_PORT);
            multicastSocket = new MulticastSocket();

            logEvent("Servidor iniciado en puerto " + SERVER_PORT);
            logEvent("Difusión multicast en " + MULTICAST_ADDRESS + ":" + MULTICAST_PORT);

            byte[] buffer = new byte[65535];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                unicastSocket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength(), "UTF-8").trim();
                processMessage(message);
            }

        } catch (IOException e) {
            logEvent("ERROR CRÍTICO: " + e.getMessage());
        }
    }

    private void processMessage(String message) {
        try {
            if (message.startsWith("<join>")) {
                String room = getTag(message, "sala");
                String user = getTag(message, "usr");
                if (room.isEmpty() || user.isEmpty()) return;

                rooms.putIfAbsent(room, new LinkedHashSet<>());
                rooms.get(room).add(user);

                logEvent(user + " se ha unido a '" + room + "'. Estado actual de las salas:\n" + generateRoomStatusString());

                multicast("<sys_join><sala>" + room + "</sala><usr>" + user + "</usr></sys_join>");
                broadcastUserList(room);
                multicastRoomsList();

            } else if (message.startsWith("<leave>")) {
                String room = getTag(message, "sala");
                String user = getTag(message, "usr");
                if (room.isEmpty() || user.isEmpty()) return;

                Set<String> usersInRoom = rooms.get(room);
                if (usersInRoom != null && usersInRoom.remove(user)) {
                    logEvent(user + " ha salido de '" + room + "'. Estado actual de las salas:\n" + generateRoomStatusString());
                    multicast("<sys_leave><sala>" + room + "</sala><usr>" + user + "</usr></sys_leave>");
                    broadcastUserList(room);
                }

            } else if (message.startsWith("<salir>")) {
                String user = getTag(message, "usr");
                if (user.isEmpty()) return;
                
                final boolean[] userWasRemoved = {false}; // Usamos un array para poder modificarlo desde el lambda
                rooms.forEach((r, users) -> {
                    if (users.remove(user)) {
                        userWasRemoved[0] = true;
                        logEvent(user + " se ha desconectado de '" + r + "'.");
                        multicast("<sys_leave><sala>" + r + "</sala><usr>" + user + "</usr></sys_leave>");
                        broadcastUserList(r);
                    }
                });
                
                if(userWasRemoved[0]){
                    logEvent("Estado de salas tras desconexión de "+user+":\n" + generateRoomStatusString());
                }


            } else if (message.startsWith("<msg>") || message.startsWith("<privado>") || message.startsWith("<audio_chunk>")) {
                multicast(message);

            } else if (message.startsWith("<createRoom>")) {
                String room = getTag(message, "sala");
                if (room != null && !room.isEmpty() && !rooms.containsKey(room)) {
                    rooms.put(room, new LinkedHashSet<>());
                    logEvent("Sala creada: '" + room + "'");
                    multicastRoomsList();
                }

            } else if (message.startsWith("<listRooms>")) {
                multicastRoomsList();
            }
        } catch (Exception e) {
            logEvent("Error procesando mensaje: " + e.getMessage());
        }
    }

    private String generateRoomStatusString() {
        StringBuilder sb = new StringBuilder();
        rooms.forEach((roomName, users) -> {
            sb.append("  - Sala '").append(roomName).append("': ");
            if (users.isEmpty()) {
                sb.append("(vacía)\n");
            } else {
                sb.append("[").append(String.join(", ", users)).append("]\n");
            }
        });
        return sb.toString();
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

    private void broadcastUserList(String room) {
        Set<String> users = rooms.get(room);
        String userList = users == null ? "" : String.join(",", users);
        multicast("<listaUsrs><sala>" + room + "</sala><usrs>" + userList + "</usrs></listaUsrs>");
    }

    private void multicastRoomsList() {
        String roomsStr = String.join(",", rooms.keySet());
        multicast("<listaSalas><salas>" + roomsStr + "</salas></listaSalas>");
        logEvent("Enviando lista de salas actualizada: [" + roomsStr + "]");
    }

    private void multicast(String msg) {
        try {
            byte[] data = msg.getBytes("UTF-8");
            DatagramPacket p = new DatagramPacket(data, data.length, multicastGroup, MULTICAST_PORT);
            multicastSocket.send(p);
        } catch (IOException e) {
            logEvent("Error al enviar multicast: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Server().setVisible(true));
    }
}