# Payment schema

# --- !Ups

CREATE TABLE Payment (
    id bigserial PRIMARY KEY,
    bidder_id bigint REFERENCES Bidder (id) ON DELETE RESTRICT,
    description text NOT NULL,
    amount money NOT NULL
);

# --- !Downs

DROP TABLE Payment;
