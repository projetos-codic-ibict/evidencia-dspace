CREATE TABLE custom_audit_log (
    id UUID PRIMARY KEY,
    eperson_id UUID,
    action_type VARCHAR(50),
    subject_type VARCHAR(50),
    subject_id UUID,
    event_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    details TEXT,
    subject_name VARCHAR(255)
);

CREATE INDEX idx_custom_audit_timestamp ON custom_audit_log(event_timestamp);
CREATE INDEX idx_custom_audit_eperson ON custom_audit_log(eperson_id);