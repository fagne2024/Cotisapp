package com.cotisapp.service;

import com.cotisapp.domain.entity.CompteModeleMembre;
import com.cotisapp.dto.request.CreateCompteModeleMembreRequest;
import com.cotisapp.dto.response.CompteModeleMembreResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.CompteModeleMembreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompteModeleMembreService {

    private final CompteModeleMembreRepository repository;

    public List<CompteModeleMembreResponse> lister(Long organisationId, boolean actifsSeulement) {
        List<CompteModeleMembre> list = actifsSeulement
                ? repository.findByOrganisationIdAndActifTrueOrderByLibelleAsc(organisationId)
                : repository.findByOrganisationIdOrderByLibelleAsc(organisationId);
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional
    public CompteModeleMembreResponse creer(Long organisationId, CreateCompteModeleMembreRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByOrganisationIdAndCode(organisationId, code)) {
            throw new BusinessException("Un compte avec le code « " + code + " » existe déjà");
        }
        CompteModeleMembre saved = repository.save(CompteModeleMembre.builder()
                .organisationId(organisationId)
                .code(code)
                .libelle(request.getLibelle().trim())
                .actif(true)
                .build());
        return toResponse(saved);
    }

    public CompteModeleMembre getEntity(Long organisationId, Long modeleId) {
        return repository.findByIdAndOrganisationId(modeleId, organisationId)
                .orElseThrow(() -> new BusinessException("Modèle de compte introuvable"));
    }

    private CompteModeleMembreResponse toResponse(CompteModeleMembre m) {
        return CompteModeleMembreResponse.builder()
                .id(m.getId())
                .code(m.getCode())
                .libelle(m.getLibelle())
                .actif(m.getActif())
                .build();
    }
}
