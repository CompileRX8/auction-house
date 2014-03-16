# Bidder schema

# --- !Ups

CREATE TABLE Bidder (
    id bigserial PRIMARY KEY,
    name text NOT NULL UNIQUE
);

# --- !Downs

DROP TABLE Bidder;
