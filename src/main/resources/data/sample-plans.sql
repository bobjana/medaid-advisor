-- Sample medical aid plans for POC testing
-- These are simplified examples based on real South African medical schemes

-- Discovery Health - Saver Plan
INSERT INTO plans (id, scheme, plan_name, plan_year, plan_type, principal_contribution, adult_dependent_contribution, child_dependent_contribution, hospital_benefits, chronic_benefits, day_to_day_benefits, has_medical_savings_account, msa_percentage, created_at)
VALUES (
    'disc-saver-2026',
    'Discovery Health',
    'Saver Plan',
    2026,
    'SAVINGS',
    4500.00,
    3200.00,
    1500.00,
    'Comprehensive in-hospital cover through Delta network at 100% of scheme tariff. Unlimited ICU cover. Theatre cover at 100%.',
    'Full CDL (Chronic Disease List) cover. Non-CDL conditions covered from MSA. DSP (Designated Service Provider) required.',
    'Day-to-day benefits funded from Medical Savings Account. Threshold of R7,500 after which above-threshold benefit applies at DHR 100.',
    true,
    0.25,
    CURRENT_DATE
);

-- Discovery Health - Comprehensive Plan
INSERT INTO plans (id, scheme, plan_name, plan_year, plan_type, principal_contribution, adult_dependent_contribution, child_dependent_contribution, hospital_benefits, chronic_benefits, day_to_day_benefits, has_medical_savings_account, msa_percentage, created_at)
VALUES (
    'disc-comprehensive-2026',
    'Discovery Health',
    'Comprehensive Plan',
    2026,
    'COMPREHENSIVE',
    6800.00,
    4800.00,
    2200.00,
    'Full in-hospital cover at 200% of scheme tariff. No network restrictions. Unlimited ICU and theatre cover.',
    'Full CDL and non-CDL chronic cover. No DSP required. Annual chronic limit unlimited.',
    'Comprehensive day-to-day benefits with R25,000 annual limit per family. GP visits, specialist visits, and medication covered.',
    false,
    NULL,
    CURRENT_DATE
);

-- Discovery Health - KeyCare Plan
INSERT INTO plans (id, scheme, plan_name, plan_year, plan_type, principal_contribution, adult_dependent_contribution, child_dependent_contribution, hospital_benefits, chronic_benefits, day_to_day_benefits, has_medical_savings_account, msa_percentage, created_at)
VALUES (
    'disc-keycare-2026',
    'Discovery Health',
    'KeyCare Plan',
    2026,
    'NETWORK',
    2800.00,
    2100.00,
    950.00,
    'State hospital network cover. 100% cover at designated state facilities. Private hospitals for emergencies only.',
    'Basic CDL cover at state facilities. Non-CDL excluded.',
    'Limited day-to-day benefits with R10,000 annual limit. GP network required.',
    false,
    NULL,
    CURRENT_DATE
);

-- Bonitas - Comprehensive Plan
INSERT INTO plans (id, scheme, plan_name, plan_year, plan_type, principal_contribution, adult_dependent_contribution, child_dependent_contribution, hospital_benefits, chronic_benefits, day_to_day_benefits, has_medical_savings_account, msa_percentage, created_at)
VALUES (
    'bon-comprehensive-2026',
    'Bonitas',
    'Comprehensive',
    2026,
    'COMPREHENSIVE',
    5200.00,
    3800.00,
    1800.00,
    'Full in-hospital cover at 200% of scheme tariff. Any hospital in South Africa. Unlimited cover.',
    'Full CDL and non-CDL chronic cover. 23 CDL conditions fully covered.',
    'R20,000 annual day-to-day benefit per family. GP, specialist, and medication included.',
    false,
    NULL,
    CURRENT_DATE
);

-- Bonitas - BonStart Plan
INSERT INTO plans (id, scheme, plan_name, plan_year, plan_type, principal_contribution, adult_dependent_contribution, child_dependent_contribution, hospital_benefits, chronic_benefits, day_to_day_benefits, has_medical_savings_account, msa_percentage, created_at)
VALUES (
    'bon-bonstart-2026',
    'Bonitas',
    'BonStart',
    2026,
    'NETWORK',
    3200.00,
    2400.00,
    1100.00,
    'Network hospital cover at 100% of scheme tariff. Designated hospital network.',
    'Full CDL cover. Non-CDL excluded.',
    'Limited day-to-day with R8,000 annual limit. Network GPs required.',
    false,
    NULL,
    CURRENT_DATE
);

