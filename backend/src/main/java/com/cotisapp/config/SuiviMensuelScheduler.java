package com.cotisapp.config;

import com.cotisapp.repository.OrganisationRepository;
import com.cotisapp.service.SuiviMensuelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class SuiviMensuelScheduler {

    private static final DateTimeFormatter MOIS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final SuiviMensuelService suiviMensuelService;
    private final OrganisationRepository organisationRepository;

    /** Au démarrage : génère le suivi du mois en cours pour toutes les orgs actives. */
    @EventListener(ApplicationReadyEvent.class)
    public void genererAuDemarrage() {
        String mois = YearMonth.now().format(MOIS_FORMAT);
        log.info("Génération suivi mensuel au démarrage pour {}", mois);
        organisationRepository.findAll().stream()
                .filter(o -> Boolean.TRUE.equals(o.getActif()))
                .forEach(o -> {
                    int count = suiviMensuelService.genererPourOrganisation(o.getId(), mois);
                    if (count > 0) {
                        log.info("Org {} : {} suivi(s) mensuel(s) créé(s)", o.getCode(), count);
                    }
                });
    }

    /** Chaque 1er du mois à 01h00 — génération automatique. */
    @Scheduled(cron = "${cotisapp.suivi-mensuel.cron:0 0 1 1 * ?}")
    public void genererMensuelPlanifie() {
        String mois = YearMonth.now().format(MOIS_FORMAT);
        log.info("Cron suivi mensuel — mois {}", mois);
        organisationRepository.findAll().stream()
                .filter(o -> Boolean.TRUE.equals(o.getActif()))
                .forEach(o -> suiviMensuelService.genererPourOrganisation(o.getId(), mois));
    }
}
