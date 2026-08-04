ALTER TABLE identity_user
    ADD COLUMN adaptive_step_up_until timestamp with time zone DEFAULT NULL;

CREATE INDEX idx_identity_user_adaptive_step_up
    ON identity_user (adaptive_step_up_until);
