CREATE TABLE users (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 email VARCHAR(254) NOT NULL UNIQUE,
 password_hash VARCHAR(255) NOT NULL,
 role VARCHAR(20) NOT NULL DEFAULT 'STUDENT',
 email_verified BOOLEAN NOT NULL DEFAULT FALSE,
 account_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 credential_version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL
);
CREATE TABLE student_profiles (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL UNIQUE,
 full_name VARCHAR(80) NOT NULL,
 registration_number VARCHAR(40) NOT NULL UNIQUE,
 department VARCHAR(80) NOT NULL,
 study_year INT NOT NULL,
 bio VARCHAR(500) NOT NULL DEFAULT '',
 avatar_url VARCHAR(500),
 preferred_location VARCHAR(100) NOT NULL DEFAULT '',
 CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES users(id),
 CONSTRAINT ck_study_year CHECK (study_year BETWEEN 1 AND 6)
);
CREATE TABLE skills (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(80) NOT NULL UNIQUE, category VARCHAR(60) NOT NULL);
CREATE TABLE student_skills (
 student_id BIGINT NOT NULL, skill_id BIGINT NOT NULL,
 PRIMARY KEY (student_id,skill_id),
 CONSTRAINT fk_student_skill FOREIGN KEY (student_id) REFERENCES student_profiles(id),
 CONSTRAINT fk_skill FOREIGN KEY (skill_id) REFERENCES skills(id)
);
CREATE TABLE email_verification_tokens (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
 token_hash VARCHAR(64) NOT NULL UNIQUE, expires_at TIMESTAMP(6) NOT NULL,
 used_at TIMESTAMP(6), created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_verification_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX ix_verification_user ON email_verification_tokens(user_id,created_at);
CREATE TABLE password_reset_tokens (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
 token_hash VARCHAR(64) NOT NULL UNIQUE, expires_at TIMESTAMP(6) NOT NULL,
 used_at TIMESTAMP(6), created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_reset_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX ix_reset_user ON password_reset_tokens(user_id,created_at);
