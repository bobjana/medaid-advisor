-- Add extraction_status column to plans table
ALTER TABLE plans
ADD COLUMN extraction_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

-- Create index on extraction_status for efficient querying
CREATE INDEX idx_plans_extraction_status ON plans(extraction_status);
