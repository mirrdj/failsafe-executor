package os.failsafe.executor.db;

import os.failsafe.executor.utils.Database;
import os.failsafe.executor.utils.FileUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class MariaDatabaseTestConfig implements DatabaseTestConfig {

    public void createTable(Database database) {
        database.execute("DROP TABLE IF EXISTS FAILSAFE_TASK");

        String createTableSql = FileUtil.readResourceFile("mariadb.sql");
        String[] split = createTableSql.split("\\n\\n");
        for (String stmt : split) {
            database.execute(stmt);
        }
    }

    public void truncateTable(Database database) {
        database.update("TRUNCATE TABLE FAILSAFE_TASK");
    }

    public String user() {
        return "failsafe";
    }

    public String password() {
        return "failsafe";
    }

    public String driver() {
        return "org.mariadb.jdbc.Driver";
    }

    public String jdbcUrl() {
        return "jdbc:mariadb://localhost:3306/failsafe";
    }

    public int maxPoolSize() {
        return 5;
    }

    public Map<String, String> getAdditionalConfigs() {
        Map<String, String>  configs = new HashMap<>();
        configs.put("useBulkStmts", "false");
        return configs;
    }
}
