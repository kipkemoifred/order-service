-- V7__seed_initial_data.sql
-- Seed baseline catalog products, inventory stock levels, initial sample sales, and backorders

-- 1. Products
INSERT INTO products (name, product_code, category, unit_of_measure, unit_price, tax_type, has_discount, discount_rate, active, synced_to_supplier_catalogue)
SELECT 'Brookside Fresh Milk 500ml (Ctn of 24)', 'PRD-MILK-01', 'Dairy', 'CARTONS', 1440.00, 'STANDARD', false, 0.00, true, true
WHERE NOT EXISTS (SELECT 1 FROM products WHERE product_code = 'PRD-MILK-01');

INSERT INTO products (name, product_code, category, unit_of_measure, unit_price, tax_type, has_discount, discount_rate, active, synced_to_supplier_catalogue)
SELECT 'Brookside Long Life UHT 1L (Ctn of 12)', 'PRD-MILK-02', 'Dairy', 'CARTONS', 1920.00, 'STANDARD', false, 0.00, true, true
WHERE NOT EXISTS (SELECT 1 FROM products WHERE product_code = 'PRD-MILK-02');

INSERT INTO products (name, product_code, category, unit_of_measure, unit_price, tax_type, has_discount, discount_rate, active, synced_to_supplier_catalogue)
SELECT 'Elianto Pure Corn Oil 5L (Box of 4)', 'PRD-OIL-01', 'Cooking Oil', 'BOXES', 4600.00, 'STANDARD', false, 0.00, true, true
WHERE NOT EXISTS (SELECT 1 FROM products WHERE product_code = 'PRD-OIL-01');

INSERT INTO products (name, product_code, category, unit_of_measure, unit_price, tax_type, has_discount, discount_rate, active, synced_to_supplier_catalogue)
SELECT 'Gental Washing Powder 1kg (Ctn of 12)', 'PRD-SOAP-01', 'Detergent', 'CARTONS', 2400.00, 'STANDARD', false, 0.00, true, true
WHERE NOT EXISTS (SELECT 1 FROM products WHERE product_code = 'PRD-SOAP-01');

INSERT INTO products (name, product_code, category, unit_of_measure, unit_price, tax_type, has_discount, discount_rate, active, synced_to_supplier_catalogue)
SELECT 'Jogoo Maize Flour 2kg (Bundle of 12)', 'PRD-FLOUR-01', 'Flour & Grains', 'BUNDLES', 1800.00, 'ZERO_RATED', false, 0.00, true, true
WHERE NOT EXISTS (SELECT 1 FROM products WHERE product_code = 'PRD-FLOUR-01');

INSERT INTO products (name, product_code, category, unit_of_measure, unit_price, tax_type, has_discount, discount_rate, active, synced_to_supplier_catalogue)
SELECT 'Amana Basmati Rice 5kg (Limited Run)', 'PRD-OUT-01', 'Rice', 'BAGS', 1250.00, 'STANDARD', false, 0.00, true, true
WHERE NOT EXISTS (SELECT 1 FROM products WHERE product_code = 'PRD-OUT-01');

-- 2. Inventory Stocks
INSERT INTO inventory_stocks (product_id, available_quantity, ordered_quantity, reorder_threshold)
SELECT p.id, 150.00, 10.00, 20.00
FROM products p
WHERE p.product_code = 'PRD-MILK-01'
  AND NOT EXISTS (SELECT 1 FROM inventory_stocks s WHERE s.product_id = p.id);

INSERT INTO inventory_stocks (product_id, available_quantity, ordered_quantity, reorder_threshold)
SELECT p.id, 80.00, 0.00, 15.00
FROM products p
WHERE p.product_code = 'PRD-MILK-02'
  AND NOT EXISTS (SELECT 1 FROM inventory_stocks s WHERE s.product_id = p.id);

INSERT INTO inventory_stocks (product_id, available_quantity, ordered_quantity, reorder_threshold)
SELECT p.id, 45.00, 5.00, 10.00
FROM products p
WHERE p.product_code = 'PRD-OIL-01'
  AND NOT EXISTS (SELECT 1 FROM inventory_stocks s WHERE s.product_id = p.id);

INSERT INTO inventory_stocks (product_id, available_quantity, ordered_quantity, reorder_threshold)
SELECT p.id, 100.00, 0.00, 20.00
FROM products p
WHERE p.product_code = 'PRD-SOAP-01'
  AND NOT EXISTS (SELECT 1 FROM inventory_stocks s WHERE s.product_id = p.id);

INSERT INTO inventory_stocks (product_id, available_quantity, ordered_quantity, reorder_threshold)
SELECT p.id, 200.00, 20.00, 30.00
FROM products p
WHERE p.product_code = 'PRD-FLOUR-01'
  AND NOT EXISTS (SELECT 1 FROM inventory_stocks s WHERE s.product_id = p.id);

