package projecte;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;

import javax.crypto.*;
import javax.crypto.spec.*;

public class client {
    private String nomClient;
    private DataInputStream in;
    public static DataOutputStream out;
    private PublicKey clauPublicaServidor;

    public static void main(String[] args) throws NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
        String host = "192.168.56.1";
        int port = 12345;
        client clientXat = new client();

        clientXat.run(host, port);
    }

    public void run(String host, int port) throws NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
        try {
            Socket socket = new Socket(host, port);
            System.out.println("Connectat al servidor: " + host + " al port: " + port);

            in = new DataInputStream(new DataInputStream(socket.getInputStream()));
            out = new DataOutputStream(socket.getOutputStream());

            // Generar el par de claves RSA del cliente
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            PublicKey clauPublicaCliente = keyPair.getPublic();
            PrivateKey clauPrivadaCliente = keyPair.getPrivate();

            // Enviar clave pública del cliente al servidor
            byte[] bytesClavePublicaCliente = clauPublicaCliente.getEncoded();
            out.writeInt(bytesClavePublicaCliente.length);
            out.write(bytesClavePublicaCliente);

            // Recibir clave pública del servidor
            byte[] bytesClavePublicaServidor = new byte[in.readInt()];
            in.readFully(bytesClavePublicaServidor);

            // Regenerar clave pública del servidor
            KeyFactory kf = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec x509Spec = new X509EncodedKeySpec(bytesClavePublicaServidor);
            clauPublicaServidor = kf.generatePublic(x509Spec);

            // Iniciar thread para leer los mensajes del servidor
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String inputLine;
                        while ((inputLine = in.readUTF()) != null) {
                            // Descifrar el mensaje recibido con la clave AES
                            String missatgeDesxifrat = descifrarMensaje(inputLine, clauPrivadaCliente);
                            System.out.println(missatgeDesxifrat);
                        }
                    } catch (IOException e) {
                        System.out.println("Error al leer los mensajes del servidor: " + e.getMessage());
                    }
                }
            }).start();

            // Enviar mensajes al servidor
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String inputLine;
            while ((inputLine = br.readLine()) != null) {
                if (inputLine.equals("/e")) {
                    break;
                }

                // Cifrar el mensaje con la clave pública del servidor antes de enviarlo
                String missatgeXifrat = cifrarMensaje(inputLine, clauPublicaServidor);

                // Enviar el mensaje cifrado al servidor
                out.writeUTF(missatgeXifrat);
            }

            // Cerrar la conexión
            socket.close();
            System.out.println("Conexión cerrada.");
        } catch (UnknownHostException e) {
            System.out.println("Servidor desconocido: " + host + " al puerto: " + port);
        } catch (IOException e) {
            System.out.println("Error al establecer la conexión al servidor: " + e.getMessage());
        } catch (InvalidKeySpecException e) {
            System.out.println("Error al generar la clave pública: " + e.getMessage());
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

    private static String descifrarMensaje(String mensajeCifrado, PrivateKey clavePrivadaCliente) {
        try {
            Cipher cifradorRSA = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cifradorRSA.init(Cipher.DECRYPT_MODE, clavePrivadaCliente);
            byte[] textoCifrado = Base64.getDecoder().decode(mensajeCifrado);
            byte[] textoDescifrado = cifradorRSA.doFinal(textoCifrado);
            return new String(textoDescifrado, "UTF-8");
        } catch (Exception e) {
            System.out.println("Error al descifrar el mensaje: " + e.getMessage());
        }
        return "";
    }
}