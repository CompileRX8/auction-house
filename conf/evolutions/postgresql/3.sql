# Payment schema

# --- !Ups

CREATE TABLE PAYMENT (
    ID BIGSERIAL PRIMARY KEY,
    BIDDER_ID BIGINT REFERENCES BIDDER (ID) ON DELETE RESTRICT,
    DESCRIPTION TEXT NOT NULL,
    AMOUNT MONEY NOT NULL
);

# --- !Downs

DROP TABLE PAYMENT;