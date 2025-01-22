package server;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

/**
 * Traite les commandes spéciales envoyées par le client.
 * <p>
 * Cette classe gère les différentes commandes que les clients peuvent envoyer,
 * telles que la déconnexion, l'affichage de l'aide, l'enregistrement d'un nouvel utilisateur,
 * la récupération de l'historique des messages, et l'envoi de fichiers.
 * </p>
 * 
 * @author 
 * @version 1.0
 */
public class CommandProcessor {

    /**
     * Référence au gestionnaire de client associé.
     */
    private ClientHandler clientHandler;

    /**
     * Constructeur de la classe CommandProcessor.
     * <p>
     * Initialise le processeur de commandes avec le gestionnaire de client correspondant.
     * </p>
     *
     * @param clientHandler Le gestionnaire de client associé à ce processeur de commandes.
     */
    public CommandProcessor(ClientHandler clientHandler) {
        this.clientHandler = clientHandler;
    }

    /**
     * Traite une commande envoyée par le client.
     * <p>
     * Cette méthode détermine le type de commande et appelle la méthode appropriée pour la gérer.
     * </p>
     *
     * @param command La commande envoyée par le client.
     */
    public void processCommand(String command) {
        if (command.equalsIgnoreCase("/quit")) {
            handleQuit();
        } else if (command.equalsIgnoreCase("/help")) {
            handleHelp();
        } else if (command.startsWith("/register")) {
            handleRegister();
        } else if (command.startsWith("/history")) {
            handleHistory(command);
        } else if (command.startsWith("/file ")) {
            handleFile(command);
        } else {
            clientHandler.sendMessage("Commande inconnue. Tapez /help pour la liste des commandes.");
        }
    }

    /**
     * Gère la commande de déconnexion (/quit).
     * <p>
     * Envoie un message de déconnexion au client et ferme sa connexion.
     * </p>
     */
    private void handleQuit() {
        clientHandler.sendMessage("Déconnexion en cours...");
        clientHandler.closeConnection();
    }

    /**
     * Gère la commande d'affichage de l'aide (/help).
     * <p>
     * Envoie au client une liste des commandes disponibles et leur description.
     * </p>
     */
    private void handleHelp() {
        clientHandler.sendMessage("Liste des commandes disponibles :");
        clientHandler.sendMessage("/quit - Se déconnecter du chat");
        clientHandler.sendMessage("/help - Afficher cette aide");
        clientHandler.sendMessage("/register - Enregistrer un nouvel utilisateur");
        clientHandler.sendMessage("/history [n] - Afficher les derniers n messages");
        clientHandler.sendMessage("/file <fileName> <base64> - Envoyer un fichier encodé");
    }

    /**
     * Gère la commande d'enregistrement d'un nouvel utilisateur (/register).
     * <p>
     * Demande au client de fournir un nom d'utilisateur et un mot de passe,
     * puis tente de les enregistrer dans la base de données.
     * </p>
     */
    private void handleRegister() {
        try {
            // Demande du nom d'utilisateur au client
            clientHandler.sendMessage("Veuillez entrer un nom d'utilisateur pour l'enregistrement :");
            String newUsername = clientHandler.readMessage();
            if (newUsername == null || newUsername.trim().isEmpty()) {
                clientHandler.sendMessage("Nom d'utilisateur invalide. Enregistrement annulé.");
                return;
            }

            // Demande du mot de passe au client
            clientHandler.sendMessage("Veuillez entrer un mot de passe :");
            String newPassword = clientHandler.readMessage();
            if (newPassword == null || newPassword.trim().isEmpty()) {
                clientHandler.sendMessage("Mot de passe invalide. Enregistrement annulé.");
                return;
            }

            // Tentative d'enregistrement de l'utilisateur dans la base de données
            boolean registrationSuccess = DBManager.registerUser(newUsername.trim(), newPassword.trim());
            if (registrationSuccess) {
                clientHandler.sendMessage("Enregistrement réussi. Vous pouvez maintenant vous connecter.");
            } else {
                clientHandler.sendMessage("Échec de l'enregistrement. L'utilisateur existe peut-être déjà.");
            }

        } catch (IOException e) {
            clientHandler.sendMessage("Erreur lors de l'enregistrement.");
            e.printStackTrace();
        }
    }

    /**
     * Gère la commande de récupération de l'historique des messages (/history).
     * <p>
     * Demande au client combien de messages il souhaite récupérer (optionnel),
     * puis envoie l'historique des messages correspondant.
     * </p>
     *
     * @param command La commande complète envoyée par le client.
     */
    private void handleHistory(String command) {
        int limit = 10; // Nombre par défaut de messages à récupérer
        String[] parts = command.split(" ");
        if (parts.length > 1) {
            try {
                limit = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                clientHandler.sendMessage("Format invalide. /history [n]");
                return;
            }
        }

        // Récupération de l'historique des messages depuis la base de données
        List<String> history = DBManager.getHistory(limit);
        if (history.isEmpty()) {
            clientHandler.sendMessage("Aucun message dans l'historique.");
        } else {
            clientHandler.sendMessage("Derniers " + limit + " messages :");
            for (String line : history) {
                clientHandler.sendMessage(line);
            }
        }
    }

    /**
     * Gère la commande d'envoi de fichier (/file).
     * <p>
     * Valide le contenu Base64 du fichier, puis diffuse le fichier à tous les clients connectés.
     * </p>
     *
     * @param command La commande complète envoyée par le client.
     */
    private void handleFile(String command) {
        String[] parts = command.split(" ", 3);
        if (parts.length < 3) {
            clientHandler.sendMessage("Format invalide. /file <filename> <base64>");
            return;
        }
        String fileName = parts[1];
        String base64Content = parts[2];

        try {
            // Vérification de la validité du contenu Base64
            Base64.getDecoder().decode(base64Content);
        } catch (IllegalArgumentException e) {
            clientHandler.sendMessage("Contenu Base64 invalide pour " + fileName);
            return;
        }

        // Construction du message final pour la diffusion
        String finalMsg = clientHandler.getUserName() + ": " + command;
        // Diffusion du message à tous les clients connectés via le serveur
        server.ChatServer.broadcastMessage(finalMsg);
        // Confirmation de la réception du fichier au client
        clientHandler.sendMessage("Fichier " + fileName + " reçu et diffusé.");
    }
}
