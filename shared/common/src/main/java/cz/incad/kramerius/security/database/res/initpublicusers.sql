-- creating table PUBLIC_USER_ENTITY --
CREATE TABLE PUBLIC_USER_ENTITY (
   USER_ID SERIAL NOT NULL,
   UPDATE_TIMESTAMP TIMESTAMP,
   NAME VARCHAR(255) NOT NULL,
   SURNAME VARCHAR(255) NOT NULL,
   LOGINNAME VARCHAR(255) NOT NULL,
   PSWD VARCHAR(255),
   DEACTIVATED BOOLEAN,
   EMAIL VARCHAR(255),
   ORGANISATION VARCHAR(255), PRIMARY KEY (USER_ID));

CREATE INDEX PUBLIC_UNAME_IDX ON PUBLIC_USER_ENTITY (NAME);

CREATE INDEX PUBLIC_SURNAME_IDX ON PUBLIC_USER_ENTITY (SURNAME);

CREATE INDEX PUBLIC_LOGINNAME_IDX ON PUBLIC_USER_ENTITY (LOGINNAME);

CREATE TABLE PROFILES(active_users_id INT, PROFILE TEXT);

ALTER TABLE PROFILES ADD CONSTRAINT PROFILES_ACTIVE_USER_ID_FK
    FOREIGN KEY (active_users_id) REFERENCES ACTIVE_USERS(ACTIVE_USERS_ID) 
