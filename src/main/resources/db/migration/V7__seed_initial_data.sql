-- V7__seed_initial_data.sql
-- Seed baseline catalog products, initial sample sales, and backorders

-- 1. Products
INSERT INTO products (product_id, supplier_id, supplier_name, name, category, unit_price, tax_rate, stock_level, reserved_stock, region)
VALUES
('PRD-MILK-01', 'SUP-BROOKSIDE', 'Brookside Dairy Ltd', 'Brookside Fresh Milk 500ml (Ctn of 24)', 'Dairy', 1440.00, 0.1600, 150, 10, 'Nairobi'),
('PRD-MILK-02', 'SUP-BROOKSIDE', 'Brookside Dairy Ltd', 'Brookside Long Life UHT 1L (Ctn of 12)', 'Dairy', 1920.00, 0.1600, 80, 0, 'Rift Valley'),
('PRD-OIL-01', 'SUP-BIDCO', 'Bidco Africa Ltd', 'Elianto Pure Corn Oil 5L (Box of 4)', 'Cooking Oil', 4600.00, 0.1600, 45, 5, 'Nairobi'),
('PRD-SOAP-01', 'SUP-BIDCO', 'Bidco Africa Ltd', 'Gental Washing Powder 1kg (Ctn of 12)', 'Detergent', 2400.00, 0.1600, 100, 0, 'Coast'),
('PRD-FLOUR-01', 'SUP-UNGA', 'Unga Group Ltd', 'Jogoo Maize Flour 2kg (Bundle of 12)', 'Flour & Grains', 1800.00, 0.0000, 200, 20, 'Nairobi'),
('PRD-OUT-01', 'SUP-UNGA', 'Unga Group Ltd', 'Amana Basmati Rice 5kg (Limited Run)', 'Rice', 1250.00, 0.1600, 0, 0, 'Nairobi');

-- 2. Linked Backorder
INSERT INTO orders (order_number, invoice_number, shop_owner_id, shop_owner_name, shop_owner_phone, supplier_id, supplier_name, region, subtotal, tax_amount, delivery_charges, total_amount, payment_method, payment_status, amount_paid, amount_outstanding, order_status, expected_delivery_date, delivery_status, created_at, updated_at)
VALUES
('ORD-BACKORDER-001', 'INV-BACKORDER-001', 'RET-001', 'Mama Jane Duka', '0712345678', 'SUP-BROOKSIDE', 'Brookside Dairy Ltd', 'Nairobi', 1440.00, 230.40, 350.00, 2020.40, 'PAY_ON_DELIVERY', 'CONFIRMED_UNPAID', 0.00, 2020.40, 'PENDING_FULFILMENT', CURRENT_DATE + 3, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, tax_rate, line_total, line_tax)
SELECT id, 'PRD-MILK-01', 'Brookside Fresh Milk 500ml (Ctn of 24)', 1, 1440.00, 0.1600, 1440.00, 230.40
FROM orders WHERE order_number = 'ORD-BACKORDER-001';

-- 3. Completed Sale with e-TIMS receipt & supplier invoice
INSERT INTO sales (sale_reference, e_tims_receipt_number, supplier_invoice_number, customer_id, customer_name, subtotal, tax_amount, total_amount, sale_date, status, linked_order_number)
VALUES
('SALE-202610-101', 'ETIMS-REC-88492041', 'INV-SUP-99102', 'CUST-KIBERA-01', 'Kibera Corner Store', 6040.00, 966.40, 7006.40, CURRENT_TIMESTAMP, 'COMPLETED', 'ORD-BACKORDER-001');

INSERT INTO sale_items (sale_id, product_id, product_name, quantity_sold, quantity_refunded, unit_price, tax_rate, total_price, tax_amount)
SELECT id, 'PRD-MILK-01', 'Brookside Fresh Milk 500ml (Ctn of 24)', 3, 0, 1440.00, 0.1600, 4320.00, 691.20
FROM sales WHERE sale_reference = 'SALE-202610-101';

INSERT INTO sale_items (sale_id, product_id, product_name, quantity_sold, quantity_refunded, unit_price, tax_rate, total_price, tax_amount)
SELECT id, 'PRD-OIL-01', 'Elianto Pure Corn Oil 5L (Box of 4)', 1, 0, 1720.00, 0.1600, 1720.00, 275.20
FROM sales WHERE sale_reference = 'SALE-202610-101';

-- 4. Regional Pending Order
INSERT INTO orders (order_number, invoice_number, shop_owner_id, shop_owner_name, shop_owner_phone, supplier_id, supplier_name, region, subtotal, tax_amount, delivery_charges, total_amount, payment_method, payment_status, amount_paid, amount_outstanding, order_status, expected_delivery_date, delivery_status, created_at, updated_at)
VALUES
('ORD-PENDING-REG-01', 'INV-PENDING-REG-01', 'RET-002', 'Eldoret Highway Wholesalers', '0722998877', 'SUP-BROOKSIDE', 'Brookside Dairy Ltd', 'Rift Valley', 3840.00, 614.40, 350.00, 4804.40, 'PAY_ON_DELIVERY', 'CONFIRMED_UNPAID', 0.00, 4804.40, 'PENDING_FULFILMENT', CURRENT_DATE + 4, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, tax_rate, line_total, line_tax)
SELECT id, 'PRD-MILK-02', 'Brookside Long Life UHT 1L (Ctn of 12)', 2, 1920.00, 0.1600, 3840.00, 614.40
FROM orders WHERE order_number = 'ORD-PENDING-REG-01';
