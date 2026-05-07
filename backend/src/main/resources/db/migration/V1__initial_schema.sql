-- V1__initial_schema.sql
-- Poll System Initial Schema

-- ==========================================
-- Enums
-- ==========================================
CREATE TYPE access_level AS ENUM ('VIEWER', 'USER', 'CREATOR', 'ADMIN', 'SUPER');
CREATE TYPE request_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED');
CREATE TYPE poll_status AS ENUM ('DRAFT', 'PUBLISHED', 'CLOSED', 'ARCHIVED');

-- ==========================================
-- Users
-- ==========================================
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20) NOT NULL UNIQUE,
    zipcode VARCHAR(5) NOT NULL,
    passcode VARCHAR(255) NOT NULL,
    access access_level NOT NULL DEFAULT 'VIEWER',
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_users_zipcode ON users(zipcode);
CREATE INDEX idx_users_access ON users(access);

-- ==========================================
-- Geographic Tables
-- ==========================================
CREATE TABLE states (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    initial VARCHAR(2) NOT NULL UNIQUE
);

CREATE TABLE counties (
    id BIGSERIAL PRIMARY KEY,
    state_id BIGINT NOT NULL REFERENCES states(id),
    name VARCHAR(255) NOT NULL
);

CREATE INDEX idx_counties_state ON counties(state_id);

CREATE TABLE county_zips (
    id BIGSERIAL PRIMARY KEY,
    county_id BIGINT NOT NULL REFERENCES counties(id),
    zipcode VARCHAR(5) NOT NULL
);

CREATE INDEX idx_county_zips_county ON county_zips(county_id);
CREATE INDEX idx_county_zips_zipcode ON county_zips(zipcode);

-- ==========================================
-- Poll Types
-- ==========================================
CREATE TABLE poll_types (
    id BIGSERIAL PRIMARY KEY,
    poll_type INTEGER NOT NULL,
    name VARCHAR(100) NOT NULL
);

-- ==========================================
-- Role Assignments
-- ==========================================
CREATE TABLE role_assignments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    role access_level NOT NULL,
    poll_type_id BIGINT REFERENCES poll_types(id),
    state_id BIGINT NOT NULL REFERENCES states(id),
    county_id BIGINT NOT NULL REFERENCES counties(id),
    zipcode VARCHAR(5) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_role_assignments_user ON role_assignments(user_id);
CREATE INDEX idx_role_assignments_role ON role_assignments(role);
CREATE INDEX idx_role_assignments_zipcode ON role_assignments(zipcode);
CREATE INDEX idx_role_assignments_role_zipcode ON role_assignments(role, zipcode, enabled);

-- ==========================================
-- Creator Requests
-- ==========================================
CREATE TABLE creator_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    assigned_admin_id BIGINT REFERENCES users(id),
    status request_status NOT NULL DEFAULT 'PENDING',
    reason TEXT NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_creator_requests_status ON creator_requests(status);
CREATE INDEX idx_creator_requests_admin ON creator_requests(assigned_admin_id, status);

