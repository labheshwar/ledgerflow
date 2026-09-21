package com.ledgerflow.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ledgerflow.storage")
public record StorageProperties(String endpoint, String accessKey, String secretKey, String region, String bucket) {}
