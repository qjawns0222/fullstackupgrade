package com.example.demo.config

import com.example.demo.query.QueryHintInterceptor
import com.example.demo.query.QueryHintRegistry
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.hibernate.cfg.AvailableSettings

@Configuration
class HibernateStatementInspectorConfig {

    @Bean
    fun queryHintHibernateCustomizer(registry: QueryHintRegistry): HibernatePropertiesCustomizer =
        HibernatePropertiesCustomizer { props ->
            props[AvailableSettings.STATEMENT_INSPECTOR] = QueryHintInterceptor(registry)
        }
}
