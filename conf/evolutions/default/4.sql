# Winning Bid schema

# --- !Ups

CREATE TABLE WinningBid (
    id bigserial PRIMARY KEY,
    bidder_id bigint REFERENCES Bidder (id) ON DELETE RESTRICT,
    item_id bigint REFERENCES Item (id) ON DELETE RESTRICT,
    amount money NOT NULL
);

# --- !Downs

DROP TABLE WinningBid;
