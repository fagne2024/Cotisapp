package com.cotisapp.config;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JpaConfig {

    /**
     * Flyway crée les colonnes enum en VARCHAR ; forcer Hibernate à les mapper en VARCHAR
     * (et non ENUM MySQL) pour que ddl-auto=validate fonctionne.
     */
    @Bean
    public HibernatePropertiesCustomizer enumAsVarcharCustomizer() {
        return props -> props.put("hibernate.type.preferred_enum_jdbc_type", "VARCHAR");
    }
}
