package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Gère la communication d'un client individuel avec le serveur de chat.
 * <p>
 * Cette classe gère l'authentification du client, la réception et l'envoi de messages,
 * ainsi que la gestion des commandes spécifiques telles que l'enregistrement, l'historique des messages,
 * et l'envoi de fichiers.
 * </p>
 * 
 * @author 
 * @version 1.0
 */
public class ClientHandler implements Runnable {

    /**
     * Socket du client connecté.
     */
    private Socket clientSocket;

    /**
     * Flux de sortie pour envoyer des messages au client.
     */
    private PrintWriter out;

    /**
     * Flux d'entrée pour recevoir des messages du client.
     */
    private BufferedReader in;

    /**
     * Fonction pour diffuser des messages à tous les clients connectés.
     */
    private Consumer<String> broadcastMessage;

    /**
     * Fonction pour retirer un client de la liste des clients connectés.
     */
    private Consumer<ClientHandler> removeClient;

    /**
     * Fonction pour diffuser la liste actuelle des clients connectés.
     */
    private Runnable broadcastClientList;

    /**
     * Nom d'utilisateur du client connecté.
     */
    private String userName;

    /**
     * Date et heure de connexion du client.
     */
    private LocalDateTime connectionTime;

    /**
     * Instance du processeur de commandes pour gérer les commandes spécifiques du client.
     */
    private CommandProcessor commandProcessor;

    /**
     * Constructeur de la classe ClientHandler.
     * <p>
     * Initialise les flux de communication et configure les fonctions de diffusion et de retrait.
     * </p>
     *
     * @param clientSocket       Socket du client connecté.
     * @param broadcastMessage   Fonction pour diffuser des messages à tous les clients.
     * @param removeClient       Fonction pour retirer un client de la liste des clients connectés.
     * @param broadcastClientList Fonction pour diffuser la liste actuelle des clients connectés.
     */
    public ClientHandler(Socket clientSocket,
                         Consumer<String> broadcastMessage,
                         Consumer<ClientHandler> removeClient,
                         Runnable broadcastClientList) {
        this.clientSocket = clientSocket;
        this.broadcastMessage = broadcastMessage;
        this.removeClient = removeClient;
        this.broadcastClientList = broadcastClientList;
        this.commandProcessor = new CommandProcessor(this);
    }

    /**
     * Méthode exécutée lorsqu'un thread est lancé pour gérer la communication avec le client.
     * <p>
     * Cette méthode gère l'authentification, la réception des messages, et la gestion des commandes.
     * Elle assure également la diffusion des messages reçus à tous les clients connectés.
     * </p>
     */
    @Override
    public void run() {
        try {
            // Initialisation des flux d'entrée et de sortie
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            // Authentification du client
            if (!authenticate()) {
                closeConnections();
                return;
            }

            // Stockage de la date et heure de connexion
            connectionTime = LocalDateTime.now();

            // Diffusion de l'arrivée du client
            broadcastMessage.accept(getTimestamp() + " " + userName + " a rejoint le chat.");
            DBManager.saveMessage("Server", userName + " a rejoint le chat.");
            broadcastClientList.run();

            String message;
            // Boucle principale pour recevoir et traiter les messages du client
            while ((message = in.readLine()) != null) {
                if (message.startsWith("/")) {
                    // Traitement des commandes spéciales
                    commandProcessor.processCommand(message);
                } else {
                    // Traitement des messages normaux
                    String finalMsg = getTimestamp() + " " + userName + ": " + message;
                    broadcastMessage.accept(finalMsg);
                    DBManager.saveMessage(userName, message);
                }
            }
        } catch (IOException e) {
            System.err.println("Client " + userName + " déconnecté ou erreur : " + e.getMessage());
        } finally {
            // Gestion de la déconnexion du client
            removeClient.accept(this);
            broadcastMessage.accept(getTimestamp() + " " + userName + " a quitté le chat.");
            DBManager.saveMessage("Server", userName + " a quitté le chat.");
            broadcastClientList.run();
            closeConnections();
        }
    }

    /**
     * Gère l'authentification du client en demandant un nom d'utilisateur et un mot de passe.
     *
     * @return {@code true} si l'authentification est réussie, {@code false} sinon.
     * @throws IOException Si une erreur d'entrée/sortie survient lors de la lecture des données.
     */
    private boolean authenticate() throws IOException {
        // Demande d'authentification au client
        out.println("Bienvenue! Veuillez vous authentifier.");
        out.println("Nom d'utilisateur:");
        String usernameInput = in.readLine();
        if (usernameInput == null || usernameInput.trim().isEmpty()) {
            out.println("Nom d'utilisateur invalide. Connexion terminée.");
            return false;
        }

        out.println("Mot de passe:");
        String passwordInput = in.readLine();
        if (passwordInput == null || passwordInput.trim().isEmpty()) {
            out.println("Mot de passe invalide. Connexion terminée.");
            return false;
        }

        // Validation des identifiants avec la base de données
        if (DBManager.validateUser(usernameInput, passwordInput)) {
            userName = usernameInput;
            out.println("Authentification réussie. Bienvenue " + userName + "!");
            return true;
        } else {
            out.println("Authentification échouée. Connexion terminée.");
            return false;
        }
    }

    /**
     * Permet à CommandProcessor de lire une ligne de message envoyée par le client.
     *
     * @return La ligne de message lue depuis le client, ou {@code null} si la connexion est fermée.
     * @throws IOException Si une erreur d'entrée/sortie survient lors de la lecture des données.
     */
    public String readMessage() throws IOException {
        return (in != null) ? in.readLine() : null;
    }

    /**
     * Envoie un message au client connecté.
     *
     * @param message Le message à envoyer.
     */
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    /**
     * Ferme les connexions d'entrée et de sortie ainsi que le socket du client.
     * <p>
     * Cette méthode est appelée lors de la déconnexion du client ou en cas d'erreur.
     * </p>
     */
    public void closeConnections() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Ferme la connexion du client de manière contrôlée.
     * <p>
     * Envoie un message au client indiquant qu'il va être déconnecté,
     * puis ferme les connexions.
     * </p>
     */
    public void closeConnection() {
        try {
            out.println("Vous allez être déconnecté...");
            closeConnections();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Retrait du client de la liste des clients connectés
            removeClient.accept(this);
        }
    }

    /**
     * Obtient le nom d'utilisateur du client connecté.
     *
     * @return Le nom d'utilisateur du client.
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Obtient la date et l'heure de connexion du client.
     *
     * @return La date et l'heure de connexion.
     */
    public LocalDateTime getConnectionTime() {
        return connectionTime;
    }

    /**
     * Renvoie l'heure de connexion formatée pour l'affichage dans la liste des clients connectés.
     *
     * @return Une chaîne de caractères représentant l'heure de connexion au format "yyyy-MM-dd HH:mm",
     *         ou une chaîne vide si la date de connexion n'est pas définie.
     */
    public String getConnectionTimeStr() {
        if (connectionTime == null) return "";
        return connectionTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    /**
     * Génère un horodatage actuel formaté pour l'affichage des messages.
     *
     * @return Une chaîne de caractères représentant l'horodatage au format "[yyyy-MM-dd HH:mm]".
     */
    private String getTimestamp() {
        LocalDateTime now = LocalDateTime.now();
        return "[" + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "]";
    }
}
