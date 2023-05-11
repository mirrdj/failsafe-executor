CREATE TABLE FAILSAFE_TASK (
    ID VARCHAR(36) NOT NULL,
    PARAMETER MEDIUMTEXT,
    NAME VARCHAR(200) NOT NULL,
    PLANNED_EXECUTION_TIME TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    NODE_ID VARCHAR(48),
    LOCK_TIME TIMESTAMP(3) NULL,
    FAIL_TIME TIMESTAMP(3) NULL,
    TIMEOUT INT8,
    EXCEPTION_MESSAGE VARCHAR(1000),
    STACK_TRACE MEDIUMTEXT,
    RETRY_COUNT INT DEFAULT 0,
    VERSION INT DEFAULT 0,
    CREATED_DATE TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (ID)
);

CREATE INDEX IDX_FAILSAFE_TASK_CREATED_DATE ON FAILSAFE_TASK (CREATED_DATE);

CREATE INDEX IDX_FAILSAFE_TASK_FAIL_TIME ON FAILSAFE_TASK (FAIL_TIME);