-- ==========================================
-- Offices
-- ==========================================
CREATE TABLE offices (
    id BIGSERIAL PRIMARY KEY,
    "desc" TEXT NOT NULL,
    date_created TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- ==========================================
-- Elections
-- ==========================================
CREATE TABLE elections (
    id BIGSERIAL PRIMARY KEY,
    creator_id BIGINT NOT NULL REFERENCES users(id),
    poll_type_id BIGINT NOT NULL REFERENCES poll_types(id),
    title VARCHAR(500) NOT NULL,
    date DATE NOT NULL,
    zipcode VARCHAR(5) NOT NULL,
    status poll_status NOT NULL DEFAULT 'DRAFT',
    close_date TIMESTAMP WITH TIME ZONE,
    date_submitted TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_elections_creator ON elections(creator_id);
CREATE INDEX idx_elections_zipcode ON elections(zipcode);
CREATE INDEX idx_elections_status ON elections(status);
CREATE INDEX idx_elections_close_date ON elections(close_date);

-- ==========================================
-- Candidates
-- ==========================================
CREATE TABLE candidates (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    affiliation VARCHAR(255) NOT NULL,
    office_id BIGINT NOT NULL REFERENCES offices(id),
    election_id BIGINT NOT NULL REFERENCES elections(id),
    create_date DATE NOT NULL DEFAULT CURRENT_DATE
);

CREATE INDEX idx_candidates_election ON candidates(election_id);

-- ==========================================
-- Candidate Responses
-- ==========================================
CREATE TABLE candidate_responses (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    candidate_id BIGINT NOT NULL REFERENCES candidates(id),
    response BOOLEAN NOT NULL,
    comment TEXT,
    date_submitted TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified TIMESTAMP WITH TIME ZONE,
    UNIQUE (user_id, candidate_id)
);

CREATE INDEX idx_candidate_responses_candidate ON candidate_responses(candidate_id);
CREATE INDEX idx_candidate_responses_user ON candidate_responses(user_id);

-- ==========================================
-- Ballot Measures
-- ==========================================
CREATE TABLE ballot_measures (
    id BIGSERIAL PRIMARY KEY,
    creator_id BIGINT NOT NULL REFERENCES users(id),
    poll_type_id BIGINT NOT NULL REFERENCES poll_types(id),
    title VARCHAR(500) NOT NULL,
    summary TEXT NOT NULL,
    election_id BIGINT NOT NULL REFERENCES elections(id),
    effective_date DATE NOT NULL,
    status poll_status NOT NULL DEFAULT 'DRAFT',
    close_date TIMESTAMP WITH TIME ZONE,
    date_created TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ballot_measures_election ON ballot_measures(election_id);
CREATE INDEX idx_ballot_measures_creator ON ballot_measures(creator_id);
CREATE INDEX idx_ballot_measures_status ON ballot_measures(status);

-- ==========================================
-- Ballot Responses
-- ==========================================
CREATE TABLE ballot_responses (
    id BIGSERIAL PRIMARY KEY,
    measure_id BIGINT NOT NULL REFERENCES ballot_measures(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    response BOOLEAN NOT NULL,
    comment TEXT,
    date_submitted TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified TIMESTAMP WITH TIME ZONE,
    UNIQUE (user_id, measure_id)
);

CREATE INDEX idx_ballot_responses_measure ON ballot_responses(measure_id);
CREATE INDEX idx_ballot_responses_user ON ballot_responses(user_id);

-- ==========================================
-- Questionnaires
-- ==========================================
CREATE TABLE questionnaires (
    id BIGSERIAL PRIMARY KEY,
    creator_id BIGINT NOT NULL REFERENCES users(id),
    poll_type_id BIGINT NOT NULL REFERENCES poll_types(id),
    title VARCHAR(500) NOT NULL,
    summary TEXT NOT NULL,
    status poll_status NOT NULL DEFAULT 'DRAFT',
    close_date TIMESTAMP WITH TIME ZONE,
    create_date DATE NOT NULL DEFAULT CURRENT_DATE,
    submit_date TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_questionnaires_creator ON questionnaires(creator_id);
CREATE INDEX idx_questionnaires_status ON questionnaires(status);

-- ==========================================
-- Questionnaire Domains
-- ==========================================
CREATE TABLE questionnaire_domains (
    id BIGSERIAL PRIMARY KEY,
    questionnaire_id BIGINT NOT NULL REFERENCES questionnaires(id),
    state_id BIGINT NOT NULL REFERENCES states(id),
    county_id BIGINT NOT NULL REFERENCES counties(id),
    zipcode VARCHAR(5) NOT NULL
);

CREATE INDEX idx_questionnaire_domains_questionnaire ON questionnaire_domains(questionnaire_id);
CREATE INDEX idx_questionnaire_domains_zipcode ON questionnaire_domains(zipcode);

-- ==========================================
-- Questions
-- ==========================================
CREATE TABLE questions (
    id BIGSERIAL PRIMARY KEY,
    questionnaire_id BIGINT NOT NULL REFERENCES questionnaires(id),
    question TEXT NOT NULL
);

CREATE INDEX idx_questions_questionnaire ON questions(questionnaire_id);

-- ==========================================
-- Question Responses
-- ==========================================
CREATE TABLE question_responses (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL REFERENCES questions(id),
    response TEXT NOT NULL,
    user_id BIGINT NOT NULL REFERENCES users(id),
    comment TEXT,
    date_submitted TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified TIMESTAMP WITH TIME ZONE,
    UNIQUE (user_id, question_id)
);

CREATE INDEX idx_question_responses_question ON question_responses(question_id);
CREATE INDEX idx_question_responses_user ON question_responses(user_id);

-- ==========================================
-- Seed Data: Poll Types
-- ==========================================
INSERT INTO poll_types (poll_type, name) VALUES
    (1, 'Election'),
    (2, 'Questionnaire'),
    (3, 'Referendum/Ballot Measure');
