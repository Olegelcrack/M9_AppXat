/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package projecte;

import java.io.*;
import java.net.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

public class servidor {
    public static ArrayList<ClientHandler> clientes;

    public static void main(String[] args) {
        int port = 12345;
        ServerSocket servidor2 = null;
        Socket socketClient = null;
        clientes = new ArrayList<ClientHandler>();

        try {
            servidor2 = new ServerSocket(port);
            System.out.println("El servidor está escuchando en el puerto: " + port);

            while (true) {
                socketClient = servidor2.accept();
                System.out.println("Un nuevo cliente se ha conectado: " + socketClient.getInetAddress().getHostAddress());

                ClientHandler clientHandler = new ClientHandler(socketClient);
                PrintWriter out = new PrintWriter(socketClient.getOutputStream(), true);
                clientes.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.out.println("Error en establecer la conexión: " + e.getMessage());
        } finally {
            try {
                servidor2.close();
            } catch (IOException e) {
                System.out.println("Error en cerrar el servidor: " + e.getMessage());
            }
        }
    }

    public servidor() {
        clientes = new ArrayList<ClientHandler>();
    }

    public ClientHandler getClientHandler(PrintWriter out) {
        for (ClientHandler clientHandler : clientes) {
            if (clientHandler.out.equals(out)) {
                return clientHandler;
            }
        }
        return null;
    }
}

class ClientHandler implements Runnable {
    private Socket socketClient;
    public String nomClient;
    private BufferedReader in;
    public PrintWriter out;
    public servidor server;
    private SecretKey clau;
    
    
    public ClientHandler(Socket socketClient) {
        this.socketClient = socketClient;
    }
    
    public ClientHandler(Socket socketClient, servidor server) {
    this.socketClient = socketClient;
    this.server = server;
}
    
    @Override
    public void run() {
        try {
            
            in = new BufferedReader(new InputStreamReader(socketClient.getInputStream()));
            out = new PrintWriter(socketClient.getOutputStream(), true);
            clau = generarClave();
            out.println("Benvingut a la sala de xat!");
            out.println("Introdueixi el seu nom:");
            String missatgeDesxifrat = descifrarMensaje(in.readLine().getBytes(), clau);
            nomClient = missatgeDesxifrat;
            out.println("Hola " + nomClient + "! Comenca la conversa.");
            
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                if (inputLine.equals("exit")) {
                    break;
                }
                if (inputLine.startsWith("/p")) {
                    String[] tokens = inputLine.split(" ", 3);
                    if (tokens.length == 3) {
                        String desti = tokens[1];
                        String missatge = tokens[2];
                        boolean destinatariTrobat = false;
                        for (ClientHandler client : server.clientes) {
                            if (client != this && client.out != null && client.nomClient.equals(desti)) {
                                client.out.println("[PRIVAT][" + nomClient + "]: " + missatge);
                                out.println("[PRIVAT][" + nomClient + "]: " + missatge);
                                destinatariTrobat = true;
                                break;
                            }
                        }
                        if (!destinatariTrobat) {
                            out.println("No s'ha trobat l'usuari " + desti);
                        }
                    }
                } else if(inputLine.startsWith("/g")){
                    for (ClientHandler client : server.clientes) {
                        if(client.nomClient == null){
                            
                        }else{
                            String[] tokens = inputLine.split(" ", 2);
                            if (tokens.length == 2) {
                                String text2 = "[" + nomClient + "]: " + tokens[1];
                                client.out.println(text2);
                            }
                        }
                        
                    }
                } else if(inputLine.startsWith("/u")){
                    StringJoiner joiner = new StringJoiner(", ");
                    for (ClientHandler client : server.clientes) {
                        if(client.nomClient == null){
                            
                        }else{
                                joiner.add(client.nomClient);
                                
                                
                        }
                    }
                    out.println("Clients Conectats: " + joiner);
                        
                    }
                
            }
            for (ClientHandler client : server.clientes) {
                if(!client.nomClient.equals(nomClient)){
                    client.out.println("El client " + nomClient + " s'ha desconnectat.");
                }
            }
            System.out.println("El client " + nomClient + " s'ha desconnectat.");
            out.println("Fi de la sessio.");
            
            socketClient.close();
        } catch (IOException e) {
            System.out.println("Error en establir la connexió: " + e.getMessage());
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(ClientHandler.class.getName()).log(Level.SEVERE, null, ex);
        }catch (NoSuchPaddingException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
                        System.out.println("Error en desxifrar el missatge: " + e.getMessage());
                    }
    }
    
    private String descifrarMensaje(byte[] mensajeCifrado, SecretKey clave) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {

        Cipher descifrador = Cipher.getInstance("AES");
        descifrador.init(Cipher.DECRYPT_MODE, clave);
        byte[] mensajeDescifrado = descifrador.doFinal(mensajeCifrado);
        return new String(mensajeDescifrado);
    }
    
    private SecretKey generarClave() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(128);
        return keyGen.generateKey();
    }
}