-- Bestmed - Beat 3 Plan
INSERT INTO plans (id, scheme, plan_name, plan_year, plan_type, principal_contribution, adult_dependent_contribution, child_dependent_contribution, hospital_benefits, chronic_benefits, day_to_day_benefits, has_medical_savings_account, msa_percentage, created_at)
VALUES (
    'best-beat3-2026',
    'Bestmed',
    'Beat 3',
    2026,
    'NETWORK',
    4100.00,
    2900.00,
    1350.00,
    'Prestige network cover at 200% of scheme tariff. 80+ hospitals in network.',
    'Full CDL cover. 27 CDL conditions.',
    'R15,000 annual day-to-day benefit. Preventative care included.',
    false,
    NULL,
    CURRENT_DATE
);

-- Add some copayments
INSERT INTO plan_copayments (plan_id, copayment_key, copayment_value)
VALUES
('disc-saver-2026', 'non_network_hospital', 15025.00),
('disc-saver-2026', 'day_procedure_acute', 2872.00),
('disc-saver-2026', 'colonoscopy', 2000.00),
('disc-comprehensive-2026', 'non_network_hospital', 5000.00),
('disc-keycare-2026', 'private_hospital', 20000.00),
('bon-comprehensive-2026', 'non_network_hospital', 5000.00),
('bon-bonstart-2026', 'non_network_hospital', 15000.00),
('best-beat3-2026', 'non_network_hospital', 10000.00);

-- Add some benefits
INSERT INTO plan_benefits (plan_id, benefit_key, benefit_value)
VALUES
('disc-saver-2026', 'hospital', 'Delta network at 100%'),
('disc-saver-2026', 'chronic', 'CDL full, non-CDL from MSA'),
('disc-saver-2026', 'maternity', 'Midwife births covered, 8 antenatal visits, 2 2D scans'),
('disc-saver-2026', 'preventative', 'Annual flu vaccine, mammogram 45+, pap smear 21+'),
('disc-saver-2026', 'app', 'Discovery app available'),
('disc-saver-2026', 'online', 'Online claims and appointments'),

('disc-comprehensive-2026', 'hospital', 'Any hospital at 200%'),
('disc-comprehensive-2026', 'chronic', 'CDL and non-CDL full cover'),
('disc-comprehensive-2026', 'maternity', 'Full maternity cover, private hospitals'),
('disc-comprehensive-2026', 'preventative', 'Full preventative care included'),
('disc-comprehensive-2026', 'app', 'Discovery app available'),
('disc-comprehensive-2026', 'online', 'Online claims and appointments'),

('disc-keycare-2026', 'hospital', 'State network only'),
('disc-keycare-2026', 'chronic', 'CDL at state facilities'),
('disc-keycare-2026', 'maternity', 'Basic maternity cover'),
('disc-keycare-2026', 'preventative', 'Limited preventative care'),
('disc-keycare-2026', 'app', 'Discovery app available'),
('disc-keycare-2026', 'online', 'Online claims and appointments'),

('bon-comprehensive-2026', 'hospital', 'Any hospital at 200%'),
('bon-comprehensive-2026', 'chronic', 'CDL and non-CDL full cover'),
('bon-comprehensive-2026', 'maternity', 'Full maternity cover'),
('bon-comprehensive-2026', 'preventative', 'Full preventative care'),
('bon-comprehensive-2026', 'app', 'Bonitas app available'),
('bon-comprehensive-2026', 'online', 'Online claims and appointments'),

('bon-bonstart-2026', 'hospital', 'Designated network at 100%'),
('bon-bonstart-2026', 'chronic', 'CDL full, non-CDL excluded'),
('bon-bonstart-2026', 'maternity', 'Basic maternity at network hospitals'),
('bon-bonstart-2026', 'preventative', 'Basic preventative care'),
('bon-bonstart-2026', 'app', 'Bonitas app available'),
('bon-bonstart-2026', 'online', 'Online claims and appointments'),

('best-beat3-2026', 'hospital', 'Prestige network at 200%'),
('best-beat3-2026', 'chronic', 'CDL full, 27 conditions'),
('best-beat3-2026', 'maternity', 'Full maternity cover at network hospitals'),
('best-beat3-2026', 'preventative', 'Preventative care included'),
('best-beat3-2026', 'app', 'Bestmed app available'),
('best-beat3-2026', 'online', 'Online claims and appointments');
