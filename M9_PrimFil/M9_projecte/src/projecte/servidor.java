
package projecte;

import java.io.*;
import java.net.*;
import java.security.*;
import java.security.spec.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.StringJoiner;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.crypto.interfaces.*;

public class servidor {
    public static ArrayList<ClientHandler> clientes;

    public static void main(String[] args) {
        int port = 12345;
        ServerSocket servidor = null;
        clientes = new ArrayList<ClientHandler>();

        try {
            servidor = new ServerSocket(port);
            System.out.println("El servidor està escoltant al port: " + port);

            while (true) {
                Socket socketClient = servidor.accept(); //Creem un socket i esperem a que un usuari es connecta
                System.out.println("Un nuevo cliente se ha conectado: " + socketClient.getInetAddress().getHostAddress()); //Imprimim  al servidor que un client s'ha connectat

                ClientHandler clientHandler = new ClientHandler(socketClient); //Li enviem el socketClient al thread per paràmetre
                clientes.add(clientHandler);
                new Thread(clientHandler).start(); //Iniciem un thread nou cada cop que un usuari entra
            }
        } catch (IOException e) {
            System.out.println("Error al establecer la conexión: " + e.getMessage());
        } finally {
            try {
                if (servidor != null) {
                    servidor.close();
                }
            } catch (IOException e) {
                System.out.println("Error al cerrar la conexión: " + e.getMessage());
            }
        }
    }
}

class ClientHandler implements Runnable {
    private Socket socketClient;
    private String nomClient;
    private DataInputStream in;
    private DataOutputStream out;
    private servidor server;
    private PublicKey clavePublicaCliente;
    private PrivateKey clavePrivadaServidor;
    
    public ClientHandler(Socket socketClient) { //Creem un constructor per poder obtenir les dades del servidor
        this.socketClient = socketClient;
        this.server = new servidor(); 
    }

