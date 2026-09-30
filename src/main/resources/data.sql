-- Seed initial sample reservations for testing and demo
INSERT INTO reservations (id, resource_id, requester_id, start_time, end_time, status, purpose, expected_attendees, created_at, updated_at)
VALUES (1, 1, 'STU001', '2026-10-05 10:00:00', '2026-10-05 12:00:00', 'APPROVED', 'Distributed Systems Research Lab', 12, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO reservations (id, resource_id, requester_id, start_time, end_time, status, purpose, expected_attendees, created_at, updated_at)
VALUES (2, 1, 'STU001', '2026-10-08 14:00:00', '2026-10-08 16:00:00', 'PENDING', 'Capstone Team Project Workshop', 15, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO reservations (id, resource_id, requester_id, start_time, end_time, status, purpose, expected_attendees, created_at, updated_at)
VALUES (3, 3, 'student-alex', '2026-10-06 09:00:00', '2026-10-06 11:00:00', 'APPROVED', 'Individual Self Study', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO reservations (id, resource_id, requester_id, start_time, end_time, status, purpose, expected_attendees, created_at, updated_at)
VALUES (4, 2, 'STU001', '2026-10-10 13:00:00', '2026-10-10 15:00:00', 'PENDING', 'Guest Lecture on Cloud Computing', 80, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO reservation_approvals (id, reservation_id, action_by, action, reason, action_timestamp)
VALUES (1, 1, 'RMG001', 'APPROVED', 'Approved for research lab session', CURRENT_TIMESTAMP);
