package com.cotisapp.repository;

import com.cotisapp.domain.entity.ActionDroit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActionDroitRepository extends JpaRepository<ActionDroit, String> {

    List<ActionDroit> findAllByOrderByOrdreAscLibelleAsc();
}
