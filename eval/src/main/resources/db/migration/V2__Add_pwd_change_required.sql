IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('employees_51') AND name = 'pwd_change_required') EXEC('ALTER TABLE employees_51 ADD pwd_change_required CHAR(1) DEFAULT ''n'' NOT NULL');
UPDATE employees_51 SET pwd_change_required = 'n';
