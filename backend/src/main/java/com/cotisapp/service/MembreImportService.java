package com.cotisapp.service;

import com.cotisapp.domain.enums.PosteMembre;
import com.cotisapp.dto.request.ComptesMembreSelection;
import com.cotisapp.dto.request.CreateMembreRequest;
import com.cotisapp.dto.response.ImportMembreLigneErreur;
import com.cotisapp.dto.response.ImportMembresResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.util.TelephoneUtil;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MembreImportService {

    private static final String[] ENTETES = {
            "prenom",
            "nom",
            "email",
            "telephone",
            "date_adhesion",
            "piece_identite",
            "poste",
            "epargne_hebdo",
            "epargne_mois",
            "solidarite",
            "penalite",
            "amende"
    };

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    };

    private final MembreService membreService;

    public byte[] genererModele() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Membres");
            CellStyle headerStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            Row header = sheet.createRow(0);
            for (int i = 0; i < ENTETES.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(ENTETES[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4200);
            }

            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("Fatou");
            example.createCell(1).setCellValue("Diop");
            example.createCell(2).setCellValue("fatou.diop@email.com");
            example.createCell(3).setCellValue("+221771234567");
            example.createCell(4).setCellValue(LocalDate.now().toString());
            example.createCell(5).setCellValue("CNI123456");
            example.createCell(6).setCellValue("SIMPLE");
            example.createCell(7).setCellValue("OUI");
            example.createCell(8).setCellValue("OUI");
            example.createCell(9).setCellValue("OUI");
            example.createCell(10).setCellValue("NON");
            example.createCell(11).setCellValue("NON");

            Sheet aide = wb.createSheet("Aide");
            String[] lignes = {
                    "Colonnes obligatoires : prenom, nom",
                    "poste : SIMPLE, PRESIDENT, VICE_PRESIDENT, SECRETAIRE_GENERAL,",
                    "       SECRETAIRE_GENERAL_ADJOINT, TRESORIER, TRESORIER_ADJOINT,",
                    "       COMMISSAIRE_AUX_COMPTES, SUPERVISEUR",
                    "date_adhesion : AAAA-MM-JJ ou JJ/MM/AAAA",
                    "Comptes (OUI/NON) : epargne_hebdo, epargne_mois, solidarite, penalite, amende",
                    "Doublons refusés : même e-mail, téléphone ou pièce d'identité (fichier ou GIE).",
                    "Supprimez la ligne d'exemple avant l'import ou laissez-la (elle sera ignorée).",
            };
            for (int i = 0; i < lignes.length; i++) {
                aide.createRow(i).createCell(0).setCellValue(lignes[i]);
                aide.setColumnWidth(0, 12000);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("Impossible de générer le modèle d'import");
        }
    }

    public ImportMembresResponse importer(Long organisationId, MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new BusinessException("Fichier vide ou manquant");
        }
        String name = fichier.getOriginalFilename() != null ? fichier.getOriginalFilename().toLowerCase() : "";
        if (name.endsWith(".csv")) {
            return importerCsv(organisationId, fichier);
        }
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new BusinessException("Format attendu : Excel (.xlsx) ou CSV (.csv)");
        }

        List<ImportMembreLigneErreur> erreurs = new ArrayList<>();
        int lignesLues = 0;
        int crees = 0;
        Set<String> emailsFichier = new HashSet<>();
        Set<String> telephonesFichier = new HashSet<>();
        Set<String> piecesFichier = new HashSet<>();

        try (InputStream in = fichier.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
            if (sheet == null) {
                throw new BusinessException("Feuille Excel introuvable");
            }

            Map<String, Integer> colonnes = lireEntetes(sheet.getRow(0));
            if (!colonnes.containsKey("prenom") || !colonnes.containsKey("nom")) {
                throw new BusinessException(
                        "En-têtes invalides. Utilisez le modèle téléchargé (colonnes prenom, nom, …)");
            }

            int lastRow = sheet.getLastRowNum();
            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null || ligneVide(row, colonnes)) {
                    continue;
                }
                lignesLues++;
                int numeroLigne = r + 1;
                try {
                    CreateMembreRequest req = mapperLigneExcel(row, colonnes);
                    if (estLigneExemple(req)) {
                        continue;
                    }
                    req.setCreerCompteAcces(false);
                    verifierDoublonDansFichier(req, emailsFichier, telephonesFichier, piecesFichier);
                    membreService.creer(organisationId, req);
                    crees++;
                } catch (BusinessException ex) {
                    erreurs.add(new ImportMembreLigneErreur(numeroLigne, ex.getMessage()));
                } catch (Exception ex) {
                    erreurs.add(new ImportMembreLigneErreur(numeroLigne, "Erreur : " + ex.getMessage()));
                }
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("Fichier Excel illisible : " + ex.getMessage());
        }

        return buildResult(lignesLues, crees, erreurs);
    }

    private ImportMembresResponse importerCsv(Long organisationId, MultipartFile fichier) {
        List<ImportMembreLigneErreur> erreurs = new ArrayList<>();
        int lignesLues = 0;
        int crees = 0;
        Set<String> emailsFichier = new HashSet<>();
        Set<String> telephonesFichier = new HashSet<>();
        Set<String> piecesFichier = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(fichier.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new BusinessException("Fichier CSV vide");
            }
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }
            String sep = headerLine.contains(";") ? ";" : ",";
            List<String> entetes = Arrays.stream(headerLine.split(sep, -1))
                    .map(this::normaliserEntete)
                    .toList();
            Map<String, Integer> colonnes = new HashMap<>();
            for (int i = 0; i < entetes.size(); i++) {
                if (!entetes.get(i).isEmpty()) {
                    colonnes.put(entetes.get(i), i);
                }
            }
            if (!colonnes.containsKey("prenom") || !colonnes.containsKey("nom")) {
                throw new BusinessException(
                        "En-têtes invalides. Utilisez le modèle téléchargé (colonnes prenom, nom, …)");
            }

            String line;
            int rowIndex = 1;
            while ((line = reader.readLine()) != null) {
                rowIndex++;
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(sep, -1);
                Map<String, String> valeurs = new HashMap<>();
                for (Map.Entry<String, Integer> e : colonnes.entrySet()) {
                    int idx = e.getValue();
                    valeurs.put(e.getKey(), idx < parts.length ? parts[idx].trim() : "");
                }
                if (valeurs.getOrDefault("prenom", "").isBlank() && valeurs.getOrDefault("nom", "").isBlank()) {
                    continue;
                }
                lignesLues++;
                try {
                    CreateMembreRequest req = mapperDepuisValeurs(valeurs);
                    if (estLigneExemple(req)) {
                        continue;
                    }
                    req.setCreerCompteAcces(false);
                    verifierDoublonDansFichier(req, emailsFichier, telephonesFichier, piecesFichier);
                    membreService.creer(organisationId, req);
                    crees++;
                } catch (BusinessException ex) {
                    erreurs.add(new ImportMembreLigneErreur(rowIndex, ex.getMessage()));
                } catch (Exception ex) {
                    erreurs.add(new ImportMembreLigneErreur(rowIndex, "Erreur : " + ex.getMessage()));
                }
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException("Fichier CSV illisible : " + ex.getMessage());
        }

        return buildResult(lignesLues, crees, erreurs);
    }

    private ImportMembresResponse buildResult(int lignesLues, int crees, List<ImportMembreLigneErreur> erreurs) {
        return ImportMembresResponse.builder()
                .lignesLues(lignesLues)
                .membresCrees(crees)
                .erreurs(erreurs)
                .build();
    }

    private Map<String, Integer> lireEntetes(Row headerRow) {
        if (headerRow == null) {
            return Map.of();
        }
        Map<String, Integer> map = new HashMap<>();
        for (Cell cell : headerRow) {
            String key = normaliserEntete(cellString(cell));
            if (!key.isEmpty()) {
                map.put(key, cell.getColumnIndex());
            }
        }
        return map;
    }

    private String normaliserEntete(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('é', 'e')
                .replace('è', 'e');
    }

    private boolean ligneVide(Row row, Map<String, Integer> colonnes) {
        String prenom = lireCell(row, colonnes.get("prenom"));
        String nom = lireCell(row, colonnes.get("nom"));
        return prenom.isBlank() && nom.isBlank();
    }

    private CreateMembreRequest mapperLigneExcel(Row row, Map<String, Integer> colonnes) {
        Map<String, String> valeurs = new HashMap<>();
        for (Map.Entry<String, Integer> e : colonnes.entrySet()) {
            valeurs.put(e.getKey(), lireCell(row, e.getValue()));
        }
        return mapperDepuisValeurs(valeurs);
    }

    private CreateMembreRequest mapperDepuisValeurs(Map<String, String> valeurs) {
        String prenom = valeurs.getOrDefault("prenom", "").trim();
        String nom = valeurs.getOrDefault("nom", "").trim();
        if (prenom.isBlank()) {
            throw new BusinessException("Prénom obligatoire");
        }
        if (nom.isBlank()) {
            throw new BusinessException("Nom obligatoire");
        }

        CreateMembreRequest req = new CreateMembreRequest();
        req.setPrenom(prenom);
        req.setNom(nom);
        req.setEmail(blankToNull(valeurs.get("email")));
        req.setTelephone(blankToNull(valeurs.get("telephone")));
        req.setPieceIdentite(blankToNull(valeurs.get("piece_identite")));
        req.setDateAdhesion(parseDate(valeurs.get("date_adhesion")));
        req.setPoste(parsePoste(valeurs.get("poste")));

        ComptesMembreSelection comptes = new ComptesMembreSelection();
        comptes.setEpargneHebdo(parseOuiNon(valeurs.get("epargne_hebdo"), true));
        comptes.setEpargneMois(parseOuiNon(valeurs.get("epargne_mois"), true));
        comptes.setSolidarite(parseOuiNon(valeurs.get("solidarite"), true));
        comptes.setPenalite(parseOuiNon(valeurs.get("penalite"), false));
        comptes.setAmende(parseOuiNon(valeurs.get("amende"), false));
        req.setComptes(comptes);
        return req;
    }

    private void verifierDoublonDansFichier(
            CreateMembreRequest req,
            Set<String> emailsFichier,
            Set<String> telephonesFichier,
            Set<String> piecesFichier) {
        String email = blankToNull(req.getEmail());
        if (email != null) {
            String cle = email.toLowerCase(Locale.ROOT);
            if (!emailsFichier.add(cle)) {
                throw new BusinessException("Doublon dans le fichier : e-mail « " + email + " » déjà présent");
            }
        }
        String tel = blankToNull(req.getTelephone());
        if (tel != null) {
            String cle = TelephoneUtil.normaliser(tel);
            if (cle != null && !telephonesFichier.add(cle)) {
                throw new BusinessException("Doublon dans le fichier : téléphone « " + tel + " » déjà présent");
            }
        }
        String piece = blankToNull(req.getPieceIdentite());
        if (piece != null) {
            String cle = piece.trim().toUpperCase(Locale.ROOT);
            if (!piecesFichier.add(cle)) {
                throw new BusinessException(
                        "Doublon dans le fichier : pièce d'identité « " + piece + " » déjà présente");
            }
        }
    }

    private boolean estLigneExemple(CreateMembreRequest req) {
        return "Fatou".equalsIgnoreCase(req.getPrenom())
                && "Diop".equalsIgnoreCase(req.getNom())
                && req.getEmail() != null
                && req.getEmail().equalsIgnoreCase("fatou.diop@email.com");
    }

    private PosteMembre parsePoste(String raw) {
        if (raw == null || raw.isBlank()) {
            return PosteMembre.SIMPLE;
        }
        String n = raw.trim()
                .toUpperCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_')
                .replace("É", "E")
                .replace("È", "E");
        return switch (n) {
            case "SIMPLE", "MEMBRE", "MEMBRE_SIMPLE" -> PosteMembre.SIMPLE;
            case "PRESIDENT", "PRESIDENTE", "PRESIDENT(E)" -> PosteMembre.PRESIDENT;
            case "VICE_PRESIDENT", "VICE_PRESIDENTE", "VICE_PRESIDENT(E)" -> PosteMembre.VICE_PRESIDENT;
            case "SG", "SECRETAIRE_GENERAL", "SECRETAIRE_GENERALE" -> PosteMembre.SECRETAIRE_GENERAL;
            case "SGA", "SECRETAIRE_GENERAL_ADJOINT", "SECRETAIRE_GENERALE_ADJOINTE" ->
                    PosteMembre.SECRETAIRE_GENERAL_ADJOINT;
            case "TRESORIER", "TRESORIERE", "TRESORIER(ERE)" -> PosteMembre.TRESORIER;
            case "TRESORIER_ADJOINT", "TRESORIERE_ADJOINTE", "TRESORIER_ADJ", "TADJ" ->
                    PosteMembre.TRESORIER_ADJOINT;
            case "COMMISSAIRE_AUX_COMPTES", "COMMISSAIRE_AU_COMPTE", "COMMISSAIRE", "CAC" ->
                    PosteMembre.COMMISSAIRE_AUX_COMPTES;
            case "SUPERVISEUR", "SUPERVISEURE" -> PosteMembre.SUPERVISEUR;
            default -> {
                try {
                    yield PosteMembre.valueOf(n);
                } catch (IllegalArgumentException e) {
                    throw new BusinessException("Poste invalide : " + raw);
                }
            }
        };
    }

    private boolean parseOuiNon(String raw, boolean defaut) {
        if (raw == null || raw.isBlank()) {
            return defaut;
        }
        String v = raw.trim().toUpperCase(Locale.ROOT);
        if (v.equals("OUI") || v.equals("O") || v.equals("1") || v.equals("TRUE") || v.equals("YES") || v.equals("Y")) {
            return true;
        }
        if (v.equals("NON") || v.equals("N") || v.equals("0") || v.equals("FALSE") || v.equals("NO")) {
            return false;
        }
        throw new BusinessException("Valeur OUI/NON attendue, reçu : " + raw);
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDate.now();
        }
        String v = raw.trim();
        if (v.contains("T")) {
            v = v.substring(0, v.indexOf('T'));
        }
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(v, fmt);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        if (v.matches("\\d+")) {
            try {
                double serial = Double.parseDouble(v);
                return LocalDate.of(1899, 12, 30).plusDays((long) serial);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new BusinessException("Date invalide : " + raw);
    }

    private String lireCell(Row row, Integer colIndex) {
        if (colIndex == null) {
            return "";
        }
        Cell cell = row.getCell(colIndex);
        return cellString(cell).trim();
    }

    private String cellString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d)) {
                    yield String.valueOf((long) d);
                }
                yield String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cellStringEval(cell);
            default -> "";
        };
    }

    private String cellStringEval(Cell cell) {
        try {
            return cell.getStringCellValue();
        } catch (Exception e) {
            try {
                return String.valueOf(cell.getNumericCellValue());
            } catch (Exception e2) {
                return "";
            }
        }
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}
