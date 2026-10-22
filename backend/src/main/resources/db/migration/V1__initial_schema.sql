CREATE TABLE users (
 id UUID PRIMARY KEY, email VARCHAR(254) NOT NULL UNIQUE,
 display_name VARCHAR(100) NOT NULL, password_hash VARCHAR(100),
 verified BOOLEAN NOT NULL DEFAULT FALSE, auth_version INTEGER NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 CHECK (email = lower(email))
);
CREATE TABLE user_identities (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id),
 provider VARCHAR(30) NOT NULL, subject VARCHAR(255) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 UNIQUE(provider, subject)
);
CREATE TABLE auth_tokens (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id),
 kind VARCHAR(20) NOT NULL CHECK (kind IN ('VERIFY','RESET')),
 token_hash VARCHAR(64) NOT NULL UNIQUE, expires_at TIMESTAMPTZ NOT NULL,
 used_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX auth_token_user_idx ON auth_tokens(user_id, kind);
CREATE TABLE businesses (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL UNIQUE REFERENCES users(id),
 name VARCHAR(120) NOT NULL, currency VARCHAR(3) NOT NULL CHECK(currency = 'INR'),
 timezone VARCHAR(80) NOT NULL, opening_date DATE NOT NULL,
 opening_balance NUMERIC(16,2) NOT NULL, reserve NUMERIC(16,2) NOT NULL CHECK(reserve >= 0),
 demo BOOLEAN NOT NULL DEFAULT FALSE, revision BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE recurring_rules (
 id UUID PRIMARY KEY, business_id UUID NOT NULL REFERENCES businesses(id),
 type VARCHAR(12) NOT NULL CHECK(type IN ('INFLOW','OUTFLOW')),
 amount NUMERIC(16,2) NOT NULL CHECK(amount > 0),
 description VARCHAR(200) NOT NULL, category VARCHAR(60) NOT NULL,
 frequency VARCHAR(12) NOT NULL CHECK(frequency IN ('WEEKLY','MONTHLY')),
 start_date DATE NOT NULL, end_date DATE, active BOOLEAN NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 CHECK(end_date IS NULL OR end_date >= start_date), UNIQUE(id, business_id)
);
CREATE INDEX recurring_business_idx ON recurring_rules(business_id);
CREATE TABLE cash_flows (
 id UUID PRIMARY KEY, business_id UUID NOT NULL REFERENCES businesses(id),
 type VARCHAR(12) NOT NULL CHECK(type IN ('INFLOW','OUTFLOW')),
 amount NUMERIC(16,2) NOT NULL CHECK(amount > 0),
 description VARCHAR(200) NOT NULL, category VARCHAR(60) NOT NULL,
 counterparty VARCHAR(120), expected_date DATE NOT NULL,
 status VARCHAR(12) NOT NULL CHECK(status IN ('PLANNED','RECEIVED','PAID','CANCELLED')),
 source VARCHAR(12) NOT NULL CHECK(source IN ('SCHEDULED','UNPLANNED')),
 recurring_rule_id UUID, occurrence_date DATE,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 FOREIGN KEY(recurring_rule_id,business_id) REFERENCES recurring_rules(id,business_id),
 UNIQUE(recurring_rule_id,occurrence_date),
 CHECK((recurring_rule_id IS NULL) = (occurrence_date IS NULL)),
 CHECK(status != 'RECEIVED' OR type = 'INFLOW'),
 CHECK(status != 'PAID' OR type = 'OUTFLOW'),
 CHECK(source != 'UNPLANNED' OR status IN ('RECEIVED','PAID'))
);
CREATE INDEX flows_business_date_idx ON cash_flows(business_id,expected_date);
CREATE TABLE forecasts (
 id UUID PRIMARY KEY, business_id UUID NOT NULL REFERENCES businesses(id),
 start_date DATE NOT NULL, opening_balance NUMERIC(16,2) NOT NULL,
 reserve NUMERIC(16,2) NOT NULL, input_revision BIGINT NOT NULL,
 model_name VARCHAR(100) NOT NULL, model_version VARCHAR(100) NOT NULL,
 limitations TEXT NOT NULL, events TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 UNIQUE(id,business_id)
);
CREATE INDEX forecasts_business_created_idx ON forecasts(business_id,created_at DESC);
CREATE TABLE forecast_points (
 id UUID PRIMARY KEY, forecast_id UUID NOT NULL REFERENCES forecasts(id) ON DELETE CASCADE,
 date DATE NOT NULL, predicted_unplanned_net NUMERIC(16,2) NOT NULL,
 scheduled_inflows NUMERIC(16,2) NOT NULL, scheduled_outflows NUMERIC(16,2) NOT NULL,
 closing_balance NUMERIC(16,2) NOT NULL, UNIQUE(forecast_id,date)
);
CREATE TABLE scenarios (
 id UUID PRIMARY KEY, business_id UUID NOT NULL REFERENCES businesses(id),
 baseline_id UUID NOT NULL, name VARCHAR(120) NOT NULL, description VARCHAR(500) NOT NULL,
 simulated_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 FOREIGN KEY(baseline_id,business_id) REFERENCES forecasts(id,business_id)
);
CREATE INDEX scenarios_business_idx ON scenarios(business_id,created_at DESC);
CREATE TABLE scenario_changes (
 id UUID PRIMARY KEY, scenario_id UUID NOT NULL REFERENCES scenarios(id) ON DELETE CASCADE,
 kind VARCHAR(20) NOT NULL CHECK(kind IN ('RESCHEDULE','AMOUNT_CHANGE','ADD_CASH_FLOW','REMOVE_CASH_FLOW')),
 target_event_id VARCHAR(100), new_date DATE, amount NUMERIC(16,2),
 type VARCHAR(12), description VARCHAR(200), position INTEGER NOT NULL,
 UNIQUE(scenario_id,position)
);
CREATE TABLE scenario_points (
 id UUID PRIMARY KEY, scenario_id UUID NOT NULL REFERENCES scenarios(id) ON DELETE CASCADE,
 date DATE NOT NULL, predicted_unplanned_net NUMERIC(16,2) NOT NULL,
 scheduled_inflows NUMERIC(16,2) NOT NULL, scheduled_outflows NUMERIC(16,2) NOT NULL,
 closing_balance NUMERIC(16,2) NOT NULL, UNIQUE(scenario_id,date)
);
