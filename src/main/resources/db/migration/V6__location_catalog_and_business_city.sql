CREATE TABLE provinces (
    id INTEGER PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE districts (
    id INTEGER PRIMARY KEY,
    province_id INTEGER NOT NULL REFERENCES provinces (id),
    name VARCHAR(100) NOT NULL,
    UNIQUE (province_id, name)
);

CREATE TABLE cities (
    id INTEGER PRIMARY KEY,
    district_id INTEGER NOT NULL REFERENCES districts (id),
    name VARCHAR(100) NOT NULL,
    UNIQUE (district_id, name)
);

INSERT INTO provinces (id, name) VALUES
    (1, 'Western'), (2, 'Central'), (3, 'Southern'), (4, 'Northern'), (5, 'Eastern'),
    (6, 'North Western'), (7, 'North Central'), (8, 'Uva'), (9, 'Sabaragamuwa');

INSERT INTO districts (id, province_id, name) VALUES
    (1, 1, 'Colombo'), (2, 1, 'Gampaha'), (3, 1, 'Kalutara'),
    (4, 2, 'Kandy'), (5, 2, 'Matale'), (6, 2, 'Nuwara Eliya'),
    (7, 3, 'Galle'), (8, 3, 'Matara'), (9, 3, 'Hambantota'),
    (10, 4, 'Jaffna'), (11, 4, 'Kilinochchi'), (12, 4, 'Mannar'), (13, 4, 'Mullaitivu'), (14, 4, 'Vavuniya'),
    (15, 5, 'Batticaloa'), (16, 5, 'Ampara'), (17, 5, 'Trincomalee'),
    (18, 6, 'Kurunegala'), (19, 6, 'Puttalam'),
    (20, 7, 'Anuradhapura'), (21, 7, 'Polonnaruwa'),
    (22, 8, 'Badulla'), (23, 8, 'Monaragala'),
    (24, 9, 'Ratnapura'), (25, 9, 'Kegalle');

-- Starter locality catalog, selected for portfolio coverage and intentionally not exhaustive.
-- Province and district hierarchy follows the Department of Census and Statistics 2024 preliminary census report:
-- https://www.statistics.gov.lk/Resource/en/Population/CPH_2024/Preliminary_Report.pdf
INSERT INTO cities (id, district_id, name) VALUES
    (1, 1, 'Colombo'), (2, 1, 'Dehiwala-Mount Lavinia'), (3, 1, 'Moratuwa'), (4, 1, 'Maharagama'), (5, 1, 'Kesbewa'),
    (6, 2, 'Negombo'), (7, 2, 'Gampaha'), (8, 2, 'Wattala'), (9, 2, 'Ja-Ela'), (10, 2, 'Minuwangoda'),
    (11, 3, 'Kalutara'), (12, 3, 'Panadura'), (13, 3, 'Beruwala'), (14, 3, 'Horana'), (15, 3, 'Matugama'),
    (16, 4, 'Kandy'), (17, 4, 'Peradeniya'), (18, 4, 'Gampola'), (19, 4, 'Nawalapitiya'), (20, 4, 'Katugastota'),
    (21, 5, 'Matale'), (22, 5, 'Dambulla'), (23, 5, 'Ukuwela'), (24, 5, 'Rattota'), (25, 5, 'Galewela'),
    (26, 6, 'Nuwara Eliya'), (27, 6, 'Hatton'), (28, 6, 'Talawakele'), (29, 6, 'Ginigathhena'), (30, 6, 'Walapane'),
    (31, 7, 'Galle'), (32, 7, 'Ambalangoda'), (33, 7, 'Hikkaduwa'), (34, 7, 'Elpitiya'), (35, 7, 'Baddegama'),
    (36, 8, 'Matara'), (37, 8, 'Weligama'), (38, 8, 'Akuressa'), (39, 8, 'Hakmana'), (40, 8, 'Dikwella'),
    (41, 9, 'Hambantota'), (42, 9, 'Tangalle'), (43, 9, 'Tissamaharama'), (44, 9, 'Beliatta'), (45, 9, 'Ambalantota'),
    (46, 10, 'Jaffna'), (47, 10, 'Chavakachcheri'), (48, 10, 'Point Pedro'), (49, 10, 'Nallur'), (50, 10, 'Valvettithurai'),
    (51, 11, 'Kilinochchi'), (52, 11, 'Paranthan'), (53, 11, 'Poonakary'), (54, 11, 'Pallai'), (55, 11, 'Mulankavil'),
    (56, 12, 'Mannar'), (57, 12, 'Pesalai'), (58, 12, 'Talaimannar'), (59, 12, 'Murunkan'), (60, 12, 'Madhu'),
    (61, 13, 'Mullaitivu'), (62, 13, 'Puthukkudiyiruppu'), (63, 13, 'Oddusuddan'), (64, 13, 'Mulliyawalai'), (65, 13, 'Maritimepattu'),
    (66, 14, 'Vavuniya'), (67, 14, 'Nedunkeni'), (68, 14, 'Settikulam'), (69, 14, 'Omanthai'), (70, 14, 'Cheddikulam'),
    (71, 15, 'Batticaloa'), (72, 15, 'Eravur'), (73, 15, 'Kattankudy'), (74, 15, 'Valaichchenai'), (75, 15, 'Kaluwanchikudy'),
    (76, 16, 'Ampara'), (77, 16, 'Kalmunai'), (78, 16, 'Akkaraipattu'), (79, 16, 'Sammanthurai'), (80, 16, 'Pottuvil'),
    (81, 17, 'Trincomalee'), (82, 17, 'Kinniya'), (83, 17, 'Kantale'), (84, 17, 'Mutur'), (85, 17, 'Kuchchaveli'),
    (86, 18, 'Kurunegala'), (87, 18, 'Kuliyapitiya'), (88, 18, 'Narammala'), (89, 18, 'Pannala'), (90, 18, 'Wariyapola'),
    (91, 19, 'Puttalam'), (92, 19, 'Chilaw'), (93, 19, 'Marawila'), (94, 19, 'Wennappuwa'), (95, 19, 'Anamaduwa'),
    (96, 20, 'Anuradhapura'), (97, 20, 'Kekirawa'), (98, 20, 'Eppawala'), (99, 20, 'Thambuttegama'), (100, 20, 'Mihintale'),
    (101, 21, 'Polonnaruwa'), (102, 21, 'Kaduruwela'), (103, 21, 'Hingurakgoda'), (104, 21, 'Medirigiriya'), (105, 21, 'Welikanda'),
    (106, 22, 'Badulla'), (107, 22, 'Bandarawela'), (108, 22, 'Haputale'), (109, 22, 'Welimada'), (110, 22, 'Mahiyanganaya'),
    (111, 23, 'Monaragala'), (112, 23, 'Wellawaya'), (113, 23, 'Bibile'), (114, 23, 'Buttala'), (115, 23, 'Kataragama'),
    (116, 24, 'Ratnapura'), (117, 24, 'Balangoda'), (118, 24, 'Embilipitiya'), (119, 24, 'Pelmadulla'), (120, 24, 'Kahawatta'),
    (121, 25, 'Kegalle'), (122, 25, 'Mawanella'), (123, 25, 'Warakapola'), (124, 25, 'Rambukkana'), (125, 25, 'Dehiowita');

ALTER TABLE business ADD COLUMN city_id INTEGER REFERENCES cities (id);

WITH matches AS (
    SELECT b.id AS business_id, c.id AS city_id,
           count(*) OVER (PARTITION BY b.id) AS match_count
    FROM business b
    JOIN cities c ON lower(btrim(b.city)) = lower(c.name)
    JOIN districts d ON d.id = c.district_id
    JOIN provinces p ON p.id = d.province_id
    WHERE (b.district IS NULL OR btrim(b.district) = '' OR lower(btrim(b.district)) = lower(d.name))
      AND (b.province IS NULL OR btrim(b.province) = '' OR lower(btrim(b.province)) = lower(p.name))
)
UPDATE business b SET city_id = m.city_id
FROM matches m
WHERE m.business_id = b.id AND m.match_count = 1;

CREATE INDEX business_city_status_name_idx ON business (city_id, status, name);
CREATE INDEX districts_province_name_idx ON districts (province_id, name);
CREATE INDEX cities_district_name_idx ON cities (district_id, name, id);
CREATE INDEX business_category_assignments_category_business_idx ON business_category_assignments (category_id, business_id);
CREATE INDEX item_category_assignments_category_item_idx ON item_category_assignments (category_id, item_id);
