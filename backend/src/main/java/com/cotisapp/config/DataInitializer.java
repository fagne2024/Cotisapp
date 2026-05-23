package com.cotisapp.config;

import com.cotisapp.domain.entity.Utilisateur;
import com.cotisapp.domain.entity.UtilisateurRole;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.repository.OrganisationRepository;
import com.cotisapp.repository.UtilisateurRepository;
import com.cotisapp.repository.UtilisateurRoleRepository;
import com.cotisapp.service.ActionDroitInitialisationService;
import com.cotisapp.service.CompteService;
import com.cotisapp.service.ExerciceService;
import com.cotisapp.service.RegleInitialisationService;
import com.cotisapp.service.TypeProfilInitialisationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    public static final String EMAIL_SUPERADMIN = "superadmin@cotisapp.sn";
    public static final String EMAIL_ADMIN = "admin@cotisapp.sn";

    @Value("${cotisapp.init.mdp-defaut:Admin@2026}")
    private String mdpDefaut;

    private final UtilisateurRepository utilisateurRepository;
    private final UtilisateurRoleRepository utilisateurRoleRepository;
    private final OrganisationRepository organisationRepository;
    private final PasswordEncoder passwordEncoder;
    private final RegleInitialisationService regleInitialisationService;
    private final TypeProfilInitialisationService typeProfilInitialisationService;
    private final ActionDroitInitialisationService actionDroitInitialisationService;
    private final ExerciceService exerciceService;
    private final CompteService compteService;

    @Override
    public void run(String... args) {
        actionDroitInitialisationService.assurerCatalogueActions();
        typeProfilInitialisationService.assurerTypesGlobaux();

        assurerUtilisateur(EMAIL_SUPERADMIN, "Super", "Admin", Role.SUPERADMIN);
        assurerUtilisateur(EMAIL_ADMIN, "Admin", "Plateforme", Role.SUPERADMIN);
        log.info("Comptes système : {} et {}", EMAIL_SUPERADMIN, EMAIL_ADMIN);

        organisationRepository.findAll().forEach(org -> {
            if (org.getExerciceCourantId() == null) {
                exerciceService.creerPremierExercice(org.getId());
            }
            typeProfilInitialisationService.initialiserTypesOrganisation(org.getId());
            regleInitialisationService.assurerReglesCotisation(org.getId());
            compteService.ensureCompteOrganisationInteret(org.getId());
            compteService.ensureCompteOrganisationAmendes(org.getId());
        });
    }

    private void assurerUtilisateur(String email, String prenom, String nom, Role role) {
        Utilisateur user = utilisateurRepository.findByEmail(email).orElseGet(() -> {
            log.info("Création du compte {}", email);
            return utilisateurRepository.save(Utilisateur.builder()
                    .email(email)
                    .motDePasse(passwordEncoder.encode(mdpDefaut))
                    .nom(nom)
                    .prenom(prenom)
                    .doitChangerMotDePasse(true)
                    .build());
        });
        // Ne pas écraser le mot de passe d'un compte existant
        user.setNom(nom);
        user.setPrenom(prenom);
        utilisateurRepository.save(user);

        if (utilisateurRoleRepository.findFirstByUtilisateurIdAndRoleOrderByIdAsc(user.getId(), role).isEmpty()) {
            utilisateurRoleRepository.save(UtilisateurRole.builder()
                    .utilisateurId(user.getId())
                    .role(role)
                    .build());
            log.info("Rôle {} assuré pour {}", role, email);
        }
    }
}