    @Override
    public void run() {
        try {
        	//El in ens serveix per detectar els missatges que rebem de l'usuari i el out serveix per enviar els missatges a l'usuari
            in = new DataInputStream(new DataInputStream(socketClient.getInputStream()));
            out = new DataOutputStream(socketClient.getOutputStream());

            // Generar par de claus RSA - Privada i Pública
            KeyPairGenerator generadorRSA = KeyPairGenerator.getInstance("RSA");
            KeyPair clauRSA = generadorRSA.genKeyPair();

            // Enviem la clau pública del servidor al client
            byte[] bytesClavePublica = clauRSA.getPublic().getEncoded(); //Creem la pública
            clavePrivadaServidor = clauRSA.getPrivate(); //Creem la privada
            out.writeInt(bytesClavePublica.length);
            out.write(bytesClavePublica);

            // Rebem la clau pública del client
            byte[] bytesClavePublicaCliente = new byte[in.readInt()];
            in.readFully(bytesClavePublicaCliente);

            // Regenerem la clau pública del client
            KeyFactory kf = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec x509Spec = new X509EncodedKeySpec(bytesClavePublicaCliente);
            clavePublicaCliente = kf.generatePublic(x509Spec);

            // Enviem el missatge de benvinguda i preguntant el nom xifrat amb la clau pública del client
            String mensajeCifrado = cifrarMensaje("Bienvengut/da a la sala de xat!\nIntrodueix el teu nom:", clavePublicaCliente);
            out.writeUTF(mensajeCifrado);
            
            // Rebem  el nom del client xifrat amb la pública del  servidor i el desxifrem amb la privada del servidor
            String nombreCifrado = in.readUTF();
            nomClient = descifrarMensaje(nombreCifrado, clavePrivadaServidor);

            // Enviem el missatge de benvinguda xifrat també
            String mensajeCifrado2 = cifrarMensaje("¡Hola " + nomClient + "! Comença la conversació.\n----------------------------\nPer enviar missatges privats : /p 'usuari' 'missatge'"
            		+ "\nPer mostrar els usuaris connectats: /u\nPer enviar missatges simplement escriut el missatge i envia\n-------", clavePublicaCliente);
            out.writeUTF(mensajeCifrado2);
            
            String inputLine; //Creem un string que serà al qual li asignarem lo que rebem de l'usuari
            while ((inputLine = descifrarMensaje(in.readUTF(), clavePrivadaServidor)) != null) { //Mirem si ens envia algo i ho desxifrem
                if (inputLine.equals("exit")) {
                    break;
                }
                if (inputLine.startsWith("/p")) { //Mirem si comença per /p si és aixi enviem missatge privat al usuari que ens posi
                    String[] tokens = inputLine.split(" ", 3);
                    if (tokens.length == 3) { //Comprobem que sigui llarg de 3 el missatge enviat
                        String destinatario = tokens[1]; //El 1r token 'paraula abans del espai' li posem per el destinatari
                        String mensaje = tokens[2]; //I el segon és el missatge
                        boolean destinatarioEncontrado = false;
                        for (ClientHandler client : server.clientes) { //Recorrem  tots els clients que estan connectats
                            if (client != this && client.out != null && client.nomClient.equals(destinatario)) {//Li enviem el missatge solament al destinatari i al qui ho envia
                                // Cifrar el mensaje con la clave pública del destinatario
                                String mensajeCifradoEnviar = cifrarMensaje("[PRIVADO][" + nomClient + "]: " + mensaje, client.clavePublicaCliente); //Missatge que xifrem amb la pública del client destinatari
                                client.out.writeUTF(mensajeCifradoEnviar); //Enviem  el missatge xifrat al destinatari
                                String mensajeCifradoEnviar2 = cifrarMensaje("[PRIVADO][" + nomClient + "]: " + mensaje, clavePublicaCliente); //Missatge que xifrem amb la pública del qui envia el missatge
                                out.writeUTF(mensajeCifradoEnviar2); //Enviem el missatge xifrat al qui envia el missatge
                                destinatarioEncontrado = true; //Boolean per saber si hem trobat a l'usuari o no
                                break;
                            }
                        }
                        if (!destinatarioEncontrado) { //Si no el trobem enviem el següent missatge xifrat al qui envia el missatge
                            String mensajeCifradoEnviar = cifrarMensaje("No se encontró el usuario " + destinatario, clavePublicaCliente);
                            out.writeUTF(mensajeCifradoEnviar);
                        }
                    }else {//En cas de enviar un mal format enviem el següent missatge xifrat al usuari
                    	String mensajeCifradoEnviar = cifrarMensaje("El format ha de ser el seguent: /p 'usuari' 'missatge'", clavePublicaCliente);
                        out.writeUTF(mensajeCifradoEnviar);
                    }
                } else if (inputLine.startsWith("/u")) { //En cas de posar /u podrem veure tots els usuaris que hi  han connectats al xat
                    StringJoiner joiner = new StringJoiner(", ");
                    for (ClientHandler client : server.clientes) {
                        if (client.nomClient != null) {
                            joiner.add(client.nomClient); //Creem un string on anem posant tots els usuaris que hi han units per després enviar un sol missatge xifrat amb tots els usuaris connectats
                        }
                    }
                    String mensajeCifradoEnviar = cifrarMensaje("Clientes Conectados: " + joiner, clavePublicaCliente);
                    out.writeUTF(mensajeCifradoEnviar);
                } else if (inputLine != null) { //Si no posem res i sol escrivim un missatge s'enviarà a tota la resta d'usuaris el missatge xifrat amb la pública de cada un d'ells
                    for (ClientHandler client : server.clientes) {
                        if (client.nomClient != null) {
                            String textoCifrado = cifrarMensaje("[" + nomClient + "]: " + inputLine, client.clavePublicaCliente);
                            client.out.writeUTF(textoCifrado); //Enviem a cada un dels usuaris el missatge xifrat
                        }
                    }
                }
            }

            // Notifiquem als demés usuaris quan algu es desconecta
            for (ClientHandler client : server.clientes) {
                if (!client.nomClient.equals(nomClient)) {
                    String mensajeCifradoEnviar = cifrarMensaje("El client " + nomClient + " s'ha desconnectat.", client.clavePublicaCliente);
                    client.out.writeUTF(mensajeCifradoEnviar);
                }
            }
            
            System.out.println("El client " + nomClient + " s'ha desconectat."); //Imprimim això al servidor
            String mensajeCifradoEnviar = cifrarMensaje("Fi de la sessió", clavePublicaCliente); //Enviem aquest missatge al client abans de desconnectar-se
            out.writeUTF(mensajeCifradoEnviar);

            socketClient.close(); //Tanquem la connexió amb el client
        } catch (IOException | NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                | IllegalBlockSizeException | BadPaddingException | InvalidKeySpecException e) {
            System.out.println("Error al comunicar-se amb el client: " + e.getMessage());
        }
    }

    // Método para cifrar un mensaje utilizando AES
    public String cifrarMensaje(String mensaje, PublicKey clavePublicaCliente) throws NoSuchAlgorithmException, //Funció  que cridem per xifrar el missatge amb la publica pasada per parametre
	    NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
		Cipher cifradorRSA = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		cifradorRSA.init(Cipher.ENCRYPT_MODE, clavePublicaCliente); //Definim el mètode de xifrat
		byte[] textoCifrado = cifradorRSA.doFinal(mensaje.getBytes()); //Xifrem el missatge
		return Base64.getEncoder().encodeToString(textoCifrado); //Retornem el missatge xifrat com un string
	}

    // Método para descifrar un mensaje utilizando AES
    private static String descifrarMensaje(String mensajeCifrado, PrivateKey clavePrivadaServidor2) { //Funció  que cridem per desxifrar els missatges que ens envia l'usuari amb la privada del servidor pasada per parametre
        try {
            Cipher cifradorRSA = Cipher.getInstance("RSA/ECB/PKCS1Padding"); 
            cifradorRSA.init(Cipher.DECRYPT_MODE, clavePrivadaServidor2); //Definim el mètode de desxifrat (el mateix que el de xifrat)
            byte[] textoCifrado = Base64.getDecoder().decode(mensajeCifrado);
            byte[] textoDescifrado = cifradorRSA.doFinal(textoCifrado); //Desxifrem el missatge i obtenim bytes
            return new String(textoDescifrado, "UTF-8"); //Pasem els bytes a string
        } catch (Exception e) {
            System.out.println("Error al descifrar el mensaje: " + e.getMessage());
        }
        return "";
    }
}