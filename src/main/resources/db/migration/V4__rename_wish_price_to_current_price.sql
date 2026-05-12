ALTER TABLE wishes ADD COLUMN current_price INT NULL AFTER image_url;

UPDATE wishes SET current_price = COALESCE(discounted_price, regular_price);

ALTER TABLE wishes DROP COLUMN discounted_price;
ALTER TABLE wishes DROP COLUMN regular_price;
