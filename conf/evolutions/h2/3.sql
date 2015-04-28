# Payment schema

# --- !Ups

CREATE TABLE PAYMENT (
    ID BIGINT IDENTITY(1) PRIMARY KEY,
    BIDDER_ID BIGINT REFERENCES "BIDDER" (ID) ON DELETE RESTRICT,
    DESCRIPTION VARCHAR(255) NOT NULL,
    AMOUNT DECIMAL(20, 2) NOT NULL
);

# --- !Downs

DROP TABLE PAYMENT;