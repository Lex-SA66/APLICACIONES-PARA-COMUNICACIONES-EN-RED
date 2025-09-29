/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package practica1;

import java.io.Serializable;
import java.util.ArrayList;

/**
 *
 * @author alex
 */

public class Ticket implements Serializable {
    private static final long serialVersionUID = 9L;
    private ArrayList<Producto> productos;
    private double precio;

    public Ticket(ArrayList<Producto> productos, double precio) {
        // Constructor para inicializar un objeto Ticket.
        this.productos = productos;
        this.precio = precio;
    }

    public ArrayList<Producto> getProductos() {
        return productos;
    }

    public double getPrecio() {
        return precio;
    }
}