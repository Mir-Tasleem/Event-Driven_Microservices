MERGE INTO stock (sku, available, reserved)
KEY (sku)
VALUES ('book', 120, 10),
       ('laptop', 50, 5),
       ('phone', 200, 20),
       ('headphones', 80, 8),
       ('monitor', 30, 2);
