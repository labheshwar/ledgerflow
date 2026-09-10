-- Demo credentials for local/dev use only. Passwords: admin123 / viewer123.
INSERT INTO users (username, password_hash, role) VALUES
    ('admin',  '$2a$10$3Qig2VIDMpum8HWRvanSLuhPQ0.8gW9YSQdURaW/jC7kpqhx08to2', 'ADMIN'),
    ('viewer', '$2a$10$.rqAnMhZysTssSaWfynAHO6qdk6wQedj9F.73lnJAbGkOVh.Zm0tK', 'VIEWER');

INSERT INTO accounts (name, currency, balance) VALUES
    ('Cash',                 'USD', 0),
    ('Accounts Receivable',  'USD', 0),
    ('Revenue',              'USD', 0);
