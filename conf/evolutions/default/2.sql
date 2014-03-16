# Item schema

# --- !Ups

CREATE TABLE Item (
    id bigserial PRIMARY KEY,
    item_number text NOT NULL UNIQUE,
    category text NOT NULL,
    donor text NOT NULL,
    description text NOT NULL,
    minbid money NOT NULL
);

# --- !Downs

DROP TABLE Item;
