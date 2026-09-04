-- Runs once on a fresh volume, after mysql-init.sql. A small e-commerce dataset for
-- the monitored database so the collectors have real tables and the load generator
-- has something to hammer.
--
-- Deliberately NO index on orders.customer_id or order_items.order_id: lookups by
-- customer/order do full scans, which is exactly the kind of thing the monitor should
-- surface (high rows-examined-per-row-sent, climbing full-scan counts).

USE myDB;

CREATE TABLE customers (
    id         INT PRIMARY KEY AUTO_INCREMENT,
    name       VARCHAR(100),
    email      VARCHAR(200),
    city       VARCHAR(80),
    created_at DATETIME,
    KEY idx_customers_email (email)
);

CREATE TABLE products (
    id    INT PRIMARY KEY AUTO_INCREMENT,
    sku   VARCHAR(40),
    name  VARCHAR(200),
    price DECIMAL(10, 2),
    stock INT,
    KEY idx_products_sku (sku)
);

CREATE TABLE orders (
    id          INT PRIMARY KEY AUTO_INCREMENT,
    customer_id INT,                 -- intentionally NOT indexed
    status      VARCHAR(20),
    total       DECIMAL(10, 2),
    created_at  DATETIME,
    KEY idx_orders_status (status)
);

CREATE TABLE order_items (
    id         INT PRIMARY KEY AUTO_INCREMENT,
    order_id   INT,                  -- intentionally NOT indexed
    product_id INT,
    qty        INT,
    price      DECIMAL(10, 2)
);

-- 0..99999 as a set, so the fills below are set-based (fast) not row-by-row.
CREATE TEMPORARY TABLE _seq (n INT PRIMARY KEY);
INSERT INTO _seq (n)
SELECT a.N + b.N * 10 + c.N * 100 + d.N * 1000 + e.N * 10000
FROM (SELECT 0 N UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
CROSS JOIN (SELECT 0 N UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
CROSS JOIN (SELECT 0 N UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c
CROSS JOIN (SELECT 0 N UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d
CROSS JOIN (SELECT 0 N UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) e;

INSERT INTO customers (name, email, city, created_at)
SELECT CONCAT('Customer ', n),
       CONCAT('user', n, '@example.com'),
       ELT(1 + n % 6, 'Berlin', 'Paris', 'Madrid', 'Rome', 'Vienna', 'Lisbon'),
       NOW() - INTERVAL (n % 3650) DAY
FROM _seq WHERE n < 20000;

INSERT INTO products (sku, name, price, stock)
SELECT CONCAT('SKU-', LPAD(n, 6, '0')),
       CONCAT('Product ', n),
       ROUND(2 + (n % 500) + RAND(), 2),
       n % 1000
FROM _seq WHERE n < 2000;

INSERT INTO orders (customer_id, status, total, created_at)
SELECT 1 + n % 20000,
       ELT(1 + n % 4, 'pending', 'paid', 'shipped', 'cancelled'),
       ROUND(10 + (n % 900) + RAND() * 50, 2),
       NOW() - INTERVAL (n % 2000) HOUR
FROM _seq WHERE n < 80000;

INSERT INTO order_items (order_id, product_id, qty, price)
SELECT 1 + s.n % 80000,
       1 + (s.n * 7 + k.k) % 2000,
       1 + (s.n + k.k) % 5,
       ROUND(2 + ((s.n + k.k) % 500) + RAND(), 2)
FROM _seq s
CROSS JOIN (SELECT 1 k UNION ALL SELECT 2 UNION ALL SELECT 3) k
WHERE s.n < 80000;

DROP TEMPORARY TABLE _seq;

ANALYZE TABLE customers, products, orders, order_items;
