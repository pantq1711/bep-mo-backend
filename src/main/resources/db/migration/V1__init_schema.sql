CREATE TABLE users (
                       id              BIGSERIAL PRIMARY KEY,
                       email           VARCHAR(255) NOT NULL UNIQUE,
                       password_hash   VARCHAR(255) NOT NULL,
                       role            VARCHAR(50)  NOT NULL,
                       status          VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
                       created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                       updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

                       CONSTRAINT ck_users_role
                           CHECK (role IN ('RESTAURANT_OWNER', 'ADMIN')),
                       CONSTRAINT ck_users_status
                           CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE refresh_tokens (
                                id              BIGSERIAL PRIMARY KEY,
                                user_id         BIGINT       NOT NULL,
                                token_hash      VARCHAR(255) NOT NULL UNIQUE,
                                expires_at      TIMESTAMPTZ  NOT NULL,
                                revoked_at      TIMESTAMPTZ,
                                created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

                                CONSTRAINT fk_refresh_tokens_user
                                    FOREIGN KEY (user_id)
                                        REFERENCES users(id)
                                        ON DELETE CASCADE
);

CREATE TABLE restaurants (
                             id                  BIGSERIAL PRIMARY KEY,
                             owner_id            BIGINT       NOT NULL UNIQUE,
                             name                VARCHAR(150) NOT NULL,
                             description         TEXT,
                             address             VARCHAR(255) NOT NULL,
                             category            VARCHAR(80),
                             avatar_url          TEXT,
                             avatar_public_id    VARCHAR(255),
                             status              VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
                             created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                             updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

                             CONSTRAINT fk_restaurants_owner
                                 FOREIGN KEY (owner_id)
                                     REFERENCES users(id)
                                     ON DELETE RESTRICT,
                             CONSTRAINT ck_restaurants_status
                                 CHECK (status IN ('ACTIVE', 'HIDDEN'))
);

CREATE TABLE dishes (
                        id                  BIGSERIAL PRIMARY KEY,
                        restaurant_id       BIGINT         NOT NULL,
                        name                VARCHAR(150)   NOT NULL,
                        description         TEXT,
                        price               NUMERIC(12, 2) NOT NULL,
                        category            VARCHAR(80),
                        image_url           TEXT,
                        image_public_id     VARCHAR(255),
                        is_available        BOOLEAN        NOT NULL DEFAULT TRUE,
                        created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
                        updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

                        CONSTRAINT fk_dishes_restaurant
                            FOREIGN KEY (restaurant_id)
                                REFERENCES restaurants(id)
                                ON DELETE RESTRICT,
                        CONSTRAINT ck_dishes_price_non_negative
                            CHECK (price >= 0)
);

CREATE TABLE profile_videos (
                                id                      BIGSERIAL PRIMARY KEY,
                                restaurant_id           BIGINT       NOT NULL,
                                type                    VARCHAR(50)  NOT NULL,
                                cloudinary_url          TEXT         NOT NULL,
                                cloudinary_public_id    VARCHAR(255) NOT NULL,
                                thumbnail_url           TEXT,
                                duration_seconds        INTEGER      NOT NULL,
                                file_size_bytes         BIGINT       NOT NULL,
                                status                  VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
                                created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                                updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

                                CONSTRAINT fk_profile_videos_restaurant
                                    FOREIGN KEY (restaurant_id)
                                        REFERENCES restaurants(id)
                                        ON DELETE RESTRICT,
                                CONSTRAINT ck_profile_videos_type
                                    CHECK (type IN ('INGREDIENT_RECEIVING', 'KITCHEN', 'HYGIENE', 'PREP')),
                                CONSTRAINT ck_profile_videos_status
                                    CHECK (status IN ('ACTIVE', 'REPLACED', 'HIDDEN', 'DELETED')),
                                CONSTRAINT ck_profile_videos_duration
                                    CHECK (duration_seconds > 0 AND duration_seconds <= 60),
                                CONSTRAINT ck_profile_videos_file_size
                                    CHECK (file_size_bytes > 0)
);

CREATE TABLE ingredient_sources (
                                    id              BIGSERIAL PRIMARY KEY,
                                    restaurant_id   BIGINT       NOT NULL,
                                    name            VARCHAR(150) NOT NULL,
                                    source_type     VARCHAR(50)  NOT NULL,
                                    note            TEXT,
                                    status          VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
                                    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                                    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

                                    CONSTRAINT fk_ingredient_sources_restaurant
                                        FOREIGN KEY (restaurant_id)
                                            REFERENCES restaurants(id)
                                            ON DELETE RESTRICT,
                                    CONSTRAINT ck_ingredient_sources_type
                                        CHECK (source_type IN (
                                                               'LOCAL_MARKET', 'WHOLESALE_MARKET', 'DIRECT_FARM',
                                                               'SUPPLIER_COMPANY', 'SUPERMARKET', 'OTHER'
                                            )),
                                    CONSTRAINT ck_ingredient_sources_status
                                        CHECK (status IN ('ACTIVE', 'HIDDEN', 'DELETED'))
);

CREATE TABLE recent_proofs (
                               id                      BIGSERIAL PRIMARY KEY,
                               restaurant_id           BIGINT       NOT NULL,
                               proof_type              VARCHAR(50)  NOT NULL,
                               media_kind              VARCHAR(20)  NOT NULL,
                               media_url               TEXT         NOT NULL,
                               cloudinary_public_id    VARCHAR(255) NOT NULL,
                               note                    TEXT,
                               status                  VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
                               uploaded_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                               updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

                               CONSTRAINT fk_recent_proofs_restaurant
                                   FOREIGN KEY (restaurant_id)
                                       REFERENCES restaurants(id)
                                       ON DELETE RESTRICT,
                               CONSTRAINT ck_recent_proofs_type
                                   CHECK (proof_type IN (
                                                         'INGREDIENT_PHOTO', 'INVOICE', 'DELIVERY_NOTE',
                                                         'PACKAGE_LABEL', 'RECEIVING_VIDEO'
                                       )),
                               CONSTRAINT ck_recent_proofs_media_kind
                                   CHECK (media_kind IN ('IMAGE', 'VIDEO')),
                               CONSTRAINT ck_recent_proofs_type_media_compatibility
                                   CHECK (
                                       (proof_type = 'RECEIVING_VIDEO' AND media_kind = 'VIDEO')
                                           OR
                                       (proof_type <> 'RECEIVING_VIDEO' AND media_kind = 'IMAGE')
                                       ),
                               CONSTRAINT ck_recent_proofs_status
                                   CHECK (status IN ('ACTIVE', 'HIDDEN', 'DELETED'))
);

-- Indexes
CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens (user_id);

CREATE INDEX idx_restaurants_status
    ON restaurants (status);

CREATE INDEX idx_dishes_restaurant_available
    ON dishes (restaurant_id, is_available);

CREATE INDEX idx_profile_videos_restaurant_status
    ON profile_videos (restaurant_id, status);

CREATE UNIQUE INDEX uq_profile_videos_one_active_per_type
    ON profile_videos (restaurant_id, type)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_ingredient_sources_restaurant_status
    ON ingredient_sources (restaurant_id, status);

CREATE INDEX idx_recent_proofs_latest_active
    ON recent_proofs (restaurant_id, uploaded_at DESC)
    WHERE status = 'ACTIVE';

-- Intentionally omitted:
-- idx_restaurants_name: no justified search query yet
-- idx_refresh_tokens_expires_at: add when cleanup job is implemented
