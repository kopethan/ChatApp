package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Base64;
import java.nio.file.Files;

/**
 * Classe principale gérant l'interface graphique (UI) du client de chat.
 * <p>
 * Cette classe offre une fenêtre de connexion et une interface principale
 * permettant à l'utilisateur d'échanger des messages, d'envoyer des fichiers,
 * et de consulter la liste des autres clients connectés. Elle intègre également
 * un panneau d'émojis pour agrémenter la conversation.
 * </p>
 * 
 * <p>
 * Plusieurs zones importantes :
 * - Fenêtre de connexion (loginFrame) pour saisir nom d'utilisateur et mot de passe
 * - Fenêtre principale (mainFrame) pour afficher les messages, la liste des clients, et les contrôles d'envoi
 * - Panneau d'émojis pour insérer rapidement des symboles
 * - Panneau de messages (messagePanel) affichant les messages dans des boîtes colorées façon WhatsApp
 * </p>
 * 
 * @author 
 * @version 1.0
 */
public class ChatClientUI {

    /**
     * Fenêtre de connexion pour l'authentification.
     */
    private JFrame loginFrame;

    /**
     * Fenêtre principale de l'application client.
     */
    private JFrame mainFrame;

    /**
     * Champ de texte pour saisir le nom d'utilisateur.
     */
    private JTextField userField;

    /**
     * Champ de texte pour saisir le mot de passe (masqué).
     */
    private JPasswordField passField;

    /**
     * Étiquette pour afficher l'état d'authentification ou les erreurs.
     */
    private JLabel statusLabel;

    /**
     * Panneau principal pour afficher les messages reçus.
     */
    private JPanel messagePanel;

    /**
     * Composant permettant de faire défiler le panneau des messages.
     */
    private JScrollPane chatScrollPane;

    /**
     * Champ de texte pour saisir un message.
     */
    private JTextField messageField;

    /**
     * Bouton pour envoyer un message.
     */
    private JButton sendButton;

    /**
     * Bouton pour sélectionner et envoyer un fichier.
     */
    private JButton fileButton;

    /**
     * Panneau contenant les boutons d'émojis.
     */
    private JPanel emojiPanel;

    /**
     * Liste des clients connectés, affichée à gauche de l'interface.
     */
    private JList<String> clientList;

    /**
     * Modèle de la liste pour gérer dynamiquement les clients connectés.
     */
    private DefaultListModel<String> listModel;

    /**
     * File d'attente des messages en attente lorsque l'interface principale n'est pas encore initialisée.
     */
    private Queue<String> messageQueue = new LinkedList<>();

    /**
     * Instance du client de chat associé à cette interface.
     */
    private ChatClient chatClient;

    /**
     * Indique si l'interface principale a été initialisée.
     */
    private boolean isMainUIInitialized = false;

    /**
     * Constructeur de la classe ChatClientUI.
     * <p>
     * Initialise l'interface de connexion et associe le client de chat à cette interface.
     * </p>
     *
     * @param chatClient Le client de chat associé.
     */
    public ChatClientUI(ChatClient chatClient) {
        this.chatClient = chatClient;
        initLoginUI();
    }

    /**
     * Initialise la fenêtre de connexion (loginFrame).
     * <p>
     * Crée la fenêtre, ses composants, et associe un écouteur pour le bouton de connexion.
     * </p>
     */
    private void initLoginUI() {
        loginFrame = new JFrame("Authentification");
        loginFrame.setSize(400, 200);
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setLayout(new BorderLayout());

        JPanel panel = new JPanel(new GridLayout(3, 2, 10, 10));

        JLabel userLabel = new JLabel("Nom d'utilisateur:");
        userField = new JTextField();

        JLabel passLabel = new JLabel("Mot de passe:");
        passField = new JPasswordField();

        JButton loginButton = new JButton("Se connecter");
        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setForeground(Color.RED);

        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.add(userLabel);
        panel.add(userField);
        panel.add(passLabel);
        panel.add(passField);
        panel.add(new JLabel()); // Placeholder
        panel.add(loginButton);

        loginFrame.add(panel, BorderLayout.CENTER);
        loginFrame.add(statusLabel, BorderLayout.SOUTH);

        // Écouteur pour tenter la connexion
        loginButton.addActionListener(e -> attemptLogin());

        loginFrame.setLocationRelativeTo(null);
    }

