package com.monitorlatino.ytdetect.worker.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = "com.monitorlatino.ytdetect.common.domain.entity")
@EnableJpaRepositories(basePackages = "com.monitorlatino.ytdetect.common.domain.repository")
public class JpaConfig {
}
