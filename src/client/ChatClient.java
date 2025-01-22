package client;

import util.SSLUtil;

import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Properties;

/**
 * Classe principale du client de chat.
 * <p>
 * Cette classe gère la connexion au serveur de chat, l'authentification de l'utilisateur,
 * l'envoi et la réception de messages, ainsi que la gestion des fichiers et de l'interface utilisateur.
 * </p>
 * 
 * <p>
 * Utilise des sockets SSL/TLS pour sécuriser les communications lorsque cela est configuré.
 * </p>
 * 
 * @author 
 * @version 1.0
 */
public class ChatClient {
    
    /**
     * Adresse du serveur de chat.
     */
    private String host;
    
    /**
     * Port du serveur de chat.
     */
    private int port;
    
    /**
     * Indique si SSL/TLS est activé pour les communications.
     */
    private boolean sslEnabled;

    /**
     * Socket utilisé pour la connexion au serveur.
     */
    private Socket socket;
    
    /**
     * Flux de lecture pour recevoir des messages du serveur.
     */
    private BufferedReader in;
    
    /**
     * Flux d'écriture pour envoyer des messages au serveur.
     */
    private PrintWriter out;

    /**
     * Interface utilisateur associée au client de chat.
     */
    private ChatClientUI chatClientUI;
    
    /**
     * Nom d'utilisateur authentifié.
     */
    private String username;

    /**
     * Constructeur de la classe ChatClient.
     * <p>
     * Initialise le client en chargeant la configuration nécessaire.
     * </p>
     */
    public ChatClient() {
        loadConfig();
    }

    /**
     * Charge la configuration du client depuis un fichier de propriétés.
     * <p>
     * Cette méthode lit les paramètres de configuration tels que l'adresse du serveur,
     * le port, et la configuration SSL/TLS à partir du fichier {@code config.properties}.
     * Si le fichier de configuration n'est pas trouvé ou qu'une erreur survient, des valeurs par défaut sont utilisées.
     * </p>
     */
    private void loadConfig() {
        Properties config = new Properties();
        try (var input = ChatClient.class.getResourceAsStream("/client/config.properties")) {
            if (input == null) {
                System.err.println("Impossible de charger /client/config.properties");
                // Valeurs par défaut si le fichier de configuration n'est pas trouvé
                host = "localhost";
                port = 12345;
                sslEnabled = false;
                return;
            }
            config.load(input);

            // Récupération des paramètres de configuration
            host = config.getProperty("client.server.host", "localhost");
            port = Integer.parseInt(config.getProperty("client.server.port", "12345"));

            // Initialisation de SSL/TLS selon la configuration
            SSLUtil.initSSL(config);
            sslEnabled = SSLUtil.isSslEnabled();

        } catch (IOException e) {
            // Gestion des exceptions liées au chargement de la configuration
            e.printStackTrace();
            // Valeurs par défaut en cas d'erreur
            host = "localhost";
            port = 12345;
            sslEnabled = false;
        }
    }

    /**
     * Associe l'interface utilisateur au client de chat.
     *
     * @param chatClientUI L'instance de l'interface utilisateur à associer.
     */
    public void setChatClientUI(ChatClientUI chatClientUI) {
        this.chatClientUI = chatClientUI;
    }

    /**
     * Authentifie l'utilisateur auprès du serveur de chat.
     * <p>
     * Cette méthode tente d'authentifier l'utilisateur en envoyant son nom d'utilisateur et son mot de passe
     * au serveur. Les résultats de l'authentification sont communiqués via un écouteur {@code AuthListener}.
     * </p>
     *
     * @param username  Le nom d'utilisateur à authentifier.
     * @param password  Le mot de passe associé.
     * @param callback  L'écouteur pour gérer les résultats de l'authentification.
     */
    public void authenticate(String username, String password, AuthListener callback) {
        new Thread(() -> {
            try {
                // Connexion au serveur
                connectToServer();
                // Initialisation des flux de communication
                initStreams();

                // Lecture des messages d'accueil du serveur
                String welcome = in.readLine();
                System.out.println("Serveur : " + welcome);
                
                // Demande du nom d'utilisateur
                String userPrompt = in.readLine();
                System.out.println("Serveur : " + userPrompt);
                out.println(username);

                // Demande du mot de passe
                String passPrompt = in.readLine();
                System.out.println("Serveur : " + passPrompt);
                out.println(password);

                // Réponse d'authentification du serveur
                String authResponse = in.readLine();
                System.out.println("Serveur : " + authResponse);

                if (authResponse != null && authResponse.startsWith("Authentification réussie")) {
                    // Si l'authentification est réussie
                    this.username = username;
                    callback.onAuthSuccess();
                    // Démarrage de la réception des messages
                    receiveMessages();
                } else {
                    // Si l'authentification échoue
                    callback.onAuthFailure(authResponse);
                    close();
                }
            } catch (IOException e) {
                // Gestion des exceptions liées à la connexion et à l'authentification
                e.printStackTrace();
                callback.onAuthFailure("Erreur de connexion au serveur.");
            }
        }).start();
    }

