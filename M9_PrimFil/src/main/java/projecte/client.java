
package projecte;

import java.io.*;
import java.net.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

public class client {
    //private String nomClient;
    private BufferedReader in;
    public static PrintWriter out;
    private SecretKey clau;

    public static void main(String[] args) {
        String host = "192.168.1.116";
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

           
            clau = generarClau();
            // Iniciar thread per llegir els missatges del servidor
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String inputLine;
                        while ((inputLine = in.readLine()) != null) {
                            // Desxifrar el missatge rebu amb la clau generada
                            String missatgeDesxifrat = desxifrarMissatge(inputLine,inputLine.getBytes(), clau);
                            System.out.println(missatgeDesxifrat);
                        }
                    } catch (IOException e) {
                        System.out.println("Error en llegir els missatges del servidor: " + e.getMessage());
                    } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
                        System.out.println("Error en desxifrar el missatge: " + e.getMessage());
                    }
                }
            }).start();

            // Enviar missatges al servidor
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String inputLine;
            while ((inputLine = br.readLine()) != null) {
                if (inputLine.equals("/e")) {
                    break;
                }

                // Xifrar el missatge amb la clau generada abans d'enviar-lo
                byte[] missatgeXifrat = xifrarMissatge(inputLine, clau);
                out.println(new String(missatgeXifrat));
            }

            // Tancar la connexió
            socket.close();
            System.out.println("Connexió tancada.");
        } catch (UnknownHostException e) {
            System.out.println("Servidor desconegut: " + host + " al port: " + port);
        } catch (IOException e) {
            System.out.println("Error en establir la connexió al servidor: " + e.getMessage());
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            System.out.println("Error en xifrar el missatge: " + e.getMessage());
        }
    }
    
    private String desxifrarMissatge(String inputLine,byte[] missatgeXifrat, SecretKey clau) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        try{
            Cipher desxifrat = Cipher.getInstance("AES");
            desxifrat.init(Cipher.DECRYPT_MODE, clau);
            byte[] missatgeDesxifrat = desxifrat.doFinal(missatgeXifrat);
            return new String(missatgeDesxifrat);
        }catch(Exception e){
            return inputLine;
        }
    }
    
    private byte[] xifrarMissatge(String missatge, SecretKey clau) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        Cipher xifrat = Cipher.getInstance("AES");
        xifrat.init(Cipher.ENCRYPT_MODE, clau);
        byte[] missatgeXifrat = xifrat.doFinal(missatge.getBytes());
        return missatgeXifrat;
    }
    
    private SecretKey generarClau() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(128);
        return keyGen.generateKey();
    }
}
