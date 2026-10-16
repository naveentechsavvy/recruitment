package com.example.recruitment.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Small, safe upgrade that Hibernate's "ddl-auto=update" cannot do by itself.
 *
 * On MySQL, Hibernate creates an @Enumerated(STRING) column as a fixed list, e.g. enum('ADMIN','HIRING_MANAGER','RECRUITER'),
 * and "update" never changes a column that already exists. The Manager and HR BP roles are new, so an older
 * app_users.role column would refuse them ("Data truncated"). This turns that one column into plain text,
 * which the application still reads and writes as before. It does nothing when the column is already fine,
 * and a failure is only logged: the application still starts.
 * (Use Flyway or Liquibase for real migrations before production, as noted in application.properties.)
 */
@Component
@Order(1) // before the admin seeder
public class SchemaUpgrade implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaUpgrade.class);

    static final String FIND_COLUMN_TYPE =
            "select column_type from information_schema.columns "
                    + "where table_schema = database() and table_name = 'app_users' and column_name = 'role'";
    static final String WIDEN_ROLE_COLUMN = "alter table app_users modify role varchar(20) not null";

    /**
     * Older versions made email unique on its own in client_role_assignments (one role per email in the whole system).
     * Hibernate adds the new (email + client) index but never drops the old one, so adding the same email to a second
     * client would fail. This finds every unique index made of the single column "email".
     */
    static final String FIND_EMAIL_ONLY_UNIQUE_INDEXES =
            "select index_name from information_schema.statistics "
                    + "where table_schema = database() and table_name = 'client_role_assignments' "
                    + "and non_unique = 0 and index_name <> 'PRIMARY' "
                    + "group by index_name having count(*) = 1 and max(column_name) = 'email'";

    private final JdbcTemplate jdbc;

    public SchemaUpgrade(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        widenRoleColumn();
        dropOldUniqueEmailIndex();
    }

    private void dropOldUniqueEmailIndex() {
        try {
            for (String name : jdbc.queryForList(FIND_EMAIL_ONLY_UNIQUE_INDEXES, String.class)) {
                if (!isSafeName(name)) continue; // only plain index names are ever put into the statement
                jdbc.execute("alter table client_role_assignments drop index `" + name + "`");
                log.info("Dropped the old unique index {} so one email can have a role on several clients", name);
            }
        } catch (Exception e) {
            log.warn("Could not check the unique indexes of client_role_assignments. If adding the same email to a second "
                    + "client fails with a duplicate-key error, drop the index that is unique on email only.", e);
        }
    }

    static boolean isSafeName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,64}");
    }

    private void widenRoleColumn() {
        try {
            List<String> types = jdbc.queryForList(FIND_COLUMN_TYPE, String.class);
            if (!types.isEmpty() && needsUpgrade(types.get(0))) {
                jdbc.execute(WIDEN_ROLE_COLUMN);
                log.info("Upgraded app_users.role so it accepts the Manager and HR BP roles");
            }
        } catch (Exception e) {
            log.warn("Could not check or upgrade app_users.role. If creating a Manager or HR BP user fails, run: {}", WIDEN_ROLE_COLUMN, e);
        }
    }

    /** True for an old fixed-list column that does not yet know the new roles. */
    static boolean needsUpgrade(String columnType) {
        if (columnType == null) return false;
        String t = columnType.toLowerCase();
        return t.startsWith("enum(") && !(t.contains("'hr_bp'") && t.contains("'manager'"));
    }
}
