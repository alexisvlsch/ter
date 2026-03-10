package com.example.resourceserver.policy;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables the {@link AccessWindowProperties} configuration-properties bean.
 */
@Configuration
@EnableConfigurationProperties(AccessWindowProperties.class)
public class PolicyConfig {
}
