package com.cotisapp.service;

import com.cotisapp.config.StorageProperties;
import com.cotisapp.domain.entity.ReleveBancaire;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.ReleveBancaireRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReleveBancaireStorageService {

    private static final Set<String> EXTENSIONS_AUTORISEES =
            Set.of("pdf", "png", "jpg", "jpeg", "webp");

    private final StorageProperties storageProperties;
    private final ReleveBancaireRepository releveBancaireRepository;

    @Transactional
    public ReleveBancaire enregistrer(Long orgId, Long operationId, MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            return null;
        }
        validerFichier(fichier);

        String nomOriginal = nettoyerNom(fichier.getOriginalFilename());
        String extension = extension(nomOriginal);
        String nomStocke = operationId + "_" + UUID.randomUUID() + "." + extension;
        Path racine = racineUpload();
        Path dossierOrg = racine.resolve(String.valueOf(orgId));
        Path cible = dossierOrg.resolve(nomStocke);

        try {
            Files.createDirectories(dossierOrg);
            Files.copy(fichier.getInputStream(), cible, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("Impossible d'enregistrer le relevé bancaire");
        }

        String cheminRelatif = orgId + "/" + nomStocke;
        ReleveBancaire releve = ReleveBancaire.builder()
                .organisationId(orgId)
                .operationId(operationId)
                .nomFichier(nomOriginal)
                .cheminStockage(cheminRelatif)
                .typeMime(fichier.getContentType())
                .tailleOctets(fichier.getSize())
                .build();
        return releveBancaireRepository.save(releve);
    }

    public record ReleveTelechargement(Resource resource, String nomFichier, String typeMime) {}

    @Transactional(readOnly = true)
    public ReleveTelechargement preparerTelechargement(Long orgId, Long releveId) {
        ReleveBancaire releve = releveBancaireRepository
                .findByIdAndOrganisationId(releveId, orgId)
                .orElseThrow(() -> new BusinessException("Relevé bancaire introuvable"));

        Path fichier = racineUpload().resolve(releve.getCheminStockage()).normalize();
        if (!fichier.startsWith(racineUpload().normalize())) {
            throw new BusinessException("Chemin de fichier invalide");
        }
        if (!Files.exists(fichier)) {
            throw new BusinessException("Fichier du relevé introuvable sur le serveur");
        }
        try {
            Resource resource = new UrlResource(fichier.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new BusinessException("Fichier du relevé illisible");
            }
            return new ReleveTelechargement(resource, releve.getNomFichier(), releve.getTypeMime());
        } catch (MalformedURLException e) {
            throw new BusinessException("Fichier du relevé introuvable");
        }
    }

    private void validerFichier(MultipartFile fichier) {
        if (fichier.getSize() > storageProperties.getMaxFileSize()) {
            throw new BusinessException("Le relevé dépasse la taille maximale autorisée (10 Mo)");
        }
        String nom = nettoyerNom(fichier.getOriginalFilename());
        String ext = extension(nom);
        if (!EXTENSIONS_AUTORISEES.contains(ext)) {
            throw new BusinessException("Format non autorisé. Utilisez PDF, PNG ou JPEG.");
        }
    }

    private String nettoyerNom(String nom) {
        if (!StringUtils.hasText(nom)) {
            return "releve.pdf";
        }
        return Paths.get(nom).getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String extension(String nom) {
        int i = nom.lastIndexOf('.');
        if (i < 0 || i == nom.length() - 1) {
            return "";
        }
        return nom.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private Path racineUpload() {
        return Paths.get(storageProperties.getUploadDir()).toAbsolutePath().normalize();
    }
}
