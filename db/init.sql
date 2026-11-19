CREATE DATABASE IF NOT EXISTS ai_guardian
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE ai_guardian;

CREATE TABLE IF NOT EXISTS chat_session (
    id          VARCHAR(20) NOT NULL PRIMARY KEY,
    created_at  DATETIME(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS chat_message (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id  VARCHAR(20) NOT NULL,
    request_id  VARCHAR(20) NOT NULL,
    role        VARCHAR(10) NOT NULL,
    content     TEXT        NOT NULL,
    created_at  DATETIME(3) NOT NULL,
    INDEX idx_msg_session (session_id),
    INDEX idx_msg_request (request_id)
);

CREATE TABLE IF NOT EXISTS verification_record (
    request_id           VARCHAR(20)  NOT NULL PRIMARY KEY,
    session_id           VARCHAR(20)  NOT NULL,
    mode                 VARCHAR(8)   NOT NULL,
    scenario_id          VARCHAR(8)   NULL,
    question             VARCHAR(500) NOT NULL,
    raw_answer           TEXT         NULL,
    verification_state   VARCHAR(16)  NULL,
    failure_reason       VARCHAR(32)  NULL,
    verifier_name        VARCHAR(16)  NULL,
    llm_calls            INT          NOT NULL DEFAULT 0,
    total_claims         INT          NOT NULL DEFAULT 0,
    supported_claims     INT          NOT NULL DEFAULT 0,
    insufficient_claims  INT          NOT NULL DEFAULT 0,
    unsupported_ratio    DECIMAL(5,4) NULL,
    truncated            TINYINT(1)   NOT NULL DEFAULT 0,
    guard_strategy       VARCHAR(16)  NULL,
    guard_threshold      DECIMAL(3,2) NULL,
    guard_action         VARCHAR(16)  NULL,
    evidence_json        JSON         NOT NULL,
    retrieval_ms         INT          NOT NULL,
    generation_ms        INT          NULL,
    verification_ms      INT          NULL,
    total_ms             INT          NOT NULL,
    error_code           VARCHAR(32)  NULL,
    created_at           DATETIME(3)  NOT NULL,
    INDEX idx_vr_created (created_at),
    INDEX idx_vr_state (verification_state),
    INDEX idx_vr_action (guard_action)
);

CREATE TABLE IF NOT EXISTS claim_record (
    id                   BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_id           VARCHAR(20) NOT NULL,
    claim_id             VARCHAR(4)  NOT NULL,
    claim                TEXT        NOT NULL,
    original_text        TEXT        NULL,
    start_index          INT         NULL,
    end_index            INT         NULL,
    model_supported      TINYINT(1)  NOT NULL,
    status               VARCHAR(16) NOT NULL,
    evidence_id          VARCHAR(20) NULL,
    quote                TEXT        NULL,
    quote_exists         TINYINT(1)  NULL,
    insufficient_reason  VARCHAR(32) NULL,
    UNIQUE KEY uk_claim (request_id, claim_id)
);

CREATE TABLE IF NOT EXISTS audit_event (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_id   VARCHAR(20) NOT NULL,
    event_type   VARCHAR(32) NOT NULL,
    success      TINYINT(1)  NOT NULL,
    duration_ms  INT         NULL,
    error_code   VARCHAR(32) NULL,
    detail_json  JSON        NULL,
    created_at   DATETIME(3) NOT NULL,
    INDEX idx_ae_request (request_id)
);
