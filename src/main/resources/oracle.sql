CREATE TABLE FAILSAFE_TASK (
    ID VARCHAR2(36) NOT NULL,
    PARAMETER CLOB,
    NAME VARCHAR2(200) NOT NULL,
    PLANNED_EXECUTION_TIME TIMESTAMP,
    LOCK_TIME TIMESTAMP,
    FAIL_TIME TIMESTAMP,
    EXCEPTION_MESSAGE CLOB,
    STACK_TRACE CLOB,
    RETRY_COUNT INT DEFAULT 0,
    VERSION INT DEFAULT 0,
    CREATED_DATE TIMESTAMP,
    PRIMARY KEY (ID)
);

CREATE INDEX IDX_FAILSAFE_TASK_CREATED_DATE ON FAILSAFE_TASK (CREATED_DATE);

CREATE INDEX IDX_FAILSAFE_TASK_FAIL_TIME ON FAILSAFE_TASK (FAIL_TIME);