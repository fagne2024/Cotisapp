package com.cotisapp.service;

import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.Utilisateur;
import com.cotisapp.domain.entity.UtilisateurRole;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.dto.request.ChangeMotDePasseInitialRequest;
import com.cotisapp.dto.request.LoginRequest;
import com.cotisapp.dto.request.VerifyTwoFactorRequest;
import com.cotisapp.dto.response.AuthResponse;
import com.cotisapp.dto.response.CompteMembreLoginDto;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.security.OrganisationContext;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.OrganisationRepository;
import com.cotisapp.repository.UtilisateurRepository;
import com.cotisapp.repository.UtilisateurRoleRepository;
import com.cotisapp.security.CustomUserDetails;
import com.cotisapp.security.JwtClaims;
import com.cotisapp.security.JwtService;
import com.cotisapp.security.TotpPolicy;
import com.cotisapp.util.TelephoneUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UtilisateurRepository utilisateurRepository;
    private final UtilisateurRoleRepository utilisateurRoleRepository;
    private final MembreRepository membreRepository;
    private final OrganisationRepository organisationRepository;
    private final JournalService journalService;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final PresenceService presenceService;

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String identifiant = request.getIdentifiant().trim();
        ContexteConnexion ctx;
        try {
            ctx = resoudreContexte(
                    identifiant,
                    request.getOrganisationId(),
                    request.getMembreId(),
                    request.getRoleSouhaite());
        } catch (BusinessException ex) {
            journalService.enregistrerConnexionEchec(
                    request.getOrganisationId(), identifiant, ex.getMessage(), httpRequest);
            throw ex;
        }

        String motDePasse = request.getMotDePasse() != null ? request.getMotDePasse().trim() : "";
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(ctx.utilisateur().getEmail(), motDePasse));
        } catch (AuthenticationException ex) {
            journalService.enregistrerConnexionEchec(
                    ctx.organisationId(),
                    identifiant,
                    "Mot de passe incorrect ou compte invalide",
                    httpRequest);
            throw ex;
        }
        CustomUserDetails user = (CustomUserDetails) auth.getPrincipal();

        Utilisateur u = utilisateurRepository.findByEmail(user.getUsername()).orElseThrow();
        String orgNom = null;
        if (ctx.organisationId() != null) {
            orgNom = organisationRepository.findById(ctx.organisationId())
                    .map(com.cotisapp.domain.entity.Organisation::getNom)
                    .orElse(null);
        }

        journalService.enregistrerConnexionReussie(
                ctx.organisationId(),
                u.getId(),
                ctx.role(),
                ctx.membreId(),
                "Connexion via " + (TelephoneUtil.estEmail(identifiant) ? "email" : "téléphone"),
                httpRequest);

        JwtClaims claims = new JwtClaims(
                user.getEmail(),
                user.getUserId(),
                ctx.role(),
                ctx.organisationId(),
                ctx.membreId());

        if (Boolean.TRUE.equals(u.getTotpEnabled()) && u.getTotpSecret() != null) {
            String pendingToken = jwtService.generatePending2faToken(claims);
            return AuthResponse.builder()
                    .requiresTwoFactor(true)
                    .twoFactorToken(pendingToken)
                    .userId(u.getId())
                    .email(u.getEmail())
                    .nomComplet(u.getPrenom() + " " + u.getNom())
                    .role(ctx.role())
                    .organisationId(ctx.organisationId())
                    .organisationNom(orgNom)
                    .membreId(ctx.membreId())
                    .mustChangePassword(Boolean.TRUE.equals(u.getDoitChangerMotDePasse()))
                    .mustSetupTwoFactor(TotpPolicy.mustSetupTwoFactor(ctx.role(), u))
                    .build();
        }

        String token = jwtService.generateToken(claims);
        return buildAuthResponse(u, ctx, token, orgNom);
    }

    @Transactional
    public void deconnexion(HttpServletRequest request) {
        Long userId = OrganisationContext.getUserId();
        Long orgId = OrganisationContext.getOrganisationId();
        if (userId != null) {
            journalService.enregistrerDeconnexion(orgId, userId, request);
        }
    }

    @Transactional
    public AuthResponse verifyTwoFactor(VerifyTwoFactorRequest request, HttpServletRequest httpRequest) {
        String pendingToken = request.getTwoFactorToken().trim();
        if (!jwtService.isPending2faToken(pendingToken)) {
            throw new BusinessException("Session expirée. Reconnectez-vous avec votre mot de passe.");
        }
        JwtClaims claims = jwtService.extractClaims(pendingToken);
        Utilisateur u = utilisateurRepository.findById(claims.userId())
                .orElseThrow(() -> new BusinessException("Compte introuvable"));
        if (!Boolean.TRUE.equals(u.getActif())) {
            throw new BusinessException("Compte suspendu");
        }
        if (!Boolean.TRUE.equals(u.getTotpEnabled())) {
            throw new BusinessException("La double authentification n'est pas activée sur ce compte");
        }
        if (!totpService.verifyUtilisateur(u, request.getCode())) {
            throw new BusinessException("Code incorrect. Vérifiez Google Authenticator et réessayez.");
        }

        String orgNom = null;
        if (claims.organisationId() != null) {
            orgNom = organisationRepository.findById(claims.organisationId())
                    .map(com.cotisapp.domain.entity.Organisation::getNom)
                    .orElse(null);
        }

        journalService.enregistrerConnexionReussie(
                claims.organisationId(),
                u.getId(),
                claims.role(),
                claims.membreId(),
                "Connexion validée par double authentification",
                httpRequest);

        String token = jwtService.generateToken(claims);
        ContexteConnexion ctx = new ContexteConnexion(u, claims.role(), claims.organisationId(), claims.membreId());
        return buildAuthResponse(u, ctx, token, orgNom);
    }

    @Transactional
    public AuthResponse changerMotDePasseInitial(ChangeMotDePasseInitialRequest request) {
        Long userId = OrganisationContext.getUserId();
        if (userId == null) {
            throw new BusinessException("Utilisateur non authentifié");
        }
        Utilisateur u = utilisateurRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Compte introuvable"));
        if (!Boolean.TRUE.equals(u.getDoitChangerMotDePasse())) {
            throw new BusinessException("Le changement de mot de passe initial n'est pas requis");
        }
        if (!request.getNouveauMotDePasse().equals(request.getConfirmationMotDePasse())) {
            throw new BusinessException("La confirmation ne correspond pas au nouveau mot de passe");
        }
        if (ActivationEmailService.MDP_MEMBRE_INITIAL.equals(request.getNouveauMotDePasse())) {
            throw new BusinessException("Choisissez un mot de passe différent du mot de passe temporaire");
        }

        u.setMotDePasse(passwordEncoder.encode(request.getNouveauMotDePasse()));
        u.setDoitChangerMotDePasse(false);
        utilisateurRepository.save(u);

        Role role = OrganisationContext.getRole();
        Long orgId = OrganisationContext.getOrganisationId();
        Long membreId = OrganisationContext.getMembreId();
        if (role == null) {
            UtilisateurRole ur = utilisateurRoleRepository.findByUtilisateurId(userId).stream()
                    .min(Comparator.comparingInt(r -> prioriteRole(r.getRole())))
                    .orElseThrow(() -> new BusinessException("Rôle introuvable"));
            role = ur.getRole();
            orgId = ur.getOrganisationId();
            membreId = ur.getMembreId();
        }

        String orgNom = null;
        if (orgId != null) {
            orgNom = organisationRepository.findById(orgId)
                    .map(com.cotisapp.domain.entity.Organisation::getNom)
                    .orElse(null);
        }

        JwtClaims claims = new JwtClaims(u.getEmail(), u.getId(), role, orgId, membreId);
        String token = jwtService.generateToken(claims);
        ContexteConnexion ctx = new ContexteConnexion(u, role, orgId, membreId);
        return buildAuthResponse(u, ctx, token, orgNom);
    }

    private AuthResponse buildAuthResponse(
            Utilisateur u, ContexteConnexion ctx, String token, String orgNom) {
        if (ctx.organisationId() != null) {
            presenceService.touch(u.getId(), ctx.organisationId());
        }
        return AuthResponse.builder()
                .token(token)
                .userId(u.getId())
                .email(u.getEmail())
                .nomComplet(u.getPrenom() + " " + u.getNom())
                .role(ctx.role())
                .organisationId(ctx.organisationId())
                .organisationNom(orgNom)
                .membreId(ctx.membreId())
                .mustChangePassword(Boolean.TRUE.equals(u.getDoitChangerMotDePasse()))
                .mustSetupTwoFactor(TotpPolicy.mustSetupTwoFactor(ctx.role(), u))
                .build();
    }

    private ContexteConnexion resoudreContexte(
            String identifiant, Long organisationIdDemande, Long membreIdDemande, String roleSouhaite) {
        if (TelephoneUtil.estEmail(identifiant)) {
            return connexionEmail(identifiant.toLowerCase(), roleSouhaite, organisationIdDemande);
        }
        return connexionTelephone(identifiant, organisationIdDemande, membreIdDemande);
    }

    @Transactional(readOnly = true)
    public List<CompteMembreLoginDto> listerComptesMembreParTelephone(String rawPhone) {
        String tel = TelephoneUtil.normaliser(rawPhone);
        if (tel == null) {
            throw new BusinessException("Numéro de téléphone invalide");
        }
        List<Membre> membres = trouverMembresParTelephone(tel);
        if (membres.isEmpty()) {
            return List.of();
        }
        Map<Long, com.cotisapp.domain.entity.Organisation> orgs = organisationRepository.findAllById(
                        membres.stream().map(Membre::getOrganisationId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(com.cotisapp.domain.entity.Organisation::getId, o -> o));
        return membres.stream()
                .map(m -> {
                    var org = orgs.get(m.getOrganisationId());
                    return CompteMembreLoginDto.builder()
                            .membreId(m.getId())
                            .organisationId(m.getOrganisationId())
                            .organisationNom(org != null ? org.getNom() : "Organisation")
                            .organisationCode(org != null ? org.getCode() : "")
                            .codeMembre(m.getCodeMembre())
                            .nomComplet(m.getNomComplet())
                            .build();
                })
                .sorted(Comparator
                        .comparing(CompteMembreLoginDto::getOrganisationNom, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(CompteMembreLoginDto::getCodeMembre, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private ContexteConnexion connexionEmail(
            String email, String roleSouhaite, Long organisationIdDemande) {
        Utilisateur user = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Identifiants incorrects"));
        if (!Boolean.TRUE.equals(user.getActif())) {
            throw new BusinessException("Compte suspendu");
        }
        List<UtilisateurRole> roles = utilisateurRoleRepository.findByUtilisateurId(user.getId());
        UtilisateurRole ur;
        if (roleSouhaite != null && !roleSouhaite.isBlank()) {
            Role cible = Role.valueOf(roleSouhaite.trim().toUpperCase());
            ur = roles.stream()
                    .filter(r -> r.getRole() == cible)
                    .filter(r -> cible != Role.ADMIN_GIE
                            || organisationIdDemande == null
                            || Objects.equals(r.getOrganisationId(), organisationIdDemande))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Rôle demandé indisponible pour ce compte"));
        } else {
            List<UtilisateurRole> candidats = roles;
            if (organisationIdDemande != null) {
                candidats = roles.stream()
                        .filter(r -> r.getRole() == Role.SUPERADMIN
                                || Objects.equals(r.getOrganisationId(), organisationIdDemande))
                        .toList();
            }
            ur = candidats.stream()
                    .min(Comparator.comparingInt(r -> prioriteRole(r.getRole())))
                    .orElseThrow(() -> new BusinessException(
                            "Aucun rôle actif pour cette organisation. Contactez l'administrateur du GIE."));
        }
        return new ContexteConnexion(user, ur.getRole(), ur.getOrganisationId(), ur.getMembreId());
    }

    private ContexteConnexion connexionTelephone(
            String rawPhone, Long organisationIdDemande, Long membreIdDemande) {
        String tel = TelephoneUtil.normaliser(rawPhone);
        if (tel == null) {
            throw new BusinessException("Numéro de téléphone invalide");
        }

        List<Membre> membres = trouverMembresParTelephone(tel);
        if (membres.isEmpty()) {
            if (!utilisateurRepository.findAllByTelephoneNormalise(tel).isEmpty()) {
                throw new BusinessException(
                        "Ce téléphone est lié à un compte administrateur : connectez-vous avec votre email.");
            }
            throw new BusinessException("Identifiants incorrects");
        }

        Membre membre = selectionnerMembre(membres, organisationIdDemande, membreIdDemande);

        Long utilisateurId = membre.getUtilisateurId();
        if (utilisateurId == null) {
            throw new BusinessException("Ce membre n'a pas encore de compte de connexion");
        }
        Utilisateur user = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new BusinessException("Compte utilisateur introuvable pour ce membre"));
        if (!Boolean.TRUE.equals(user.getActif())) {
            throw new BusinessException("Compte suspendu");
        }

        UtilisateurRole ur = utilisateurRoleRepository
                .findFirstByMembreIdAndRole(membre.getId(), Role.MEMBRE)
                .orElseThrow(() -> new BusinessException("Rôle membre introuvable pour cette fiche"));

        return new ContexteConnexion(user, ur.getRole(), membre.getOrganisationId(), membre.getId());
    }

    private Membre selectionnerMembre(List<Membre> membres, Long organisationIdDemande, Long membreIdDemande) {
        if (membreIdDemande != null) {
            return membres.stream()
                    .filter(m -> Objects.equals(m.getId(), membreIdDemande))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Compte membre introuvable pour ce numéro"));
        }
        if (membres.size() == 1) {
            return membres.get(0);
        }
        if (organisationIdDemande != null) {
            List<Membre> dansOrg = membres.stream()
                    .filter(m -> Objects.equals(m.getOrganisationId(), organisationIdDemande))
                    .toList();
            if (dansOrg.size() == 1) {
                return dansOrg.get(0);
            }
            if (dansOrg.size() > 1) {
                throw new BusinessException(
                        "Plusieurs fiches membre pour ce numéro dans cette organisation. Sélectionnez votre compte.");
            }
            throw new BusinessException("Aucun compte membre pour cette organisation");
        }
        throw new BusinessException(
                "Plusieurs comptes sont associés à ce numéro. Sélectionnez la fiche membre à utiliser.");
    }

    private List<Membre> trouverMembresParTelephone(String tel) {
        List<Membre> parColonne = membreRepository.findByTelephoneNormaliseAndActifTrue(tel).stream()
                .filter(m -> m.getUtilisateurId() != null)
                .toList();
        if (!parColonne.isEmpty()) {
            return parColonne;
        }
        return membreRepository.findByActifTrueAndUtilisateurIdIsNotNull().stream()
                .filter(m -> tel.equals(TelephoneUtil.normaliser(m.getTelephone())))
                .toList();
    }

    private static int prioriteRole(Role role) {
        return switch (role) {
            case SUPERADMIN -> 0;
            case ADMIN_GIE -> 1;
            case MEMBRE -> 2;
        };
    }

    private record ContexteConnexion(Utilisateur utilisateur, Role role, Long organisationId, Long membreId) {}
}
