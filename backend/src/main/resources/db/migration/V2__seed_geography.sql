-- V2__seed_geography.sql
-- Seeds all 50 US states + DC, plus a representative subset of counties
-- and zipcodes for development. Replace with full datasets when needed.

INSERT INTO states (name, initial) VALUES
    ('Alabama', 'AL'),
    ('Alaska', 'AK'),
    ('Arizona', 'AZ'),
    ('Arkansas', 'AR'),
    ('California', 'CA'),
    ('Colorado', 'CO'),
    ('Connecticut', 'CT'),
    ('Delaware', 'DE'),
    ('District of Columbia', 'DC'),
    ('Florida', 'FL'),
    ('Georgia', 'GA'),
    ('Hawaii', 'HI'),
    ('Idaho', 'ID'),
    ('Illinois', 'IL'),
    ('Indiana', 'IN'),
    ('Iowa', 'IA'),
    ('Kansas', 'KS'),
    ('Kentucky', 'KY'),
    ('Louisiana', 'LA'),
    ('Maine', 'ME'),
    ('Maryland', 'MD'),
    ('Massachusetts', 'MA'),
    ('Michigan', 'MI'),
    ('Minnesota', 'MN'),
    ('Mississippi', 'MS'),
    ('Missouri', 'MO'),
    ('Montana', 'MT'),
    ('Nebraska', 'NE'),
    ('Nevada', 'NV'),
    ('New Hampshire', 'NH'),
    ('New Jersey', 'NJ'),
    ('New Mexico', 'NM'),
    ('New York', 'NY'),
    ('North Carolina', 'NC'),
    ('North Dakota', 'ND'),
    ('Ohio', 'OH'),
    ('Oklahoma', 'OK'),
    ('Oregon', 'OR'),
    ('Pennsylvania', 'PA'),
    ('Rhode Island', 'RI'),
    ('South Carolina', 'SC'),
    ('South Dakota', 'SD'),
    ('Tennessee', 'TN'),
    ('Texas', 'TX'),
    ('Utah', 'UT'),
    ('Vermont', 'VT'),
    ('Virginia', 'VA'),
    ('Washington', 'WA'),
    ('West Virginia', 'WV'),
    ('Wisconsin', 'WI'),
    ('Wyoming', 'WY');

-- Sample counties for CA, NY, TX
INSERT INTO counties (state_id, name) VALUES
    ((SELECT id FROM states WHERE initial = 'CA'), 'Los Angeles'),
    ((SELECT id FROM states WHERE initial = 'CA'), 'San Diego'),
    ((SELECT id FROM states WHERE initial = 'CA'), 'Orange'),
    ((SELECT id FROM states WHERE initial = 'CA'), 'Riverside'),
    ((SELECT id FROM states WHERE initial = 'CA'), 'San Bernardino'),
    ((SELECT id FROM states WHERE initial = 'NY'), 'New York'),
    ((SELECT id FROM states WHERE initial = 'NY'), 'Kings'),
    ((SELECT id FROM states WHERE initial = 'NY'), 'Queens'),
    ((SELECT id FROM states WHERE initial = 'NY'), 'Bronx'),
    ((SELECT id FROM states WHERE initial = 'NY'), 'Richmond'),
    ((SELECT id FROM states WHERE initial = 'TX'), 'Harris'),
    ((SELECT id FROM states WHERE initial = 'TX'), 'Dallas'),
    ((SELECT id FROM states WHERE initial = 'TX'), 'Tarrant'),
    ((SELECT id FROM states WHERE initial = 'TX'), 'Bexar'),
    ((SELECT id FROM states WHERE initial = 'TX'), 'Travis');

-- Sample zipcodes per county
INSERT INTO county_zips (county_id, zipcode) VALUES
    -- CA / Los Angeles
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'CA' AND c.name = 'Los Angeles'), '90001'),
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'CA' AND c.name = 'Los Angeles'), '90012'),
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'CA' AND c.name = 'Los Angeles'), '90210'),
    -- CA / San Diego
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'CA' AND c.name = 'San Diego'), '92101'),
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'CA' AND c.name = 'San Diego'), '92103'),
    -- CA / Orange
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'CA' AND c.name = 'Orange'), '92602'),
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'CA' AND c.name = 'Orange'), '92801'),
    -- CA / Riverside
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'CA' AND c.name = 'Riverside'), '92501'),
    -- CA / San Bernardino
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'CA' AND c.name = 'San Bernardino'), '92401'),

    -- NY / New York (Manhattan)
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'NY' AND c.name = 'New York'), '10001'),
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'NY' AND c.name = 'New York'), '10013'),
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'NY' AND c.name = 'New York'), '10025'),
    -- NY / Kings (Brooklyn)
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'NY' AND c.name = 'Kings'), '11201'),
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'NY' AND c.name = 'Kings'), '11215'),
    -- NY / Queens
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'NY' AND c.name = 'Queens'), '11354'),
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'NY' AND c.name = 'Queens'), '11377'),
    -- NY / Bronx
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'NY' AND c.name = 'Bronx'), '10451'),
    -- NY / Richmond (Staten Island)
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'NY' AND c.name = 'Richmond'), '10301'),

    -- TX / Harris (Houston)
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'TX' AND c.name = 'Harris'), '77002'),
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'TX' AND c.name = 'Harris'), '77019'),
    -- TX / Dallas
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'TX' AND c.name = 'Dallas'), '75201'),
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'TX' AND c.name = 'Dallas'), '75204'),
    -- TX / Tarrant (Fort Worth)
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'TX' AND c.name = 'Tarrant'), '76102'),
    -- TX / Bexar (San Antonio)
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'TX' AND c.name = 'Bexar'), '78205'),
    -- TX / Travis (Austin)
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'TX' AND c.name = 'Travis'), '78701'),
    ((SELECT c.id FROM counties c JOIN states s ON c.state_id = s.id WHERE s.initial = 'TX' AND c.name = 'Travis'), '78704');
