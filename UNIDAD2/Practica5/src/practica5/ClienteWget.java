package practica5;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.*;
import java.text.SimpleDateFormat;

public class ClienteWget extends JFrame {

    private JTextField txtUrl, txtRuta;
    private JTextArea txtLog;
    private JEditorPane visorWeb; 
    private JTable tablaArchivos;
    private DefaultTableModel modeloTabla;
    private JButton btnDescargar, btnRuta, btnPreviewUrl;
    private JTabbedPane panelDerecho;

    private File directorioBase;
    private Queue<String> colaDescargas = new LinkedList<>();
    private Set<String> visitados = new HashSet<>();
    private String hostGlobal;
    private int puertoGlobal;

    public static void main(String[] args) {
        try {
            UIManager.put("Panel.background", new Color(30, 30, 30));
            UIManager.put("OptionPane.background", new Color(30, 30, 30));
            UIManager.put("OptionPane.messageForeground", Color.WHITE);
            UIManager.put("Button.background", new Color(70, 70, 70));
            UIManager.put("Button.foreground", Color.WHITE);
            UIManager.put("Label.foreground", Color.WHITE);
            UIManager.put("Table.background", new Color(45, 45, 45));
            UIManager.put("Table.foreground", Color.WHITE);
            UIManager.put("TableHeader.foreground", Color.BLACK); 
            UIManager.put("TabbedPane.foreground", Color.BLACK);
        } catch (Exception e) {}
        SwingUtilities.invokeLater(() -> new ClienteWget().setVisible(true));
    }