    /**
     * Tente de se connecter avec le nom d'utilisateur et le mot de passe fournis.
     */
    private void attemptLogin() {
        String username = userField.getText().trim();
        String password = new String(passField.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Veuillez entrer un nom d'utilisateur et un mot de passe.");
            return;
        }

        statusLabel.setText("Authentification en cours...");

        chatClient.authenticate(username, password, new ChatClient.AuthListener() {
            @Override
            public void onAuthSuccess() {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Authentification réussie !");
                    loginFrame.dispose(); // Fermer la fenêtre de connexion
                    initMainUI();         // Initialiser et afficher l'interface principale
                });
            }

            @Override
            public void onAuthFailure(String message) {
                SwingUtilities.invokeLater(() -> {
                    String errorMessage = (message != null && !message.trim().isEmpty())
                            ? message
                            : "Échec de l'authentification.";
                    statusLabel.setText(errorMessage);
                });
            }
        });
    }

    /**
     * Initialise l'interface principale du client (mainFrame).
     * <p>
     * Crée la fenêtre, les panneaux pour les messages, la liste des clients, et les contrôles d'envoi.
     * </p>
     */
    private void initMainUI() {
        mainFrame = new JFrame("Chat Client - " + chatClient.getUsername());
        mainFrame.setSize(1000, 600);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setLayout(new BorderLayout());

        // Panneau des messages
        messagePanel = new JPanel();
        messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
        messagePanel.setBackground(Color.WHITE);

        chatScrollPane = new JScrollPane(messagePanel);
        chatScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // S'assure de scroller vers le bas lors du redimensionnement
        mainFrame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                scrollToBottom();
            }
        });

        // Liste des clients connectés
        listModel = new DefaultListModel<>();
        clientList = new JList<>(listModel);
        clientList.setCellRenderer(new ClientListRenderer());
        JScrollPane clientScrollPaneList = new JScrollPane(clientList);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(clientScrollPaneList, BorderLayout.CENTER);

        // Panneau d'émojis
        emojiPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        emojiPanel.setBorder(BorderFactory.createTitledBorder("Émojis"));
        String[] emojis = {"😀","😂","😍","👍","🎉","😎","😢","❤️","🔥","🌟"};
        for (String e : emojis) {
            JButton b = new JButton(e);
            b.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
            b.setMargin(new Insets(2, 2, 2, 2));
            b.addActionListener(ev -> insertEmoji(e));
            emojiPanel.add(b);
        }
        leftPanel.add(emojiPanel, BorderLayout.SOUTH);

        // Zone d'envoi (bas de la fenêtre)
        messageField = new JTextField();
        sendButton = new JButton("Envoyer");
        fileButton = new JButton("Fichier...");

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(messageField, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(fileButton);
        buttonPanel.add(sendButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        // Diviseur horizontal entre la liste des clients et la zone de messages
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, chatScrollPane);
        splitPane.setDividerLocation(250);

        mainFrame.add(splitPane, BorderLayout.CENTER);
        mainFrame.add(bottomPanel, BorderLayout.SOUTH);

        // Écouteurs pour envoyer un message ou sélectionner un fichier
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
        fileButton.addActionListener(e -> selectAndSendFile());

        mainFrame.setLocationRelativeTo(null);
        mainFrame.setVisible(true);

        isMainUIInitialized = true;

        // Affiche les messages en attente si l'interface n'était pas encore prête
        SwingUtilities.invokeLater(() -> {
            synchronized (messageQueue) {
                while (!messageQueue.isEmpty()) {
                    String msg = messageQueue.poll();
                    if (msg != null) {
                        addMessage(msg);
                    }
                }
            }
        });
    }

    /**
     * Insère un émoji dans le champ de saisie de message.
     *
     * @param emoji L'émoji à insérer.
     */
    private void insertEmoji(String emoji) {
        messageField.setText(messageField.getText() + " " + emoji);
        messageField.requestFocus();
    }

    /**
     * Envoie un message saisi dans le champ de texte.
     */
    private void sendMessage() {
        String txt = messageField.getText().trim();
        if (!txt.isEmpty()) {
            chatClient.sendMessage(txt);
            messageField.setText("");
        }
    }

    /**
     * Ouvre un sélecteur de fichiers et envoie le fichier sélectionné via le client.
     */
    private void selectAndSendFile() {
        JFileChooser fc = new JFileChooser();
        int res = fc.showOpenDialog(mainFrame);
        if (res == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            chatClient.sendFile(f);
        }
    }

    /**
     * Ajoute un message dans la file d'attente ou l'affiche immédiatement si l'interface est prête.
     *
     * @param rawMessage Le message brut à afficher.
     */
    public void appendMessage(String rawMessage) {
        if (!isMainUIInitialized) {
            synchronized (messageQueue) {
                messageQueue.add(rawMessage);
            }
        } else {
            addMessage(rawMessage);
        }
    }

    /**
     * Ajoute un message spécifique lié aux fichiers dans la file d'attente ou l'affiche immédiatement.
     * <p>
     * Alias pour {@code appendMessage}.
     * </p>
     *
     * @param rawMessage Le message brut lié à l'envoi de fichier.
     */
    public void appendFileMessage(String rawMessage) {
        appendMessage(rawMessage);
    }

    /**
     * Ajoute le message dans la zone de chat, dans une bulle colorée.
     *
     * @param rawMessage Le message brut à formater et afficher.
     */
    private void addMessage(String rawMessage) {
        SwingUtilities.invokeLater(() -> {
            JPanel bubble = createMessageBubble(rawMessage);
            messagePanel.add(bubble);
            messagePanel.add(Box.createVerticalStrut(10));
            scrollToBottom();
        });
    }

    /**
     * Crée un composant "bulle" pour afficher le message dans le style de discussion (type WhatsApp).
     *
     * @param rawMessage Le message brut incluant éventuellement l'heure et le nom de l'utilisateur.
     * @return Un panneau {@code JPanel} représentant la bulle de message.
     */
    private JPanel createMessageBubble(String rawMessage) {
        // Exemple : [2023-05-26 15:00] alice: coucou
        String timePart = "";
        String userPart = "Système";
        String msgPart = rawMessage;

        // Extraction de la partie heure si le message commence par "["
        if (rawMessage.startsWith("[")) {
            int closeBracket = rawMessage.indexOf("]");
            if (closeBracket > 0) {
                timePart = rawMessage.substring(0, closeBracket + 1);
                msgPart = rawMessage.substring(closeBracket + 1).trim();
            }
        }

        // Extraction du nom d'utilisateur
        int idx = msgPart.indexOf(":");
        if (idx > 0) {
            userPart = msgPart.substring(0, idx).trim();
            msgPart = msgPart.substring(idx + 1).trim();
        }

        // Conteneur global (pour alignement à gauche ou droite)
        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);

        // Bulle de message
        JPanel bubble = new JPanel(new BorderLayout());
        bubble.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        bubble.setOpaque(true);

        // Couleur de fond selon si l'utilisateur est le client local ou non
        boolean isMe = userPart.equals(chatClient.getUsername());
        Color bg = isMe ? new Color(220, 248, 198) : new Color(235, 235, 235);
        bubble.setBackground(bg);

        // Si le message correspond à un fichier
        if (msgPart.startsWith("/file ")) {
            processFileBubble(bubble, msgPart, userPart);
        } else {
            // Sinon, c'est du texte normal
            JLabel msgLabel = new JLabel("<html><p style='max-width: 300px; word-wrap: break-word;'>"
                    + msgPart.replaceAll("\n", "<br>") + "</p></html>");
            msgLabel.setFont(new Font("Arial", Font.PLAIN, 14));
            bubble.add(msgLabel, BorderLayout.CENTER);
        }

        // Afficher le nom de l'utilisateur (sauf pour soi-même et "Système")
        if (!isMe && !userPart.equals("Système")) {
            JLabel userLabel = new JLabel(userPart);
            userLabel.setFont(new Font("Arial", Font.BOLD, 12));
            userLabel.setForeground(Color.DARK_GRAY);
            bubble.add(userLabel, BorderLayout.NORTH);
        }

        // Afficher l'horodatage s'il existe
        if (!timePart.isEmpty()) {
            JLabel timeLabel = new JLabel(timePart);
            timeLabel.setFont(new Font("Arial", Font.ITALIC, 10));
            timeLabel.setForeground(Color.GRAY);
            bubble.add(timeLabel, BorderLayout.SOUTH);
        }

        // Alignement à droite pour soi-même, sinon à gauche
        if (isMe) {
            container.add(bubble, BorderLayout.EAST);
            container.setBorder(BorderFactory.createEmptyBorder(0, 50, 0, 10));
        } else {
            container.add(bubble, BorderLayout.WEST);
            container.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 50));
        }

        return container;
    }

    /**
     * Traite un message contenant un fichier encodé en Base64 et crée une bulle adaptée.
     *
     * @param bubble   Le panneau dans lequel le contenu du fichier sera ajouté.
     * @param msgPart  La partie du message correspondant à "/file <filename> <base64>".
     * @param username Le nom de l'utilisateur ayant envoyé le fichier (éventuellement utilisé en affichage).
     */
    private void processFileBubble(JPanel bubble, String msgPart, String username) {
        // Format : /file fileName base64
        String[] p = msgPart.split(" ", 3);
        if (p.length < 3) {
            bubble.add(new JLabel("Fichier invalide."), BorderLayout.CENTER);
            return;
        }
        String fileName = p[1];
        String base64 = p[2];

        if (isImageFile(fileName)) {
            // S'il s'agit d'une image, tenter de l'afficher
            try {
                byte[] fileBytes = Base64.getDecoder().decode(base64);
                ImageIcon icon = new ImageIcon(fileBytes);
                JLabel imgLabel = new JLabel(icon);
                bubble.add(imgLabel, BorderLayout.CENTER);
            } catch (Exception e) {
                bubble.add(new JLabel("Image invalide."), BorderLayout.CENTER);
            }
        } else {
            // Sinon, proposer un bouton pour télécharger le fichier
            JLabel info = new JLabel("Fichier : " + fileName);
            info.setFont(new Font("Arial", Font.PLAIN, 14));
            bubble.add(info, BorderLayout.CENTER);

            JButton downloadBtn = new JButton("Télécharger");
            downloadBtn.addActionListener(ev -> saveFile(fileName, base64));
            bubble.add(downloadBtn, BorderLayout.SOUTH);
        }
    }

    /**
     * Vérifie si un fichier est une image en se basant sur son extension.
     *
     * @param fname Le nom de fichier.
     * @return {@code true} si le fichier est une image (png, jpg, jpeg, gif), {@code false} sinon.
     */
    private boolean isImageFile(String fname) {
        String lower = fname.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif");
    }

    /**
     * Ouvre un {@link JFileChooser} pour que l'utilisateur sélectionne où sauvegarder un fichier.
     * Écrit le contenu décodé en Base64 dans le fichier sélectionné.
     *
     * @param fileName Le nom de fichier proposé.
     * @param base64   Le contenu encodé en Base64.
     */
    private void saveFile(String fileName, String base64) {
        byte[] content = Base64.getDecoder().decode(base64);
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(fileName));
        int res = chooser.showSaveDialog(mainFrame);
        if (res == JFileChooser.APPROVE_OPTION) {
            File sel = chooser.getSelectedFile();
            try {
                Files.write(sel.toPath(), content);
                JOptionPane.showMessageDialog(mainFrame, "Fichier enregistré : " + sel.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(mainFrame, "Erreur enregistrement.");
            }
        }
    }

    /**
     * Fait défiler la zone de chat jusqu'en bas pour afficher le dernier message.
     */
    private void scrollToBottom() {
        chatScrollPane.getVerticalScrollBar().setValue(chatScrollPane.getVerticalScrollBar().getMaximum());
        messagePanel.revalidate();
        messagePanel.repaint();
    }

    /**
     * Met à jour la liste des clients connectés (sous forme de boîtes vertes).
     * <p>
     * Les éléments sont des chaînes formatées "username|heureConnexion",
     * puis affichées via le {@link ClientListRenderer}.
     * </p>
     *
     * @param users Un tableau de chaînes, chacune contenant "username|heureConnexion".
     */
    public void updateClientList(String[] users) {
        SwingUtilities.invokeLater(() -> {
            listModel.clear();
            for (String u : users) {
                if (!u.isEmpty()) {
                    listModel.addElement(u);
                }
            }
        });
    }

    /**
     * Affiche la fenêtre de connexion (loginFrame) de manière asynchrone.
     */
    public void showLoginUI() {
        SwingUtilities.invokeLater(() -> loginFrame.setVisible(true));
    }

    /**
     * Rendu personnalisé pour la liste des clients connectés.
     * <p>
     * Chaque élément est au format "username|YYYY-MM-dd HH:mm". Le renderer sépare
     * le username et l'heure, puis les affiche en texte formaté HTML avec une couleur de fond verte.
     * </p>
     */
    private class ClientListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            // Format attendu : "username|YYYY-MM-dd HH:mm"
            String val = (String) value;
            int idx = val.indexOf("|");
            String uname = val, time = "";
            if (idx > 0) {
                uname = val.substring(0, idx);
                time = val.substring(idx + 1);
            }

            // Affichage en style HTML pour permettre retours à la ligne et styles
            label.setText("<html><b>" + uname + "</b><br><i style='color:gray'>" + time + "</i></html>");
            label.setBackground(new Color(210, 255, 210));

            if (isSelected) {
                label.setBackground(new Color(160, 240, 160));
            }
            label.setOpaque(true);

            return label;
        }
    }
}
