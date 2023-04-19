/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package projecte;

/**
 *
 * @author DAM
 */
import java.io.*;
import java.net.*;
import java.util.ArrayList;

public class servidor {
    private ArrayList<PrintWriter> clientes;

    public static void main(String[] args) {
        int port = 12345;
        servidor server = new servidor();
        server.run(port);
    }

    public void run(int port) {
        clientes = new ArrayList<PrintWriter>();

        try {
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Servidor iniciat al port: " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connectat des de l'adreça: " + clientSocket.getInetAddress().getHostAddress());

                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                clientes.add(out);

                // Iniciar thread per llegir els missatges del client
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                            String inputLine;
                            while ((inputLine = in.readLine()) != null) {
                                // Enviar el missatge a tots els altres clients
                                for (PrintWriter clientOut : clientes) {
                                    if (clientOut != out) {
                                        clientOut.println(inputLine);
                                    }
                                }
                            }
                            // Eliminar el PrintWriter d'aquest client de la llista
                            clientes.remove(out);
                            // Tancar la connexió amb aquest client
                            clientSocket.close();
                        } catch (IOException e) {
                            System.out.println("Error en llegir el missatge del client: " + e.getMessage());
                        }
                    }
                }).start();
            }
        } catch (IOException e) {
            System.out.println("Error en iniciar el servidor: " + e.getMessage());
        }
    }
}
