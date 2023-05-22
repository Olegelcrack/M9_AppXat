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
        String host = "192.168.56.1"; //Posem la ip del servidor
        int port = 12345; //El port del servidor
        client clientXat = new client();

        clientXat.run(host, port);
    }

    public void run(String host, int port) throws NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
        try {
            Socket socket = new Socket(host, port); //Creem un socket per connectar-nos  al servidor
            System.out.println("Connectat al servidor: " + host + " al port: " + port);
           //El in ens serveix per detectar els missatges que rebem del servidor i el out serveix per enviar els missatges al servidor
            in = new DataInputStream(new DataInputStream(socket.getInputStream()));
            out = new DataOutputStream(socket.getOutputStream());

            // Generem un par de claus una pública i una privada per desxifrar el que ens retorni el servidor
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            PublicKey clauPublicaClient = keyPair.getPublic();
            PrivateKey clauPrivadaClient = keyPair.getPrivate();

            // Enviem la nostra clau publica al client per a que així pugui xifrar els missatges amb la nostra clau pública
            byte[] bytesClauPublicaClient = clauPublicaClient.getEncoded();
            out.writeInt(bytesClauPublicaClient.length);
            out.write(bytesClauPublicaClient);

            // Rebem la clau pública del servidor per poder xifrar tot el que li enviem amb la seva clau pública
            byte[] bytesClauPublicaServidor = new byte[in.readInt()];
            in.readFully(bytesClauPublicaServidor);

            // Regenerem la clau pública del servidor
            KeyFactory kf = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec x509Spec = new X509EncodedKeySpec(bytesClauPublicaServidor);
            clauPublicaServidor = kf.generatePublic(x509Spec);

            // Iniciem un thread per poder llegir els missatges del servidor
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String inputLine;
                        while ((inputLine = in.readUTF()) != null) {
                            // Desxifrem els missatges que ens envia el servidor els quals estan xifrats amb la nostra publica i els desxifrem amb la nostra privada
                            String missatgeDesxifrat = desxifrarMissatge(inputLine, clauPrivadaClient);
                            System.out.println(missatgeDesxifrat);
                        }
                    } catch (IOException e) {
                        System.out.println("Error al llegir els missatges del servidor: " + e.getMessage());
                    }
                }
            }).start();

            // Enviar missatges al servidor
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String inputLine;
            while ((inputLine = br.readLine()) != null) { //En cas d'escriure /e sortim i sino enviem  el missatge al servidor
                if (inputLine.equals("/e")) {
                    break;
                }

                // Xifrem  el missatge que escrivim amb la pública del servidor abans d'enviar el missatge
                String missatgeXifrat = xifrarMissatge(inputLine, clauPublicaServidor);

                // Enviar el missatge xifrat al servidor
                out.writeUTF(missatgeXifrat);
            }

            // Tanquem la connexió amb el servidor
            socket.close();
            System.out.println("Conexió tancada.");
        } catch (UnknownHostException e) {
            System.out.println("Servidor desconegut: " + host + " al port: " + port);
        } catch (IOException e) {
            System.out.println("Error al establir la  connexió: " + e.getMessage());
        } catch (InvalidKeySpecException e) {
            System.out.println("Error al generar la clau pública: " + e.getMessage());
        }
    }

 // Mètode per xifrar el missatge utilitzant AES
    public String xifrarMissatge(String mensaje, PublicKey clauPublicaServidor) throws NoSuchAlgorithmException,
	    NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
		Cipher xifradorRSA = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		xifradorRSA.init(Cipher.ENCRYPT_MODE, clauPublicaServidor); //Xifrem el missatge amb la pública del client
		byte[] textoCifrado = xifradorRSA.doFinal(mensaje.getBytes()); //Obtenim el missatge amb bytes i el xifrem
		return Base64.getEncoder().encodeToString(textoCifrado); //Pasem el missatge a string i el retornem per enviar-lo al servidor
	}
    //Mètode per desxifrar el missatge que rebem del servidor amb la nostra clau privada
    private static String desxifrarMissatge(String mensajeCifrado, PrivateKey clauPrivadaClient) {
        try {
            Cipher desxifradorRSA = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            desxifradorRSA.init(Cipher.DECRYPT_MODE, clauPrivadaClient); //Desxifrem el missatge amb la privada
            byte[] textoCifrado = Base64.getDecoder().decode(mensajeCifrado); // obtenim  en bytes el texte xifrat
            byte[] textoDescifrado = desxifradorRSA.doFinal(textoCifrado); //Obtenim en bytes el desxifrat  del texte xifrat
            return new String(textoDescifrado, "UTF-8"); //Retornem en string el texte desxifrat
        } catch (Exception e) {
            System.out.println("Error al desxifrar el missatge: " + e.getMessage());
        }
        return "";
    }
}