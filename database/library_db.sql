-- =====================================================================
--  LIBRARY MANAGEMENT SYSTEM  -  DBMS Mini Project
--  Database script for MySQL 8.0+
--
--  How to run (pick one):
--    MySQL Workbench : File > Open SQL Script > select this file >
--                      click the lightning-bolt (Execute) button
--    Command line    : mysql -u root -p < library_db.sql
--
--  Running it again is safe: it drops and recreates library_db.
-- =====================================================================

DROP DATABASE IF EXISTS library_db;
CREATE DATABASE library_db;
USE library_db;

-- ---------------------------------------------------------------------
-- 1. TABLES  (normalised to 3NF)
-- ---------------------------------------------------------------------

-- Librarians who can log in to the application
CREATE TABLE admin (
    admin_id      INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50) NOT NULL UNIQUE,
    password_hash CHAR(64)    NOT NULL          -- SHA-256 hash, never plain text
);

-- Book categories (kept separate so the name is not repeated in every book row)
CREATE TABLE categories (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE books (
    book_id          INT AUTO_INCREMENT PRIMARY KEY,
    isbn             VARCHAR(20)  NOT NULL UNIQUE,
    title            VARCHAR(150) NOT NULL,
    author           VARCHAR(100) NOT NULL,
    category_id      INT,
    publisher        VARCHAR(100),
    total_copies     INT NOT NULL DEFAULT 1,
    available_copies INT NOT NULL DEFAULT 1,
    CONSTRAINT fk_books_category FOREIGN KEY (category_id)
        REFERENCES categories(category_id) ON DELETE SET NULL,
    CONSTRAINT chk_copies CHECK (total_copies > 0
                             AND available_copies >= 0
                             AND available_copies <= total_copies)
);

CREATE TABLE members (
    member_id   INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(100) NOT NULL UNIQUE,
    phone       CHAR(10)     NOT NULL,
    member_type ENUM('Student', 'Faculty') NOT NULL DEFAULT 'Student',
    join_date   DATE NOT NULL
);

-- Resolves the many-to-many relationship between books and members
CREATE TABLE issues (
    issue_id    INT AUTO_INCREMENT PRIMARY KEY,
    book_id     INT  NOT NULL,
    member_id   INT  NOT NULL,
    issue_date  DATE NOT NULL,
    due_date    DATE NOT NULL,
    return_date DATE NULL,                      -- NULL means "not returned yet"
    fine        DECIMAL(8,2) NOT NULL DEFAULT 0.00,
    CONSTRAINT fk_issues_book   FOREIGN KEY (book_id)   REFERENCES books(book_id),
    CONSTRAINT fk_issues_member FOREIGN KEY (member_id) REFERENCES members(member_id),
    CONSTRAINT chk_dates CHECK (due_date >= issue_date
                            AND (return_date IS NULL OR return_date >= issue_date))
);

-- Indexes to speed up the most common searches
CREATE INDEX idx_books_title   ON books(title);
CREATE INDEX idx_issues_return ON issues(return_date);


-- ---------------------------------------------------------------------
-- 2. FUNCTION, PROCEDURES AND TRIGGERS
-- ---------------------------------------------------------------------
DELIMITER $$

-- Fine rule: Rs. 5 for every day after the due date
CREATE FUNCTION calc_fine(p_due DATE, p_return DATE)
RETURNS DECIMAL(8,2)
DETERMINISTIC NO SQL
BEGIN
    RETURN GREATEST(DATEDIFF(p_return, p_due), 0) * 5.00;
END$$


-- Issues a book after checking every business rule.
-- Called from Java with CallableStatement: {call issue_book(?, ?, ?)}
CREATE PROCEDURE issue_book(IN p_book_id INT, IN p_member_id INT, IN p_days INT)
BEGIN
    DECLARE v_count     INT DEFAULT 0;
    DECLARE v_available INT DEFAULT 0;
    DECLARE v_type      VARCHAR(10);
    DECLARE v_limit     INT DEFAULT 3;

    IF p_days IS NULL OR p_days < 1 OR p_days > 60 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Issue period must be between 1 and 60 days.';
    END IF;

    SELECT COUNT(*) INTO v_count FROM books WHERE book_id = p_book_id;
    IF v_count = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'This book does not exist.';
    END IF;

    SELECT COUNT(*) INTO v_count FROM members WHERE member_id = p_member_id;
    IF v_count = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'This member does not exist.';
    END IF;

    SELECT available_copies INTO v_available FROM books WHERE book_id = p_book_id;
    IF v_available <= 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'No copies of this book are available right now.';
    END IF;

    -- Students can hold 3 books at a time, faculty can hold 5
    SELECT member_type INTO v_type FROM members WHERE member_id = p_member_id;
    SET v_limit = IF(v_type = 'Faculty', 5, 3);

    SELECT COUNT(*) INTO v_count FROM issues
     WHERE member_id = p_member_id AND return_date IS NULL;
    IF v_count >= v_limit THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Borrowing limit reached (Student: 3 books, Faculty: 5 books). Return a book first.';
    END IF;

    SELECT COUNT(*) INTO v_count FROM issues
     WHERE member_id = p_member_id AND book_id = p_book_id AND return_date IS NULL;
    IF v_count > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'This member already has a copy of this book.';
    END IF;

    -- trg_after_issue reduces available_copies automatically
    INSERT INTO issues (book_id, member_id, issue_date, due_date)
    VALUES (p_book_id, p_member_id, CURDATE(), DATE_ADD(CURDATE(), INTERVAL p_days DAY));
END$$


-- Returns a book and hands back the fine through an OUT parameter.
-- Called from Java with CallableStatement: {call return_book(?, ?)}
CREATE PROCEDURE return_book(IN p_issue_id INT, OUT p_fine DECIMAL(8,2))
BEGIN
    DECLARE v_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_count FROM issues
     WHERE issue_id = p_issue_id AND return_date IS NULL;
    IF v_count = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Issue record not found, or the book is already returned.';
    END IF;

    -- trg_before_return calculates the fine, trg_after_return restores the copy
    UPDATE issues SET return_date = CURDATE() WHERE issue_id = p_issue_id;

    SELECT fine INTO p_fine FROM issues WHERE issue_id = p_issue_id;
END$$


-- When a book is issued, one copy is no longer available
CREATE TRIGGER trg_after_issue
AFTER INSERT ON issues
FOR EACH ROW
BEGIN
    IF NEW.return_date IS NULL THEN
        UPDATE books SET available_copies = available_copies - 1
         WHERE book_id = NEW.book_id;
    END IF;
END$$

-- When a book is returned, calculate the fine
CREATE TRIGGER trg_before_return
BEFORE UPDATE ON issues
FOR EACH ROW
BEGIN
    IF OLD.return_date IS NULL AND NEW.return_date IS NOT NULL THEN
        SET NEW.fine = calc_fine(NEW.due_date, NEW.return_date);
    END IF;
END$$

-- When a book is returned, the copy becomes available again
CREATE TRIGGER trg_after_return
AFTER UPDATE ON issues
FOR EACH ROW
BEGIN
    IF OLD.return_date IS NULL AND NEW.return_date IS NOT NULL THEN
        UPDATE books SET available_copies = available_copies + 1
         WHERE book_id = NEW.book_id;
    END IF;
END$$

DELIMITER ;


-- ---------------------------------------------------------------------
-- 3. VIEW  -  books that are currently out, with live overdue fine
-- ---------------------------------------------------------------------
CREATE VIEW v_current_issues AS
SELECT i.issue_id,
       b.book_id,
       b.title,
       m.member_id,
       m.name AS member_name,
       i.issue_date,
       i.due_date,
       GREATEST(DATEDIFF(CURDATE(), i.due_date), 0) AS days_overdue,
       calc_fine(i.due_date, CURDATE())             AS fine_so_far
  FROM issues i
  JOIN books   b ON b.book_id   = i.book_id
  JOIN members m ON m.member_id = i.member_id
 WHERE i.return_date IS NULL;


-- ---------------------------------------------------------------------
-- 4. SAMPLE DATA  (dates are relative to today, so the demo always has
--    some overdue books whatever day you run it)
-- ---------------------------------------------------------------------

-- Login: admin / admin123
INSERT INTO admin (username, password_hash) VALUES ('admin', SHA2('admin123', 256));

INSERT INTO categories (name) VALUES
('Computer Science'), ('Electronics'), ('Mathematics'),
('Fiction'), ('Self-Help'), ('Biography');

INSERT INTO books (isbn, title, author, category_id, publisher, total_copies, available_copies) VALUES
('978-93-0000-001', 'Database System Concepts',                'Silberschatz, Korth, Sudarshan', 1, 'McGraw Hill',        5, 5),
('978-93-0000-002', 'Operating System Concepts',               'Silberschatz, Galvin, Gagne',    1, 'Wiley',              4, 4),
('978-93-0000-003', 'Computer Networks',                       'Andrew S. Tanenbaum',            1, 'Pearson',            3, 3),
('978-93-0000-004', 'Introduction to Algorithms',              'Cormen, Leiserson, Rivest, Stein', 1, 'MIT Press',        3, 3),
('978-93-0000-005', 'Let Us C',                                'Yashavant Kanetkar',             1, 'BPB Publications',   6, 6),
('978-93-0000-006', 'Head First Java',                         'Kathy Sierra, Bert Bates',       1, 'O''Reilly',          4, 4),
('978-93-0000-007', 'Digital Logic and Computer Design',       'M. Morris Mano',                 2, 'Pearson',            3, 3),
('978-93-0000-008', 'Higher Engineering Mathematics',          'B. S. Grewal',                   3, 'Khanna Publishers',  5, 5),
('978-93-0000-009', 'Discrete Mathematics and Its Applications','Kenneth H. Rosen',              3, 'McGraw Hill',        2, 2),
('978-93-0000-010', 'The Alchemist',                           'Paulo Coelho',                   4, 'HarperCollins',      2, 2),
('978-93-0000-011', 'Atomic Habits',                           'James Clear',                    5, 'Penguin',            3, 3),
('978-93-0000-012', 'Wings of Fire',                           'A. P. J. Abdul Kalam',           6, 'Universities Press', 3, 3);

INSERT INTO members (name, email, phone, member_type, join_date) VALUES
('Aarav Patil',       'aarav.patil@example.com',  '9876500001', 'Student', DATE_SUB(CURDATE(), INTERVAL 200 DAY)),
('Sneha Kulkarni',    'sneha.k@example.com',      '9876500002', 'Student', DATE_SUB(CURDATE(), INTERVAL 150 DAY)),
('Rohan Deshmukh',    'rohan.d@example.com',      '9876500003', 'Student', DATE_SUB(CURDATE(), INTERVAL 120 DAY)),
('Priya Joshi',       'priya.joshi@example.com',  '9876500004', 'Student', DATE_SUB(CURDATE(), INTERVAL 90 DAY)),
('Omkar Pawar',       'omkar.pawar@example.com',  '9876500005', 'Student', DATE_SUB(CURDATE(), INTERVAL 60 DAY)),
('Dr. Meera Iyer',    'meera.iyer@example.com',   '9876500006', 'Faculty', DATE_SUB(CURDATE(), INTERVAL 400 DAY)),
('Prof. Anil Shinde', 'anil.shinde@example.com',  '9876500007', 'Faculty', DATE_SUB(CURDATE(), INTERVAL 300 DAY)),
('Kavya Nair',        'kavya.nair@example.com',   '9876500008', 'Student', DATE_SUB(CURDATE(), INTERVAL 10 DAY));

-- Past issues that were already returned (history)
INSERT INTO issues (book_id, member_id, issue_date, due_date, return_date) VALUES
(1,  1, DATE_SUB(CURDATE(), INTERVAL 60 DAY), DATE_SUB(CURDATE(), INTERVAL 46 DAY), DATE_SUB(CURDATE(), INTERVAL 48 DAY)),
(5,  2, DATE_SUB(CURDATE(), INTERVAL 50 DAY), DATE_SUB(CURDATE(), INTERVAL 36 DAY), DATE_SUB(CURDATE(), INTERVAL 30 DAY)),
(1,  3, DATE_SUB(CURDATE(), INTERVAL 45 DAY), DATE_SUB(CURDATE(), INTERVAL 31 DAY), DATE_SUB(CURDATE(), INTERVAL 31 DAY)),
(11, 4, DATE_SUB(CURDATE(), INTERVAL 40 DAY), DATE_SUB(CURDATE(), INTERVAL 26 DAY), DATE_SUB(CURDATE(), INTERVAL 20 DAY)),
(10, 6, DATE_SUB(CURDATE(), INTERVAL 35 DAY), DATE_SUB(CURDATE(), INTERVAL 5 DAY),  DATE_SUB(CURDATE(), INTERVAL 25 DAY)),
(1,  7, DATE_SUB(CURDATE(), INTERVAL 30 DAY), DATE_SUB(CURDATE(), INTERVAL 16 DAY), DATE_SUB(CURDATE(), INTERVAL 10 DAY)),
(4,  1, DATE_SUB(CURDATE(), INTERVAL 25 DAY), DATE_SUB(CURDATE(), INTERVAL 11 DAY), DATE_SUB(CURDATE(), INTERVAL 12 DAY));

-- Work out the fines for that history using our function
UPDATE issues SET fine = calc_fine(due_date, return_date) WHERE return_date IS NOT NULL;

-- Books currently issued (trg_after_issue updates available copies)
INSERT INTO issues (book_id, member_id, issue_date, due_date) VALUES
(2,  1, DATE_SUB(CURDATE(), INTERVAL 20 DAY), DATE_SUB(CURDATE(), INTERVAL 6 DAY)),   -- overdue
(3,  2, DATE_SUB(CURDATE(), INTERVAL 18 DAY), DATE_SUB(CURDATE(), INTERVAL 4 DAY)),   -- overdue
(8,  5, DATE_SUB(CURDATE(), INTERVAL 25 DAY), DATE_SUB(CURDATE(), INTERVAL 11 DAY)),  -- overdue
(6,  3, DATE_SUB(CURDATE(), INTERVAL 5 DAY),  DATE_ADD(CURDATE(), INTERVAL 9 DAY)),
(1,  4, DATE_SUB(CURDATE(), INTERVAL 3 DAY),  DATE_ADD(CURDATE(), INTERVAL 11 DAY)),
(12, 6, DATE_SUB(CURDATE(), INTERVAL 7 DAY),  DATE_ADD(CURDATE(), INTERVAL 23 DAY)),
(9,  7, DATE_SUB(CURDATE(), INTERVAL 2 DAY),  DATE_ADD(CURDATE(), INTERVAL 12 DAY)),
(5,  5, DATE_SUB(CURDATE(), INTERVAL 1 DAY),  DATE_ADD(CURDATE(), INTERVAL 13 DAY));


-- ---------------------------------------------------------------------
-- 5. QUICK CHECK  (you should see 12 books, 8 members, 15 issues)
-- ---------------------------------------------------------------------
SELECT (SELECT COUNT(*) FROM books)   AS books,
       (SELECT COUNT(*) FROM members) AS members,
       (SELECT COUNT(*) FROM issues)  AS issues;
