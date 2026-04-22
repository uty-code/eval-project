ALTER TABLE evaluation_elements_51 ADD dept_id bigint NULL;
ALTER TABLE evaluation_elements_51 ADD CONSTRAINT FK_eval_elements_dept FOREIGN KEY (dept_id) REFERENCES departments_51(dept_id);
