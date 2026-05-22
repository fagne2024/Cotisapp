package com.cotisapp.service;

import com.cotisapp.domain.entity.Compte;
import com.cotisapp.domain.entity.Membre;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.CompteRepository;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.SuiviMensuelRepository;
import com.cotisapp.repository.UtilisateurRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MembreSuppressionService {

    private final MembreRepository membreRepository;
    private final OperationRepository operationRepository;
    private final EmpruntRepository empruntRepository;
    private final CompteRepository compteRepository;
    private final SuiviMensuelRepository suiviMensuelRepository;
    private final MembreCompteAccesService membreCompteAccesService;
    private final UtilisateurRoleRepository utilisateurRoleRepository;

    @Transactional
    public void supprimer(Long organisationId, Long membreId) {
        Membre membre = membreRepository.findByIdAndOrganisationId(membreId, organisationId)
                .orElseThrow(() -> new BusinessException("Membre introuvable"));

        long nbOps = operationRepository.countByMembreId(membreId);
        long nbEmprunts = empruntRepository.countByMembreId(membreId);
        if (nbOps > 0 || nbEmprunts > 0) {
            throw new BusinessException(
                    "Impossible de supprimer ce membre : cotisations, opérations ou emprunts existants. "
                            + "Suspendez le membre si vous souhaitez le désactiver.");
        }

        membreCompteAccesService.supprimerCompteAccesMembre(organisationId, membreId);

        suiviMensuelRepository.deleteByMembreId(membreId);

        for (Compte compte : compteRepository.findByMembreId(membreId)) {
            compteRepository.delete(compte);
        }

        utilisateurRoleRepository.deleteByMembreId(membreId);

        membreRepository.delete(membre);
    }
}
