/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package practica1;

import java.io.Serializable;

/**
 *
 * @author alex
 */

public class Producto implements Serializable, Comparable<Producto> {

    private static final long serialVersionUID = 4L;

    private int id;
    private String nombre;
    private double precio;
    private int existencias;
    private String descripcion;
    private boolean promocion;
    private byte[] imagen;
    private int cantidad;

    public Producto(int id, String nombre, double precio, int existencias, String descripcion, boolean promocion, byte[] imagen) {
        // Constructor para inicializar un objeto Producto.
        this.id = id;
        this.nombre = nombre;
        this.precio = precio;
        this.existencias = existencias;
        this.descripcion = descripcion;
        this.promocion = promocion;
        this.imagen = imagen;
        this.cantidad = 0;
    }

    public int getID() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public double getPrecio() {
        return precio;
    }

    public int getExistencias() {
        return existencias;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean getPromocion() {
        return promocion;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setID(int id) {
        this.id = id;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public void setPrecio(double precio) {
        this.precio = precio;
    }

    public void setExistencias(int existencias) {
        this.existencias = existencias;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public void setPromocion(boolean promocion) {
        this.promocion = promocion;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public byte[] getImagen() {
        return imagen;
    }

    public void setImagen(byte[] imagen) {
        this.imagen = imagen;
    }

    @Override
    public int compareTo(Producto otroProducto) {
        // Compara productos por su ID.
        return Integer.compare(this.id, otroProducto.id);
    }
}