package com.aiguardian.domain.guard;

import com.aiguardian.common.BusinessException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class OutputGuardRegistry {
    private static final List<String> BUILT_INS = List.of("annotate", "block");
    private final Map<String, OutputGuard> guards;

    public OutputGuardRegistry(List<OutputGuard> guards) {
        this.guards = guards.stream().collect(Collectors.toUnmodifiableMap(OutputGuard::name, Function.identity()));
    }

    public OutputGuard get(String name) {
        OutputGuard guard = name == null ? null : guards.get(name);
        if (guard == null) {
            throw BusinessException.invalidRequest("输出防护策略不存在。");
        }
        return guard;
    }

    public List<String> availableStrategies() {
        return guards.keySet().stream()
                .sorted(Comparator.<String>comparingInt(name -> BUILT_INS.contains(name)
                        ? BUILT_INS.indexOf(name) : BUILT_INS.size()).thenComparing(Comparator.naturalOrder()))
                .toList();
    }
}
