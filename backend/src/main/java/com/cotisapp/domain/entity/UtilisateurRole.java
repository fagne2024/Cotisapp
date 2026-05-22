package com.cotisapp.domain.entity;

import com.cotisapp.domain.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "utilisateur_role")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UtilisateurRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "utilisateur_id", nullable = false)
    private Long utilisateurId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private Role role;

    @Column(name = "organisation_id")
    private Long organisationId;

    @Column(name = "membre_id")
    private Long membreId;

    @Column(name = "type_profil_id")
    private Long typeProfilId;
}
