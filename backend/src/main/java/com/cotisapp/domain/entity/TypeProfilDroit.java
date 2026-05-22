package com.cotisapp.domain.entity;

import com.cotisapp.domain.enums.NiveauDroit;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;

@Entity
@Table(name = "type_profil_droit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(TypeProfilDroit.TypeProfilDroitId.class)
public class TypeProfilDroit {

    @Id
    @Column(name = "type_profil_id")
    private Long typeProfilId;

    @Id
    @Column(name = "action_code", length = 80)
    private String actionCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private NiveauDroit niveau;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_code", insertable = false, updatable = false)
    private ActionDroit action;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TypeProfilDroitId implements Serializable {
        private Long typeProfilId;
        private String actionCode;
    }
}
