package server;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Gère les interactions avec la base de données MySQL.
 * <p>
 * Cette classe s'occupe de l'initialisation de la connexion à la base de données,
 * de la validation des utilisateurs, de l'enregistrement de nouveaux utilisateurs,
 * de la sauvegarde des messages échangés, et de la récupération de l'historique des messages.
 * </p>
 * 
 * <p>
 * Utilise JDBC pour interagir avec la base de données et assure une gestion sécurisée
 * des données grâce à l'utilisation de requêtes préparées pour prévenir les attaques par injection SQL.
 * </p>
 * 
 * <p>
 * Les mots de passe des utilisateurs sont stockés en clair dans cet exemple, mais il est fortement recommandé
 * d'utiliser un mécanisme de hachage (comme SHA-256) pour sécuriser les mots de passe dans un environnement de production.
 * </p>
 * 
 * @author 
 * @version 1.0
 */
public class DBManager {

    /**
     * Connexion active à la base de données.
     */
    private static Connection connection;

    /**
     * Initialise la connexion à la base de données MySQL.
     * <p>
     * Cette méthode établit une connexion à la base de données en utilisant les
     * paramètres fournis et stocke la connexion pour une utilisation ultérieure.
     * </p>
     *
     * @param url      L'URL de connexion JDBC à la base de données.
     * @param user     Le nom d'utilisateur pour se connecter à la base de données.
     * @param password Le mot de passe correspondant à l'utilisateur.
     */
    public static void init(String url, String user, String password) {
        try {
            // Établissement de la connexion à la base de données
            connection = DriverManager.getConnection(url, user, password);
            System.out.println("Connexion à la base de données établie.");
        } catch (SQLException e) {
            // Gestion des exceptions liées à la connexion
            e.printStackTrace();
        }
    }

    /**
     * Valide les identifiants d'un utilisateur.
     * <p>
     * Cette méthode vérifie si le nom d'utilisateur et le mot de passe fournis
     * correspondent à un enregistrement existant dans la base de données.
     * </p>
     *
     * @param username Le nom d'utilisateur à valider.
     * @param password Le mot de passe associé.
     * @return {@code true} si les identifiants sont valides, {@code false} sinon.
     */
    public static boolean validateUser(String username, String password) {
        if (connection == null) return false;
        String sql = "SELECT * FROM Users WHERE username = ? AND password = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            // Paramétrage de la requête pour éviter les injections SQL
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();
            // Vérifie si un enregistrement correspond aux identifiants
            return rs.next();
        } catch (SQLException e) {
            // Gestion des exceptions liées à la requête
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Enregistre un nouvel utilisateur dans la base de données.
     * <p>
     * Cette méthode ajoute un nouvel utilisateur avec un nom d'utilisateur et un mot de passe
     * à la table {@code Users} si le nom d'utilisateur n'est pas déjà pris.
     * </p>
     *
     * @param username Le nom d'utilisateur à enregistrer.
     * @param password Le mot de passe associé.
     * @return {@code true} si l'enregistrement est réussi, {@code false} sinon.
     */
    public static boolean registerUser(String username, String password) {
        if (connection == null) return false;
        String checkSql = "SELECT id FROM Users WHERE username = ?";
        try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
            // Vérifie si le nom d'utilisateur existe déjà
            checkStmt.setString(1, username);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next()) {
                // L'utilisateur existe déjà
                return false;
            }
        } catch (SQLException e) {
            // Gestion des exceptions liées à la vérification
            e.printStackTrace();
            return false;
        }

        String insertSql = "INSERT INTO Users (username, password) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(insertSql)) {
            // Paramétrage de la requête d'insertion
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            // Gestion des exceptions liées à l'insertion
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Sauvegarde un message échangé dans la base de données.
     * <p>
     * Cette méthode enregistre un message associé à un utilisateur spécifique avec un horodatage.
     * </p>
     *
     * @param userId  Le nom d'utilisateur qui a envoyé le message.
     * @param message Le contenu du message à sauvegarder.
     */
    public static void saveMessage(String userId, String message) {
        if (connection == null) return;
        // Requête pour obtenir l'ID de l'utilisateur à partir du nom d'utilisateur
        String sql = "SELECT id FROM Users WHERE username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int uid = rs.getInt("id");
                // Requête pour insérer le message dans la table Messages
                String insertSql = "INSERT INTO Messages (user_id, message, timestamp) VALUES (?, ?, NOW())";
                try (PreparedStatement pstmt = connection.prepareStatement(insertSql)) {
                    pstmt.setInt(1, uid);
                    pstmt.setString(2, message);
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            // Gestion des exceptions liées à l'enregistrement des messages
            e.printStackTrace();
        }
    }

    /**
     * Récupère l'historique des messages depuis la base de données.
     * <p>
     * Cette méthode récupère les derniers {@code limit} messages échangés,
     * triés par ordre décroissant de date et heure.
     * </p>
     *
     * @param limit Le nombre maximum de messages à récupérer.
     * @return Une liste de chaînes de caractères représentant l'historique des messages.
     */
    public static List<String> getHistory(int limit) {
        List<String> history = new ArrayList<>();
        if (connection == null) return history;

        String sql = "SELECT m.timestamp, u.username, m.message " +
                     "FROM Messages m JOIN Users u ON m.user_id = u.id " +
                     "ORDER BY m.timestamp DESC LIMIT ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            // Paramétrage de la requête pour limiter le nombre de messages
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("timestamp");
                String uname = rs.getString("username");
                String msg = rs.getString("message");
                // Formatage de chaque message avec l'horodatage et le nom de l'utilisateur
                history.add("[" + ts.toLocalDateTime().toString().replace('T',' ') + "] " 
                            + uname + ": " + msg);
            }
        } catch (SQLException e) {
            // Gestion des exceptions liées à la récupération des messages
            e.printStackTrace();
        }
        return history;
    }
}
