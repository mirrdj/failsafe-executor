package os.failsafe.executor.db;

import os.failsafe.executor.utils.Database;
import os.failsafe.executor.utils.FileUtil;

class H2DatabaseTestConfig implements DatabaseTestConfig {

    public void createTable(Database database) {
        String createTableSql = FileUtil.readResourceFile("oracle.sql");

        String[] split = createTableSql.split("\\n\\n");

        database.execute("DROP TABLE IF EXISTS FAILSAFE_TASK",
                split[0], split[1]);
    }

    public void truncateTable(Database database) {
        database.update("TRUNCATE TABLE FAILSAFE_TASK");
    }

    public String user() {
        return "sa";
    }

    public String password() {
        return "";
    }

    public String driver() {
        return "org.h2.Driver";
    }

    public String jdbcUrl() {
        return "jdbc:h2:mem:taskdb";
    }

    public int maxPoolSize() {
        return 2;
    }
}
