/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package practica1;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;

/**
 *
 * @author alex
 */

public class Cliente extends JFrame {

    private static final String HOST = "127.0.0.1";
    private static final int PUERTO = 9999;
    private JButton agregarCarritoBtn, verCarritoBtn;
    private JLabel imagenLabel, nombreLabel, idLabel, precioLabel, existenciaLabel, descripcionLabel;
    private JTextField cantidadTextField;
    private JList<String> catalogoList;
    private DefaultListModel<String> listModel;
    private Producto[] catalogoProductos;
    private Producto productoSeleccionado;
    private final CarritoDialog carritoDialog;
    private final TicketDialog ticketDialog;

    public Cliente() {
        super("Tienda LexStation");
        this.carritoDialog = new CarritoDialog(this);
        this.ticketDialog = new TicketDialog(this);
        initUI();
        cargarCatalogoAutomaticamente();
    }

    private void initUI() {
        // Inicializa los componentes de la interfaz de usuario.
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(850, 600);
        setLocationRelativeTo(null);
        JSplitPane splitPane = new JSplitPane();
        splitPane.setDividerLocation(250);
        add(splitPane);
        JPanel panelIzquierdo = new JPanel(new BorderLayout(10, 10));
        panelIzquierdo.setBorder(new EmptyBorder(10, 10, 10, 10));
        JLabel tituloCatalogo = new JLabel("Catálogo de Productos");
        tituloCatalogo.setFont(new Font("Segoe UI", Font.BOLD, 16));
        panelIzquierdo.add(tituloCatalogo, BorderLayout.NORTH);
        listModel = new DefaultListModel<>();
        catalogoList = new JList<>(listModel);
        catalogoList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        catalogoList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panelIzquierdo.add(new JScrollPane(catalogoList), BorderLayout.CENTER);
        JPanel panelBotonesIzquierdo = new JPanel(new GridLayout(1, 1, 5, 5));
        verCarritoBtn = new JButton("Ver Carrito de Compras");
        panelBotonesIzquierdo.add(verCarritoBtn);
        panelIzquierdo.add(panelBotonesIzquierdo, BorderLayout.SOUTH);
        splitPane.setLeftComponent(panelIzquierdo);
        JPanel panelDerecho = new JPanel(new BorderLayout(10, 10));
        panelDerecho.setBorder(new EmptyBorder(5, 12, 12, 5));
        imagenLabel = new JLabel("Cargando catálogo...", SwingConstants.CENTER);
        imagenLabel.setFont(new Font("Segoe UI", Font.ITALIC, 18));
        JScrollPane imageScrollPane = new JScrollPane(imagenLabel);
        imageScrollPane.setPreferredSize(new Dimension(300, 306));
        imageScrollPane.setBorder(BorderFactory.createEtchedBorder());
        panelDerecho.add(imageScrollPane, BorderLayout.NORTH);
        JPanel panelDetalles = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        idLabel = new JLabel("ID: -");
        nombreLabel = new JLabel("Nombre: -");
        nombreLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        precioLabel = new JLabel("Precio: $0.00");
        existenciaLabel = new JLabel("Disponibles: 0");
        descripcionLabel = new JLabel("Descripción: -");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panelDetalles.add(nombreLabel, gbc);
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        panelDetalles.add(idLabel, gbc);
        gbc.gridy = 2;
        panelDetalles.add(precioLabel, gbc);
        gbc.gridy = 3;
        panelDetalles.add(existenciaLabel, gbc);
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panelDetalles.add(descripcionLabel, gbc);
        panelDerecho.add(panelDetalles, BorderLayout.CENTER);
        JPanel panelAccionAgregar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panelAccionAgregar.add(new JLabel("Cantidad:"));
        cantidadTextField = new JTextField(5);
        agregarCarritoBtn = new JButton("Agregar al Carrito");
        panelAccionAgregar.add(cantidadTextField);
        panelAccionAgregar.add(agregarCarritoBtn);
        panelDerecho.add(panelAccionAgregar, BorderLayout.SOUTH);
        splitPane.setRightComponent(panelDerecho);
        verCarritoBtn.setEnabled(false);
        agregarCarritoBtn.setEnabled(false);
        verCarritoBtn.addActionListener(e -> carritoDialog.setVisible(true));
        agregarCarritoBtn.addActionListener(e -> anadirProductoAlCarrito());
        catalogoList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && catalogoList.getSelectedIndex() != -1) {
                productoSeleccionado = catalogoProductos[catalogoList.getSelectedIndex()];
                mostrarDetallesProducto(productoSeleccionado);
            }
        });
    }

    private void cargarCatalogoAutomaticamente() {
        // Carga el catálogo de productos desde el servidor al iniciar.
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            ArrayList<Producto> productosTemp = new ArrayList<>();
            try (Socket socket = new Socket(HOST, PUERTO);
                 PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                pw.println("GET_CATALOG");
                String linea;
                while ((linea = br.readLine()) != null && !linea.equals("CATALOG_END")) {
                    String[] partes = linea.split("\\|");
                    int id = Integer.parseInt(partes[0]);
                    String nombre = partes[1];
                    double precio = Double.parseDouble(partes[2]);
                    int existencias = Integer.parseInt(partes[3]);
                    String descripcion = partes[4];
                    String imagenBase64 = partes[5];

                    byte[] datosImagen = null;
                    if (!"none".equals(imagenBase64)) {
                        datosImagen = Base64.getDecoder().decode(imagenBase64);
                    }
                    Producto p = new Producto(id, nombre, precio, existencias, descripcion, false, datosImagen);
                    productosTemp.add(p);
                }
            }
            catalogoProductos = productosTemp.toArray(new Producto[0]);
            if (catalogoProductos.length > 0) {
                listModel.clear();
                for (Producto p : catalogoProductos) {
                    listModel.addElement(p.getNombre());
                }
                verCarritoBtn.setEnabled(true);
                agregarCarritoBtn.setEnabled(true);
                catalogoList.setSelectedIndex(0);
            } else {
                imagenLabel.setText("No se pudo cargar el catálogo.");
            }
        } catch (Exception e) {
            imagenLabel.setText("Error de conexión.");
            JOptionPane.showMessageDialog(this, "No se pudo conectar al servidor. Asegúrate de que esté en línea.\nError: " + e.getMessage(), "Error de Conexión", JOptionPane.ERROR_MESSAGE);
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    private void realizarCompra(ArrayList<Producto> productosFinales) {
        // Procesa la compra de los productos en el carrito, enviando la información al servidor.
        try (Socket socket = new Socket(HOST, PUERTO);
             PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            pw.println("BUY_PRODUCTS");
            for (Producto p : productosFinales) {
                pw.println(p.getID() + "," + p.getCantidad());
            }
            pw.println("BUY_END");
            double total = 0;
            ArrayList<Producto> productosTicket = new ArrayList<>();
            String lineaRespuesta;
            while ((lineaRespuesta = br.readLine()) != null && !lineaRespuesta.equals("TICKET_END")) {
                String[] partes = lineaRespuesta.split("\\|");
                String tipo = partes[0];
                if ("TOTAL".equals(tipo)) {
                    total = Double.parseDouble(partes[1]);
                } else if ("ITEM".equals(tipo)) {
                    String nombre = partes[1];
                    int cantidad = Integer.parseInt(partes[2]);
                    double precio = Double.parseDouble(partes[3]);
                    Producto p = new Producto(0, nombre, precio, 0, "", false, null);
                    p.setCantidad(cantidad);
                    productosTicket.add(p);
                }
            }
            Ticket ticket = new Ticket(productosTicket, total);
            ticketDialog.mostrar(ticket);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error al procesar la compra: " + e.getMessage(), "Error de Compra", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void mostrarDetallesProducto(Producto producto) {
        // Muestra los detalles de un producto seleccionado en la interfaz.
        idLabel.setText("ID: " + producto.getID());
        nombreLabel.setText(producto.getNombre());
        precioLabel.setText("Precio: " + formatearMoneda(producto.getPrecio()));
        existenciaLabel.setText("Disponibles: " + producto.getExistencias());
        descripcionLabel.setText("<html><p style='width:300px;'>" + producto.getDescripcion() + "</p></html>");
        cantidadTextField.setText("1");
        byte[] imgData = producto.getImagen();
        if (imgData != null && imgData.length > 0) {
            ImageIcon originalIcon = new ImageIcon(imgData);
            ImageIcon imagenCuadrada = crearImagenCuadrada(originalIcon, 300);
            imagenLabel.setIcon(imagenCuadrada);
            imagenLabel.setText("");
        } else {
            imagenLabel.setIcon(null);
            imagenLabel.setText("Imagen no disponible");
        }
    }

    private ImageIcon crearImagenCuadrada(ImageIcon originalIcon, int targetSize) {
        // Escala una imagen para que se ajuste a un tamaño cuadrado.
        Image originalImage = originalIcon.getImage();
        int originalWidth = originalIcon.getIconWidth();
        int originalHeight = originalIcon.getIconHeight();
        BufferedImage canvas = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, targetSize, targetSize);
        int newWidth, newHeight;
        if (originalWidth > originalHeight) {
            newWidth = targetSize;
            newHeight = (targetSize * originalHeight) / originalWidth;
        } else {
            newHeight = targetSize;
            newWidth = (targetSize * originalWidth) / originalHeight;
        }
        int x = (targetSize - newWidth) / 2;
        int y = (targetSize - newHeight) / 2;
        g2.drawImage(originalImage, x, y, newWidth, newHeight, null);
        g2.dispose();
        return new ImageIcon(canvas);
    }

    private void anadirProductoAlCarrito() {
        // Añade una cantidad específica del producto seleccionado al carrito.
        if (productoSeleccionado == null)
            return;
        try {
            int cantidad = Integer.parseInt(cantidadTextField.getText());
            if (cantidad > 0 && cantidad <= productoSeleccionado.getExistencias()) {
                Producto productoParaCarrito = new Producto(
                        productoSeleccionado.getID(), productoSeleccionado.getNombre(), productoSeleccionado.getPrecio(),
                        0, "", false, null);
                productoParaCarrito.setCantidad(cantidad);
                carritoDialog.agregarProducto(productoParaCarrito);
                productoSeleccionado.setExistencias(productoSeleccionado.getExistencias() - cantidad);
                mostrarDetallesProducto(productoSeleccionado);
            } else {
                JOptionPane.showMessageDialog(this, "Cantidad no válida o insuficiente.", "Error", JOptionPane.WARNING_MESSAGE);
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Por favor, ingrese un número válido.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String formatearMoneda(double valor) {
        // Formatea un valor numérico como moneda.
        return NumberFormat.getCurrencyInstance().format(valor);
    }

    public static void main(String[] args) {
        // Método principal para iniciar la aplicación del cliente.
        SwingUtilities.invokeLater(() -> new Cliente().setVisible(true));
    }

    private class CarritoDialog extends JDialog {
        private DefaultTableModel modeloTabla;
        private JLabel costoTotalLabel;
        private ArrayList<Producto> miCarrito = new ArrayList<>();

        CarritoDialog(Frame owner) {
            super(owner, "Carrito de Compras", true);
            setSize(600, 400);
            setLocationRelativeTo(owner);
            modeloTabla = new DefaultTableModel(new String[]{"ID", "Producto", "Cantidad", "Subtotal"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            JTable tabla = new JTable(modeloTabla);
            add(new JScrollPane(tabla), BorderLayout.CENTER);
            costoTotalLabel = new JLabel("Costo Total: $0.00", SwingConstants.RIGHT);
            costoTotalLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
            costoTotalLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
            add(costoTotalLabel, BorderLayout.NORTH);
            JPanel panelBotones = new JPanel();
            JButton comprarBtn = new JButton("Comprar");
            comprarBtn.addActionListener(e -> {
                if (miCarrito.isEmpty()) {
                    JOptionPane.showMessageDialog(this,
                            "Debe tener al menos un producto en su carrito para comprar.",
                            "Carrito Vacío",
                            JOptionPane.WARNING_MESSAGE);
                } else {
                    realizarCompra(new ArrayList<>(miCarrito));
                    limpiarCarrito();
                    setVisible(false);
                }
            });
            JButton eliminarBtn = new JButton("Eliminar Producto");
            eliminarBtn.addActionListener(e -> {
                String idStr = JOptionPane.showInputDialog(this, "Ingrese el ID del producto para eliminar una unidad:");
                if (idStr != null && !idStr.isBlank()) {
                    try {
                        int idEliminar = Integer.parseInt(idStr);
                        eliminarUnaUnidad(idEliminar);
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(this, "ID inválido.");
                    }
                }
            });
            panelBotones.add(comprarBtn);
            panelBotones.add(eliminarBtn);
            add(panelBotones, BorderLayout.SOUTH);
        }

        void agregarProducto(Producto p) {
            // Agrega un producto al carrito o aumenta la cantidad si ya existe.
            for (Producto prodExistente : miCarrito) {
                if (prodExistente.getID() == p.getID()) {
                    prodExistente.setCantidad(prodExistente.getCantidad() + p.getCantidad());
                    actualizarTabla();
                    return;
                }
            }
            miCarrito.add(p);
            actualizarTabla();
        }

        void eliminarUnaUnidad(int idAEliminar) {
            // Elimina una unidad de un producto del carrito y la devuelve al stock.
            int indiceEnCarrito = -1;
            Producto productoEnCarrito = null;

            for (int i = 0; i < miCarrito.size(); i++) {
                if (miCarrito.get(i).getID() == idAEliminar) {
                    indiceEnCarrito = i;
                    productoEnCarrito = miCarrito.get(i);
                    break;
                }
            }
            if (productoEnCarrito != null) {
                for (Producto pCatalogo : catalogoProductos) {
                    if (pCatalogo.getID() == idAEliminar) {
                        pCatalogo.setExistencias(pCatalogo.getExistencias() + 1);
                        if (productoSeleccionado != null && productoSeleccionado.getID() == idAEliminar) {
                            mostrarDetallesProducto(pCatalogo);
                        }
                        break;
                    }
                }
                if (productoEnCarrito.getCantidad() > 1) {
                    productoEnCarrito.setCantidad(productoEnCarrito.getCantidad() - 1);
                } else {
                    miCarrito.remove(indiceEnCarrito);
                }
                actualizarTabla();
            } else {
                JOptionPane.showMessageDialog(this, "ID no encontrado en el carrito.");
            }
        }

        void limpiarCarrito() {
            // Vacía la lista de productos en el carrito.
            miCarrito.clear();
            actualizarTabla();
        }

        void actualizarTabla() {
            // Actualiza la tabla del carrito con los productos actuales.
            Collections.sort(miCarrito);
            modeloTabla.setRowCount(0);
            double total = 0;
            for (Producto p : miCarrito) {
                double subtotal = p.getCantidad() * p.getPrecio();
                modeloTabla.addRow(new Object[]{p.getID(), p.getNombre(), p.getCantidad(), formatearMoneda(subtotal)});
                total += subtotal;
            }
            costoTotalLabel.setText("Costo Total: " + formatearMoneda(total));
        }
    }

    private class TicketDialog extends JDialog {
        private DefaultTableModel modelo;
        private JLabel costoFinalLabel;

        TicketDialog(Frame owner) {
            super(owner, "Ticket de Compra", true);
            setSize(450, 400);
            setLocationRelativeTo(owner);
            modelo = new DefaultTableModel(new String[]{"Producto", "Cantidad", "Precio Unitario", "Subtotal"}, 0);
            JTable tabla = new JTable(modelo);
            add(new JScrollPane(tabla), BorderLayout.CENTER);
            costoFinalLabel = new JLabel("TOTAL A PAGAR: $0.00", SwingConstants.RIGHT);
            costoFinalLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
            costoFinalLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
            add(costoFinalLabel, BorderLayout.NORTH);
            JButton aceptarBtn = new JButton("Aceptar");
            aceptarBtn.addActionListener(e -> setVisible(false));
            JPanel panelBoton = new JPanel(new FlowLayout(FlowLayout.CENTER));
            panelBoton.add(aceptarBtn);
            add(panelBoton, BorderLayout.SOUTH);
        }

        void mostrar(Ticket ticket) {
            // Muestra el ticket de compra con los detalles de la transacción.
            modelo.setRowCount(0);
            for (Producto p : ticket.getProductos()) {
                double subtotal = p.getCantidad() * p.getPrecio();
                modelo.addRow(new Object[]{
                        p.getNombre(), p.getCantidad(),
                        formatearMoneda(p.getPrecio()), formatearMoneda(subtotal)
                });
            }
            costoFinalLabel.setText("TOTAL A PAGAR: " + formatearMoneda(ticket.getPrecio()));
            setVisible(true);
        }
    }
}