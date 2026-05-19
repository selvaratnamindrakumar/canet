-- Sample Departments
INSERT INTO departments (name) VALUES
    ('Engineering'),
    ('Data & Analytics'),
    ('DevOps & Infrastructure'),
    ('Product Management'),
    ('Quality Assurance');

-- Sample Employees
INSERT INTO employees (employee_code, first_name, last_name, email, department_id, job_title, hire_date) VALUES
    ('EMP001', 'Alice',   'Johnson',  'alice.johnson@company.com',  1, 'Senior Software Engineer',    '2020-03-15'),
    ('EMP002', 'Bob',     'Smith',    'bob.smith@company.com',      1, 'Software Engineer',           '2021-07-01'),
    ('EMP003', 'Carol',   'Williams', 'carol.williams@company.com', 2, 'Data Scientist',              '2019-11-20'),
    ('EMP004', 'David',   'Brown',    'david.brown@company.com',    3, 'DevOps Engineer',             '2022-01-10'),
    ('EMP005', 'Emma',    'Davis',    'emma.davis@company.com',     1, 'Junior Software Engineer',    '2023-06-01'),
    ('EMP006', 'Frank',   'Miller',   'frank.miller@company.com',   4, 'Product Manager',             '2020-09-14'),
    ('EMP007', 'Grace',   'Wilson',   'grace.wilson@company.com',   5, 'QA Engineer',                 '2021-03-22'),
    ('EMP008', 'Henry',   'Moore',    'henry.moore@company.com',    2, 'Data Engineer',               '2022-08-05'),
    ('EMP009', 'Isla',    'Taylor',   'isla.taylor@company.com',    1, 'Software Engineer',           '2021-11-30'),
    ('EMP010', 'James',   'Anderson', 'james.anderson@company.com', 3, 'Senior DevOps Engineer',      '2018-05-12');

-- Skill Categories
INSERT INTO skill_categories (name, description) VALUES
    ('Programming Languages', 'Core programming and scripting languages'),
    ('Databases',             'Database technologies and query languages'),
    ('Cloud & Infrastructure','Cloud platforms and infrastructure tools'),
    ('Frameworks & Libraries','Software frameworks and libraries'),
    ('Soft Skills',           'Non-technical and interpersonal skills');

-- Skills
INSERT INTO skills (category_id, name, description) VALUES
    -- Programming Languages
    (1, 'Python',       'Python programming language'),
    (1, 'Java',         'Java programming language'),
    (1, 'JavaScript',   'JavaScript / TypeScript'),
    (1, 'SQL',          'Structured Query Language'),
    (1, 'Bash/Shell',   'Shell scripting'),
    -- Databases
    (2, 'PostgreSQL',   'PostgreSQL relational database'),
    (2, 'MongoDB',      'MongoDB document store'),
    (2, 'Redis',        'Redis in-memory data store'),
    -- Cloud & Infrastructure
    (3, 'AWS',          'Amazon Web Services'),
    (3, 'Docker',       'Containerisation with Docker'),
    (3, 'Kubernetes',   'Container orchestration'),
    (3, 'Terraform',    'Infrastructure as Code'),
    -- Frameworks & Libraries
    (4, 'FastAPI',      'Python FastAPI framework'),
    (4, 'React',        'React frontend framework'),
    (4, 'Spring Boot',  'Java Spring Boot framework'),
    (4, 'Pandas',       'Python data analysis library'),
    -- Soft Skills
    (5, 'Communication','Verbal and written communication'),
    (5, 'Agile/Scrum',  'Agile methodology and Scrum framework'),
    (5, 'Mentoring',    'Coaching and mentoring others');

-- Employee Skill Ratings  (level ids: 1=None, 2=Initial, 3=OK, 4=Good)
INSERT INTO employee_skills (employee_id, skill_id, experience_level_id, notes) VALUES
    -- Alice (EMP001) - Senior Engineer
    (1, 1, 4, 'Primary language'), (1, 2, 3, NULL), (1, 3, 3, NULL),
    (1, 4, 4, NULL), (1, 5, 4, NULL), (1, 6, 4, NULL), (1, 7, 2, NULL),
    (1, 9, 3, NULL), (1, 10, 4, NULL), (1, 13, 4, NULL),
    (1, 17, 4, NULL), (1, 18, 4, NULL), (1, 19, 3, NULL),
    -- Bob (EMP002)
    (2, 1, 3, NULL), (2, 2, 4, 'Primary language'), (2, 4, 3, NULL),
    (2, 6, 3, NULL), (2, 10, 3, NULL), (2, 15, 4, NULL),
    (2, 17, 3, NULL), (2, 18, 3, NULL),
    -- Carol (EMP003) - Data Scientist
    (3, 1, 4, 'Expert'), (3, 4, 4, NULL), (3, 6, 4, NULL),
    (3, 7, 3, NULL), (3, 8, 2, NULL), (3, 16, 4, NULL),
    (3, 17, 4, NULL), (3, 18, 3, NULL),
    -- David (EMP004) - DevOps
    (4, 1, 3, NULL), (4, 4, 3, NULL), (4, 5, 4, NULL),
    (4, 6, 3, NULL), (4, 9, 4, NULL), (4, 10, 4, NULL),
    (4, 11, 4, NULL), (4, 12, 4, NULL), (4, 18, 4, NULL),
    -- Emma (EMP005) - Junior
    (5, 1, 2, 'Learning'), (5, 3, 2, NULL), (5, 4, 2, NULL),
    (5, 6, 2, NULL), (5, 10, 2, NULL), (5, 13, 2, NULL),
    (5, 17, 3, NULL), (5, 18, 2, NULL),
    -- Frank (EMP006) - PM
    (6, 4, 2, NULL), (6, 17, 4, NULL), (6, 18, 4, NULL), (6, 19, 3, NULL),
    -- Grace (EMP007) - QA
    (7, 1, 3, NULL), (7, 3, 2, NULL), (7, 4, 3, NULL),
    (7, 6, 3, NULL), (7, 17, 3, NULL), (7, 18, 4, NULL),
    -- Henry (EMP008) - Data Engineer
    (8, 1, 4, NULL), (8, 4, 4, NULL), (8, 5, 3, NULL),
    (8, 6, 4, NULL), (8, 7, 3, NULL), (8, 8, 3, NULL),
    (8, 9, 3, NULL), (8, 10, 3, NULL), (8, 16, 3, NULL), (8, 18, 3, NULL),
    -- Isla (EMP009)
    (9, 1, 3, NULL), (9, 2, 2, NULL), (9, 3, 4, NULL),
    (9, 4, 3, NULL), (9, 6, 3, NULL), (9, 14, 4, NULL),
    (9, 17, 3, NULL), (9, 18, 3, NULL),
    -- James (EMP010) - Senior DevOps
    (10, 1, 3, NULL), (10, 4, 4, NULL), (10, 5, 4, NULL),
    (10, 6, 4, NULL), (10, 9, 4, NULL), (10, 10, 4, NULL),
    (10, 11, 4, NULL), (10, 12, 4, NULL), (10, 17, 4, NULL),
    (10, 18, 4, NULL), (10, 19, 4, NULL);
