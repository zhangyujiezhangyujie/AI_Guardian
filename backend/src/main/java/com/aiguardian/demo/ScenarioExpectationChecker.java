package com.aiguardian.demo;

import com.aiguardian.domain.model.ChatResponse;

public interface ScenarioExpectationChecker {
    ScenarioCheckResult check(Scenario scenario, ChatResponse response, int httpStatus, String errorCode);
}
