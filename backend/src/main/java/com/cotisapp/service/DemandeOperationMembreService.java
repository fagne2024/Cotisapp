package com.cotisapp.service;

import com.cotisapp.domain.entity.DemandeOperationMembre;
import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.enums.DemandeOperationStatut;
import com.cotisapp.domain.enums.DemandeOperationType;
import com.cotisapp.domain.enums.ModePaiement;
import com.cotisapp.dto.request.CotisationHebdoRequest;
import com.cotisapp.dto.request.CotisationMoisRequest;
import com.cotisapp.dto.request.RefuserDemandeOperationRequest;
import com.cotisapp.dto.request.RembourserRequest;
import com.cotisapp.dto.response.DemandeOperationMembreResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.DemandeOperationMembreRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.dto.response.EmpruntResponse;
import com.cotisapp.security.OrganisationContext;
import com.cotisapp.util.ModePaiementHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DemandeOperationMembreService {

    private final DemandeOperationMembreRepository demandeRepository;
    private final MembreRepository membreRepository;
    private final MoteurOperationService moteurOperationService;
    private final RembourserService rembourserService;
    private final EmpruntService empruntService;
    private final ObjectMapper objectMapper;
    private final OperationMemeJourControleService operationMemeJourControleService;

    @Transactional
    public DemandeOperationMembreResponse soumettreCotisationHebdo(
            Long orgId, Long membreId, CotisationHebdoRequest request) {
        request.setMembreId(membreId);
        request.setMontantAmende(null);
        operationMemeJourControleService.verifierCotisationHebdo(orgId, membreId, request);
        validerDemandeMobile(orgId, membreId, request.getModePaiement(), request.getReferencePaiement());
        String resume = resumeCotisationHebdo(request);
        DemandeOperationMembre d = enregistrerDemande(
                orgId,
                membreId,
                DemandeOperationType.COTISATION_HEBDO,
                toJson(request),
                null,
                request.getMontant(),
                ModePaiementHelper.parser(request.getModePaiement()),
                request.getReferencePaiement(),
                resume);
        return toResponse(d, membre(orgId, membreId), messageSoumise());
    }

    @Transactional
    public DemandeOperationMembreResponse soumettreCotisationMois(
            Long orgId, Long membreId, CotisationMoisRequest request) {
        request.setMembreId(membreId);
        request.setMontantAmende(null);
        operationMemeJourControleService.verifierCotisationMois(orgId, membreId, request);
        validerDemandeMobile(orgId, membreId, request.getModePaiement(), request.getReferencePaiement());
        String resume = resumeCotisationMois(request);
        DemandeOperationMembre d = enregistrerDemande(
                orgId,
                membreId,
                DemandeOperationType.COTISATION_MOIS,
                toJson(request),
                null,
                request.getMontant(),
                ModePaiementHelper.parser(request.getModePaiement()),
                request.getReferencePaiement(),
                resume);
        return toResponse(d, membre(orgId, membreId), messageSoumise());
    }

    @Transactional
    public DemandeOperationMembreResponse soumettreRemboursement(
            Long orgId, Long membreId, Long empruntId, RembourserRequest request) {
        validerDemandeMobile(orgId, membreId, request.getModePaiement(), request.getReferencePaiement());
        EmpruntResponse emprunt = verifierEmpruntAppartient(orgId, membreId, empruntId);
        LocalDate datePaiement =
                request.getDatePaiement() != null ? request.getDatePaiement() : LocalDate.now();
        operationMemeJourControleService.verifierRemboursement(
                orgId, membreId, emprunt.getTypeEmprunt(), datePaiement);
        BigDecimal montant = request.getMontant() != null ? request.getMontant() : BigDecimal.ZERO;
        String resume = String.format(
                Locale.FRENCH,
                "Remboursement %s FCFA — %s",
                formatMontant(montant),
                libelleMode(request.getModePaiement()));
        DemandeOperationMembre d = enregistrerDemande(
                orgId,
                membreId,
                DemandeOperationType.REMBOURSEMENT,
                toJson(request),
                empruntId,
                montant,
                ModePaiementHelper.parser(request.getModePaiement()),
                request.getReferencePaiement(),
                resume);
        return toResponse(d, membre(orgId, membreId), messageSoumise());
    }

    @Transactional(readOnly = true)
    public List<DemandeOperationMembre> listerEnAttente(Long orgId) {
        return demandeRepository.findByOrganisationIdAndStatutOrderByDateDemandeDesc(
                orgId, DemandeOperationStatut.EN_ATTENTE);
    }

    /** Demandes du membre pour le centre de notifications (en attente + traitées récentes). */
    @Transactional(readOnly = true)
    public List<DemandeOperationMembre> listerPourNotificationsMembre(Long orgId, Long membreId) {
        List<DemandeOperationMembre> result = new ArrayList<>();
        result.addAll(demandeRepository.findByOrganisationIdAndMembreIdAndStatutOrderByDateDemandeDesc(
                orgId, membreId, DemandeOperationStatut.EN_ATTENTE));
        LocalDateTime limite = LocalDateTime.now().minusDays(30);
        for (DemandeOperationMembre d :
                demandeRepository.findByOrganisationIdAndMembreIdAndStatutInOrderByDateDemandeDesc(
                        orgId,
                        membreId,
                        List.of(DemandeOperationStatut.VALIDEE, DemandeOperationStatut.REFUSEE))) {
            if (d.getDateTraitement() != null && !d.getDateTraitement().isBefore(limite)) {
                result.add(d);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<DemandeOperationMembreResponse> mesDemandesEnAttente(Long orgId, Long membreId) {
        return mesDemandesSuivi(orgId, membreId).stream()
                .filter(d -> d.statut() == DemandeOperationStatut.EN_ATTENTE)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DemandeOperationMembreResponse> mesDemandesSuivi(Long orgId, Long membreId) {
        Membre m = membre(orgId, membreId);
        return demandeRepository
                .findByOrganisationIdAndMembreIdAndStatutInOrderByDateDemandeDesc(
                        orgId,
                        membreId,
                        List.of(DemandeOperationStatut.EN_ATTENTE, DemandeOperationStatut.REFUSEE))
                .stream()
                .map(d -> toResponse(d, m, null))
                .toList();
    }

    @Transactional
    public DemandeOperationMembreResponse approuver(
            Long orgId, Long demandeId, com.cotisapp.dto.request.ApprouverDemandeOperationRequest body) {
        assertPeutValider();
        DemandeOperationMembre d = chargerEnAttente(orgId, demandeId);
        Operation op = executerDemande(orgId, d, body);
        d.setStatut(DemandeOperationStatut.VALIDEE);
        d.setDateTraitement(LocalDateTime.now());
        d.setValidateurUtilisateurId(OrganisationContext.getUserId());
        d.setOperationId(op.getId());
        demandeRepository.save(d);
        return toResponse(d, membre(orgId, d.getMembreId()), "Demande validée et comptabilisée.");
    }

    @Transactional
    public DemandeOperationMembreResponse refuser(
            Long orgId, Long demandeId, RefuserDemandeOperationRequest body) {
        assertPeutValider();
        DemandeOperationMembre d = chargerEnAttente(orgId, demandeId);
        d.setStatut(DemandeOperationStatut.REFUSEE);
        d.setDateTraitement(LocalDateTime.now());
        d.setValidateurUtilisateurId(OrganisationContext.getUserId());
        String motif = body != null && body.getMotif() != null ? body.getMotif().trim() : null;
        if (motif != null && motif.length() > 500) {
            motif = motif.substring(0, 500);
        }
        d.setMotifRefus(motif != null && !motif.isBlank() ? motif : null);
        demandeRepository.save(d);
        String msg = d.getMotifRefus() != null && !d.getMotifRefus().isBlank()
                ? "Demande rejetée. Motif : " + d.getMotifRefus()
                : "Demande rejetée.";
        return toResponse(d, membre(orgId, d.getMembreId()), msg);
    }

    private Operation executerDemande(
            Long orgId,
            DemandeOperationMembre d,
            com.cotisapp.dto.request.ApprouverDemandeOperationRequest body) {
        try {
            return switch (d.getTypeDemande()) {
                case COTISATION_HEBDO -> {
                    CotisationHebdoRequest req =
                            objectMapper.readValue(d.getPayloadJson(), CotisationHebdoRequest.class);
                    req.setMembreId(d.getMembreId());
                    appliquerAmendeValidation(req, body);
                    yield moteurOperationService.cotisationHebdo(orgId, req);
                }
                case COTISATION_MOIS -> {
                    CotisationMoisRequest req =
                            objectMapper.readValue(d.getPayloadJson(), CotisationMoisRequest.class);
                    req.setMembreId(d.getMembreId());
                    appliquerAmendeValidation(req, body);
                    yield moteurOperationService.cotisationMois(orgId, req);
                }
                case REMBOURSEMENT -> {
                    if (body != null
                            && body.getMontantAmende() != null
                            && body.getMontantAmende().signum() > 0) {
                        throw new BusinessException(
                                "Une amende ne peut être appliquée qu'à une cotisation mobile money");
                    }
                    RembourserRequest req =
                            objectMapper.readValue(d.getPayloadJson(), RembourserRequest.class);
                    yield rembourserService.rembourser(orgId, d.getEmpruntId(), req);
                }
            };
        } catch (JsonProcessingException e) {
            throw new BusinessException("Données de la demande invalides");
        }
    }

    /** À l'approbation, seul le validateur fixe l'amende (le membre ne peut pas en soumettre une). */
    private static void appliquerAmendeValidation(
            CotisationHebdoRequest req, com.cotisapp.dto.request.ApprouverDemandeOperationRequest body) {
        req.setMontantAmende(montantAmendeDepuisValidation(body));
    }

    private static void appliquerAmendeValidation(
            CotisationMoisRequest req, com.cotisapp.dto.request.ApprouverDemandeOperationRequest body) {
        req.setMontantAmende(montantAmendeDepuisValidation(body));
    }

    private static java.math.BigDecimal montantAmendeDepuisValidation(
            com.cotisapp.dto.request.ApprouverDemandeOperationRequest body) {
        if (body == null || body.getMontantAmende() == null || body.getMontantAmende().signum() <= 0) {
            return null;
        }
        return body.getMontantAmende();
    }

    private DemandeOperationMembre enregistrerDemande(
            Long orgId,
            Long membreId,
            DemandeOperationType type,
            String payloadJson,
            Long empruntId,
            BigDecimal montant,
            ModePaiement modePaiement,
            String referencePaiement,
            String libelleResume) {
        Long userId = OrganisationContext.getUserId();
        if (userId == null) {
            throw new BusinessException("Utilisateur non authentifié");
        }
        DemandeOperationMembre d = DemandeOperationMembre.builder()
                .organisationId(orgId)
                .membreId(membreId)
                .demandeurUtilisateurId(userId)
                .typeDemande(type)
                .statut(DemandeOperationStatut.EN_ATTENTE)
                .payloadJson(payloadJson)
                .empruntId(empruntId)
                .montant(montant)
                .modePaiement(modePaiement)
                .referencePaiement(referencePaiement)
                .libelleResume(libelleResume)
                .dateDemande(LocalDateTime.now())
                .build();
        return demandeRepository.save(d);
    }

    private DemandeOperationMembre chargerEnAttente(Long orgId, Long demandeId) {
        DemandeOperationMembre d = demandeRepository
                .findByIdAndOrganisationId(demandeId, orgId)
                .orElseThrow(() -> new BusinessException("Demande introuvable"));
        if (d.getStatut() != DemandeOperationStatut.EN_ATTENTE) {
            throw new BusinessException("Cette demande a déjà été traitée");
        }
        return d;
    }

    private void assertPeutValider() {
        if (!peutValiderDemandes()) {
            throw new BusinessException("Vous n'avez pas le droit de valider cette demande");
        }
    }

    public boolean peutValiderDemandes() {
        var role = OrganisationContext.getRole();
        return role == com.cotisapp.domain.enums.Role.SUPERADMIN
                || (role == com.cotisapp.domain.enums.Role.ADMIN_GIE
                        && OrganisationContext.getOrganisationId() != null);
    }

    private void validerDemandeMobile(Long orgId, Long membreId, String modePaiement, String reference) {
        Membre membre = membre(orgId, membreId);
        if (!Boolean.TRUE.equals(membre.getPaiementMobileActif())) {
            throw new BusinessException(
                    "Le paiement mobile money n'est pas activé pour votre compte. "
                            + "Demandez à l'administrateur GIE de l'activer.");
        }
        ModePaiement mode = ModePaiementHelper.parser(modePaiement);
        if (mode != ModePaiement.WAVE && mode != ModePaiement.ORANGE_MONEY) {
            throw new BusinessException("Seuls Wave et Orange Money sont acceptés pour une demande membre");
        }
        if (reference == null || reference.isBlank()) {
            throw new BusinessException("La référence de transaction mobile est obligatoire");
        }
    }

    private EmpruntResponse verifierEmpruntAppartient(Long orgId, Long membreId, Long empruntId) {
        EmpruntResponse emprunt = empruntService.getById(orgId, empruntId);
        if (!membreId.equals(emprunt.getMembreId())) {
            throw new BusinessException("Cet emprunt ne vous appartient pas");
        }
        return emprunt;
    }

    private Membre membre(Long orgId, Long membreId) {
        return membreRepository
                .findByIdAndOrganisationId(membreId, orgId)
                .orElseThrow(() -> new BusinessException("Membre introuvable"));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("Enregistrement de la demande impossible");
        }
    }

    private static String messageSoumise() {
        return "Demande envoyée. Elle apparaît dans les notifications de l'administrateur pour validation.";
    }

    private static String resumeCotisationHebdo(CotisationHebdoRequest r) {
        return String.format(
                Locale.FRENCH,
                "Cotisation hebdo %s FCFA — semaine %s — %s",
                formatMontant(r.getMontant()),
                r.getSemaineKey(),
                libelleMode(r.getModePaiement()));
    }

    private static String resumeCotisationMois(CotisationMoisRequest r) {
        return String.format(
                Locale.FRENCH,
                "Cotisation mensuelle %s FCFA — %s — %s",
                formatMontant(r.getMontant()),
                r.getMoisAnnee(),
                libelleMode(r.getModePaiement()));
    }

    private static String libelleMode(String mode) {
        if (mode == null) return "—";
        return switch (mode.toUpperCase(Locale.ROOT)) {
            case "WAVE" -> "Wave";
            case "ORANGE_MONEY" -> "Orange Money";
            default -> mode;
        };
    }

    private static String formatMontant(BigDecimal m) {
        if (m == null) return "0";
        return m.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private DemandeOperationMembreResponse toResponse(
            DemandeOperationMembre d, Membre m, String message) {
        return DemandeOperationMembreResponse.builder()
                .id(d.getId())
                .membreId(d.getMembreId())
                .membreNom(m.getNomComplet())
                .codeMembre(m.getCodeMembre())
                .typeDemande(d.getTypeDemande())
                .statut(d.getStatut())
                .montant(d.getMontant())
                .modePaiement(d.getModePaiement() != null ? d.getModePaiement().name() : null)
                .referencePaiement(d.getReferencePaiement())
                .libelleResume(d.getLibelleResume())
                .dateDemande(d.getDateDemande())
                .dateTraitement(d.getDateTraitement())
                .motifRefus(d.getMotifRefus())
                .operationId(d.getOperationId())
                .message(message)
                .build();
    }
}
