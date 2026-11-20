-- One Postgres instance for the laptop, but one database per service: no service can join
-- another service's tables; they only share data through events and APIs.
CREATE DATABASE authdb;
CREATE DATABASE orderdb;
CREATE DATABASE dispatchdb;
CREATE DATABASE notificationdb;
