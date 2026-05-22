package com.cotisapp.config;

import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.OrganisationRepository;
import com.cotisapp.service.CompteService;
import com.cotisapp.service.ParametrageCompteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@Order(100)
@RequiredArgsConstructor
public class CompteParametrageBootstrap implements CommandLineRunner {

    private final OrganisationRepository organisationRepository;
    private final MembreRepository membreRepository;
    private final ParametrageCompteService parametrageCompteService;
    private final CompteService compteService;

    @Override
    public void run(String... args) {
        organisationRepository.findAll().forEach(org -> {
            parametrageCompteService.initialiserParametrageParDefaut(org.getId());
            membreRepository.findByOrganisationId(org.getId()).forEach(m -> {
                parametrageCompteService.migrerEpargneLegacy(org.getId(), m.getId());
                compteService.ensureComptesMembre(org.getId(), m.getId());
            });
        });
        log.debug("Paramétrage comptes : vérification terminée pour toutes les organisations");
    }
}
