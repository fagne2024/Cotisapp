package com.cotisapp.repository;

import com.cotisapp.domain.entity.TypeProfilDroit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TypeProfilDroitRepository extends JpaRepository<TypeProfilDroit, TypeProfilDroit.TypeProfilDroitId> {

    List<TypeProfilDroit> findByTypeProfilIdOrderByActionCodeAsc(Long typeProfilId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TypeProfilDroit t where t.typeProfilId = :typeProfilId")
    void deleteByTypeProfilId(@Param("typeProfilId") Long typeProfilId);

    long countByTypeProfilId(Long typeProfilId);
}