    /**
     * Établit la connexion au serveur de chat.
     * <p>
     * Utilise une connexion SSL/TLS si elle est configurée, sinon une connexion en clair.
     * </p>
     *
     * @throws IOException Si une erreur survient lors de la connexion au serveur.
     */
    private void connectToServer() throws IOException {
        if (sslEnabled) {
            // Création d'une socket SSL sécurisée
            SSLSocketFactory sslSocketFactory = SSLUtil.getSSLSocketFactory();
            if (sslSocketFactory == null) {
                throw new IOException("SSL activé mais SSLUtil n'est pas initialisé.");
            }
            socket = sslSocketFactory.createSocket(host, port);
            System.out.println("Connexion SSL établie sur " + host + ":" + port);
        } else {
            // Création d'une socket en clair
            socket = new Socket(host, port);
            System.out.println("Connexion en clair établie sur " + host + ":" + port);
        }
    }

    /**
     * Initialise les flux d'entrée et de sortie pour la communication avec le serveur.
     *
     * @throws IOException Si une erreur survient lors de l'initialisation des flux.
     */
    private void initStreams() throws IOException {
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
    }

    /**
     * Gère la réception des messages du serveur de chat.
     * <p>
     * Cette méthode écoute en continu les messages envoyés par le serveur et les transmet
     * à l'interface utilisateur appropriée.
     * </p>
     */
    private void receiveMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                // Détection de la liste des clients connectés
                if (message.startsWith("CLIENT_LIST:")) {
                    // Format : CLIENT_LIST:username1|time1,username2|time2,...
                    String data = message.substring(12);
                    String[] users = data.split(",");
                    chatClientUI.updateClientList(users);
                } else if (message.contains(": /file ")) {
                    // Format : "username: /file filename base64"
                    // Gestion de l'affichage des fichiers côté UI
                    chatClientUI.appendFileMessage(message);
                } else {
                    // Message normal
                    chatClientUI.appendMessage(message);
                }
            }
        } catch (IOException e) {
            // Gestion des exceptions liées à la réception des messages
            e.printStackTrace();
        } finally {
            // Fermeture de la connexion en cas de fin de flux ou d'erreur
            close();
        }
    }

    /**
     * Envoie un message au serveur de chat.
     *
     * @param msg Le message à envoyer.
     */
    public void sendMessage(String msg) {
        if (out != null && msg != null && !msg.trim().isEmpty()) {
            out.println(msg);
        }
    }

    /**
     * Envoie un fichier au serveur de chat.
     * <p>
     * Lit le contenu du fichier, l'encode en Base64, et envoie une commande spéciale au serveur.
     * </p>
     *
     * @param file Le fichier à envoyer.
     */
    public void sendFile(File file) {
        if (file == null || !file.exists()) return;
        try {
            // Lecture des octets du fichier
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            // Encodage des octets en Base64
            String base64 = Base64.getEncoder().encodeToString(fileBytes);
            // Construction de la commande d'envoi de fichier
            String command = "/file " + file.getName() + " " + base64;
            out.println(command);
        } catch (IOException e) {
            // Gestion des exceptions liées à l'envoi de fichiers
            e.printStackTrace();
        }
    }

    /**
     * Ferme la connexion au serveur de chat et les flux associés.
     * <p>
     * Cette méthode ferme les flux d'entrée et de sortie ainsi que la socket de connexion.
     * </p>
     */
    public void close() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("Client déconnecté.");
        } catch (IOException e) {
            // Gestion des exceptions liées à la fermeture des connexions
            e.printStackTrace();
        }
    }

    /**
     * Obtient le nom d'utilisateur authentifié.
     *
     * @return Le nom d'utilisateur.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Point d'entrée de l'application client de chat.
     * <p>
     * Initialise le client et l'interface utilisateur, puis affiche l'interface de connexion.
     * </p>
     *
     * @param args Arguments de la ligne de commande (non utilisés).
     */
    public static void main(String[] args) {
        // Création de l'instance du client de chat
        ChatClient client = new ChatClient();
        // Création de l'interface utilisateur associée
        ChatClientUI ui = new ChatClientUI(client);
        // Association de l'interface utilisateur au client
        client.setChatClientUI(ui);
        // Affichage de l'interface de connexion
        ui.showLoginUI();
    }

    /**
     * Interface d'écoute pour les résultats d'authentification.
     * <p>
     * Les implémentations de cette interface définissent les actions à entreprendre en cas de succès ou d'échec de l'authentification.
     * </p>
     */
    public interface AuthListener {
        /**
         * Appelé lorsque l'authentification réussit.
         */
        void onAuthSuccess();

        /**
         * Appelé lorsque l'authentification échoue.
         *
         * @param message Le message d'erreur associé à l'échec de l'authentification.
         */
        void onAuthFailure(String message);
    }
}