INSERT INTO inventory_stocks (product_id, available_quantity, ordered_quantity, reorder_threshold)
SELECT p.id, 0.00, 0.00, 10.00
FROM products p
WHERE p.product_code = 'PRD-OUT-01'
  AND NOT EXISTS (SELECT 1 FROM inventory_stocks s WHERE s.product_id = p.id);

-- 3. Linked Backorder
INSERT INTO orders (order_number, invoice_number, shop_owner_id, shop_owner_name, shop_owner_phone, supplier_id, supplier_name, region, subtotal, tax_amount, delivery_charges, total_amount, payment_method, payment_status, amount_paid, amount_outstanding, order_status, expected_delivery_date, delivery_status, created_at, updated_at)
SELECT 'ORD-BACKORDER-001', 'INV-BACKORDER-001', 'RET-001', 'Mama Jane Duka', '0712345678', 'SUP-BROOKSIDE', 'Brookside Dairy Ltd', 'Nairobi', 1440.00, 230.40, 350.00, 2020.40, 'PAY_ON_DELIVERY', 'CONFIRMED_UNPAID', 0.00, 2020.40, 'PENDING_FULFILMENT', CAST(CURRENT_DATE + INTERVAL '3' DAY AS DATE), 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-BACKORDER-001');

INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, tax_rate, line_total, line_tax)
SELECT o.id, 'PRD-MILK-01', 'Brookside Fresh Milk 500ml (Ctn of 24)', 1, 1440.00, 0.1600, 1440.00, 230.40
FROM orders o
WHERE o.order_number = 'ORD-BACKORDER-001'
  AND NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.order_id = o.id AND oi.product_id = 'PRD-MILK-01');

-- 4. Completed Sale with e-TIMS receipt & supplier invoice
INSERT INTO sales (sale_reference, e_tims_receipt_number, supplier_invoice_number, customer_id, customer_name, subtotal, tax_amount, total_amount, sale_date, status, linked_order_number)
SELECT 'SALE-202610-101', 'ETIMS-REC-88492041', 'INV-SUP-99102', 'CUST-KIBERA-01', 'Kibera Corner Store', 6040.00, 966.40, 7006.40, CURRENT_TIMESTAMP, 'COMPLETED', 'ORD-BACKORDER-001'
WHERE NOT EXISTS (SELECT 1 FROM sales WHERE sale_reference = 'SALE-202610-101');

INSERT INTO sale_items (sale_id, product_id, product_name, quantity_sold, quantity_refunded, unit_price, tax_rate, total_price, tax_amount)
SELECT s.id, 'PRD-MILK-01', 'Brookside Fresh Milk 500ml (Ctn of 24)', 3, 0, 1440.00, 0.1600, 4320.00, 691.20
FROM sales s
WHERE s.sale_reference = 'SALE-202610-101'
  AND NOT EXISTS (SELECT 1 FROM sale_items si WHERE si.sale_id = s.id AND si.product_id = 'PRD-MILK-01');

INSERT INTO sale_items (sale_id, product_id, product_name, quantity_sold, quantity_refunded, unit_price, tax_rate, total_price, tax_amount)
SELECT s.id, 'PRD-OIL-01', 'Elianto Pure Corn Oil 5L (Box of 4)', 1, 0, 1720.00, 0.1600, 1720.00, 275.20
FROM sales s
WHERE s.sale_reference = 'SALE-202610-101'
  AND NOT EXISTS (SELECT 1 FROM sale_items si WHERE si.sale_id = s.id AND si.product_id = 'PRD-OIL-01');

-- 5. Regional Pending Order
INSERT INTO orders (order_number, invoice_number, shop_owner_id, shop_owner_name, shop_owner_phone, supplier_id, supplier_name, region, subtotal, tax_amount, delivery_charges, total_amount, payment_method, payment_status, amount_paid, amount_outstanding, order_status, expected_delivery_date, delivery_status, created_at, updated_at)
SELECT 'ORD-PENDING-REG-01', 'INV-PENDING-REG-01', 'RET-002', 'Eldoret Highway Wholesalers', '0722998877', 'SUP-BROOKSIDE', 'Brookside Dairy Ltd', 'Rift Valley', 3840.00, 614.40, 350.00, 4804.40, 'PAY_ON_DELIVERY', 'CONFIRMED_UNPAID', 0.00, 4804.40, 'PENDING_FULFILMENT', CAST(CURRENT_DATE + INTERVAL '4' DAY AS DATE), 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-PENDING-REG-01');

INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, tax_rate, line_total, line_tax)
SELECT o.id, 'PRD-MILK-02', 'Brookside Long Life UHT 1L (Ctn of 12)', 2, 1920.00, 0.1600, 3840.00, 614.40
FROM orders o
WHERE o.order_number = 'ORD-PENDING-REG-01'
  AND NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.order_id = o.id AND oi.product_id = 'PRD-MILK-02');
