ALTER TABLE identity_user
    ADD COLUMN failed_login_attempts integer NOT NULL DEFAULT 0;

ALTER TABLE identity_user
    ADD COLUMN locked_until timestamp with time zone DEFAULT NULL;

ALTER TABLE identity_user
    ADD COLUMN last_failed_login_at timestamp with time zone DEFAULT NULL;
