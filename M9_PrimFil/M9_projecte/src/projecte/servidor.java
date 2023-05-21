
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
            System.out.println("El servidor está escuchando en el puerto: " + port);

            while (true) {
                Socket socketClient = servidor.accept();
                System.out.println("Un nuevo cliente se ha conectado: " + socketClient.getInetAddress().getHostAddress());

                ClientHandler clientHandler = new ClientHandler(socketClient);
                clientes.add(clientHandler);
                new Thread(clientHandler).start();
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
    
    public ClientHandler(Socket socketClient) {
        this.socketClient = socketClient;
        this.server = new servidor();
    }

    @Override
    public void run() {
        try {
            in = new DataInputStream(new DataInputStream(socketClient.getInputStream()));
            out = new DataOutputStream(socketClient.getOutputStream());

            // Generar par de claves RSA
            KeyPairGenerator generadorRSA = KeyPairGenerator.getInstance("RSA");
            KeyPair clauRSA = generadorRSA.genKeyPair();

            // Enviar clave pública al cliente
            byte[] bytesClavePublica = clauRSA.getPublic().getEncoded();
            clavePrivadaServidor = clauRSA.getPrivate();
            out.writeInt(bytesClavePublica.length);
            out.write(bytesClavePublica);

            // Recibir clave pública del cliente
            byte[] bytesClavePublicaCliente = new byte[in.readInt()];
            in.readFully(bytesClavePublicaCliente);

            // Regenerar clave pública del cliente
            KeyFactory kf = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec x509Spec = new X509EncodedKeySpec(bytesClavePublicaCliente);
            clavePublicaCliente = kf.generatePublic(x509Spec);

            // Enviar mensaje cifrado de bienvenida
            String mensajeCifrado = cifrarMensaje("¡Bienvenido/a a la sala de chat!", clavePublicaCliente);
            out.writeUTF(mensajeCifrado);

            // Enviar mensaje cifrado para introducir el nombre del cliente
            String mensajeCifrado2 = cifrarMensaje("Introduce tu nombre:", clavePublicaCliente);
            out.writeUTF(mensajeCifrado2);

            // Recibir y descifrar el nombre del cliente
            String nombreCifrado = in.readUTF();
            nomClient = descifrarMensaje(nombreCifrado, clavePrivadaServidor);

            // Enviar mensaje cifrado de bienvenida personalizado
            String mensajeCifrado3 = cifrarMensaje("¡Hola " + nomClient + "! Comienza la conversación.", clavePublicaCliente);
            out.writeUTF(mensajeCifrado3);

            String inputLine;
            while ((inputLine = descifrarMensaje(in.readUTF(), clavePrivadaServidor)) != null) {
                if (inputLine.equals("exit")) {
                    break;
                }
                if (inputLine.startsWith("/p")) {
                    String[] tokens = inputLine.split(" ", 3);
                    if (tokens.length == 3) {
                        String destinatario = tokens[1];
                        String mensaje = tokens[2];
                        boolean destinatarioEncontrado = false;
                        for (ClientHandler client : server.clientes) {
                            if (client != this && client.out != null && client.nomClient.equals(destinatario)) {
                                // Cifrar el mensaje con la clave pública del destinatario
                                String mensajeCifradoEnviar = cifrarMensaje("[PRIVADO][" + nomClient + "]: " + mensaje, client.clavePublicaCliente);
                                client.out.writeUTF(mensajeCifradoEnviar);
                                String mensajeCifradoEnviar2 = cifrarMensaje("[PRIVADO][" + nomClient + "]: " + mensaje, clavePublicaCliente);
                                out.writeUTF(mensajeCifradoEnviar2);
                                destinatarioEncontrado = true;
                                break;
                            }
                        }
                        if (!destinatarioEncontrado) {
                            String mensajeCifradoEnviar = cifrarMensaje("No se encontró el usuario " + destinatario, clavePublicaCliente);
                            out.writeUTF(mensajeCifradoEnviar);
                        }
                    }else {
                    	String mensajeCifradoEnviar = cifrarMensaje("El format ha de ser el seguent: /p 'usuari' 'missatge'", clavePublicaCliente);
                        out.writeUTF(mensajeCifradoEnviar);
                    }
                } else if (inputLine.startsWith("/u")) {
                    StringJoiner joiner = new StringJoiner(", ");
                    for (ClientHandler client : server.clientes) {
                        if (client.nomClient != null) {
                            joiner.add(client.nomClient);
                        }
                    }
                    String mensajeCifradoEnviar = cifrarMensaje("Clientes Conectados: " + joiner, clavePublicaCliente);
                    out.writeUTF(mensajeCifradoEnviar);
                } else if (inputLine != null) {
                    for (ClientHandler client : server.clientes) {
                        if (client.nomClient != null) {
                            String textoCifrado = cifrarMensaje("[" + nomClient + "]: " + inputLine, client.clavePublicaCliente);
                            client.out.writeUTF(textoCifrado);
                        }
                    }
                }
            }

            // Notificar a los otros clientes que el cliente se ha desconectado
            for (ClientHandler client : server.clientes) {
                if (!client.nomClient.equals(nomClient)) {
                    String mensajeCifradoEnviar = cifrarMensaje("El cliente " + nomClient + " se ha desconectado.", client.clavePublicaCliente);
                    client.out.writeUTF(mensajeCifradoEnviar);
                }
            }

            System.out.println("El cliente " + nomClient + " se ha desconectado.");
            String mensajeCifradoEnviar = cifrarMensaje("Fin de la sesión", clavePublicaCliente);
            out.writeUTF(mensajeCifradoEnviar);

            socketClient.close();
        } catch (IOException | NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                | IllegalBlockSizeException | BadPaddingException | InvalidKeySpecException e) {
            System.out.println("Error en la comunicación con el cliente: " + e.getMessage());
        }
    }

    // Método para cifrar un mensaje utilizando AES
    public String cifrarMensaje(String mensaje, PublicKey clavePublicaCliente) throws NoSuchAlgorithmException,
	    NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
		Cipher cifradorRSA = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		cifradorRSA.init(Cipher.ENCRYPT_MODE, clavePublicaCliente);
		byte[] textoCifrado = cifradorRSA.doFinal(mensaje.getBytes());
		return Base64.getEncoder().encodeToString(textoCifrado);
	}

    // Método para descifrar un mensaje utilizando AES
    private static String descifrarMensaje(String mensajeCifrado, PrivateKey clavePrivadaServidor2) {
        try {
            Cipher cifradorRSA = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cifradorRSA.init(Cipher.DECRYPT_MODE, clavePrivadaServidor2);
            byte[] textoCifrado = Base64.getDecoder().decode(mensajeCifrado);
            byte[] textoDescifrado = cifradorRSA.doFinal(textoCifrado);
            return new String(textoDescifrado, "UTF-8");
        } catch (Exception e) {
            System.out.println("Error al descifrar el mensaje: " + e.getMessage());
        }
        return "";
    }
}