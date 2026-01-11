package practica5;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

public class ServerWget {

    private static final int PUERTO = 8000;
    private static final File ROOT = new File("."); 

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  SERVER APACHE - PUERTO " + PUERTO);
        System.out.println("==================================================");
        
        try (ServerSocket server = new ServerSocket(PUERTO)) {
            while (true) {
                Socket cliente = server.accept();
                new Thread(() -> manejarCliente(cliente)).start();
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static void manejarCliente(Socket socket) {
        try (BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
             OutputStream out = socket.getOutputStream()) {
            
            byte[] buffer = new byte[8192];
            int leidos = in.read(buffer);
            if(leidos <= 0) return;

            String peticion = new String(buffer, 0, leidos);
            StringTokenizer st = new StringTokenizer(peticion);
            
            if(!st.hasMoreTokens()) return;
            String metodo = st.nextToken();
            String rutaRaw = st.nextToken();
            String ruta = URLDecoder.decode(rutaRaw, "UTF-8");

            if(ruta.equals("/") || ruta.endsWith("/")) {
                ruta += "index.html";
            }
            
            File archivo = new File(ROOT, ruta.startsWith("/") ? ruta.substring(1) : ruta);

            byte[] contenido;
            String estado;
            String mime = "application/octet-stream";

            if (archivo.exists() && !archivo.isDirectory()) {
                contenido = Files.readAllBytes(archivo.toPath());
                estado = "200 OK";
                
                String n = archivo.getName().toLowerCase();
                if(n.endsWith(".html")) mime = "text/html";
                else if(n.endsWith(".txt") || n.endsWith(".java") || n.endsWith(".c")) mime = "text/plain";
                else if(n.endsWith(".jpg")) mime = "image/jpeg";
                else if(n.endsWith(".png")) mime = "image/png";
                else if(n.endsWith(".pdf")) mime = "application/pdf";
                
            } else {
                String msg = "<html><h1>404 Not Found</h1></html>";
                contenido = msg.getBytes();
                estado = "404 Not Found";
                mime = "text/html";
            }

            SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            sdf.setTimeZone(TimeZone.getDefault());

            StringBuilder headers = new StringBuilder();
            headers.append("HTTP/1.1 ").append(estado).append("\r\n");
            headers.append("Date: ").append(sdf.format(new Date())).append("\r\n");
            headers.append("Server: Apache/Simulado\r\n");
            headers.append("Content-Length: ").append(contenido.length).append("\r\n");
            headers.append("Content-Type: ").append(mime).append("\r\n");
            headers.append("Connection: close\r\n\r\n");

            out.write(headers.toString().getBytes());
            out.write(contenido);
            out.flush();

        } catch (Exception e) {
        } finally {
            try { socket.close(); } catch(Exception e) {}
        }
    }
}