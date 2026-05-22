package com.cotisapp.service;

import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.Utilisateur;
import com.cotisapp.domain.entity.UtilisateurRole;
import com.cotisapp.domain.enums.PosteMembre;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.TypeProfilRepository;
import com.cotisapp.repository.UtilisateurRepository;
import com.cotisapp.repository.UtilisateurRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Liaison membre ↔ utilisateur (un membre avec accès est toujours un utilisateur MEMBRE).
 */
@Service
@RequiredArgsConstructor
public class MembreCompteAccesService {

    private final UtilisateurRepository utilisateurRepository;
    private final UtilisateurRoleRepository utilisateurRoleRepository;
    private final MembreRepository membreRepository;
    private final TypeProfilRepository typeProfilRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivationEmailService activationEmailService;

    @Transactional
    public Long creerCompteAccesPourMembre(
            Long organisationId,
            Membre membre,
            String email,
            PosteMembre poste,
            Long typeProfilId,
            boolean compteActif,
            boolean envoyerEmailActivation) {
        if (membre.getUtilisateurId() != null) {
            throw new BusinessException("Ce membre a déjà un compte utilisateur");
        }
        MembreService.appliquerTelephoneNormalise(membre);
        if (membre.getTelephoneNormalise() == null) {
            throw new BusinessException("Un numéro de téléphone valide est requis pour l'accès membre");
        }

        List<Membre> membresMemeTel = membreRepository.findByTelephoneNormaliseAndActifTrue(
                membre.getTelephoneNormalise());
        var compteExistant = membresMemeTel.stream()
                .filter(m -> m.getUtilisateurId() != null)
                .filter(m -> !Objects.equals(m.getId(), membre.getId()))
                .findFirst();
        if (compteExistant.isPresent()) {
            return lierCompteTelephoneExistant(organisationId, membre, compteExistant.get(), poste, typeProfilId);
        }

        String emailNorm = email.trim().toLowerCase();
        if (utilisateurRepository.existsByEmail(emailNorm)) {
            throw new BusinessException("Un utilisateur avec cet email existe déjà");
        }

        PosteMembre posteEffectif = poste != null ? poste : membre.getPoste();
        if (poste != null) {
            membre.setPoste(posteEffectif);
        }

        Utilisateur user = utilisateurRepository.save(Utilisateur.builder()
                .email(emailNorm)
                .motDePasse(passwordEncoder.encode(ActivationEmailService.MDP_MEMBRE_INITIAL))
                .prenom(membre.getPrenom())
                .nom(membre.getNom())
                .telephone(membre.getTelephone())
                .telephoneNormalise(membre.getTelephoneNormalise())
                .actif(compteActif)
                .doitChangerMotDePasse(true)
                .build());

        membre.setUtilisateurId(user.getId());
        if (membre.getEmail() == null || membre.getEmail().isBlank()) {
            membre.setEmail(emailNorm);
        }
        membreRepository.save(membre);

        Long profilId = resoudreTypeProfilId(organisationId, typeProfilId, posteEffectif);
        utilisateurRoleRepository.save(UtilisateurRole.builder()
                .utilisateurId(user.getId())
                .role(Role.MEMBRE)
                .organisationId(organisationId)
                .membreId(membre.getId())
                .typeProfilId(profilId)
                .build());

        if (envoyerEmailActivation) {
            activationEmailService.envoyerMotDePasseMembre(
                    user.getEmail(), user.getPrenom(), ActivationEmailService.MDP_MEMBRE_INITIAL);
        }
        return user.getId();
    }

    @Transactional
    public void supprimerCompteAccesMembre(Long organisationId, Long membreId) {
        Membre membre = membreRepository.findByIdAndOrganisationId(membreId, organisationId)
                .orElseThrow(() -> new BusinessException("Membre introuvable"));
        Long utilisateurId = membre.getUtilisateurId();
        if (utilisateurId == null) {
            return;
        }

        // Détacher le membre avant de supprimer l'utilisateur (contrainte fk_membre_user).
        membre.setUtilisateurId(null);
        membreRepository.saveAndFlush(membre);

        utilisateurRoleRepository.findByUtilisateurId(utilisateurId).stream()
                .filter(ur -> ur.getRole() == Role.MEMBRE && Objects.equals(ur.getOrganisationId(), organisationId))
                .forEach(utilisateurRoleRepository::delete);

        if (utilisateurRoleRepository.findByUtilisateurId(utilisateurId).isEmpty()) {
            utilisateurRepository.deleteById(utilisateurId);
        }
    }

    /**
     * Plusieurs fiches membre peuvent partager le même téléphone et le même compte utilisateur
     * (mot de passe unique, cloisonnement par fiche membre à la connexion).
     */
    private Long lierCompteTelephoneExistant(
            Long organisationId,
            Membre membre,
            Membre membreReferent,
            PosteMembre poste,
            Long typeProfilId) {
        Long utilisateurId = membreReferent.getUtilisateurId();
        PosteMembre posteEffectif = poste != null ? poste : membre.getPoste();
        if (poste != null) {
            membre.setPoste(posteEffectif);
        }

        membre.setUtilisateurId(utilisateurId);
        if (membre.getEmail() == null || membre.getEmail().isBlank()) {
            membre.setEmail(membreReferent.getEmail());
        }
        membreRepository.save(membre);

        boolean dejaLie = utilisateurRoleRepository.findByUtilisateurId(utilisateurId).stream()
                .anyMatch(ur -> ur.getRole() == Role.MEMBRE
                        && Objects.equals(ur.getMembreId(), membre.getId()));
        if (!dejaLie) {
            Long profilId = resoudreTypeProfilId(organisationId, typeProfilId, posteEffectif);
            utilisateurRoleRepository.save(UtilisateurRole.builder()
                    .utilisateurId(utilisateurId)
                    .role(Role.MEMBRE)
                    .organisationId(organisationId)
                    .membreId(membre.getId())
                    .typeProfilId(profilId)
                    .build());
        }

        return utilisateurId;
    }

    private Long resoudreTypeProfilId(Long organisationId, Long typeProfilId, PosteMembre poste) {
        if (typeProfilId != null) {
            var tp = typeProfilRepository
                    .findById(typeProfilId)
                    .filter(t -> t.getOrganisationId() == null
                            || Objects.equals(t.getOrganisationId(), organisationId))
                    .orElseThrow(() -> new BusinessException("Type de profil invalide pour cette organisation"));
            if (tp.getRole() != Role.MEMBRE) {
                throw new BusinessException(
                        "Seuls les profils « membre » peuvent être assignés à un compte membre de bureau");
            }
            if (tp.getPosteMembre() != null
                    && tp.getPosteMembre() != PosteMembre.SIMPLE
                    && poste != null
                    && poste != PosteMembre.SIMPLE
                    && tp.getPosteMembre() != poste) {
                throw new BusinessException(
                        "Le profil « "
                                + tp.getLibelle()
                                + " » ne correspond pas au poste sélectionné. Choisissez un profil aligné sur le poste.");
            }
            return typeProfilId;
        }
        return typeProfilRepository
                .findFirstByOrganisationIdAndRoleAndPosteMembre(organisationId, Role.MEMBRE, poste)
                .map(com.cotisapp.domain.entity.TypeProfil::getId)
                .orElse(null);
    }
}
