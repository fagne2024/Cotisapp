package com.cotisapp.service;

import com.cotisapp.config.StorageProperties;
import com.cotisapp.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrganisationLogoStorageService {

    private static final long MAX_LOGO_SIZE = 2L * 1024 * 1024;
    private static final Set<String> EXTENSIONS_AUTORISEES = Set.of("png", "jpg", "jpeg", "webp", "svg");

    private final StorageProperties storageProperties;

    public record LogoTelechargement(Resource resource, String typeMime) {}

    public String enregistrer(Long orgId, MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new BusinessException("Aucun fichier logo fourni");
        }
        validerFichier(fichier);

        String extension = extension(nettoyerNom(fichier.getOriginalFilename()));
        String cheminRelatif = "org-logos/" + orgId + "/logo." + extension;
        Path cible = racineUpload().resolve(cheminRelatif).normalize();
        if (!cible.startsWith(racineUpload())) {
            throw new BusinessException("Chemin de stockage invalide");
        }

        try {
            Files.createDirectories(cible.getParent());
            Files.copy(fichier.getInputStream(), cible, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("Impossible d'enregistrer le logo");
        }
        return cheminRelatif;
    }

    public LogoTelechargement preparerTelechargement(String cheminRelatif) {
        if (!StringUtils.hasText(cheminRelatif)) {
            throw new BusinessException("Cette organisation n'a pas de logo");
        }
        Path fichier = racineUpload().resolve(cheminRelatif).normalize();
        if (!fichier.startsWith(racineUpload().normalize())) {
            throw new BusinessException("Chemin de fichier invalide");
        }
        if (!Files.exists(fichier)) {
            throw new BusinessException("Fichier logo introuvable sur le serveur");
        }
        try {
            Resource resource = new UrlResource(fichier.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new BusinessException("Fichier logo illisible");
            }
            String typeMime = Files.probeContentType(fichier);
            if (typeMime == null) {
                typeMime = typeMimeDepuisExtension(extension(fichier.getFileName().toString()));
            }
            return new LogoTelechargement(resource, typeMime);
        } catch (IOException e) {
            throw new BusinessException("Fichier logo introuvable");
        }
    }

    public void supprimerSiPresent(String cheminRelatif) {
        if (!StringUtils.hasText(cheminRelatif)) {
            return;
        }
        try {
            Path fichier = racineUpload().resolve(cheminRelatif).normalize();
            if (fichier.startsWith(racineUpload().normalize())) {
                Files.deleteIfExists(fichier);
            }
        } catch (IOException ignored) {
            // nettoyage best-effort
        }
    }

    private void validerFichier(MultipartFile fichier) {
        if (fichier.getSize() > MAX_LOGO_SIZE) {
            throw new BusinessException("Le logo dépasse la taille maximale autorisée (2 Mo)");
        }
        String nom = nettoyerNom(fichier.getOriginalFilename());
        String ext = extension(nom);
        if (!EXTENSIONS_AUTORISEES.contains(ext)) {
            throw new BusinessException("Format non autorisé. Utilisez PNG, JPEG, WebP ou SVG.");
        }
    }

    private String nettoyerNom(String nom) {
        if (!StringUtils.hasText(nom)) {
            return "logo.png";
        }
        return Paths.get(nom).getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String extension(String nom) {
        int i = nom.lastIndexOf('.');
        if (i < 0 || i == nom.length() - 1) {
            return "png";
        }
        return nom.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private String typeMimeDepuisExtension(String ext) {
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            default -> "application/octet-stream";
        };
    }

    private Path racineUpload() {
        return Paths.get(storageProperties.getUploadDir()).toAbsolutePath().normalize();
    }
}
