-- Solo para tests (Testcontainers). Equivalente a 00_create_schema.sql sin USE ni GO.
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'youtube') EXEC('CREATE SCHEMA youtube');
