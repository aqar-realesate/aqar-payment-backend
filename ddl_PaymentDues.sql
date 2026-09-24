CREATE TABLE payment_dues
(
    id            INT AUTO_INCREMENT NOT NULL,
    customer_id   INT                NOT NULL,
    unit_id       INT                NOT NULL,
    request_id    INT                NOT NULL,
    amount        DOUBLE             NULL,
    currency      VARCHAR(255)       NOT NULL,
    due_date      datetime(6)        NULL,
    `description` VARCHAR(255)       NULL,
    CONSTRAINT pk_payment_dues PRIMARY KEY (id)
);