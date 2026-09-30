-- Brand or friendly name ("ASSAI") next to the legal name SEFAZ shows ("SENDAS DISTRIBUIDORA S/A").
-- Filled by the application on import and by a startup backfill for existing stores.
ALTER TABLE stores ADD COLUMN display_name VARCHAR(200);
