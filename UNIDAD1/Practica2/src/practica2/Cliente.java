/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package practica2;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import javazoom.jl.player.Player;
import com.mpatric.mp3agic.*;

/**
 *
 * @author Alexa
 */
public class Cliente {

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        DatagramSocket socket = new DatagramSocket();
        InetAddress ipServidor = InetAddress.getByName("localhost");

        while (true) {
            String mensajeLista = "LISTA";
            byte[] envio = mensajeLista.getBytes();
            DatagramPacket dpLista = new DatagramPacket(envio, envio.length, ipServidor, 4000);
            socket.send(dpLista);

            byte[] bufferLista = new byte[4096]; // tamaño suficiente para la lista
            DatagramPacket dpRecLista = new DatagramPacket(bufferLista, bufferLista.length);
            socket.receive(dpRecLista);

            String listaStr = new String(dpRecLista.getData(), 0, dpRecLista.getLength()).trim();
            String[] canciones = listaStr.split(";");

            System.out.println("Canciones disponibles:");
            for (int i = 0; i < canciones.length; i++) {
                if (!canciones[i].isEmpty()) {
                    String[] partes = canciones[i].split(":", 2);
                    System.out.println("[" + partes[0] + "] " + partes[1]);
                }
            }

            System.out.print("\nElige el número de la canción: ");
            int idx = sc.nextInt();
            if (idx < 1 || idx > canciones.length) {
                System.out.println("Selección inválida.");
                continue;
            }

            System.out.print("Tamaño de paquete (bytes, ej. 60000): ");
            int TAM_PAQUETE = sc.nextInt();

            System.out.print("Tamaño de ventana (ej. 5): ");
            int VENTANA = sc.nextInt();

            // Enviar índice de canción, tamaño y ventana al servidor
            String msg = idx + ":" + TAM_PAQUETE + ":" + VENTANA;
            byte[] envioConfig = msg.getBytes();
            DatagramPacket dp = new DatagramPacket(envioConfig, envioConfig.length, ipServidor, 4000);
            socket.send(dp);

            FileOutputStream fos = null;
            boolean fin = false;
            int esperado = 0;
            int total = -1;
            String nombreArchivo = "";

            System.out.println("\nEsperando transmisión del servidor...\n");
            System.out.println("Tamaño de paquete: " + TAM_PAQUETE + " bytes");
            System.out.println("Tamaño de ventana: " + VENTANA + "\n");

            while (!fin) {
                byte[] buffer = new byte[TAM_PAQUETE + 1024]; // buffer mayor que paquete por seguridad
                DatagramPacket dpRec = new DatagramPacket(buffer, buffer.length);
                socket.receive(dpRec);

                ByteArrayInputStream bais = new ByteArrayInputStream(dpRec.getData());
                ObjectInputStream ois = new ObjectInputStream(bais);
                Paquete paquete = (Paquete) ois.readObject();

                System.out.println("\n-------------------------------------------------------------");
                System.out.println("--------------- METAINFORMACIÓN RECIBIDA ------------------");
                System.out.println(" -- Archivo: " + paquete.nombre);
                System.out.println(" -- Total paquetes: " + paquete.total);
                System.out.println(" -- No. de paquete: " + (paquete.numero + 1));
                System.out.println(" -- Tamaño datos: " + paquete.tamDatos + " bytes");
                System.out.println(" -- Tamaño total archivo: " + paquete.tamArchivo + " bytes");

                if (fos == null) {
                    nombreArchivo = paquete.nombre;
                    fos = new FileOutputStream("recibido_" + nombreArchivo);
                    total = paquete.total;
                    System.out.println("Iniciando recepción de: " + nombreArchivo);
                }

                if (paquete.numero == esperado) {
                    fos.write(paquete.datos);
                    double progreso = ((double) (paquete.numero + 1) / total) * 100;
                    System.out.printf("Progreso: %.2f%%\n", progreso);
                    esperado++;
                    System.out.println("-------------------------------------------------------------");
                    System.out.println("-------------------------------------------------------------");
                }

                String msgAck = "ACK " + (esperado - 1);
                byte[] ack = msgAck.getBytes();
                DatagramPacket pAck = new DatagramPacket(ack, ack.length, dpRec.getAddress(), dpRec.getPort());
                socket.send(pAck);

                if (paquete.fin) {
                    fin = true;
                }
            }

            if (fos != null) {
                fos.close();
            }

            System.out.println("\nArchivo recibido correctamente: " + nombreArchivo);

            try {
                Mp3File mp3 = new Mp3File("recibido_" + nombreArchivo);
                System.out.println("\n-------------------------------------------------------------");
                System.out.println("------------- Información del MP3 recibido: ----------------");
                System.out.println(" -- Archivo: " + nombreArchivo);
                System.out.println(" -- Duración: " + (mp3.getLengthInSeconds() / 60) + " min "
                        + (mp3.getLengthInSeconds() % 60) + " seg");

                if (mp3.hasId3v2Tag()) {
                    ID3v2 tag = mp3.getId3v2Tag();
                    System.out.println(" -- Artista: " + tag.getArtist());
                    System.out.println(" -- Álbum: " + tag.getAlbum());
                    System.out.println(" -- Año: " + tag.getYear());
                    System.out.println(" -- Título: " + tag.getTitle());
                    System.out.println("-------------------------------------------------------------");
                    System.out.println("-------------------------------------------------------------");

                } else {
                    System.out.println("No hay metadatos ID3v2.");
                }
            } catch (Exception e) {
                System.out.println("No se pudieron leer metadatos del MP3 recibido.");
            }

            // Reproducir el archivo recibido
            System.out.println("\nReproduciendo...\n");
            try (FileInputStream fis = new FileInputStream("recibido_" + nombreArchivo)) {
                Player player = new Player(fis);
                player.play();
            }

            System.out.println("Reproducción finalizada.");

            System.out.print("\n¿Deseas escuchar otra canción? (s/n): ");
            String resp = sc.next();
            if (!resp.equalsIgnoreCase("s")) {
                String msgSalir = "SALIR";
                byte[] salida = msgSalir.getBytes();
                DatagramPacket dpSalir = new DatagramPacket(salida, salida.length, ipServidor, 4000);
                socket.send(dpSalir);
                break;
            }
        }

        socket.close();
    }
}
