#!/bin/sh
set -eu

# Use hex + SQL QUOTE() so passwords never become executable SQL.
for service in user policy engagement; do
  case "$service" in
    user) password="$USER_DB_PASSWORD" ;;
    policy) password="$POLICY_DB_PASSWORD" ;;
    engagement) password="$ENGAGEMENT_DB_PASSWORD" ;;
  esac
  test -n "$password"
  hex=$(printf '%s' "$password" | od -An -tx1 | tr -d ' \n')
  MYSQL_PWD="$MARIADB_ROOT_PASSWORD" mariadb --protocol=socket -uroot <<SQL
CREATE DATABASE IF NOT EXISTS \`${service}_db\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @password = CONVERT(0x$hex USING utf8mb4);
SET @statement = CONCAT('CREATE USER IF NOT EXISTS ''${service}_app''@''%'' IDENTIFIED BY ', QUOTE(@password));
PREPARE create_account FROM @statement;
EXECUTE create_account;
DEALLOCATE PREPARE create_account;
GRANT ALL PRIVILEGES ON \`${service}_db\`.* TO '${service}_app'@'%';
SQL
done
