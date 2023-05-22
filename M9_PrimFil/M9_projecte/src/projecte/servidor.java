
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
            System.out.println("El servidor esta escoltant al port: " + port);

            while (true) {
                Socket socketClient = servidor.accept(); //Creem un socket i esperem a que un usuari es connecta
                System.out.println("Un nou client s'ha connectat: " + socketClient.getInetAddress().getHostAddress()); //Imprimim  al servidor que un client s'ha connectat

                ClientHandler clientHandler = new ClientHandler(socketClient); //Li enviem el socketClient al thread per parametre
                clientes.add(clientHandler);
                new Thread(clientHandler).start(); //Iniciem un thread nou cada cop que un usuari entra
            }
        } catch (IOException e) {
            System.out.println("Error al establir la connexi�: " + e.getMessage());
        } finally {
            try {
                if (servidor != null) {
                    servidor.close();
                }
            } catch (IOException e) {
                System.out.println("Error al tancar la connexi�: " + e.getMessage());
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
    private PublicKey clauPublicaClient;
    private PrivateKey clauPrivadaServidor;
    
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

            // Generar par de claus RSA - Privada i P�blica
            KeyPairGenerator generadorRSA = KeyPairGenerator.getInstance("RSA");
            KeyPair clauRSA = generadorRSA.genKeyPair();

            // Enviem la clau p�blica del servidor al client
            byte[] bytesClauPublica = clauRSA.getPublic().getEncoded(); //Creem la p�blica
            clauPrivadaServidor = clauRSA.getPrivate(); //Creem la privada
            out.writeInt(bytesClauPublica.length);
            out.write(bytesClauPublica);

            // Rebem la clau p�blica del client
            byte[] bytesClauPublicaClient = new byte[in.readInt()];
            in.readFully(bytesClauPublicaClient);

            // Regenerem la clau publica del client
            KeyFactory kf = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec x509Spec = new X509EncodedKeySpec(bytesClauPublicaClient);
            clauPublicaClient = kf.generatePublic(x509Spec);

            // Enviem el missatge de benvinguda i preguntant el nom xifrat amb la clau publica del client
            String missatgeXifrat = xifrarMissatge("Benvengut/da a la sala de xat!\nIntrodueix el teu nom:", clauPublicaClient);
            out.writeUTF(missatgeXifrat);
            
            // Rebem  el nom del client xifrat amb la p�blica del  servidor i el desxifrem amb la privada del servidor
            String nomXifrat = in.readUTF();
            nomClient = desxifrarMissatge(nomXifrat, clauPrivadaServidor);

            // Enviem el missatge de benvinguda xifrat tambe
            String missatgeXifrat2 = xifrarMissatge("Hola " + nomClient + "! Comenca la conversacio.\n----------------------------\nPer enviar missatges privats : /p 'usuari' 'missatge'"
            		+ "\nPer mostrar els usuaris connectats: /u\nPer enviar missatges simplement escriut el missatge i envia", clauPublicaClient);
            out.writeUTF(missatgeXifrat2);
            String missatgeXifrat3 = xifrarMissatge("Per sortir del xat: /e\n----------", clauPublicaClient);
            out.writeUTF(missatgeXifrat3);
            for (ClientHandler client : server.clientes) {
                if (!client.nomClient.equals(nomClient)) {
                    String missatgeXifratEnviar = xifrarMissatge("S'ha connectat l'usuari: " + nomClient + ".", client.clauPublicaClient);
                    client.out.writeUTF(missatgeXifratEnviar);
                }
            }
            String inputLine; //Creem un string que sera al qual li asignarem lo que rebem de l'usuari
            while ((inputLine = desxifrarMissatge(in.readUTF(), clauPrivadaServidor)) != null) { //Mirem si ens envia algo i ho desxifrem
            	if (inputLine.equals("/e")) {
                    break;
                }
                if (inputLine.startsWith("/p")) { //Mirem si comenca per /p si es aixi enviem missatge privat al usuari que ens posi
                    String[] tokens = inputLine.split(" ", 3);
                    if (tokens.length == 3) { //Comprobem que sigui llarg de 3 el missatge enviat
                        String destinatari = tokens[1]; //El 1r token 'paraula abans del espai' li posem per el destinatari
                        String missatge = tokens[2]; //I el segon es el missatge
                        boolean destinatariTrobat = false;
                        for (ClientHandler client : server.clientes) { //Recorrem  tots els clients que estan connectats
                            if (client != this && client.out != null && client.nomClient.equals(destinatari)) {//Li enviem el missatge solament al destinatari i al qui ho envia
                                // Xifrar el missatge amb la clau publica del destinatari
                                String missatgeXifratEnviar = xifrarMissatge("[PRIVAT][" + nomClient + "]: " + missatge, client.clauPublicaClient); //Missatge que xifrem amb la publica del client destinatari
                                client.out.writeUTF(missatgeXifratEnviar); //Enviem  el missatge xifrat al destinatari
                                String missatgeXifratEnviar2 = xifrarMissatge("\u001B[31m[PRIVAT][" + client.nomClient + "]: " + missatge + "\u001B[0m", clauPublicaClient); //Missatge que xifrem amb la publica del qui envia el missatge
                                out.writeUTF(missatgeXifratEnviar2); //Enviem el missatge xifrat al qui envia el missatge
                                destinatariTrobat = true; //Boolean per saber si hem trobat a l'usuari o no
                                break;
                            }
                        }
                        if (!destinatariTrobat) { //Si no el trobem enviem el seguent missatge xifrat al qui envia el missatge
                            String missatgeXifratEnviar = xifrarMissatge("No s'ha trobar l'usuari " + destinatari, clauPublicaClient);
                            out.writeUTF(missatgeXifratEnviar);
                        }
                    }else {//En cas de enviar un mal format enviem el seguent missatge xifrat al usuari
                    	String missatgeXifratEnviar = xifrarMissatge("El format ha de ser el seguent: /p 'usuari' 'missatge'", clauPublicaClient);
                        out.writeUTF(missatgeXifratEnviar);
                    }
                } else if (inputLine.startsWith("/u")) { //En cas de posar /u podrem veure tots els usuaris que hi  han connectats al xat
                    StringJoiner joiner = new StringJoiner(", ");
                    for (ClientHandler client : server.clientes) {
                        if (client.nomClient != null) {
                            joiner.add(client.nomClient); //Creem un string on anem posant tots els usuaris que hi han units per despres enviar un sol missatge xifrat amb tots els usuaris connectats
                        }
                    }
                    String missatgeXifratEnviar = xifrarMissatge("Clients Conectats: " + joiner, clauPublicaClient);
                    out.writeUTF(missatgeXifratEnviar);
                } else if (inputLine != null) { //Si no posem res i sol escrivim un missatge s'enviar� a tota la resta d'usuaris el missatge xifrat amb la publica de cada un d'ells
                    for (ClientHandler client : server.clientes) {
                        if (client.nomClient != null) {
                            String textXifrat = xifrarMissatge("[" + nomClient + "]: " + inputLine, client.clauPublicaClient);
                            client.out.writeUTF(textXifrat); //Enviem a cada un dels usuaris el missatge xifrat
                        }
                    }
                }
            }

            // Notifiquem als demes usuaris quan algu es desconecta
            for (ClientHandler client : server.clientes) {
                if (!client.nomClient.equals(nomClient)) {
                    String missatgeXifratEnviar = xifrarMissatge("El client " + nomClient + " s'ha desconnectat.", client.clauPublicaClient);
                    client.out.writeUTF(missatgeXifratEnviar);
                }
            }
            
            System.out.println("El client " + nomClient + " s'ha desconectat."); //Imprimim aixo al servidor
            String missatgeXifratEnviar=  xifrarMissatge("Fi de la sessio", clauPublicaClient); //Enviem aquest missatge al client abans de desconnectar-se
            out.writeUTF(missatgeXifratEnviar);

            socketClient.close(); //Tanquem la connexio amb el client
        } catch (IOException | NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                | IllegalBlockSizeException | BadPaddingException | InvalidKeySpecException e) {
            System.out.println("Error al comunicar-se amb el client: " + e.getMessage());
        }
    }

    // Metode  per xifrar el missatge amb AES
    public String xifrarMissatge(String missatge, PublicKey clauPublicaClient) throws NoSuchAlgorithmException, //Funcio  que cridem per xifrar el missatge amb la publica pasada per parametre
	    NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
		Cipher xifradorRSA = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		xifradorRSA.init(Cipher.ENCRYPT_MODE, clauPublicaClient); //Definim el metode de xifrat
		byte[] textXifrat = xifradorRSA.doFinal(missatge.getBytes()); //Xifrem el missatge
		return Base64.getEncoder().encodeToString(textXifrat); //Retornem el missatge xifrat com un string
	}

    // Metode per desxifrar el missatge amb AES
    private static String desxifrarMissatge(String missatgeXifrat, PrivateKey clauPrivadaServidor) { //Funcio  que cridem per desxifrar els missatges que ens envia l'usuari amb la privada del servidor pasada per parametre
        try {
            Cipher desxifradorRSA = Cipher.getInstance("RSA/ECB/PKCS1Padding"); 
            desxifradorRSA.init(Cipher.DECRYPT_MODE, clauPrivadaServidor); //Definim el metode de desxifrat (el mateix que el de xifrat)
            byte[] textXifrat = Base64.getDecoder().decode(missatgeXifrat);
            byte[] textDesxifrat = desxifradorRSA.doFinal(textXifrat); //Desxifrem el missatge i obtenim bytes
            return new String(textDesxifrat, "UTF-8"); //Pasem els bytes a string
        } catch (Exception e) {
            System.out.println("Error al desxifrar el missatge: " + e.getMessage());
        }
        return "";
    }
}