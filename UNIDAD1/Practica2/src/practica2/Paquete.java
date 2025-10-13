/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package practica2;

import java.io.Serializable;

/**
 *
 * @author Alexa
 */

public class Paquete implements Serializable {
    int numero;          // número del paquete
    int total;           // total de paquetes
    boolean fin;         // indica si es el último
    String nombre;       // nombre del archivo
    long tamArchivo;     // tamaño total del archivo
    int tamDatos;        // tamaño del arreglo de datos
    byte[] datos;        // bytes del paquete

    public Paquete(int numero, int total, boolean fin,
                   String nombre, long tamArchivo, byte[] datos) {
        this.numero = numero;
        this.total = total;
        this.fin = fin;
        this.nombre = nombre;
        this.tamArchivo = tamArchivo;
        this.tamDatos = datos.length;
        this.datos = datos;
    }
}
