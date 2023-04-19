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
    public static ArrayList<ClientHandler> clients = new ArrayList<>();
    public static void main(String[] args) {
        int port = 12345;
        ServerSocket servidor = null;
        Socket socketClient = null;
        
        try {
            servidor = new ServerSocket(port);
            System.out.println("El servidor està escoltant al port: " + port);
            
            while (true) {
                socketClient = servidor.accept();
                System.out.println("Un nou client s'ha connectat: " + socketClient.getInetAddress().getHostAddress());
                
                ClientHandler clientHandler = new ClientHandler(socketClient);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.out.println("Error en establir la connexió: " + e.getMessage());
        } finally {
            try {
                servidor.close();
            } catch (IOException e) {
                System.out.println("Error en tancar el servidor: " + e.getMessage());
            }
        }
    }
}

class ClientHandler implements Runnable {
    private Socket socketClient;
    private String nomClient;
    private BufferedReader in;
    private PrintWriter out;
    
    public ClientHandler(Socket socketClient) {
        this.socketClient = socketClient;
    }
    
    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socketClient.getInputStream()));
            out = new PrintWriter(socketClient.getOutputStream(), true);
            
            out.println("Benvingut a la sala de xat!");
            out.println("Introdueixi el seu nom:");
            nomClient = in.readLine();
            out.println("Hola " + nomClient + "! Comença la conversa.");
            
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                if (inputLine.equals("fi")) {
                    break;
                }
                if (inputLine.startsWith("/privat")) {
                    // Processar missatges privats
                    String[] missatge = inputLine.split(" ");
                    String desti = missatge[1];
                    String text = inputLine.substring(inputLine.indexOf(desti) + desti.length() + 1);
                    text = "[" + nomClient + "] (privat): " + text;
                    ClientHandler clientDesti = null;
                    for (ClientHandler client : servidor.clients) {
                        if (client.nomClient.equals(desti)) {
                            clientDesti = client;
                            break;
                        }
                    }
                    if (clientDesti != null) {
                        clientDesti.out.println(text);
                        out.println(text);
                    } else {
                        out.println("No s'ha trobat l'usuari " + desti);
                    }
                } else {
                    // Enviar missatges a la sala de xat
                    String text = "[" + nomClient + "]: " + inputLine;
		    for (ClientHandler client : servidor.clients) {
                        if (client != this) {
                            client.out.println(text);
                        }
                    }
                }
            }
            
            System.out.println("El client " + nomClient + " s'ha desconnectat.");
            out.println("Fi de la sessió.");
            
            socketClient.close();
        } catch (IOException e) {
            System.out.println("Error en establir la connexió: " + e.getMessage());
        }
    }
}
