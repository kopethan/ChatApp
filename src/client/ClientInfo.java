package client;

/**
 * Représente les informations d'un client connecté au chat.
 * <p>
 * Cette classe encapsule les détails d'un client, notamment son nom d'utilisateur
 * et le moment de sa connexion. Elle est utilisée pour afficher la liste des clients
 * connectés dans l'interface utilisateur.
 * </p>
 * 
 * <p>
 * Les instances de cette classe sont créées et gérées par l'interface utilisateur du client
 * pour maintenir et afficher les informations des clients connectés.
 * </p>
 * 
 * @author 
 * @version 1.0
 */
public class ClientInfo {
    
    /**
     * Nom d'utilisateur du client.
     */
    private String name;
    
    /**
     * Heure de connexion du client au format "yyyy-MM-dd HH:mm".
     */
    private String connectionTime;

    /**
     * Constructeur de la classe ClientInfo.
     * <p>
     * Initialise un nouvel objet ClientInfo avec le nom d'utilisateur et l'heure de connexion spécifiés.
     * </p>
     *
     * @param name            Le nom d'utilisateur du client.
     * @param connectionTime  L'heure de connexion du client au format "yyyy-MM-dd HH:mm".
     */
    public ClientInfo(String name, String connectionTime) {
        this.name = name;
        this.connectionTime = connectionTime;
    }

    /**
     * Obtient le nom d'utilisateur du client.
     *
     * @return Le nom d'utilisateur du client.
     */
    public String getName() {
        return name;
    }

    /**
     * Obtient l'heure de connexion du client.
     *
     * @return L'heure de connexion du client au format "yyyy-MM-dd HH:mm".
     */
    public String getConnectionTime() {
        return connectionTime;
    }

    /**
     * Retourne une représentation sous forme de chaîne de caractères de l'objet ClientInfo.
     * <p>
     * Le format retourné est : "nom (Connecté à heure_de_connexion)".
     * </p>
     *
     * @return Une chaîne décrivant le client et son heure de connexion.
     */
    @Override
    public String toString() {
        return name + " (Connecté à " + connectionTime + ")";
    }
}
