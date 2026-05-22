package com.cotisapp.service;

import com.cotisapp.domain.entity.Compte;
import com.cotisapp.domain.entity.Emprunt;
import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.entity.Organisation;
import com.cotisapp.domain.entity.Utilisateur;
import com.cotisapp.domain.entity.UtilisateurRole;
import com.cotisapp.domain.enums.PosteMembre;
import com.cotisapp.domain.enums.ProprietaireCompte;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.domain.enums.StatutEmprunt;
import com.cotisapp.domain.enums.TypeCompte;
import com.cotisapp.dto.response.CotisationOrgChartResponse;
import com.cotisapp.dto.response.OrganisationResumeResponse;
import com.cotisapp.dto.response.SuperadminActiviteResponse;
import com.cotisapp.dto.response.SuperadminKpiResponse;
import com.cotisapp.dto.response.SuperadminVueGlobaleResponse;
import com.cotisapp.repository.CompteRepository;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.OrganisationRepository;
import com.cotisapp.repository.RegleOperationRepository;
import com.cotisapp.repository.UtilisateurRepository;
import com.cotisapp.repository.UtilisateurRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SuperadminVueService {

    private static final DateTimeFormatter ACTIVITE_FMT =
            DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.FRENCH);

    private final OrganisationRepository organisationRepository;
    private final MembreRepository membreRepository;
    private final CompteRepository compteRepository;
    private final EmpruntRepository empruntRepository;
    private final RegleOperationRepository regleOperationRepository;
    private final OperationRepository operationRepository;
    private final UtilisateurRoleRepository utilisateurRoleRepository;
    private final UtilisateurRepository utilisateurRepository;

    @Transactional(readOnly = true)
    public SuperadminVueGlobaleResponse vueGlobale() {
        List<Organisation> orgs = organisationRepository.findAll();
        List<OrganisationResumeResponse> resumes = new ArrayList<>();
        BigDecimal caisseTotale = BigDecimal.ZERO;
        BigDecimal solidariteTotale = BigDecimal.ZERO;
        long totalMembres = 0;
        long empruntsActifs = 0;
        long empruntsRetard = 0;
        long orgsActives = 0;

        for (Organisation org : orgs) {
            OrganisationResumeResponse resume = buildResume(org);
            resumes.add(resume);
            if (org.getActif()) {
                orgsActives++;
            }
            totalMembres += resume.getNbMembres();
            caisseTotale = caisseTotale.add(nullToZero(resume.getSoldeCaisse()));
            solidariteTotale = solidariteTotale.add(nullToZero(resume.getSoldeSolidarite()));
            empruntsActifs += resume.getNbEmpruntsActifs();
            empruntsRetard += resume.getNbEmpruntsEnRetard();
        }

        resumes.sort(Comparator.comparing(OrganisationResumeResponse::getNom, String.CASE_INSENSITIVE_ORDER));

        return SuperadminVueGlobaleResponse.builder()
                .kpi(SuperadminKpiResponse.builder()
                        .organisationsActives(orgsActives)
                        .totalMembres(totalMembres)
                        .caisseTotale(caisseTotale)
                        .empruntsActifs(empruntsActifs)
                        .empruntsEnRetard(empruntsRetard)
                        .solidariteTotale(solidariteTotale)
                        .build())
                .organisations(resumes)
                .cotisationsParOrganisation(buildChart(resumes))
                .activiteRecente(buildActivite())
                .build();
    }

    private OrganisationResumeResponse buildResume(Organisation org) {
        Long orgId = org.getId();
        List<Membre> membres = membreRepository.findByOrganisationId(orgId);
        long bureau = membres.stream().filter(m -> m.getPoste() != PosteMembre.SIMPLE).count();
        long simples = membres.stream().filter(m -> m.getPoste() == PosteMembre.SIMPLE).count();

        BigDecimal caisse = soldeOrg(orgId, TypeCompte.CAISSE);
        BigDecimal solidarite = soldeOrg(orgId, TypeCompte.SOLIDARITE);
        BigDecimal banque = soldeOrg(orgId, TypeCompte.BANQUE);

        List<Emprunt> emprunts = empruntRepository.findByOrganisationId(orgId);
        long actifs = emprunts.stream().filter(e -> e.getStatut() == StatutEmprunt.EN_COURS).count();
        long retards = 0;

        Optional<UtilisateurRole> adminRole =
                utilisateurRoleRepository.findFirstByOrganisationIdAndRole(orgId, Role.ADMIN_GIE);
        Long adminUtilisateurId = null;
        String adminPrenom = null;
        String adminNom = "";
        String adminEmail = "—";
        boolean adminActif = false;
        boolean adminTwoFactorEnabled = false;
        if (adminRole.isPresent()) {
            Optional<Utilisateur> admin = utilisateurRepository.findById(adminRole.get().getUtilisateurId());
            if (admin.isPresent()) {
                Utilisateur u = admin.get();
                adminUtilisateurId = u.getId();
                adminPrenom = u.getPrenom();
                adminNom = u.getNom();
                adminEmail = u.getEmail();
                adminActif = Boolean.TRUE.equals(u.getActif());
                adminTwoFactorEnabled = Boolean.TRUE.equals(u.getTotpEnabled()) && u.getTotpSecret() != null;
            }
        }
        if (adminPrenom == null && adminNom.isEmpty()) {
            adminNom = "—";
        }

        String logoUrl = null;
        if (org.getLogoChemin() != null && !org.getLogoChemin().isBlank()) {
            logoUrl = "/api/organisations/" + orgId + "/logo";
        }

        return OrganisationResumeResponse.builder()
                .id(orgId)
                .code(org.getCode())
                .nom(org.getNom())
                .description(org.getDescription())
                .logoUrl(logoUrl)
                .actif(Boolean.TRUE.equals(org.getActif()))
                .dateCreation(org.getDateCreation())
                .adminUtilisateurId(adminUtilisateurId)
                .adminPrenom(adminPrenom)
                .adminNom(adminNom)
                .adminEmail(adminEmail)
                .adminActif(adminActif)
                .adminTwoFactorEnabled(adminTwoFactorEnabled)
                .nbMembres(membres.size())
                .nbMembresBureau(bureau)
                .nbMembresSimples(simples)
                .soldeCaisse(caisse)
                .soldeSolidarite(solidarite)
                .soldeBanque(banque)
                .nbEmpruntsActifs(actifs)
                .nbEmpruntsEnRetard(retards)
                .nbRegles(regleOperationRepository.findByOrganisationId(orgId).size())
                .build();
    }

    private BigDecimal soldeOrg(Long orgId, TypeCompte type) {
        return compteRepository
                .findByOrganisationIdAndTypeCompteAndProprietaire(orgId, type, ProprietaireCompte.ORGANISATION)
                .map(Compte::getSolde)
                .orElse(BigDecimal.ZERO);
    }

    private List<CotisationOrgChartResponse> buildChart(List<OrganisationResumeResponse> resumes) {
        return resumes.stream()
                .map(r -> CotisationOrgChartResponse.builder()
                        .code(r.getCode())
                        .nom(r.getNom())
                        .montant(nullToZero(r.getSoldeCaisse()))
                        .build())
                .sorted(Comparator.comparing(CotisationOrgChartResponse::getMontant).reversed())
                .toList();
    }

    private List<SuperadminActiviteResponse> buildActivite() {
        return operationRepository.findTop10ByOrderByDateCreationDesc().stream()
                .map(op -> {
                    Organisation org = organisationRepository
                            .findById(op.getOrganisationId())
                            .orElse(Organisation.builder().nom("—").build());
                    return toActivite(org, op);
                })
                .limit(5)
                .toList();
    }

    private SuperadminActiviteResponse toActivite(Organisation org, Operation op) {
        String type = op.getTypeOperation() != null ? op.getTypeOperation().name() : "OP";
        boolean credit = op.getMontant().signum() >= 0;
        return SuperadminActiviteResponse.builder()
                .icone(credit ? "💰" : "📋")
                .fondCouleur(credit ? "var(--g3)" : "var(--re2)")
                .libelle(type.replace('_', ' ') + " — " + formatMontant(op.getMontant()))
                .meta(org.getNom() + " · " + (op.getDateCreation() != null
                        ? op.getDateCreation().format(ACTIVITE_FMT)
                        : ""))
                .montant(op.getMontant().abs())
                .credit(credit)
                .build();
    }

    private static String formatMontant(BigDecimal m) {
        return m.setScale(0, RoundingMode.HALF_UP).toPlainString() + " F";
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
