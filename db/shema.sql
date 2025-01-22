-- 1. Création des bases de données
CREATE DATABASE IF NOT EXISTS ChatApp;
CREATE DATABASE IF NOT EXISTS ChatAppTest;

-- 2. Création des tables
USE ChatApp;

CREATE TABLE IF NOT EXISTS Messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
);

USE ChatAppTest;

CREATE TABLE IF NOT EXISTS Messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 3. Création d'un utilisateur dédié et attribution des privilèges (optionnel)
CREATE USER IF NOT EXISTS 'chatuser'@'localhost' IDENTIFIED BY 'chatpass';
GRANT ALL PRIVILEGES ON ChatApp.* TO 'chatuser'@'localhost';
GRANT ALL PRIVILEGES ON ChatAppTest.* TO 'chatuser'@'localhost';
FLUSH PRIVILEGES;

-- Créer la base de données (si elle n'existe pas déjà)
CREATE DATABASE IF NOT EXISTS ChatApp;
USE ChatApp;

drop tables if exists Users;

-- Créer la table Users
CREATE TABLE IF NOT EXISTS Users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL, -- Il est recommandé de stocker des mots de passe hachés
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Créer la table Messages
CREATE TABLE IF NOT EXISTS Messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    message TEXT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE CASCADE
);

-- Insérer 10 utilisateurs dans la table Users
INSERT INTO Users (username, password) VALUES
('alice', 'ChatApp'),
('bob', 'ChatApp'),
('carol', 'ChatApp'),
('dave', 'ChatApp'),
('eve', 'ChatApp'),
('frank', 'ChatApp'),
('grace', 'ChatApp'),
('heidi', 'ChatApp'),
('ivan', 'ChatApp'),
('judy', 'ChatApp');

