package com.aiguardian.demo;

import java.util.List;
import java.util.Optional;

/** 场景仓库（契约 10.6）：按 scenarioId 升序返回全部场景。 */
public interface ScenarioRepository {

    List<Scenario> findAll();

    Optional<Scenario> findById(String scenarioId);
}
