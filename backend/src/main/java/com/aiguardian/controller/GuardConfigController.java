package com.aiguardian.controller;

import com.aiguardian.config.AiGuardianProperties;
import com.aiguardian.domain.guard.OutputGuardRegistry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequiredArgsConstructor
public class GuardConfigController {
    private final AiGuardianProperties properties;
    private final OutputGuardRegistry registry;

    @GetMapping(path = "/api/guard/config", produces = APPLICATION_JSON_VALUE)
    public GuardConfig config() {
        var defaults = properties.getOutputGuard();
        return new GuardConfig(defaults.getStrategy(), defaults.getBlockThreshold(), defaults.isStrictForToolCall(),
                registry.availableStrategies());
    }

    public record GuardConfig(String strategy, double blockThreshold, boolean strictForToolCall,
                              List<String> availableStrategies) {}
}
