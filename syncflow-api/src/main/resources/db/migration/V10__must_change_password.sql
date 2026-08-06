-- Force admin-provisioned accounts to set their own password on first login.
ALTER TABLE app_users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;