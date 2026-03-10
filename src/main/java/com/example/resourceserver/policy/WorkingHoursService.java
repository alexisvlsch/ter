package com.example.resourceserver.policy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Business rule: contextual access is only allowed during the configured working-hours
 * window ({@code access.window.start} → {@code access.window.end}).
 *
 * <p>This is the <em>contextual legitimacy</em> layer on top of JWT validity: even a
 * technically valid (unexpired, correctly signed) JWT is rejected outside business hours
 * on the {@code /api/medical/contextual} endpoint.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkingHoursService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final AccessWindowProperties props;

    /**
     * Returns {@code true} when the current local time falls within the configured
     * working-hours window (inclusive of both bounds).
     */
    public boolean isWithinWorkingHours() {
        LocalTime now   = LocalTime.now();
        LocalTime start = LocalTime.parse(props.getStart(), HH_MM);
        LocalTime end   = LocalTime.parse(props.getEnd(),   HH_MM);

        boolean allowed = !now.isBefore(start) && !now.isAfter(end);
        log.debug("Working-hours check: now={} window=[{}-{}] allowed={}", now, start, end, allowed);
        return allowed;
    }
}
