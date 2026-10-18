CREATE TABLE business_categories (
    id INTEGER PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT NOT NULL
);

CREATE TABLE item_categories (
    id INTEGER PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT NOT NULL
);

CREATE TABLE business_category_assignments (
    business_id INTEGER NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    category_id INTEGER NOT NULL REFERENCES business_categories (id),
    PRIMARY KEY (business_id, category_id)
);

CREATE TABLE item_category_assignments (
    item_id BIGINT NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    category_id INTEGER NOT NULL REFERENCES item_categories (id),
    PRIMARY KEY (item_id, category_id)
);

INSERT INTO business_categories (id, name, description) VALUES
    (1, 'Automotive', 'Vehicle sales maintenance repairs and related services'),
    (2, 'Beauty & Wellness', 'Beauty grooming personal care and wellness businesses'),
    (3, 'Construction', 'Building construction renovation and structural services'),
    (4, 'Education', 'Schools training centers tutoring and educational services'),
    (5, 'Electronics', 'Electronic devices appliances and technical services'),
    (6, 'Entertainment', 'Events recreation gaming and entertainment businesses'),
    (7, 'Fashion & Apparel', 'Clothing footwear accessories and fashion businesses'),
    (8, 'Food & Beverage', 'Restaurants cafes catering and food businesses'),
    (9, 'Furniture & Interiors', 'Furniture home furnishing and interior design businesses'),
    (10, 'Health & Medical', 'Healthcare clinics pharmacies and medical services'),
    (11, 'Home & Garden', 'Household supplies gardening and home improvement businesses'),
    (12, 'Hospitality', 'Hotels guesthouses accommodation and hospitality services'),
    (13, 'IT & Technology', 'Software development IT support and technology businesses'),
    (14, 'Legal & Professional', 'Legal accounting consulting and professional services'),
    (15, 'Manufacturing', 'Product manufacturing fabrication and industrial businesses'),
    (16, 'Marketing & Media', 'Advertising photography media and creative agencies'),
    (17, 'Pets & Animals', 'Pet supplies veterinary care and animal services'),
    (18, 'Printing & Stationery', 'Printing publishing stationery and related businesses'),
    (19, 'Real Estate', 'Property sales rentals and property management businesses'),
    (20, 'Repair & Maintenance', 'Equipment appliance and general repair businesses'),
    (21, 'Retail & General Stores', 'General merchandise shops and mixed product retailers'),
    (22, 'Sports & Fitness', 'Gyms sports equipment fitness training and related businesses'),
    (23, 'Transport & Logistics', 'Transportation courier moving and logistics businesses'),
    (24, 'Travel & Tourism', 'Travel agencies tour operators and tourism services'),
    (25, 'Agriculture & Farming', 'Farming agricultural supplies and agricultural services');

INSERT INTO item_categories (id, name, description) VALUES
    (1, 'Accessories', 'Fashion personal and general-purpose accessories'),
    (2, 'Agricultural Products', 'Crops seeds fertilizers and agricultural goods'),
    (3, 'Agricultural Services', 'Farming equipment operation cultivation and farm support'),
    (4, 'Appliances', 'Household kitchen and electrical appliances'),
    (5, 'Art & Crafts', 'Artwork handmade products and craft services'),
    (6, 'Automotive Parts', 'Vehicle spare parts tires batteries and accessories'),
    (7, 'Automotive Services', 'Vehicle repairs maintenance inspections and detailing'),
    (8, 'Beauty Products', 'Cosmetics skincare haircare and beauty supplies'),
    (9, 'Beauty Services', 'Haircuts grooming makeup and personal beauty treatments'),
    (10, 'Books & Stationery', 'Books educational materials and stationery products'),
    (11, 'Building Materials', 'Cement timber tiles hardware and construction supplies'),
    (12, 'Cleaning Products', 'Household commercial and industrial cleaning supplies'),
    (13, 'Cleaning Services', 'Residential commercial and specialized cleaning services'),
    (14, 'Clothing & Footwear', 'Apparel shoes uniforms and related products'),
    (15, 'Computer Hardware', 'Computers laptops components and peripheral devices'),
    (16, 'Construction Services', 'Building renovation masonry and construction work'),
    (17, 'Consulting Services', 'Business management technical and professional consulting'),
    (18, 'Delivery Services', 'Courier parcel delivery and local transportation services'),
    (19, 'Digital Products', 'Software licenses digital templates and downloadable products'),
    (20, 'Education & Training', 'Tutoring professional courses workshops and skills training'),
    (21, 'Electrical Services', 'Electrical installation wiring maintenance and repairs'),
    (22, 'Electronics', 'Phones televisions audio devices and electronic accessories'),
    (23, 'Entertainment Services', 'Event entertainment performances and recreational activities'),
    (24, 'Event Services', 'Event planning decoration catering and event support'),
    (25, 'Food & Groceries', 'Prepared foods groceries ingredients and beverages'),
    (26, 'Furniture', 'Home office and commercial furniture products'),
    (27, 'Gardening Services', 'Landscaping lawn care planting and garden maintenance'),
    (28, 'Health Products', 'Medical supplies health accessories and wellness products'),
    (29, 'Healthcare Services', 'Medical consultations therapy and healthcare treatments'),
    (30, 'Home Decor', 'Decorative household products lighting and interior accessories'),
    (31, 'IT Services', 'Software development technical support networking and IT consulting'),
    (32, 'Legal & Financial Services', 'Legal advice bookkeeping accounting and financial consulting'),
    (33, 'Marketing Services', 'Advertising branding SEO and digital marketing services'),
    (34, 'Mechanical Services', 'Mechanical repairs fabrication and equipment maintenance'),
    (35, 'Pet Products & Services', 'Pet food accessories grooming boarding and animal care'),
    (36, 'Photography & Media', 'Photography videography editing and media production services'),
    (37, 'Plumbing Services', 'Plumbing installation pipe repairs and drainage maintenance'),
    (38, 'Printing Services', 'Document printing graphic printing and customization services'),
    (39, 'Property Services', 'Property maintenance inspection management and real estate services'),
    (40, 'Rental Services', 'Equipment vehicle furniture and other rental services');
