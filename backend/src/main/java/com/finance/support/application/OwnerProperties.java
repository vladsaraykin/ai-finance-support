package com.finance.support.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;
import java.util.UUID;

@ConfigurationProperties(prefix = "app")
public record OwnerProperties(UUID ownerId, ZoneId timezone) {}
