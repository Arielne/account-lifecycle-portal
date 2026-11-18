-- V16: nới cột mã phiếu từ 14 lên 20 ký tự (spec phiếu mục 7.1).
-- Mã phiếu dạng PYC-<năm>-<số từ app.ticket_code_seq>; sequence không đếm lại theo năm, nên từ phiếu thứ 100.000
-- phần số có 6 chữ số, mã dài 15 ký tự (PYC-2026-100000) và câu INSERT phiếu bị lỗi vì vượt varchar(14).
-- Nới varchar chỉ đổi giới hạn độ dài, Postgres không ghi lại dữ liệu của bảng; index UNIQUE của cột giữ nguyên.

ALTER TABLE app.ticket ALTER COLUMN code TYPE varchar(20);