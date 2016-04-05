# --- !Ups

CREATE TABLE "USER"
(
  USER_ID    TEXT PRIMARY KEY,
  FIRST_NAME TEXT,
  LAST_NAME  TEXT,
  FULL_NAME  TEXT,
  EMAIL      TEXT
);
CREATE TABLE LOGIN_INFO
(
  ID           BIGSERIAL PRIMARY KEY,
  PROVIDER_ID  TEXT NOT NULL,
  PROVIDER_KEY TEXT NOT NULL
);
CREATE TABLE USER_LOGIN_INFO (
  USER_ID       TEXT   NOT NULL REFERENCES "USER",
  LOGIN_INFO_ID BIGINT NOT NULL REFERENCES LOGIN_INFO(ID)
);
CREATE TABLE "PASSWORD_INFO" (
  HASHER        TEXT   NOT NULL,
  PASSWORD      TEXT   NOT NULL,
  SALT          TEXT,
  LOGIN_INFO_ID BIGINT NOT NULL REFERENCES USER_LOGIN_INFO
);
CREATE TABLE "OAUTH1_INFO" (
  ID            BIGSERIAL PRIMARY KEY,
  TOKEN         TEXT   NOT NULL,
  SECRET        TEXT   NOT NULL,
  LOGIN_INFO_ID BIGINT NOT NULL REFERENCES USER_LOGIN_INFO
);
CREATE TABLE "OAUTH2_INFO" (
  ID            BIGSERIAL PRIMARY KEY,
  ACCESS_TOKEN  TEXT   NOT NULL,
  TOKEN_TYPE    TEXT,
  EXPIRES_IN    INTEGER,
  REFRESH_TOKEN TEXT,
  LOGIN_INFO_ID BIGINT NOT NULL REFERENCES USER_LOGIN_INFO
);
CREATE TABLE "OPENID_INFO" (
  ID            TEXT PRIMARY KEY,
  LOGIN_INFO_ID BIGINT NOT NULL REFERENCES USER_LOGIN_INFO
);
CREATE TABLE "OPENID_ATTRIBUTES" (
  ID    TEXT NOT NULL REFERENCES "OPENID_INFO",
  KEY   TEXT NOT NULL,
  VALUE TEXT NOT NULL
);


# --- !Downs

DROP TABLE "OPENID_ATTRIBUTES";
DROP TABLE "OPENID_INFO";
DROP TABLE "OAUTH2_INFO";
DROP TABLE "OAUTH1_INFO";
DROP TABLE "PASSWORD_INFO";
DROP TABLE USER_LOGIN_INFO;
DROP TABLE LOGIN_INFO;
DROP TABLE "USER";
