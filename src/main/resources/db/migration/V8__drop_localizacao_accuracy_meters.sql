-- WhatsApp/Twilio location pins never send accuracy; drop unused nullable column.
ALTER TABLE localizacao DROP COLUMN accuracy_meters;
