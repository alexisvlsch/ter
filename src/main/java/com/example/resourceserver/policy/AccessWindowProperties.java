package com.example.resourceserver.policy;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code access.window} section of {@code application.yml}.
 *
 * <pre>
 * access:
 *   window:
 *     start: "08:00"
 *     end:   "18:00"
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "access.window")
public class AccessWindowProperties {

    /** Start of the authorized working-hours window (HH:mm, 24-hour). */
    private String start = "08:00";

    /** End of the authorized working-hours window (HH:mm, 24-hour). */
    private String end = "18:00";
}
