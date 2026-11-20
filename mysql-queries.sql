-- =====================================================================
--  CropDisease AI Pro - handy MySQL queries (run in MySQL Workbench)
--  Database "cropdisease" is created automatically on first start.
-- =====================================================================
USE cropdisease;

-- Tables created by the app
SHOW TABLES;                                   -- crops, diseases, users, scans, media

-- Knowledge base
SELECT code, name, emoji, season FROM crops;
SELECT crop_code, COUNT(*) AS diseases FROM diseases GROUP BY crop_code;
SELECT code, name, type, severity, pattern FROM diseases ORDER BY crop_code, name;

-- Farmers (passwords are BCrypt hashes)
SELECT id, full_name, email, village, primary_crop, created_at FROM users;

-- Latest scans
SELECT id, user_id, crop_code, diagnosis, status, ROUND(confidence * 100) AS conf_pct,
       severity_pct, health_score, stage, field_name, created_at
FROM scans ORDER BY created_at DESC LIMIT 20;

-- Most detected diseases
SELECT diagnosis, COUNT(*) AS times FROM scans WHERE status <> 'HEALTHY'
GROUP BY diagnosis ORDER BY times DESC;

-- Photo storage used (leaf photos + AI heatmaps)
SELECT COUNT(*) AS files, ROUND(SUM(LENGTH(data)) / 1024 / 1024, 2) AS mb FROM media;
