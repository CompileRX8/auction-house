# Bidder schema

# --- !Ups

CREATE TABLE BIDDER (
    ID BIGINT IDENTITY(1) PRIMARY KEY,
    NAME varchar(63) NOT NULL UNIQUE
);

# --- !Downs

DROP TABLE BIDDER;
