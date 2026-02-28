-- V1__Initial_schema.sql
-- Initial database schema for MedAid Advisor

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Plan Types Enum
CREATE TYPE plan_type AS ENUM ('NETWORK', 'COMPREHENSIVE', 'SAVINGS', 'HOSPITAL', 'CAP');

-- Plans table
CREATE TABLE plans (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    scheme VARCHAR(255) NOT NULL,
    plan_name VARCHAR(255) NOT NULL,
    plan_year INTEGER NOT NULL,
    plan_type plan_type NOT NULL,
    principal_contribution DOUBLE PRECISION NOT NULL,
    adult_dependent_contribution DOUBLE PRECISION,
    child_dependent_contribution DOUBLE PRECISION,
    hospital_benefits VARCHAR(4000),
    chronic_benefits VARCHAR(4000),
    day_to_day_benefits VARCHAR(4000),
    has_medical_savings_account BOOLEAN DEFAULT FALSE,
    msa_percentage DOUBLE PRECISION,
    created_at DATE NOT NULL DEFAULT CURRENT_DATE,
    source_document VARCHAR(2000),
    CONSTRAINT uk_plan_scheme_name_year UNIQUE (scheme, plan_name, plan_year)
);

-- Plan benefits (ElementCollection)
CREATE TABLE plan_benefits (
    plan_id UUID NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    benefit_key VARCHAR(255) NOT NULL,
    benefit_value VARCHAR(4000),
    PRIMARY KEY (plan_id, benefit_key)
);

-- Plan copayments (ElementCollection)
CREATE TABLE plan_copayments (
    plan_id UUID NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    copayment_key VARCHAR(255) NOT NULL,
    copayment_value DOUBLE PRECISION,
    PRIMARY KEY (plan_id, copayment_key)
);

-- Risk Tolerance Enum
CREATE TYPE risk_tolerance AS ENUM ('LOW', 'MEDIUM', 'HIGH');

-- Employee profiles table
CREATE TABLE employee_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    age INTEGER NOT NULL CHECK (age >= 18 AND age <= 100),
    dependents INTEGER DEFAULT 0 CHECK (dependents >= 0 AND dependents <= 20),
    planned_procedures TEXT,
    planning_pregnancy BOOLEAN DEFAULT FALSE,
    max_monthly_budget DOUBLE PRECISION,
    max_annual_budget DOUBLE PRECISION,
    risk_tolerance risk_tolerance DEFAULT 'MEDIUM'
);

-- Chronic conditions (ElementCollection)
CREATE TABLE chronic_conditions (
    profile_id UUID NOT NULL REFERENCES employee_profiles(id) ON DELETE CASCADE,
    condition_type VARCHAR(255) NOT NULL,
    PRIMARY KEY (profile_id, condition_type)
);

-- Preferred providers (ElementCollection)
CREATE TABLE preferred_providers (
    profile_id UUID NOT NULL REFERENCES employee_profiles(id) ON DELETE CASCADE,
    provider_type VARCHAR(255) NOT NULL,
    provider_name VARCHAR(255),
    PRIMARY KEY (profile_id, provider_type)
);

-- Member Type Enum
CREATE TYPE member_type AS ENUM (
    'PRINCIPAL', 'SPOUSE', 'CHILD_FIRST', 'CHILD_SECOND', 
    'CHILD_THIRD', 'CHILD_FOURTH', 'CHILD_FIFTH_OR_MORE'
);

-- Benefit Category Enum
CREATE TYPE benefit_category AS ENUM (
    'HOSPITAL_COVER', 'CHRONIC_MEDICINE', 'SPECIALIST_CONSULTATION', 
    'EMERGENCY_SERVICE', 'MATERNITY', 'DENTAL', 'OPTICAL', 
    'PRESCRIBED_MINIMUM_BENEFITS'
);

-- Contributions table
CREATE TABLE contributions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    plan_id UUID NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    member_type member_type NOT NULL,
    monthly_amount DOUBLE PRECISION NOT NULL,
    age_bracket VARCHAR(50),
    conditions VARCHAR(2000)
);

CREATE INDEX idx_contributions_plan_id ON contributions(plan_id);
CREATE INDEX idx_contributions_plan_member_type ON contributions(plan_id, member_type);

-- Hospital benefits table
CREATE TABLE hospital_benefits (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    plan_id UUID NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    category benefit_category NOT NULL,
    benefit_name VARCHAR(500) NOT NULL,
    limit_per_family VARCHAR(500),
    limit_per_person VARCHAR(500),
    annual_limit VARCHAR(500),
    covered BOOLEAN NOT NULL,
    notes VARCHAR(2000),
    conditions VARCHAR(2000)
);

CREATE INDEX idx_hospital_benefits_plan_id ON hospital_benefits(plan_id);
CREATE INDEX idx_hospital_benefits_plan_category ON hospital_benefits(plan_id, category);
CREATE INDEX idx_hospital_benefits_covered ON hospital_benefits(plan_id, covered);

-- Indexes for plans table
CREATE INDEX idx_plans_scheme ON plans(scheme);
CREATE INDEX idx_plans_plan_year ON plans(plan_year);
CREATE INDEX idx_plans_plan_type ON plans(plan_type);
CREATE INDEX idx_plans_scheme_year ON plans(scheme, plan_year);
