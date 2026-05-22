package com.cotisapp.service;

import com.cotisapp.domain.entity.Compte;
import com.cotisapp.domain.entity.ParametrageCompteOrganisation;
import com.cotisapp.domain.enums.FamilleCompte;
import com.cotisapp.domain.enums.ProprietaireCompte;
import com.cotisapp.domain.enums.TypeCompte;
import com.cotisapp.dto.request.ComptesOrganisationSelection;
import com.cotisapp.dto.request.UpdateParametrageCompteRequest;
import com.cotisapp.dto.request.UpdateParametrageComptesRequest;
import com.cotisapp.dto.response.ParametrageCompteResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.CompteRepository;
import com.cotisapp.repository.ParametrageCompteOrganisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ParametrageCompteService {

    private final ParametrageCompteOrganisationRepository parametrageRepository;
    private final CompteRepository compteRepository;

    @Transactional
    public void initialiserParametrageParDefaut(Long organisationId) {
        initialiserParametrage(organisationId, new ComptesOrganisationSelection());
    }

    @Transactional
    public void initialiserParametrage(Long organisationId, ComptesOrganisationSelection selection) {
        if (parametrageRepository.existsByOrganisationId(organisationId)) {
            completerFamillesManquantes(organisationId, selection);
            return;
        }
        ComptesOrganisationSelection sel = selection != null ? selection : new ComptesOrganisationSelection();
        for (FamilleCompte famille : FamilleCompte.values()) {
            var meta = metaPourFamille(famille);
            parametrageRepository.save(ParametrageCompteOrganisation.builder()
                    .organisationId(organisationId)
                    .famille(famille)
                    .libelle(libelleDefaut(famille))
                    .typeCompte(meta.typeCompte())
                    .proprietaire(meta.proprietaire())
                    .actif(actifPourFamille(famille, sel))
                    .build());
        }
        synchroniserLibellesComptesOrganisation(organisationId);
    }

    private void completerFamillesManquantes(Long organisationId, ComptesOrganisationSelection selection) {
        ComptesOrganisationSelection sel = selection != null ? selection : new ComptesOrganisationSelection();
        for (FamilleCompte famille : FamilleCompte.values()) {
            if (parametrageRepository.findByOrganisationIdAndFamille(organisationId, famille).isPresent()) {
                continue;
            }
            var meta = metaPourFamille(famille);
            parametrageRepository.save(ParametrageCompteOrganisation.builder()
                    .organisationId(organisationId)
                    .famille(famille)
                    .libelle(libelleDefaut(famille))
                    .typeCompte(meta.typeCompte())
                    .proprietaire(meta.proprietaire())
                    .actif(actifPourFamille(famille, sel))
                    .build());
        }
    }

    private static boolean actifPourFamille(FamilleCompte famille, ComptesOrganisationSelection sel) {
        return switch (famille) {
            case CAISSE -> true;
            case SOLIDARITE -> sel.isSolidarite();
            case EPARGNE_HEBDO -> sel.isEpargneHebdo();
            case EPARGNE_MOIS -> sel.isEpargneMois();
            case PENALITE -> sel.isPenalite();
            case AMENDE -> sel.isAmende();
            case INTERET -> true;
        };
    }

    public List<ParametrageCompteResponse> lister(Long organisationId) {
        if (!parametrageRepository.existsByOrganisationId(organisationId)) {
            initialiserParametrageParDefaut(organisationId);
        }
        return parametrageRepository.findByOrganisationIdOrderByFamilleAsc(organisationId).stream()
                .map(p -> toResponse(p, organisationId))
                .toList();
    }

    @Transactional
    public List<ParametrageCompteResponse> mettreAJour(Long organisationId, UpdateParametrageComptesRequest request) {
        if (!parametrageRepository.existsByOrganisationId(organisationId)) {
            initialiserParametrageParDefaut(organisationId);
        }
        Map<FamilleCompte, UpdateParametrageCompteRequest> patch = request.getComptes();
        if (patch == null || patch.isEmpty()) {
            throw new BusinessException("Aucun paramétrage fourni");
        }
        for (FamilleCompte famille : FamilleCompte.values()) {
            UpdateParametrageCompteRequest item = patch.get(famille);
            if (item == null) {
                continue;
            }
            ParametrageCompteOrganisation p = parametrageRepository
                    .findByOrganisationIdAndFamille(organisationId, famille)
                    .orElseThrow(() -> new BusinessException("Paramétrage introuvable: " + famille));
            p.setLibelle(item.getLibelle().trim());
            if (item.getActif() != null) {
                p.setActif(item.getActif());
            }
            parametrageRepository.save(p);
        }
        synchroniserLibellesComptesOrganisation(organisationId);
        return lister(organisationId);
    }

    @Transactional
    public void migrerEpargneLegacy(Long organisationId, Long membreId) {
        compteRepository.findByMembreIdAndTypeCompte(membreId, TypeCompte.EPARGNE).ifPresent(legacy -> {
            Compte hebdo = compteRepository
                    .findByMembreIdAndTypeCompte(membreId, TypeCompte.EPARGNE_HEBDO)
                    .orElseGet(() -> compteRepository.save(Compte.builder()
                            .organisationId(organisationId)
                            .membreId(membreId)
                            .typeCompte(TypeCompte.EPARGNE_HEBDO)
                            .proprietaire(ProprietaireCompte.MEMBRE)
                            .solde(BigDecimal.ZERO)
                            .libelle(libelleDefaut(FamilleCompte.EPARGNE_HEBDO))
                            .build()));
            hebdo.setSolde(hebdo.getSolde().add(legacy.getSolde()));
            compteRepository.save(hebdo);
            legacy.setActif(false);
            compteRepository.save(legacy);
        });
        if (compteRepository.findByMembreIdAndTypeCompte(membreId, TypeCompte.EPARGNE_MOIS).isEmpty()) {
            compteRepository.save(Compte.builder()
                    .organisationId(organisationId)
                    .membreId(membreId)
                    .typeCompte(TypeCompte.EPARGNE_MOIS)
                    .proprietaire(ProprietaireCompte.MEMBRE)
                    .solde(BigDecimal.ZERO)
                    .libelle(libelleDefaut(FamilleCompte.EPARGNE_MOIS))
                    .build());
        }
    }

    private void synchroniserLibellesComptesOrganisation(Long organisationId) {
        for (ParametrageCompteOrganisation p : parametrageRepository.findByOrganisationIdOrderByFamilleAsc(organisationId)) {
            if (p.getProprietaire() != ProprietaireCompte.ORGANISATION) {
                continue;
            }
            compteRepository
                    .findByOrganisationIdAndTypeCompteAndProprietaire(
                            organisationId, p.getTypeCompte(), ProprietaireCompte.ORGANISATION)
                    .ifPresent(c -> {
                        c.setLibelle(p.getLibelle());
                        c.setActif(p.getActif());
                        compteRepository.save(c);
                    });
        }
    }

    private ParametrageCompteResponse toResponse(ParametrageCompteOrganisation p, Long organisationId) {
        BigDecimal soldeOrg = null;
        if (p.getProprietaire() == ProprietaireCompte.ORGANISATION) {
            soldeOrg = compteRepository
                    .findByOrganisationIdAndTypeCompteAndProprietaire(
                            organisationId, p.getTypeCompte(), ProprietaireCompte.ORGANISATION)
                    .map(Compte::getSolde)
                    .orElse(BigDecimal.ZERO);
        }
        return ParametrageCompteResponse.builder()
                .famille(p.getFamille())
                .libelle(p.getLibelle())
                .typeCompte(p.getTypeCompte())
                .proprietaire(p.getProprietaire())
                .actif(p.getActif())
                .soldeOrganisation(soldeOrg)
                .description(descriptionPourFamille(p.getFamille()))
                .build();
    }

    private static String descriptionPourFamille(FamilleCompte famille) {
        return switch (famille) {
            case CAISSE -> "Compte principal de trésorerie du GIE";
            case SOLIDARITE -> "Fonds solidarité collectif de l'organisation";
            case EPARGNE_HEBDO -> "Épargne liée aux cotisations hebdomadaires (un compte par membre)";
            case EPARGNE_MOIS -> "Épargne liée aux cotisations mensuelles (un compte par membre)";
            case PENALITE -> "Compte pénalité (un compte par membre, si activé)";
            case AMENDE -> "Compte amende (un compte par membre, si activé)";
            case INTERET -> "Compte intérêts — regroupe les frais et intérêts d'emprunt collectés";
        };
    }

    static String libelleDefaut(FamilleCompte famille) {
        return switch (famille) {
            case CAISSE -> "Caisse";
            case SOLIDARITE -> "Fonds solidarité";
            case EPARGNE_HEBDO -> "Épargne hebdomadaire";
            case EPARGNE_MOIS -> "Épargne mensuelle";
            case PENALITE -> "Compte pénalité";
            case AMENDE -> "Compte amende";
            case INTERET -> "Compte intérêts";
        };
    }

    static CompteMeta metaPourFamille(FamilleCompte famille) {
        return switch (famille) {
            case CAISSE -> new CompteMeta(TypeCompte.CAISSE, ProprietaireCompte.ORGANISATION);
            case SOLIDARITE -> new CompteMeta(TypeCompte.SOLIDARITE, ProprietaireCompte.ORGANISATION);
            case EPARGNE_HEBDO -> new CompteMeta(TypeCompte.EPARGNE_HEBDO, ProprietaireCompte.MEMBRE);
            case EPARGNE_MOIS -> new CompteMeta(TypeCompte.EPARGNE_MOIS, ProprietaireCompte.MEMBRE);
            case PENALITE -> new CompteMeta(TypeCompte.PENALITE, ProprietaireCompte.MEMBRE);
            case AMENDE -> new CompteMeta(TypeCompte.AMENDE, ProprietaireCompte.MEMBRE);
            case INTERET -> new CompteMeta(TypeCompte.INTERET, ProprietaireCompte.ORGANISATION);
        };
    }

    public boolean familleActive(Long organisationId, FamilleCompte famille) {
        if (!parametrageRepository.existsByOrganisationId(organisationId)) {
            return true;
        }
        return parametrageRepository
                .findByOrganisationIdAndFamille(organisationId, famille)
                .map(ParametrageCompteOrganisation::getActif)
                .orElse(false);
    }

    record CompteMeta(TypeCompte typeCompte, ProprietaireCompte proprietaire) {}

    public static List<FamilleCompte> famillesParametrables() {
        return Arrays.asList(FamilleCompte.values());
    }
}
