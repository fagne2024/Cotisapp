package com.cotisapp.service;

import com.cotisapp.domain.catalogue.ActionDroitCatalogue;
import com.cotisapp.domain.entity.ActionDroit;
import com.cotisapp.repository.ActionDroitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ActionDroitInitialisationService {

    private final ActionDroitRepository actionDroitRepository;

    @Transactional
    public void assurerCatalogueActions() {
        for (ActionDroitCatalogue.ActionDef def : ActionDroitCatalogue.toutes()) {
            actionDroitRepository.findById(def.code()).ifPresentOrElse(
                    existing -> {
                        boolean changed = !Objects.equals(existing.getSection(), def.section())
                                || !Objects.equals(existing.getLibelle(), def.libelle())
                                || existing.getOrdre() != def.ordre();
                        if (changed) {
                            existing.setSection(def.section());
                            existing.setLibelle(def.libelle());
                            existing.setOrdre(def.ordre());
                            actionDroitRepository.save(existing);
                        }
                    },
                    () -> actionDroitRepository.save(ActionDroit.builder()
                            .code(def.code())
                            .section(def.section())
                            .libelle(def.libelle())
                            .ordre(def.ordre())
                            .build()));
        }
    }
}
