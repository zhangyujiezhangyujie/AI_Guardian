package com.aiguardian.demo;

import java.util.List;

/** Result returned by the scenario expectation checker. */
public record ScenarioCheckResult(boolean checked, Boolean passed, List<String> mismatches) {
    public ScenarioCheckResult {
        mismatches = mismatches == null ? List.of() : List.copyOf(mismatches);
    }
}
