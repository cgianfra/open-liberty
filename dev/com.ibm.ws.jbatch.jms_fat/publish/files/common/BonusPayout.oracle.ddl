ALTER SESSION SET CURRENT_SCHEMA = BONUSPAYOUT;

DROP TABLESPACE BONUSPAYOUT_TS INCLUDING CONTENTS AND DATAFILES;

CREATE TABLESPACE BONUSPAYOUT_TS DATAFILE '/home/oracle/app/oracle/oradata/&DBNAME/BONUSPAYOUT_TS.dbf' 
   SIZE 5M AUTOEXTEND ON NEXT 1M MAXSIZE UNLIMITED DEFAULT 
   STORAGE (INITIAL 1M NEXT 1M MAXEXTENTS UNLIMITED PCTINCREASE 0);

CREATE TABLE ACCOUNT  (
		  ACCTNUM INTEGER NOT NULL, 
		  BALANCE INTEGER NOT NULL,		 
		  INSTANCEID NUMBER(20) NOT NULL,		  
		  ACCTCODE VARCHAR(30) )  
TABLESPACE BONUSPAYOUT_TS; 
		 
ALTER TABLE ACCOUNT
	ADD CONSTRAINT ACCOUNT_PK PRIMARY KEY
		(ACCTNUM, INSTANCEID);		 
                 
COMMIT WORK;
