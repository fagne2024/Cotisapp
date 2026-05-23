package com.cotisapp.service;

import com.cotisapp.domain.entity.Emprunt;
import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.enums.TypeEmprunt;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.util.ModePaiementHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Libellés explicites pour le journal caisse (membre, type d'emprunt, détail métier lisible).
 */
public final class JournalCaisseLibelleFormatter {

    private static final Pattern EMPRUNT_ID = Pattern.compile("emprunt\\s*#(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAIEMENT_SEGMENT =
            Pattern.compile("Paiement:\\s*[^·—]+(?:\\s*\\(réf\\.[^)]+\\))?", Pattern.CASE_INSENSITIVE);

    private JournalCaisseLibelleFormatter() {}

    public record Context(Map<Long, Membre> membres, Map<Long, Emprunt> emprunts) {}

    public static Context buildContext(
            List<Operation> operations, MembreRepository membreRepository, EmpruntRepository empruntRepository) {
        Set<Long> membreIds = new HashSet<>();
        Set<Long> empruntIds = new HashSet<>();
        for (Operation op : operations) {
            if (op == null) {
                continue;
            }
            if (op.getMembreId() != null) {
                membreIds.add(op.getMembreId());
            }
            if (op.getEmpruntId() != null) {
                empruntIds.add(op.getEmpruntId());
            }
            extraireEmpruntIdsObservation(op.getObservation()).forEach(empruntIds::add);
        }
        Map<Long, Emprunt> emprunts =
                empruntRepository.findAllById(empruntIds).stream().collect(Collectors.toMap(Emprunt::getId, e -> e));
        for (Emprunt e : emprunts.values()) {
            membreIds.add(e.getMembreId());
        }
        Map<Long, Membre> membres =
                membreRepository.findAllById(membreIds).stream().collect(Collectors.toMap(Membre::getId, m -> m));
        return new Context(membres, emprunts);
    }

    public static String format(Operation op, Context ctx) {
        if (op == null) {
            return "";
        }
        String observation = op.getObservation();
        boolean annulation = observation != null && observation.contains("[ANNULATION]");

        LinkedHashSet<String> parties = new LinkedHashSet<>();
        if (annulation) {
            parties.add("Annulation");
            String refOp = extraireRefOperationAnnulation(observation);
            if (refOp != null) {
                parties.add("contre-passation op. " + refOp);
            }
            observation = retirerPrefixeAnnulation(observation);
        }

        String principal = libellePrincipal(op, ctx);
        if (principal != null && !principal.isBlank()) {
            parties.add(principal);
        }

        String detail = formaterSegmentsObservation(observation, op, ctx);
        if (detail != null && !detail.isBlank() && !contientDeja(parties, detail)) {
            parties.add(detail);
        }

        String paiement = segmentPaiement(op, observation);
        if (paiement != null && !contientDeja(parties, paiement)) {
            parties.add(paiement);
        }

        if (parties.isEmpty()) {
            return libelleTypeOperationCourt(op.getTypeOperation());
        }
        return String.join(" · ", parties);
    }

    private static String libellePrincipal(Operation op, Context ctx) {
        TypeOperation type = op.getTypeOperation();
        if (type == null) {
            return null;
        }
        String membre = libelleMembre(op.getMembreId(), ctx);
        Long empruntId = op.getEmpruntId();
        if (empruntId == null) {
            empruntId = premierEmpruntIdObservation(op.getObservation());
        }

        return switch (type) {
            case COTISATION -> prefixerMembre("Cotisation hebdomadaire", membre);
            case COTISATION_MOIS -> prefixerMembre("Cotisation mensuelle", membre);
            case VERSEMENT -> prefixerMembre("Versement", membre);
            case REMBOURSEMENT -> prefixerMembre("Remboursement", membre);
            case EMPRUNT -> "Octroi — " + libelleEmprunt(empruntId, ctx);
            case PENALITE -> prefixerMembre("Pénalité", membre);
            case AMENDE -> prefixerMembre("Amende", membre);
            case REPARTITION_EXERCICE -> "Répartition clôture exercice";
            case DEPENSE, BANQUE_VERSEMENT, BANQUE_RETRAIT -> null;
        };
    }

    private static String formaterSegmentsObservation(String observation, Operation op, Context ctx) {
        if (observation == null || observation.isBlank()) {
            return "";
        }
        String reste = retirerSegmentPaiement(observation.trim());
        if (reste.isBlank()) {
            return "";
        }

        List<String> segments = new ArrayList<>();
        for (String raw : reste.split("\\s*[·—]\\s*")) {
            String seg = raw.trim();
            if (seg.isBlank()) {
                continue;
            }
            String formate = formaterUnSegment(seg, op, ctx);
            if (formate != null && !formate.isBlank()) {
                segments.add(formate);
            }
        }
        return String.join(" · ", segments);
    }

    private static String formaterUnSegment(String seg, Operation op, Context ctx) {
        if (seg.contains(EmpruntAvanceCaisseHelper.PREFIX_REMBOURSEMENT_SPLIT)) {
            return formaterRepartitionRemboursement(seg);
        }
        if (seg.contains(EmpruntAvanceCaisseHelper.PREFIX_AVANCE_OCTROI)) {
            return formaterAvanceCaisse(seg);
        }
        if (seg.toLowerCase().contains("frais / intérêts") || seg.toLowerCase().contains("frais / interets")) {
            return formaterFraisInterets(seg, ctx);
        }
        seg = remplacerEmpruntIds(seg, ctx);
        seg = seg.replace("→ compte intérêts", "→ compte Intérêts org.");
        if (PAIEMENT_SEGMENT.matcher(seg).matches()) {
            return "";
        }
        return seg.trim();
    }

    private static String formaterRepartitionRemboursement(String seg) {
        var partCaisse = EmpruntAvanceCaisseHelper.extrairePartCaisseRemboursement(seg);
        String sol = "";
        int idxSol = seg.indexOf("Solidarité:");
        if (idxSol >= 0) {
            int start = idxSol + "Solidarité:".length();
            int end = seg.indexOf('F', start);
            if (end > start) {
                sol = seg.substring(start, end).trim();
            }
        }
        if (partCaisse.signum() > 0 && !sol.isBlank()) {
            return "Répartition : " + formatMontantF(partCaisse) + " (caisse, avance à l'octroi) + " + sol + " F (solidarité)";
        }
        if (partCaisse.signum() > 0) {
            return "Part caisse (avance octroi) : " + formatMontantF(partCaisse);
        }
        return "Répartition remboursement solidarité";
    }

    private static String formaterAvanceCaisse(String seg) {
        String num = seg.replace(EmpruntAvanceCaisseHelper.PREFIX_AVANCE_OCTROI, "").trim();
        if (num.toLowerCase().contains("complétés") || num.toLowerCase().contains("completes")) {
            int i = num.toLowerCase().indexOf("compl");
            if (i > 0) {
                num = num.substring(0, i).trim();
            }
        }
        return "Avance caisse " + num + " (fonds solidarité insuffisant à l'octroi)";
    }

    private static String formaterFraisInterets(String seg, Context ctx) {
        Long empruntId = premierEmpruntIdObservation(seg);
        String cible = empruntId != null ? libelleEmprunt(empruntId, ctx) : "emprunt";
        return "Frais et intérêts — " + cible + " → compte Intérêts org.";
    }

    private static String remplacerEmpruntIds(String texte, Context ctx) {
        Matcher m = EMPRUNT_ID.matcher(texte);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            long id = Long.parseLong(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(libelleEmprunt(id, ctx)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String libelleEmprunt(Long empruntId, Context ctx) {
        if (empruntId == null) {
            return "emprunt";
        }
        Emprunt e = ctx.emprunts().get(empruntId);
        if (e == null) {
            return "emprunt #" + empruntId;
        }
        String type = libelleTypeEmprunt(e.getTypeEmprunt());
        String membre = libelleMembre(e.getMembreId(), ctx);
        if (membre != null) {
            return "emprunt " + type + " — " + membre;
        }
        return "emprunt " + type + " #" + empruntId;
    }

    private static String libelleMembre(Long membreId, Context ctx) {
        if (membreId == null) {
            return null;
        }
        Membre m = ctx.membres().get(membreId);
        if (m == null) {
            return null;
        }
        return m.getNom() + " " + m.getPrenom() + " (" + m.getCodeMembre() + ")";
    }

    private static String prefixerMembre(String action, String membre) {
        if (membre == null || membre.isBlank()) {
            return action;
        }
        return action + " — " + membre;
    }

    private static String libelleTypeEmprunt(TypeEmprunt type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case ETALE -> "étalé";
            case SOLIDARITE -> "solidarité";
            case CAISSE -> "caisse";
        };
    }

    private static String segmentPaiement(Operation op, String observation) {
        if (op.getModePaiement() != null) {
            String lib = ModePaiementHelper.libelle(op.getModePaiement());
            if (op.getReferencePaiement() != null && !op.getReferencePaiement().isBlank()) {
                return lib + " (réf. " + op.getReferencePaiement().trim() + ")";
            }
            return lib;
        }
        if (observation == null) {
            return null;
        }
        Matcher m = PAIEMENT_SEGMENT.matcher(observation);
        if (m.find()) {
            String brut = m.group().trim();
            return brut.replaceFirst("(?i)Paiement:\\s*", "").trim();
        }
        return null;
    }

    private static String retirerSegmentPaiement(String observation) {
        return PAIEMENT_SEGMENT.matcher(observation).replaceAll("").replaceAll("\\s*[·—]\\s*$", "").trim();
    }

    private static String retirerPrefixeAnnulation(String observation) {
        if (observation == null) {
            return "";
        }
        return observation
                .replaceFirst("\\[ANNULATION]\\s*Contre-passation opération\\s*#\\d+\\s*[·—]?\\s*", "")
                .trim();
    }

    private static String extraireRefOperationAnnulation(String observation) {
        if (observation == null) {
            return null;
        }
        Matcher m = Pattern.compile("Contre-passation opération\\s*#(\\d+)", Pattern.CASE_INSENSITIVE)
                .matcher(observation);
        return m.find() ? "#" + m.group(1) : null;
    }

    private static Set<Long> extraireEmpruntIdsObservation(String observation) {
        Set<Long> ids = new HashSet<>();
        if (observation == null) {
            return ids;
        }
        Matcher m = EMPRUNT_ID.matcher(observation);
        while (m.find()) {
            ids.add(Long.parseLong(m.group(1)));
        }
        return ids;
    }

    private static Long premierEmpruntIdObservation(String observation) {
        Set<Long> ids = extraireEmpruntIdsObservation(observation);
        return ids.isEmpty() ? null : ids.iterator().next();
    }

    private static String formatMontantF(java.math.BigDecimal montant) {
        return montant.stripTrailingZeros().toPlainString() + " F";
    }

    private static boolean contientDeja(Set<String> parties, String fragment) {
        String f = fragment.toLowerCase();
        for (String p : parties) {
            if (p.toLowerCase().contains(f) || f.contains(p.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static String libelleTypeOperationCourt(TypeOperation type) {
        if (type == null) {
            return "Opération";
        }
        return switch (type) {
            case DEPENSE -> "Dépense";
            case BANQUE_VERSEMENT -> "Versement vers banque";
            case BANQUE_RETRAIT -> "Retrait banque";
            case COTISATION -> "Cotisation hebdo";
            case COTISATION_MOIS -> "Cotisation mensuelle";
            case VERSEMENT -> "Versement";
            case REMBOURSEMENT -> "Remboursement";
            case EMPRUNT -> "Emprunt";
            case PENALITE -> "Pénalité";
            case AMENDE -> "Amende";
            case REPARTITION_EXERCICE -> "Répartition clôture";
        };
    }
}
