/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package practica2;

import java.io.*;
import java.net.*;
import java.util.*;
import com.mpatric.mp3agic.*;


/**
 *
 * @author Alexa
 */

public class Servidor {
    public static void main(String[] args) throws Exception {
        DatagramSocket socket = new DatagramSocket(4000);
        Scanner sc = new Scanner(System.in);

        String carpetaMusica = System.getProperty("user.home") + "/Desktop/";
        File carpeta = new File(carpetaMusica);
        File[] mp3s = carpeta.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp3"));

        if (mp3s == null || mp3s.length == 0) {
            System.out.println("No hay canciones MP3 en la carpeta de música.");
            socket.close();
            return;
        }

        System.out.println("Servidor iniciado. Esperando mensajes del cliente...");

        while (true) {
            try {
                byte[] buf = new byte[1024];
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                socket.receive(dp);
                String msg = new String(dp.getData(), 0, dp.getLength()).trim();

                InetAddress ipCliente = dp.getAddress();
                int puertoCliente = dp.getPort();

                // Si el cliente quiere la lista de canciones
                if (msg.equalsIgnoreCase("LISTA")) {
                    StringBuilder lista = new StringBuilder();
                    for (int i = 0; i < mp3s.length; i++) {
                        lista.append((i + 1) + ":" + mp3s[i].getName() + ";");
                    }
                    byte[] listaBytes = lista.toString().getBytes();
                    DatagramPacket dpLista = new DatagramPacket(listaBytes, listaBytes.length, ipCliente, puertoCliente);
                    socket.send(dpLista);
                    continue;
                }

                // Si el cliente desea salir, cerrar servidor
                if (msg.equalsIgnoreCase("SALIR")) {
                    System.out.println("Cliente solicitó cerrar la conexión. Cerrando servidor...");
                    socket.close();
                    return;
                }

                // Procesar envío de canción
                String[] partes = msg.split(":");
                if (partes.length != 3) {
                    System.out.println("Mensaje inválido recibido: " + msg);
                    continue;
                }

                int idx = Integer.parseInt(partes[0]) - 1;
                int TAM_PAQUETE = Integer.parseInt(partes[1]);
                int VENTANA = Integer.parseInt(partes[2]);

                if (TAM_PAQUETE > 58000) {
                    System.out.println("Tamaño máximo seguro para UDP es 58000 bytes. Ajustando...");
                    TAM_PAQUETE = 58000;
                }

                if (idx < 0 || idx >= mp3s.length) {
                    System.out.println("Índice inválido recibido: " + (idx + 1));
                    continue;
                }

                File archivo = mp3s[idx];

                try {
                    Mp3File mp3 = new Mp3File(archivo);
                    System.out.println("\n-------------------------------------------------------------");
                    System.out.println("------------- Información del MP3 recibido: ----------------");
                    System.out.println(" -- Archivo: " + archivo.getName());
                    System.out.println(" -- Duración: " + (mp3.getLengthInSeconds() / 60) + " min " +
                                       (mp3.getLengthInSeconds() % 60) + " seg");
                    if (mp3.hasId3v2Tag()) {
                        ID3v2 tag = mp3.getId3v2Tag();
                        System.out.println(" -- Artista: " + tag.getArtist());
                        System.out.println(" -- Álbum: " + tag.getAlbum());
                        System.out.println(" -- Año: " + tag.getYear());
                        System.out.println(" -- Título: " + tag.getTitle());
                    }
                    System.out.println("-------------------------------------------------------------");
                    System.out.println("-------------------------------------------------------------");
                } catch (Exception e) {
                    System.out.println("No se pudieron leer metadatos del MP3.");
                }

                FileInputStream fis = new FileInputStream(archivo);
                int totalPaquetes = (int) Math.ceil((double) archivo.length() / TAM_PAQUETE);
                byte[] buffer = new byte[TAM_PAQUETE];

                int base = 0;
                int siguiente = 0;
                socket.setSoTimeout(5000);

                System.out.println("\nIniciando envío...");
                System.out.println("Total paquetes: " + totalPaquetes);
                System.out.println("Ventana: " + VENTANA + " | Tamaño paquete: " + TAM_PAQUETE + " bytes\n");

                while (base < totalPaquetes) {
                    while (siguiente < base + VENTANA && siguiente < totalPaquetes) {
                        int leidos = fis.read(buffer);
                        if (leidos == -1) break;

                        byte[] datos = Arrays.copyOf(buffer, leidos);
                        Paquete paquete = new Paquete(
                                siguiente, totalPaquetes, siguiente == totalPaquetes - 1,
                                archivo.getName(), archivo.length(), datos);

                        System.out.println("\n-------------------------------------------------------------");
                        System.out.println("------------ METAINFORMACIÓN DEL PAQUETE #" + (paquete.numero + 1)+" ---------------");
                        System.out.println(" -- Archivo: " + paquete.nombre);
                        System.out.println(" -- Total paquetes: " + paquete.total);
                        System.out.println(" -- Nº de paquete: " + (paquete.numero + 1));
                        System.out.println(" -- Tamaño datos: " + paquete.tamDatos + " bytes");
                        System.out.println(" -- Tamaño total archivo: " + paquete.tamArchivo + " bytes");

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(baos);
                        oos.writeObject(paquete);
                        byte[] bytesPaquete = baos.toByteArray();

                        DatagramPacket dpEnvio = new DatagramPacket(bytesPaquete, bytesPaquete.length, ipCliente, puertoCliente);
                        socket.send(dpEnvio);
                        System.out.println("Enviado paquete #" + (paquete.numero + 1));
                        siguiente++;
                    }

                    try {
                        byte[] ack = new byte[20];
                        DatagramPacket pAck = new DatagramPacket(ack, ack.length);
                        socket.receive(pAck);
                        String msgAck = new String(pAck.getData(), 0, pAck.getLength()).trim();
                        int ackNum = Integer.parseInt(msgAck.replace("ACK", "").trim());
                        System.out.println("ACK recibido: " + (ackNum + 1));
                        base = ackNum + 1;
                        System.out.println("-------------------------------------------------------------");
                        System.out.println("-------------------------------------------------------------");
                    } catch (SocketTimeoutException e) {
                        System.out.println("Timeout, reenviando desde paquete #" + (base + 1) + "...");
                        fis.getChannel().position((long) base * TAM_PAQUETE);
                        siguiente = base;
                    }
                }

                fis.close();
                System.out.println("\nTransmisión completa de " + archivo.getName());
            } catch (SocketTimeoutException e) {
                continue;
            }
        }
    }
}