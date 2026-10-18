-- Users who can sign in when login is enabled (AUTH_ENABLED=true)
CREATE TABLE app_user (
    username      VARCHAR(50)  PRIMARY KEY,
    password_hash VARCHAR(200) NOT NULL,
    role          VARCHAR(10)  NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- AI usage per subject (a signed-in user, or a client IP when login is off) per UTC day.
-- cost_micros is US dollars times 1,000,000, so sums stay exact.
CREATE TABLE usage_daily (
    subject       VARCHAR(120) NOT NULL,
    day           DATE         NOT NULL,
    requests      INTEGER      NOT NULL DEFAULT 0,
    input_tokens  BIGINT       NOT NULL DEFAULT 0,
    output_tokens BIGINT       NOT NULL DEFAULT 0,
    cost_micros   BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (subject, day)
);

CREATE INDEX usage_daily_day ON usage_daily (day);
