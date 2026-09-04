-- Runs once on first container start (empty DB volume).
-- The `myDB` schema and the application user (`fares`) are created by the
-- MYSQL_DATABASE / MYSQL_USER / MYSQL_PASSWORD env vars in docker-compose.yml.

-- Dedicated read-only monitoring account (the production pattern: a monitor would
-- connect to the target DB as this user). Not wired into the app - kept as a
-- reference for how a real deployment would separate collection from storage.
CREATE USER IF NOT EXISTS 'monitor'@'%' IDENTIFIED BY 'fares';
GRANT SELECT, PROCESS, REPLICATION CLIENT ON *.* TO 'monitor'@'%';
GRANT SELECT ON performance_schema.* TO 'monitor'@'%';

-- Give the app's own target user the read-only monitoring privileges too, so the
-- collector can read SHOW GLOBAL STATUS / VARIABLES, performance_schema and InnoDB
-- state through its normal connection. Must match MYSQL_USER in docker-compose.yml.
GRANT PROCESS, REPLICATION CLIENT ON *.* TO 'fares'@'%';
GRANT SELECT ON performance_schema.* TO 'fares'@'%';
-- read-only on the grant tables so the security-posture checks can see which accounts
-- have no password / a wildcard host
GRANT SELECT ON mysql.user TO 'fares'@'%';
GRANT SELECT ON mysql.db TO 'fares'@'%';

FLUSH PRIVILEGES;
