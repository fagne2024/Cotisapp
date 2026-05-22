package com.cotisapp.service;

import com.cotisapp.domain.catalogue.ActionDroitCatalogue;
import com.cotisapp.domain.entity.ActionDroit;
import com.cotisapp.domain.entity.TypeProfil;
import com.cotisapp.domain.entity.TypeProfilDroit;
import com.cotisapp.domain.enums.NiveauDroit;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.dto.request.TypeProfilDroitItemRequest;
import com.cotisapp.dto.response.TypeProfilDroitResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.ActionDroitRepository;
import com.cotisapp.repository.TypeProfilDroitRepository;
import com.cotisapp.repository.TypeProfilRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TypeProfilDroitServiceTest {

    @Mock
    private ActionDroitRepository actionDroitRepository;
    @Mock
    private TypeProfilDroitRepository typeProfilDroitRepository;
    @Mock
    private TypeProfilRepository typeProfilRepository;
    @Mock
    private ActionDroitInitialisationService actionDroitInitialisationService;

    @InjectMocks
    private TypeProfilDroitService service;

    private TypeProfil profil;

    @BeforeEach
    void setUp() {
        profil = TypeProfil.builder().id(10L).organisationId(1L).code("SG").role(Role.MEMBRE).build();
        when(typeProfilRepository.findByIdAndOrganisationId(10L, 1L)).thenReturn(Optional.of(profil));
    }

    @Test
    void sauvegarderDroits_rejetteListeVide() {
        assertThrows(BusinessException.class, () -> service.sauvegarderDroits(1L, 10L, List.of()));
    }

    @Test
    void sauvegarderDroits_persisteToutesLesActions() {
        List<ActionDroit> catalogue = catalogueComplet();
        when(actionDroitRepository.findAll()).thenReturn(catalogue);
        when(actionDroitRepository.findAllByOrderByOrdreAscLibelleAsc()).thenReturn(catalogue);
        when(typeProfilDroitRepository.findByTypeProfilIdOrderByActionCodeAsc(10L)).thenReturn(List.of());

        List<TypeProfilDroitItemRequest> items = catalogue.stream()
                .map(a -> {
                    TypeProfilDroitItemRequest r = new TypeProfilDroitItemRequest();
                    r.setActionCode(a.getCode());
                    r.setNiveau(
                            "OP_COTISATION".equals(a.getCode()) ? NiveauDroit.OK : NiveauDroit.NO);
                    return r;
                })
                .toList();

        service.sauvegarderDroits(1L, 10L, items);

        verify(typeProfilDroitRepository).deleteByTypeProfilId(10L);
        ArgumentCaptor<TypeProfilDroit> captor = ArgumentCaptor.forClass(TypeProfilDroit.class);
        verify(typeProfilDroitRepository, atLeast(catalogue.size())).save(captor.capture());
        TypeProfilDroit cotisation = captor.getAllValues().stream()
                .filter(d -> "OP_COTISATION".equals(d.getActionCode()))
                .findFirst()
                .orElseThrow();
        assertEquals(NiveauDroit.OK, cotisation.getNiveau());
    }

    private static List<ActionDroit> catalogueComplet() {
        List<ActionDroit> list = new ArrayList<>();
        for (ActionDroitCatalogue.ActionDef def : ActionDroitCatalogue.toutes()) {
            list.add(ActionDroit.builder()
                    .code(def.code())
                    .section(def.section())
                    .libelle(def.libelle())
                    .ordre(def.ordre())
                    .build());
        }
        return list;
    }
}
