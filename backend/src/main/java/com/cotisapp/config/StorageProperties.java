package com.cotisapp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "cotisapp.storage")
public class StorageProperties {

    /** Répertoire racine des fichiers uploadés (relatif ou absolu). */
    private String uploadDir = "./uploads";

    /** Taille max d'un relevé en octets (défaut 10 Mo). */
    private long maxFileSize = 10L * 1024 * 1024;
}
