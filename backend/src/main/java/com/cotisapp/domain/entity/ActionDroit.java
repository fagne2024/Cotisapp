package com.cotisapp.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "action_droit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionDroit {

    @Id
    @Column(length = 80)
    private String code;

    @Column(length = 120)
    private String section;

    @Column(nullable = false, length = 200)
    private String libelle;

    @Builder.Default
    private Integer ordre = 0;
}