    public ClienteWget() {
        setTitle("Wget Java - Cliente");
        setSize(1100, 750);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        directorioBase = new File(System.getProperty("user.home"), "Downloads/Practica5Archivos");
        if(!directorioBase.exists()) directorioBase.mkdirs();

        JPanel pTop = new JPanel(new GridBagLayout());
        pTop.setBackground(new Color(45, 45, 45));
        pTop.setBorder(new EmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; pTop.add(new JLabel("URL Objetivo:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0;
        txtUrl = new JTextField("http://localhost:8000/"); 
        estilizarInput(txtUrl); pTop.add(txtUrl, gbc);

        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0;
        btnPreviewUrl = crearBoton(" Previsualizar", new Color(0, 100, 200));
        pTop.add(btnPreviewUrl, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; pTop.add(new JLabel("Guardar en:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0;
        txtRuta = new JTextField(directorioBase.getAbsolutePath());
        estilizarInput(txtRuta); txtRuta.setEditable(false); pTop.add(txtRuta, gbc);

        gbc.gridx = 2; gbc.gridy = 1; gbc.weightx = 0;
        btnRuta = crearBoton(" Cambiar Ruta", new Color(100, 100, 100));
        pTop.add(btnRuta, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.HORIZONTAL;
        btnDescargar = crearBoton(" INICIAR DESCARGA WGET", new Color(0, 150, 50));
        btnDescargar.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnDescargar.setPreferredSize(new Dimension(0, 40));
        pTop.add(btnDescargar, gbc);

        add(pTop, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setDividerLocation(450);
        split.setBackground(new Color(30, 30, 30));

        String[] columnas = {"Archivo", "Estado", "Ruta Relativa"};
        modeloTabla = new DefaultTableModel(columnas, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };
        tablaArchivos = new JTable(modeloTabla);
        estilizarTabla(tablaArchivos);
        
        tablaArchivos.getSelectionModel().addListSelectionListener(e -> {
            if(!e.getValueIsAdjusting() && tablaArchivos.getSelectedRow() != -1) {
                String ruta = (String) modeloTabla.getValueAt(tablaArchivos.getSelectedRow(), 2);
                previsualizarArchivoLocal(ruta);
            }
        });

        JScrollPane scrollTabla = new JScrollPane(tablaArchivos);
        scrollTabla.getViewport().setBackground(new Color(40, 40, 40));
        split.setLeftComponent(scrollTabla);

        panelDerecho = new JTabbedPane();
        panelDerecho.setForeground(Color.BLACK);
        
        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        txtLog.setBackground(Color.BLACK);
        txtLog.setForeground(new Color(50, 255, 50));
        panelDerecho.addTab(" Terminal RAW", new JScrollPane(txtLog));

        visorWeb = new JEditorPane();
        visorWeb.setEditable(false);
        visorWeb.setContentType("text/html");
        panelDerecho.addTab(" Vista Previa", new JScrollPane(visorWeb));

        split.setRightComponent(panelDerecho);
        add(split, BorderLayout.CENTER);

        btnRuta.addActionListener(e -> seleccionarRuta());
        btnPreviewUrl.addActionListener(e -> {
            try { visorWeb.setPage(txtUrl.getText().trim()); panelDerecho.setSelectedIndex(1); } 
            catch(IOException ex) { JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage()); }
        });
        btnDescargar.addActionListener(e -> iniciarDescarga());
    }

    private void iniciarDescarga() {
        txtLog.setText(">>> INICIANDO PROCESO WGET (NIO)...\n");
        modeloTabla.setRowCount(0);
        visitados.clear();
        colaDescargas.clear();
        btnDescargar.setEnabled(false);
        panelDerecho.setSelectedIndex(0);

        new Thread(() -> {
            try {
                String urlStr = txtUrl.getText().trim();
                if(!urlStr.endsWith("/") && !urlStr.endsWith(".html") && !urlStr.endsWith(".php") && !urlStr.endsWith(".java")) {
                    urlStr += "/";
                }
                
                URI uri = new URI(urlStr);
                hostGlobal = uri.getHost();
                puertoGlobal = uri.getPort() > 0 ? uri.getPort() : 80;
                String pathInicial = uri.getPath().isEmpty() ? "/" : uri.getPath();

                agregarACola(pathInicial);

                while(!colaDescargas.isEmpty()) {
                    String path = colaDescargas.poll();
                    procesarArchivoNIO(path);
                }
                
                log("\n>>> [FIN] DESCARGA COMPLETA.");
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Descarga Finalizada!"));

            } catch (Exception e) {
                log("ERROR FATAL: " + e.getMessage());
                e.printStackTrace();
            } finally {
                SwingUtilities.invokeLater(() -> btnDescargar.setEnabled(true));
            }
        }).start();
    }

    private void agregarACola(String path) {
        if (path.contains("?") || path.contains("=") || path.contains(";") || path.contains(":")) return;
        if (path.contains("..")) return;
        if(!visitados.contains(path)) {
            visitados.add(path);
            colaDescargas.add(path);
            SwingUtilities.invokeLater(() -> modeloTabla.addRow(new Object[]{new File(path).getName(), "Pendiente", path}));
        }
    }

    private void procesarArchivoNIO(String path) {
        String pathGuardado = path;
        if(pathGuardado.endsWith("/")) pathGuardado += "index.html";

        try {
            actualizarEstadoTabla(path, "Descargando...");
            
            Selector selector = Selector.open();
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.connect(new InetSocketAddress(hostGlobal, puertoGlobal));
            channel.register(selector, SelectionKey.OP_CONNECT);

            String rutaRelativa = URLDecoder.decode(pathGuardado.startsWith("/") ? pathGuardado.substring(1) : pathGuardado, StandardCharsets.UTF_8.name());
            File archivoLocal = new File(directorioBase, rutaRelativa);
            
            if (archivoLocal.exists() && archivoLocal.isDirectory()) archivoLocal = new File(archivoLocal, "index.html");
            archivoLocal.getParentFile().mkdirs(); 

            FileOutputStream fos = new FileOutputStream(archivoLocal);
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            boolean headerLeido = false;
            ByteArrayOutputStream bufferAcumulado = new ByteArrayOutputStream();

            while(channel.isOpen()) {
                if(selector.select(5000) == 0) { log("!!! Timeout."); break; }
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

                while(keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if(key.isConnectable()) {
                        if(channel.finishConnect()) {
                            String request = "GET " + path + " HTTP/1.1\r\nHost: " + hostGlobal + "\r\nUser-Agent: WgetJava/1.0 (NIO)\r\nConnection: close\r\n\r\n";
                            log("\n>>> [CLIENTE] ENVIANDO (RAW):\n" + request.trim());
                            channel.write(ByteBuffer.wrap(request.getBytes()));
                            key.interestOps(SelectionKey.OP_READ);
                        }
                    } else if(key.isReadable()) {
                        buffer.clear();
                        int read = channel.read(buffer);
                        if(read == -1) { channel.close(); break; }
                        buffer.flip();
                        byte[] data = new byte[buffer.limit()];
                        buffer.get(data);

                        if(!headerLeido) {
                            bufferAcumulado.write(data);
                            byte[] totalBytes = bufferAcumulado.toByteArray();
                            int split = buscarFinHeaders(totalBytes);

                            if(split != -1) {
                                String headers = new String(totalBytes, 0, split, StandardCharsets.UTF_8);
                                
                                headers = corregirHoraEnHeader(headers);

                                log("\n>>> [CLIENTE] RECIBIDO (RAW):\n" + headers + "\n--------------------");
                                int bodyStart = split + 4;
                                int bodyLen = totalBytes.length - bodyStart;
                                if (bodyLen > 0) fos.write(totalBytes, bodyStart, bodyLen);
                                headerLeido = true;
                            }
                        } else {
                            fos.write(data);
                        }
                    }
                }
            }
            fos.close();
            selector.close();
            actualizarEstadoTabla(path, "Completado");

            boolean esCodigo = pathGuardado.endsWith(".java") || pathGuardado.endsWith(".c") || pathGuardado.endsWith(".cpp");
            if(!esCodigo && (pathGuardado.endsWith(".html") || pathGuardado.endsWith(".php") || path.endsWith("/"))) {
                analizarHtml(archivoLocal, path);
            }

        } catch(Exception e) {
            actualizarEstadoTabla(path, "Error");
            log("Error: " + e.getMessage());
        }
    }

    private String corregirHoraEnHeader(String headers) {
        try {
            // Buscamos la fecha que manda el servidor (ej: Date: Wed, 17 Dec 2025 05:00:00 GMT)
            Pattern p = Pattern.compile("Date: (.*?)\r\n");
            Matcher m = p.matcher(headers);
            if (m.find()) {
                String fechaServer = m.group(1);
                
                SimpleDateFormat sdfGMT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
                sdfGMT.setTimeZone(TimeZone.getTimeZone("GMT"));
                Date fecha = sdfGMT.parse(fechaServer);
                
                SimpleDateFormat sdfLocal = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
                sdfLocal.setTimeZone(TimeZone.getDefault());
                String fechaLocal = sdfLocal.format(fecha);
                
                return headers.replace(fechaServer, fechaLocal);
            }
        } catch (Exception e) {
        }
        return headers;
    }

    private int buscarFinHeaders(byte[] data) {
        for(int i = 0; i < data.length - 3; i++) {
            if(data[i] == '\r' && data[i+1] == '\n' && data[i+2] == '\r' && data[i+3] == '\n') return i;
        }
        return -1;
    }

    private void analizarHtml(File f, String rutaActual) {
        try {
            String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            Pattern p = Pattern.compile("(src|href)=\"([^\"]+)\"");
            Matcher m = p.matcher(content);
            while(m.find()) {
                String link = m.group(2);
                if(link.startsWith("http") || link.startsWith("#") || link.startsWith("mailto") || link.contains("?")) continue;
                String base = rutaActual.endsWith("/") ? rutaActual : rutaActual.substring(0, rutaActual.lastIndexOf('/') + 1);
                agregarACola(base + link);
            }
        } catch(Exception e) {}
    }

    private void actualizarEstadoTabla(String path, String estado) {
        SwingUtilities.invokeLater(() -> {
            for(int i=0; i<modeloTabla.getRowCount(); i++) if(modeloTabla.getValueAt(i, 2).equals(path)) { modeloTabla.setValueAt(estado, i, 1); break; }
        });
    }
    private void log(String s) {
        SwingUtilities.invokeLater(() -> { txtLog.append(s + "\n"); txtLog.setCaretPosition(txtLog.getDocument().getLength()); });
    }
    private void previsualizarArchivoLocal(String rutaRelativa) {
        File f = new File(directorioBase, rutaRelativa.startsWith("/") ? rutaRelativa.substring(1) : rutaRelativa);
        if(f.isDirectory()) f = new File(f, "index.html");
        try { visorWeb.setPage(f.toURI().toURL()); panelDerecho.setSelectedIndex(1); } catch(Exception e) {}
    }
    private void estilizarInput(JTextField t) { t.setBackground(new Color(60, 60, 60)); t.setForeground(Color.WHITE); t.setCaretColor(Color.WHITE); t.setBorder(BorderFactory.createLineBorder(Color.GRAY)); t.setPreferredSize(new Dimension(0, 25)); }
    private void estilizarTabla(JTable t) {
        t.setBackground(new Color(45, 45, 45)); t.setForeground(Color.WHITE); t.setGridColor(Color.GRAY); t.setSelectionBackground(new Color(0, 120, 200)); t.setSelectionForeground(Color.WHITE); t.setRowHeight(22);
        t.getTableHeader().setBackground(Color.LIGHT_GRAY); t.getTableHeader().setForeground(Color.BLACK);
    }
    private JButton crearBoton(String texto, Color bg) {
        JButton b = new JButton(texto); b.setBackground(bg); b.setForeground(Color.WHITE); b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.BLACK), BorderFactory.createEmptyBorder(5, 10, 5, 10))); return b;
    }
    private void seleccionarRuta() {
        JFileChooser fc = new JFileChooser(directorioBase); fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if(fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) { directorioBase = fc.getSelectedFile(); txtRuta.setText(directorioBase.getAbsolutePath()); }
    }
}