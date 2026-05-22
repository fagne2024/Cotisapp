package com.cotisapp.service;

import com.cotisapp.domain.entity.Organisation;
import com.cotisapp.dto.request.ComptesOrganisationSelection;
import com.cotisapp.dto.request.CreateCompteModeleMembreRequest;
import com.cotisapp.dto.request.CreateOrganisationRequest;
import com.cotisapp.dto.request.UpdateOrganisationRequest;
import com.cotisapp.dto.response.OrganisationResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.CompteModeleMembreRepository;
import com.cotisapp.repository.CompteRepository;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.OrganisationRepository;
import com.cotisapp.repository.ParametrageCompteOrganisationRepository;
import com.cotisapp.repository.RegleOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrganisationService {

    private final OrganisationRepository organisationRepository;
    private final MembreRepository membreRepository;
    private final OperationRepository operationRepository;
    private final EmpruntRepository empruntRepository;
    private final CompteRepository compteRepository;
    private final ParametrageCompteOrganisationRepository parametrageRepository;
    private final RegleOperationRepository regleOperationRepository;
    private final CompteModeleMembreRepository compteModeleMembreRepository;
    private final CompteService compteService;
    private final RegleInitialisationService regleInitialisationService;
    private final ParametrageCompteService parametrageCompteService;
    private final CompteModeleMembreService compteModeleMembreService;
    private final OrganisationLogoStorageService organisationLogoStorageService;
    private final TypeProfilInitialisationService typeProfilInitialisationService;
    private final UtilisateurAccesService utilisateurAccesService;
    private final ExerciceService exerciceService;

    @Transactional
    public OrganisationResponse creer(CreateOrganisationRequest request) {
        if (organisationRepository.existsByCode(request.getCode())) {
            throw new BusinessException("Code organisation déjà utilisé");
        }
        ComptesOrganisationSelection comptes = request.getComptes() != null
                ? request.getComptes()
                : new ComptesOrganisationSelection();
        Organisation org = Organisation.builder()
                .code(request.getCode().trim().toUpperCase())
                .nom(request.getNom().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .build();
        org = organisationRepository.save(org);
        exerciceService.creerPremierExercice(org.getId());
        compteService.creerComptesOrganisation(org.getId(), comptes);
        parametrageCompteService.initialiserParametrage(org.getId(), comptes);
        regleInitialisationService.initialiserReglesParDefaut(org.getId());
        if (request.getModelesComptePersonnalises() != null) {
            for (CreateCompteModeleMembreRequest modele : request.getModelesComptePersonnalises()) {
                compteModeleMembreService.creer(org.getId(), modele);
            }
        }
        typeProfilInitialisationService.initialiserTypesOrganisation(org.getId());
        if (request.getAdministrateurGie() == null) {
            throw new BusinessException("L'administrateur GIE est obligatoire pour une nouvelle organisation");
        }
        utilisateurAccesService.upsertAdminGie(org.getId(), request.getAdministrateurGie());
        return toResponse(org);
    }

    public List<OrganisationResponse> listerToutes() {
        return organisationRepository.findAll().stream().map(this::toResponse).toList();
    }

    public OrganisationResponse getById(Long id) {
        return organisationRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException("Organisation introuvable"));
    }

    @Transactional
    public OrganisationResponse modifier(Long id, UpdateOrganisationRequest request) {
        Organisation org = organisationRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Organisation introuvable"));
        org.setNom(request.getNom().trim());
        org.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        if (request.getActif() != null) {
            org.setActif(request.getActif());
        }
        org = organisationRepository.save(org);
        return toResponse(org);
    }

    @Transactional
    public void supprimer(Long id) {
        Organisation org = organisationRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Organisation introuvable"));
        if (membreRepository.countByOrganisationId(id) > 0
                || operationRepository.countByOrganisationId(id) > 0
                || empruntRepository.countByOrganisationId(id) > 0) {
            throw new BusinessException(
                    "Impossible de supprimer cette organisation : elle contient des membres, opérations ou emprunts. "
                            + "Utilisez « Modifier » pour la désactiver.");
        }
        organisationLogoStorageService.supprimerSiPresent(org.getLogoChemin());
        purgerDonneesOrganisation(id);
        organisationRepository.delete(org);
    }

    @Transactional
    public OrganisationResponse enregistrerLogo(Long id, MultipartFile fichier) {
        Organisation org = organisationRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Organisation introuvable"));
        String ancien = org.getLogoChemin();
        String nouveau = organisationLogoStorageService.enregistrer(id, fichier);
        if (StringUtils.hasText(ancien) && !ancien.equals(nouveau)) {
            organisationLogoStorageService.supprimerSiPresent(ancien);
        }
        org.setLogoChemin(nouveau);
        org = organisationRepository.save(org);
        return toResponse(org);
    }

    public OrganisationLogoStorageService.LogoTelechargement preparerLogo(Long id) {
        Organisation org = organisationRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Organisation introuvable"));
        return organisationLogoStorageService.preparerTelechargement(org.getLogoChemin());
    }

    private void purgerDonneesOrganisation(Long organisationId) {
        compteRepository.deleteAll(compteRepository.findByOrganisationId(organisationId));
        parametrageRepository.deleteAll(
                parametrageRepository.findByOrganisationIdOrderByFamilleAsc(organisationId));
        regleOperationRepository.deleteAll(regleOperationRepository.findByOrganisationId(organisationId));
        compteModeleMembreRepository.deleteAll(
                compteModeleMembreRepository.findByOrganisationIdOrderByLibelleAsc(organisationId));
    }

    private OrganisationResponse toResponse(Organisation org) {
        return OrganisationResponse.builder()
                .id(org.getId())
                .code(org.getCode())
                .nom(org.getNom())
                .description(org.getDescription())
                .actif(org.getActif())
                .logoUrl(logoUrl(org))
                .build();
    }

    private String logoUrl(Organisation org) {
        if (!StringUtils.hasText(org.getLogoChemin())) {
            return null;
        }
        return "/api/organisations/" + org.getId() + "/logo";
    }
}
