package com.aiguardian.demo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模拟模型的单次响应（契约 10.3）。
 *
 * <p>type：TEXT / TIMEOUT / API_ERROR / EMPTY；content 仅 TEXT 使用；delayMs 可空。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockResponse {
    private String type;
    private String content;
    private Long delayMs;
}
