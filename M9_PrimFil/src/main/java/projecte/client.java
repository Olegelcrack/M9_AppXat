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

public class client {
    private String nomClient;
    private BufferedReader in;
    private PrintWriter out;

    public static void main(String[] args) {
        String host = "localhost";
        int port = 12345;
        client clientXat = new client();
        clientXat.run(host, port);
    }

    public void run(String host, int port) {
        try {
            Socket socket = new Socket(host, port);
            System.out.println("Connectat al servidor: " + host + " al port: " + port);

            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

           

            // Iniciar thread per llegir els missatges del servidor
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String inputLine;
                        while ((inputLine = in.readLine()) != null) {
                            System.out.println(inputLine);
                        }
                    } catch (IOException e) {
                        System.out.println("Error en llegir els missatges del servidor: " + e.getMessage());
                    }
                }
            }).start();

            // Enviar missatges al servidor
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String inputLine;
            while ((inputLine = br.readLine()) != null) {
                if (inputLine.equals("fi")) {
                    break;
                }
                out.println(inputLine);
            }

            // Tancar la connexió
            socket.close();
            System.out.println("Connexió tancada.");
        } catch (UnknownHostException e) {
            System.out.println("Servidor desconegut: " + host + " al port: " + port);
        } catch (IOException e) {
            System.out.println("Error en establir la connexió al servidor: " + e.getMessage());
        }
    }
}
