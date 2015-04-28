# Winning Bid schema

# --- !Ups

CREATE TABLE WINNINGBID (
    ID BIGINT IDENTITY(1) PRIMARY KEY,
    BIDDER_ID BIGINT REFERENCES "BIDDER" (ID) ON DELETE RESTRICT,
    ITEM_ID BIGINT REFERENCES "ITEM" (ID) ON DELETE RESTRICT,
    AMOUNT DECIMAL(20, 2) NOT NULL
);

# --- !Downs

DROP TABLE WINNINGBID;