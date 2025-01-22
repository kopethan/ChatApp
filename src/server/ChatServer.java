package server;

import util.SSLUtil;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Properties;

/**
 * Classe principale du serveur de chat.
 * <p>
 * Cette classe gère l'initialisation du serveur, la gestion des connexions clients,
 * la diffusion des messages, et l'intégration avec la base de données et les mécanismes de sécurité.
 * </p>
 * 
 * @author 
 * @version 1.0
 */
public class ChatServer {

    /**
     * Liste thread-safe des clients connectés au serveur.
     */
    private static final List<ClientHandler> connectedClients = new CopyOnWriteArrayList<>();

    /**
     * Indicateur pour contrôler l'état d'exécution du serveur.
     */
    private static volatile boolean running = false;

    /**
     * Socket du serveur pour accepter les connexions entrantes.
     */
    private static ServerSocket serverSocket;

    /**
     * Point d'entrée de l'application serveur de chat.
     * <p>
     * Cette méthode charge la configuration, initialise la base de données et les mécanismes de sécurité,
     * puis démarre le serveur sur le port spécifié.
     * </p>
     *
     * @param args Arguments de la ligne de commande (non utilisés).
     */
    public static void main(String[] args) {
        // Chargement des propriétés de configuration
        Properties config = new Properties();
        try (InputStream input = ChatServer.class.getResourceAsStream("/server/config.properties")) {
            if (input == null) {
                System.err.println("Impossible de trouver /server/config.properties");
                return;
            }
            config.load(input);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // Récupération des paramètres de la base de données depuis la configuration
        String dbUrl = config.getProperty("db.url");
        String dbUser = config.getProperty("db.user");
        String dbPassword = config.getProperty("db.password");
        DBManager.init(dbUrl, dbUser, dbPassword);

        // Initialisation de SSL/TLS selon la configuration
        SSLUtil.initSSL(config);
        boolean sslEnabled = SSLUtil.isSslEnabled();

        // Récupération du port du serveur depuis la configuration, avec une valeur par défaut de 12345
        int port = Integer.parseInt(config.getProperty("server.port", "12345"));

        // Démarrage du serveur
        try {
            startServer(port, sslEnabled);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Démarre le serveur sur le port spécifié avec ou sans SSL/TLS.
     * <p>
     * Cette méthode initialise le serveur socket, configure SSL si nécessaire,
     * et lance un thread pour accepter les connexions clients.
     * </p>
     *
     * @param port        Le port sur lequel le serveur écoutera les connexions entrantes.
     * @param sslEnabled  Indique si SSL/TLS doit être activé.
     * @throws IOException Si une erreur survient lors de la création du serveur socket.
     */
    public static void startServer(int port, boolean sslEnabled) throws IOException {
        // Vérification si le serveur est déjà en cours d'exécution
        if (running) {
            System.out.println("Le serveur est déjà en cours d'exécution.");
            return;
        }
        running = true;

        // Configuration du serveur avec ou sans SSL/TLS
        if (sslEnabled) {
            // Obtention de la fabrique de sockets SSL
            SSLServerSocketFactory sslFactory = SSLUtil.getSSLServerSocketFactory();
            if (sslFactory == null) {
                System.err.println("Impossible de créer SSLServerSocketFactory. Vérifiez config SSL.");
                return;
            }
            // Création du serveur SSL
            SSLServerSocket sslServerSocket = (SSLServerSocket) sslFactory.createServerSocket(port);
            serverSocket = sslServerSocket;
            System.out.println("Serveur SSL/TLS démarré sur le port " + port);
        } else {
            // Création du serveur en clair
            serverSocket = new ServerSocket(port);
            System.out.println("Serveur en clair démarré sur le port " + port);
        }

        // Thread pour accepter les connexions clients de manière asynchrone
        Thread acceptThread = new Thread(() -> {
            while (running) {
                try {
                    // Acceptation d'une nouvelle connexion client
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Nouvelle connexion client : " + clientSocket.getInetAddress());

                    // Création d'un gestionnaire de client pour la nouvelle connexion
                    ClientHandler clientHandler = new ClientHandler(
                            clientSocket,
                            ChatServer::broadcastMessage,
                            ChatServer::removeClient,
                            ChatServer::broadcastClientList
                    );
                    connectedClients.add(clientHandler);

                    // Démarrage d'un nouveau thread pour gérer le client
                    new Thread(clientHandler).start();

                } catch (IOException e) {
                    if (running) {
                        e.printStackTrace();
                    } else {
                        System.out.println("Serveur en cours d'arrêt...");
                    }
                }
            }
            System.out.println("Le serveur a été arrêté.");
        });
        acceptThread.start();
    }

    /**
     * Arrête le serveur en cours d'exécution.
     * <p>
     * Cette méthode ferme le serveur socket et déconnecte tous les clients connectés.
     * </p>
     */
    public static void stopServer() {
        // Vérification si le serveur est en cours d'exécution
        if (!running) {
            System.out.println("Le serveur n'est pas en cours d'exécution.");
            return;
        }
        running = false;
        try {
            // Fermeture du serveur socket
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            // Fermeture de toutes les connexions clients
            for (ClientHandler client : connectedClients) {
                client.closeConnection();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Diffuse un message à tous les clients connectés.
     * <p>
     * Cette méthode parcourt la liste des clients connectés et envoie le message à chacun d'eux.
     * </p>
     *
     * @param message Le message à diffuser.
     */
    public static void broadcastMessage(String message) {
        for (ClientHandler client : connectedClients) {
            client.sendMessage(message);
        }
    }

    /**
     * Diffuse la liste actuelle des clients connectés.
     * <p>
     * Cette méthode compile une chaîne de caractères contenant les noms des clients et leur heure de connexion,
     * puis la diffuse à tous les clients connectés.
     * </p>
     */
    public static void broadcastClientList() {
        StringBuilder sb = new StringBuilder("CLIENT_LIST:");
        for (ClientHandler client : connectedClients) {
            sb.append(client.getUserName()).append("|")
              .append(client.getConnectionTimeStr()).append(",");
        }
        // Suppression de la virgule finale si nécessaire
        if (sb.length() > 12 && sb.charAt(sb.length() - 1) == ',') {
            sb.deleteCharAt(sb.length() - 1);
        }
        // Diffusion de la liste des clients
        broadcastMessage(sb.toString());
    }

    /**
     * Retire un client de la liste des clients connectés et diffuse la mise à jour.
     * <p>
     * Cette méthode est appelée lorsqu'un client se déconnecte. Elle retire le client de la liste
     * et diffuse la nouvelle liste des clients restants.
     * </p>
     *
     * @param client Le client à retirer.
     */
    public static void removeClient(ClientHandler client) {
        connectedClients.remove(client);
        System.out.println("Client déconnecté : " + client.getUserName());
        broadcastClientList();
    }
}
