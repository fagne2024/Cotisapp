package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.Role;
import com.cotisapp.domain.enums.TypeEvenementJournal;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class JournalUtilisateurResponse {
    private Long id;
    private Long organisationId;
    private Long utilisateurId;
    private String utilisateurEmail;
    private String utilisateurNom;
    private Role role;
    private Long membreId;
    private String action;
    private TypeEvenementJournal typeEvenement;
    private String typeEvenementLibelle;
    private String moduleCode;
    private String moduleLibelle;
    private String routePath;
    private String details;
    private String ipAddress;
    private String userAgent;
    /** Navigateur et OS lisibles (ex. Chrome · Windows). */
    private String navigateurResume;
    private Boolean succes;
    private LocalDateTime dateCreation;
    private String libelleResume;
}
