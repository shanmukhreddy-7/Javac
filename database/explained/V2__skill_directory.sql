-- STUDY COPY ONLY — annotated explanation of migrations/V2__skill_directory.sql. Do not execute
-- this copy or configure Flyway to scan explained/. The original migration remains the runtime
-- source of truth.

-- Seeds 20 reusable skills across directory categories. Generated IDs are database allocated;
-- profile clients load the directory instead of assuming hard-coded IDs.
INSERT INTO skills (name,category) VALUES
('Java','Programming'),
('Python','Programming'),
('Web development','Programming'),
('C++','Programming'),
('Mathematics','Tutoring'),
('Physics','Tutoring'),
('English','Tutoring'),
('Logo design','Graphic design'),
('Figma','Graphic design'),
('Illustration','Graphic design'),
('Video editing','Video editing'),
('Motion graphics','Video editing'),
('Photography','Photography'),
('Photo editing','Photography'),
('Presentation design','Presentation design'),
('Report formatting','Document assistance'),
('Proofreading','Document assistance'),
('Campus errands','Delivery and errands'),
('Event coordination','Event support'),
('Social media design','Graphic design');
