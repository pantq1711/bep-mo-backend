-- Capability-based signed media upload workflow.
-- Existing media rows remain valid: media_upload_session_id is nullable for legacy/demo data.

CREATE TABLE media_upload_sessions (
    id                      UUID         PRIMARY KEY,
    owner_id                BIGINT       NOT NULL,
    restaurant_id           BIGINT       NOT NULL,
    purpose                 VARCHAR(30)  NOT NULL,
    profile_video_type      VARCHAR(50),
    recent_proof_type       VARCHAR(50),
    resource_type           VARCHAR(20)  NOT NULL,
    expected_public_id      VARCHAR(255) NOT NULL,
    status                  VARCHAR(30)  NOT NULL DEFAULT 'ISSUED',
    expires_at              TIMESTAMPTZ  NOT NULL,
    validated_at            TIMESTAMPTZ,
    consumed_at             TIMESTAMPTZ,
    rejection_reason        VARCHAR(500),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_media_upload_sessions_owner
        FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_media_upload_sessions_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE RESTRICT,
    CONSTRAINT uq_media_upload_sessions_expected_public_id
        UNIQUE (expected_public_id),
    CONSTRAINT ck_media_upload_sessions_purpose
        CHECK (purpose IN ('PROFILE_VIDEO', 'RECENT_PROOF')),
    CONSTRAINT ck_media_upload_sessions_resource_type
        CHECK (resource_type IN ('IMAGE', 'VIDEO')),
    CONSTRAINT ck_media_upload_sessions_status
        CHECK (status IN ('ISSUED', 'VALIDATED', 'CONSUMED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT ck_media_upload_sessions_video_type
        CHECK (profile_video_type IS NULL OR profile_video_type IN (
            'INGREDIENT_RECEIVING', 'KITCHEN', 'HYGIENE', 'PREP'
        )),
    CONSTRAINT ck_media_upload_sessions_proof_type
        CHECK (recent_proof_type IS NULL OR recent_proof_type IN (
            'INGREDIENT_PHOTO', 'INVOICE', 'DELIVERY_NOTE', 'PACKAGE_LABEL', 'RECEIVING_VIDEO'
        )),
    CONSTRAINT ck_media_upload_sessions_binding
        CHECK (
            (purpose = 'PROFILE_VIDEO'
                AND profile_video_type IS NOT NULL
                AND recent_proof_type IS NULL
                AND resource_type = 'VIDEO')
            OR
            (purpose = 'RECENT_PROOF'
                AND profile_video_type IS NULL
                AND recent_proof_type IS NOT NULL
                AND (
                    (recent_proof_type = 'RECEIVING_VIDEO' AND resource_type = 'VIDEO')
                    OR
                    (recent_proof_type <> 'RECEIVING_VIDEO' AND resource_type = 'IMAGE')
                ))
        )
);

CREATE INDEX idx_media_upload_sessions_owner_status
    ON media_upload_sessions (owner_id, status);

CREATE INDEX idx_media_upload_sessions_restaurant_status
    ON media_upload_sessions (restaurant_id, status);

CREATE INDEX idx_media_upload_sessions_expires_at
    ON media_upload_sessions (expires_at)
    WHERE status IN ('ISSUED', 'VALIDATED');

ALTER TABLE profile_videos
    ADD COLUMN media_upload_session_id UUID;

ALTER TABLE profile_videos
    ADD CONSTRAINT fk_profile_videos_media_upload_session
        FOREIGN KEY (media_upload_session_id)
            REFERENCES media_upload_sessions(id)
            ON DELETE RESTRICT;

CREATE UNIQUE INDEX uq_profile_videos_media_upload_session
    ON profile_videos (media_upload_session_id)
    WHERE media_upload_session_id IS NOT NULL;

ALTER TABLE recent_proofs
    ADD COLUMN media_upload_session_id UUID;

ALTER TABLE recent_proofs
    ADD CONSTRAINT fk_recent_proofs_media_upload_session
        FOREIGN KEY (media_upload_session_id)
            REFERENCES media_upload_sessions(id)
            ON DELETE RESTRICT;

CREATE UNIQUE INDEX uq_recent_proofs_media_upload_session
    ON recent_proofs (media_upload_session_id)
    WHERE media_upload_session_id IS NOT NULL;
