package util;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Properties;

/**
 * SSLUtil gère la configuration et l'initialisation SSL/TLS pour le serveur et le client.
 */
public class SSLUtil {

    private static boolean sslEnabled = false;    // Indique si SSL est activé
    private static SSLContext sslContext = null; // Contexte SSL initialisé

    /**
     * Initialise le contexte SSL/TLS à partir des propriétés fournies.
     *
     * @param config Les propriétés chargées (depuis un fichier config.properties).
     */
    public static void initSSL(Properties config) {
        // Vérifier si SSL est activé
        sslEnabled = Boolean.parseBoolean(config.getProperty("ssl.enabled", "false"));
        if (!sslEnabled) {
            System.out.println("SSL désactivé : le serveur et le client utiliseront des connexions non sécurisées.");
            return;
        }

        // Chemins et mots de passe des fichiers keystore/truststore
        String keystorePath = config.getProperty("ssl.keystore.path");
        String keystorePassword = config.getProperty("ssl.keystore.password");
        String truststorePath = config.getProperty("ssl.truststore.path");
        String truststorePassword = config.getProperty("ssl.truststore.password");

        if (keystorePath == null || keystorePath.isEmpty() || keystorePassword == null) {
            System.err.println("Chemin ou mot de passe du keystore non spécifié. SSL ne peut pas être initialisé.");
            sslEnabled = false;
            return;
        }

        if (truststorePath == null || truststorePath.isEmpty() || truststorePassword == null) {
            System.err.println("Chemin ou mot de passe du truststore non spécifié. SSL ne peut pas être initialisé.");
            sslEnabled = false;
            return;
        }

        // Charger le keystore et le truststore pour initialiser SSL
        try {
            sslContext = createSSLContext(keystorePath, keystorePassword, truststorePath, truststorePassword);
            System.out.println("SSL/TLS activé avec le keystore : " + keystorePath + " et le truststore : " + truststorePath);
        } catch (Exception e) {
            System.err.println("Erreur lors de l'initialisation SSL : " + e.getMessage());
            e.printStackTrace();
            sslEnabled = false;
        }
    }

    /**
     * Crée un contexte SSL/TLS en utilisant les fichiers keystore et truststore.
     *
     * @param keystorePath     Chemin vers le keystore (JKS/PKCS12).
     * @param keystorePassword Mot de passe du keystore.
     * @param truststorePath   Chemin vers le truststore (JKS/PKCS12).
     * @param truststorePassword Mot de passe du truststore.
     * @return SSLContext initialisé.
     */
    private static SSLContext createSSLContext(String keystorePath, String keystorePassword,
                                               String truststorePath, String truststorePassword)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException,
                   IOException, UnrecoverableKeyException, KeyManagementException {

        // Charger le keystore
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream keystoreStream = new FileInputStream(keystorePath)) {
            keyStore.load(keystoreStream, keystorePassword.toCharArray());
        }

        // Charger le truststore
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream truststoreStream = new FileInputStream(truststorePath)) {
            trustStore.load(truststoreStream, truststorePassword.toCharArray());
        }

        // Initialiser KeyManagerFactory pour le keystore
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keystorePassword.toCharArray());

        // Initialiser TrustManagerFactory pour le truststore
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        // Créer et initialiser le SSLContext
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
        return context;
    }

    /**
     * Indique si SSL est activé.
     *
     * @return true si SSL est activé, false sinon.
     */
    public static boolean isSslEnabled() {
        return sslEnabled;
    }

    /**
     * Retourne une fabrique pour créer des SSLServerSocket, si SSL est activé.
     *
     * @return Un SSLServerSocketFactory si SSL est activé, sinon null.
     */
    public static SSLServerSocketFactory getSSLServerSocketFactory() {
        if (!sslEnabled || sslContext == null) {
            System.err.println("SSL n'est pas activé ou le contexte SSL n'est pas initialisé.");
            return null;
        }
        return sslContext.getServerSocketFactory();
    }

    /**
     * Retourne une fabrique pour créer des SSLSocket, si SSL est activé.
     *
     * @return Un SSLSocketFactory si SSL est activé, sinon null.
     */
    public static SSLSocketFactory getSSLSocketFactory() {
        if (!sslEnabled || sslContext == null) {
            System.err.println("SSL n'est pas activé ou le contexte SSL n'est pas initialisé.");
            return null;
        }
        return sslContext.getSocketFactory();
    }
}
