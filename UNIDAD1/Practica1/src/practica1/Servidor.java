/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package practica1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author alex
 */

public class Servidor {

    private final int puerto;
    private Producto[] misProductos;
    private ServerSocket servidorSocket;

    public Servidor(int puerto) {
        this.puerto = puerto;
        generarProductos();
    }

    public void iniciar() {
        // Inicia el servidor y espera conexiones de clientes.
        try {
            servidorSocket = new ServerSocket(this.puerto);
            System.out.println("Servidor iniciado y en línea en el puerto: " + this.puerto);
            System.out.println("Esperando clientes...");

            while (true) {
                Socket clienteSocket = servidorSocket.accept();
                System.out.println("Cliente conectado desde: " + clienteSocket.getInetAddress());
                new ManejadorCliente(clienteSocket).start();
            }
        } catch (IOException e) {
            Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, "Error al iniciar el servidor", e);
        }
    }

    private void generarProductos() {
        // Genera una lista de productos iniciales para la tienda.
        this.misProductos = new Producto[20];
        int[] id = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
        String[] nombre = {"Alcohol Etílico", "Cloro", "Pino", "Jabon en barra", "Jabon líquido", "Arroz Blanco", "Frijoles Refritos", "Aceite de Cocina", "Tortillas de Maíz", "Huevo (12 piezas)", "Leche Entera", "Queso Panela", "Jamón de Pavo", "Pan de Caja", "Refresco de Cola", "Papas Fritas", "Galletas Marías", "Café Soluble", "Azúcar Estándar", "Cerillos"};
        double[] precio = {60.50, 50.0, 90.0, 27.90, 27.95, 25.00, 22.50, 45.00, 18.00, 38.00, 24.00, 55.00, 60.00, 42.00, 17.00, 16.00, 15.50, 85.00, 28.00, 10.00};
        String[] descripcion = {"Alcohol etilico desnaturalizado 70%, 1 Litro", "Blanqueador y desinfectante de ropa y superficies, 5.8 litros", "Limpiador liquido multiusos con aroma a pino, 5.1 litros", "Jabon de barra para manos, 90g", "Jabon liquido para manos con dosificador, 255 mililitros", "Bolsa de 1kg de arroz blanco tipo Morelos, grano largo.", "Lata de frijoles negros refritos, listos para servir. 440g.", "Botella de 1 litro de aceite vegetal comestible.", "Paquete de 1kg de tortillas de maíz, frescas del día.", "Cartón con 12 huevos blancos de gallina.", "Caja de 1 litro de leche entera ultrapasteurizada.", "Paquete de 400g de queso panela fresco.", "Paquete de 250g de jamón de pavo en rebanadas.", "Pan de barra blanco grande, ideal para sándwiches.", "Botella de 600ml de refresco sabor cola.", "Bolsa de 42g de papas fritas con sal.", "Paquete de galletas tipo María, ideal para postres. 170g.", "Frasco de 100g de café soluble instantáneo.", "Bolsa de 1kg de azúcar estándar de caña.", "Caja con 50 cerillos de madera."};
        String[] archivosImagen = {"etilico.jpg", "cloro.jpg", "pino.jpg", "jabon_barra.jpg", "jabon_liquido.jpg", "arroz.jpg", "frijoles.jpg", "aceite.jpg", "tortillas.jpg", "huevo.jpg", "leche.jpg", "queso_panela.jpg", "jamon.jpg", "pan_barra.jpg", "refresco.jpg", "papas_fritas.jpg", "galletas.jpg", "cafe_soluble.jpg", "azucar.jpg", "cerillos.jpg"};

        for (int i = 0; i < misProductos.length; i++) {
            int existencias = (int) ((Math.random() * (100 - 20)) + 20);
            byte[] datosImagen = null;
            try {
                String rutaRecurso = "/imagenes_productos/" + archivosImagen[i];
                InputStream is = getClass().getResourceAsStream(rutaRecurso);
                if (is != null) {
                    datosImagen = is.readAllBytes();
                    is.close();
                } else {
                    System.err.println("Recurso no encontrado: " + rutaRecurso);
                }
            } catch (IOException e) {
                System.err.println("Error al leer el recurso de imagen: " + archivosImagen[i] + " -> " + e.getMessage());
            }
            misProductos[i] = new Producto(id[i], nombre[i], precio[i], existencias, descripcion[i], false, datosImagen);
        }
        System.out.println("Productos cargados y listos en el servidor.");
    }

    private void actualizarExistencias(int id, int cantidadComprada) {
        // Actualiza el inventario de un producto después de una compra.
        for (Producto pInventario : misProductos) {
            if (pInventario.getID() == id) {
                pInventario.setExistencias(pInventario.getExistencias() - cantidadComprada);
                break;
            }
        }
    }

    public static void main(String[] args) {
        // Método principal para iniciar el servidor.
        Servidor miServidor = new Servidor(9999);
        miServidor.iniciar();
    }

    private class ManejadorCliente extends Thread {
        private final Socket clienteSocket;

        public ManejadorCliente(Socket socket) {
            this.clienteSocket = socket;
        }

        @Override
        public void run() {
            // Maneja la comunicación con un cliente conectado.
            try (
                    PrintWriter pw = new PrintWriter(clienteSocket.getOutputStream(), true);
                    BufferedReader br = new BufferedReader(new InputStreamReader(clienteSocket.getInputStream()))
            ) {
                String accion = br.readLine();
                if ("GET_CATALOG".equals(accion)) {
                    System.out.println("Enviando catálogo al cliente...");
                    for (Producto p : misProductos) {
                        String imagenBase64 = "none";
                        if (p.getImagen() != null) {
                            imagenBase64 = Base64.getEncoder().encodeToString(p.getImagen());
                        }
                        String productoString = p.getID() + "|" + p.getNombre() + "|" + p.getPrecio() + "|" + p.getExistencias() + "|" + p.getDescripcion() + "|" + imagenBase64;
                        pw.println(productoString);
                    }
                    pw.println("CATALOG_END");
                } else if ("BUY_PRODUCTS".equals(accion)) {
                    System.out.println("\nProcesando una nueva compra...");
                    ArrayList<Producto> productosComprados = new ArrayList<>();
                    double precioTotal = 0;
                    String lineaCompra;
                    while ((lineaCompra = br.readLine()) != null && !lineaCompra.equals("BUY_END")) {
                        String[] partes = lineaCompra.split(",");
                        int id = Integer.parseInt(partes[0]);
                        int cantidad = Integer.parseInt(partes[1]);
                        for (Producto pOriginal : misProductos) {
                            if (pOriginal.getID() == id) {
                                actualizarExistencias(id, cantidad);
                                precioTotal += cantidad * pOriginal.getPrecio();
                                Producto pTicket = new Producto(id, pOriginal.getNombre(), pOriginal.getPrecio(), 0, "", false, null);
                                pTicket.setCantidad(cantidad);
                                productosComprados.add(pTicket);
                                break;
                            }
                        }
                    }
                    pw.println("TOTAL|" + precioTotal);
                    for (Producto p : productosComprados) {
                        pw.println("ITEM|" + p.getNombre() + "|" + p.getCantidad() + "|" + p.getPrecio());
                    }
                    pw.println("TICKET_END");
                    System.out.println("Inventario actualizado. Ticket enviado al cliente.");
                }
            } catch (IOException e) {
                System.out.println("Conexión con el cliente perdida: " + e.getMessage());
            }
        }
    }
}