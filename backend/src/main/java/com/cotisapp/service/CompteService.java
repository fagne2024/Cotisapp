package com.cotisapp.service;

import com.cotisapp.domain.entity.Compte;
import com.cotisapp.domain.enums.ProprietaireCompte;
import com.cotisapp.domain.enums.SensMouvement;
import com.cotisapp.domain.enums.TypeCompte;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.domain.enums.FamilleCompte;
import com.cotisapp.dto.request.ComptesOrganisationSelection;
import com.cotisapp.domain.entity.ParametrageCompteOrganisation;
import com.cotisapp.repository.CompteModeleMembreRepository;
import com.cotisapp.repository.CompteRepository;
import com.cotisapp.repository.ParametrageCompteOrganisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompteService {

    private final CompteRepository compteRepository;
    private final CompteModeleMembreRepository compteModeleMembreRepository;
    private final ParametrageCompteOrganisationRepository parametrageRepository;

    @Transactional
    public List<Compte> creerComptesOrganisation(Long organisationId) {
        return creerComptesOrganisation(organisationId, new ComptesOrganisationSelection());
    }

    @Transactional
    public List<Compte> creerComptesOrganisation(Long organisationId, ComptesOrganisationSelection selection) {
        ComptesOrganisationSelection sel = selection != null ? selection : new ComptesOrganisationSelection();
        List<Compte> comptes = new ArrayList<>();
        comptes.add(creerCompteOrganisation(
                organisationId, TypeCompte.CAISSE, ParametrageCompteService.libelleDefaut(FamilleCompte.CAISSE)));
        comptes.add(creerCompteOrganisation(
                organisationId, TypeCompte.INTERET, ParametrageCompteService.libelleDefaut(FamilleCompte.INTERET)));
        comptes.add(creerCompteOrganisation(
                organisationId, TypeCompte.AMENDES, "Compte amendes & pénalités"));
        if (sel.isSolidarite()) {
            comptes.add(creerCompteOrganisation(
                    organisationId,
                    TypeCompte.SOLIDARITE,
                    ParametrageCompteService.libelleDefaut(FamilleCompte.SOLIDARITE)));
        }
        if (sel.isBanque()) {
            comptes.add(creerCompteOrganisation(organisationId, TypeCompte.BANQUE, "Banque"));
        }
        return compteRepository.saveAll(comptes);
    }

    private Compte creerCompteOrganisation(Long organisationId, TypeCompte type, String libelle) {
        return Compte.builder()
                .organisationId(organisationId)
                .typeCompte(type)
                .proprietaire(ProprietaireCompte.ORGANISATION)
                .solde(BigDecimal.ZERO)
                .libelle(libelle)
                .build();
    }

    @Transactional
    public List<Compte> creerComptesMembre(Long organisationId, Long membreId) {
        List<Compte> comptes = new ArrayList<>();
        for (TypeCompte type : List.of(
                TypeCompte.EPARGNE_HEBDO,
                TypeCompte.EPARGNE_MOIS,
                TypeCompte.SOLIDARITE)) {
            comptes.add(creerCompteMembreSiAbsent(organisationId, membreId, type, null));
        }
        return comptes;
    }

    @Transactional
    public void ensureComptesMembre(Long organisationId, Long membreId) {
        assurerComptesMembreSelonParametrage(organisationId, membreId);
    }

    /** Crée les comptes membre manquants pour chaque famille membre active (hors dépense / caisse GIE). */
    @Transactional
    public void assurerComptesMembreSelonParametrage(Long organisationId, Long membreId) {
        if (!parametrageRepository.existsByOrganisationId(organisationId)) {
            for (TypeCompte type : List.of(
                    TypeCompte.EPARGNE_HEBDO,
                    TypeCompte.EPARGNE_MOIS,
                    TypeCompte.SOLIDARITE)) {
                creerCompteMembreSiAbsent(organisationId, membreId, type, null);
            }
            return;
        }
        for (ParametrageCompteOrganisation p :
                parametrageRepository.findByOrganisationIdOrderByFamilleAsc(organisationId)) {
            if (!Boolean.TRUE.equals(p.getActif()) || p.getProprietaire() != ProprietaireCompte.MEMBRE) {
                continue;
            }
            if (p.getTypeCompte() == TypeCompte.DEPENSE) {
                continue;
            }
            creerCompteMembreSiAbsent(organisationId, membreId, p.getTypeCompte(), null);
        }
    }

    @Transactional
    public Compte creerCompteMembre(Long organisationId, Long membreId, TypeCompte type, Long modeleCompteId) {
        if (type == TypeCompte.CUSTOM) {
            if (modeleCompteId == null) {
                throw new BusinessException("Modèle de compte requis pour un compte personnalisé");
            }
            if (compteRepository.existsByMembreIdAndModeleCompteId(membreId, modeleCompteId)) {
                return compteRepository.findByMembreIdAndModeleCompteId(membreId, modeleCompteId).orElseThrow();
            }
            var modele = compteModeleMembreRepository.findByIdAndOrganisationId(modeleCompteId, organisationId)
                    .orElseThrow(() -> new BusinessException("Modèle de compte introuvable"));
            return compteRepository.save(Compte.builder()
                    .organisationId(organisationId)
                    .membreId(membreId)
                    .typeCompte(TypeCompte.CUSTOM)
                    .modeleCompteId(modeleCompteId)
                    .proprietaire(ProprietaireCompte.MEMBRE)
                    .solde(BigDecimal.ZERO)
                    .libelle(modele.getLibelle())
                    .build());
        }
        return creerCompteMembreSiAbsent(organisationId, membreId, type, null);
    }

    private Compte creerCompteMembreSiAbsent(Long organisationId, Long membreId, TypeCompte type, Long modeleCompteId) {
        return compteRepository.findByMembreIdAndTypeCompte(membreId, type).orElseGet(() -> {
            String libelle =
                    switch (type) {
                        case EPARGNE_HEBDO -> ParametrageCompteService.libelleDefaut(FamilleCompte.EPARGNE_HEBDO);
                        case EPARGNE_MOIS -> ParametrageCompteService.libelleDefaut(FamilleCompte.EPARGNE_MOIS);
                        case PENALITE -> "Compte pénalité";
                        case AMENDE -> "Compte amende";
                        case INTERET -> ParametrageCompteService.libelleDefaut(FamilleCompte.INTERET);
                        default -> null;
                    };
            return compteRepository.save(Compte.builder()
                    .organisationId(organisationId)
                    .membreId(membreId)
                    .typeCompte(type)
                    .modeleCompteId(modeleCompteId)
                    .proprietaire(ProprietaireCompte.MEMBRE)
                    .solde(BigDecimal.ZERO)
                    .libelle(libelle)
                    .build());
        });
    }

    @Transactional
    public Compte ensureCompteOrganisationInteret(Long organisationId) {
        return compteRepository
                .findByOrganisationIdAndTypeCompteAndProprietaire(
                        organisationId, TypeCompte.INTERET, ProprietaireCompte.ORGANISATION)
                .orElseGet(() -> compteRepository.save(creerCompteOrganisation(
                        organisationId,
                        TypeCompte.INTERET,
                        ParametrageCompteService.libelleDefaut(FamilleCompte.INTERET))));
    }

    @Transactional
    public Compte ensureCompteOrganisationAmendes(Long organisationId) {
        return compteRepository
                .findByOrganisationIdAndTypeCompteAndProprietaire(
                        organisationId, TypeCompte.AMENDES, ProprietaireCompte.ORGANISATION)
                .orElseGet(() -> compteRepository.save(creerCompteOrganisation(
                        organisationId, TypeCompte.AMENDES, "Compte amendes & pénalités")));
    }

    public Compte getCompteOrg(Long orgId, TypeCompte type) {
        return compteRepository.findByOrganisationIdAndTypeCompteAndProprietaire(
                        orgId, type, ProprietaireCompte.ORGANISATION)
                .orElseThrow(() -> new BusinessException("Compte organisation introuvable: " + type));
    }

    public Compte getCompteMembre(Long membreId, TypeCompte type) {
        return compteRepository.findByMembreIdAndTypeCompte(membreId, type)
                .orElseThrow(() -> new BusinessException("Compte membre introuvable: " + type));
    }

    @Transactional
    public void appliquerMouvement(Long compteId, SensMouvement sens, BigDecimal montant) {
        appliquerMouvement(compteId, sens, montant, true);
    }

    /**
     * @param autoriserSoldeNegatif si false, un débit qui rendrait le solde négatif est refusé (ex. caisse organisation).
     */
    @Transactional
    public void appliquerMouvement(Long compteId, SensMouvement sens, BigDecimal montant, boolean autoriserSoldeNegatif) {
        Compte compte = compteRepository.findById(compteId)
                .orElseThrow(() -> new BusinessException("Compte introuvable"));
        if (sens == SensMouvement.DEBIT && !autoriserSoldeNegatif) {
            if (compte.getSolde().compareTo(montant) < 0) {
                throw new BusinessException("Solde insuffisant sur le compte (solde: "
                        + compte.getSolde() + ", débit: " + montant + ")");
            }
        }
        if (sens == SensMouvement.CREDIT) {
            compte.setSolde(compte.getSolde().add(montant));
        } else {
            compte.setSolde(compte.getSolde().subtract(montant));
        }
        compteRepository.save(compte);
    }
}
