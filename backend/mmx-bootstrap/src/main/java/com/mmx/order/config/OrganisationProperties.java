package com.mmx.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mmx.organisation")
public record OrganisationProperties(String code) {}
