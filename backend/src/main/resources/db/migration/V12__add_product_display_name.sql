-- Readable pt-BR name written by the AI ("BEB LACTEA YOPRO 250ML BAUNILHA" -> "Bebida láctea YoPro 250 ml baunilha").
-- Filled by the categorization job; screens fall back to normalized_name while it is empty.
ALTER TABLE products ADD COLUMN display_name VARCHAR(200);
