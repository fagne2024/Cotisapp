-- Réinitialise entièrement la base CotisApp (MySQL).
-- Exécuter en tant qu'utilisateur ayant le droit DROP/CREATE DATABASE.
--
-- Exemple :
--   mysql -u root -p < scripts/reset-database.sql
--
-- Puis redémarrer le backend : Flyway recrée le schéma et DataInitializer
-- ne crée que les utilisateurs par défaut (aucune organisation démo).

DROP DATABASE IF EXISTS cotisapp;

CREATE DATABASE cotisapp
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
