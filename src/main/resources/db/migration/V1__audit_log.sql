-- admin-api audit trail (ADR-0011). The one table admin-api owns. Every operator
-- action is dual-recorded here and on the Kafka admin.action topic, so an
-- incident can be cross-verified from two independent stores.
CREATE TABLE audit_log (
    action_id   UUID         PRIMARY KEY,
    actor_id    VARCHAR(128) NOT NULL,
    actor_role  VARCHAR(32)  NOT NULL,
    action      VARCHAR(64)  NOT NULL,
    target      VARCHAR(256),
    outcome     VARCHAR(16)  NOT NULL,
    http_status INTEGER      NOT NULL,
    reason      VARCHAR(512),
    trace_id    VARCHAR(64),
    occurred_at TIMESTAMPTZ  NOT NULL
);

-- The audit-log query filters by time window and optional actor, newest first.
CREATE INDEX idx_audit_log_occurred_at ON audit_log (occurred_at DESC);
CREATE INDEX idx_audit_log_actor_time ON audit_log (actor_id, occurred_at DESC);
