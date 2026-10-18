-- ==========================================================
-- MediCare: Hospital Patient Appointment & Management System
-- Complete DDL, DML, DCL, TCL & Analytical Queries (CO1-CO5)
-- ==========================================================

CREATE DATABASE IF NOT EXISTS medicare;
USE medicare;

-- 1. DDL: Table Creation with Integrity Constraints (CO2 & CO3)
CREATE TABLE Department (
    department_id INT AUTO_INCREMENT PRIMARY KEY,
    department_name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE Doctor (
    doctor_id INT AUTO_INCREMENT PRIMARY KEY,
    doctor_name VARCHAR(60) NOT NULL,
    specialization VARCHAR(50) NOT NULL,
    phone VARCHAR(15) UNIQUE,
    department_id INT NOT NULL,
    FOREIGN KEY (department_id) REFERENCES Department(department_id)
        ON DELETE RESTRICT ON UPDATE CASCADE
);

CREATE TABLE Patient (
    patient_id INT AUTO_INCREMENT PRIMARY KEY,
    patient_name VARCHAR(60) NOT NULL,
    age INT NOT NULL CHECK (age > 0 AND age <= 120),
    gender VARCHAR(10) NOT NULL CHECK (gender IN ('Male', 'Female', 'Other')),
    phone VARCHAR(15) NOT NULL UNIQUE,
    address VARCHAR(150) NOT NULL
);

CREATE TABLE Appointment (
    appointment_id INT AUTO_INCREMENT PRIMARY KEY,
    patient_id INT NOT NULL,
    doctor_id INT NOT NULL,
    appointment_date DATE NOT NULL,
    appointment_time TIME NOT NULL,
    status VARCHAR(15) NOT NULL DEFAULT 'Scheduled'
        CHECK (status IN ('Scheduled', 'Completed', 'Cancelled')),
    UNIQUE (doctor_id, appointment_date, appointment_time),
    FOREIGN KEY (patient_id) REFERENCES Patient(patient_id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    FOREIGN KEY (doctor_id) REFERENCES Doctor(doctor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE
);

CREATE TABLE Bill (
    bill_id INT AUTO_INCREMENT PRIMARY KEY,
    appointment_id INT NOT NULL UNIQUE,
    amount DECIMAL(10,2) NOT NULL CHECK (amount >= 0),
    payment_status VARCHAR(10) NOT NULL DEFAULT 'Pending'
        CHECK (payment_status IN ('Pending', 'Paid')),
    bill_date DATE NOT NULL DEFAULT (CURRENT_DATE),
    FOREIGN KEY (appointment_id) REFERENCES Appointment(appointment_id)
        ON DELETE RESTRICT ON UPDATE CASCADE
);

-- 2. Analytical Queries: JOIN, GROUP BY, HAVING, Subqueries (CO3)

-- A. 3-Table INNER JOIN (Appointments with Patient & Doctor details)
SELECT A.appointment_id, P.patient_name, D.doctor_name, A.appointment_date, A.appointment_time, A.status
FROM Appointment A
JOIN Patient P ON A.patient_id = P.patient_id
JOIN Doctor D ON A.doctor_id = D.doctor_id;

-- B. GROUP BY with HAVING (Doctors with at least 1 appointment)
SELECT D.doctor_name, COUNT(A.appointment_id) AS total_appointments
FROM Doctor D
LEFT JOIN Appointment A ON D.doctor_id = A.doctor_id
GROUP BY D.doctor_id, D.doctor_name
HAVING COUNT(A.appointment_id) >= 1;

-- C. Nested Subquery (Patients who have Scheduled appointments)
SELECT patient_name, phone
FROM Patient
WHERE patient_id IN (
    SELECT patient_id FROM Appointment WHERE status = 'Scheduled'
);

-- D. Aggregate Functions (Hospital Revenue Summary)
SELECT COUNT(*) AS total_bills, SUM(amount) AS total_revenue, AVG(amount) AS avg_bill
FROM Bill;

-- 3. TCL: Transaction Management Demonstration (CO4)
START TRANSACTION;
UPDATE Bill SET payment_status = 'Paid' WHERE bill_id = 1;
UPDATE Appointment SET status = 'Completed' WHERE appointment_id = 1;
COMMIT;

-- 4. DCL: Database Access Control Demonstration (CO3)
-- CREATE USER 'medicare_app'@'localhost' IDENTIFIED BY 'MediCare@123';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON medicare.* TO 'medicare_app'@'localhost';