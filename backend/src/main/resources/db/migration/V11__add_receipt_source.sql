-- How the receipt reached us: QR_CODE (downloaded from SEFAZ by the backend) or
-- CAPTCHA_PAGE (page sent by the app after the user solved the SEFAZ captcha).
ALTER TABLE receipts ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'QR_CODE';
