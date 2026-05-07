-- V6__ip_rules.sql
-- Super-managed IP allow/deny rules. Storage only — enforcement filter not wired.

CREATE TYPE ip_rule_type AS ENUM ('ALLOW', 'DENY');

CREATE TABLE ip_rules (
    id BIGSERIAL PRIMARY KEY,
    value VARCHAR(64) NOT NULL,        -- single IP or CIDR
    type ip_rule_type NOT NULL,
    note TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by_id BIGINT REFERENCES users(id)
);

CREATE INDEX idx_ip_rules_type_enabled ON ip_rules(type, enabled);
