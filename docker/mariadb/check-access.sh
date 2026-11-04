#!/bin/sh
set -eu

service=${1:?Service name required}
case "$service" in
  user) MYSQL_PWD=$USER_DB_PASSWORD; other=policy ;;
  policy) MYSQL_PWD=$POLICY_DB_PASSWORD; other=user ;;
  engagement) MYSQL_PWD=$ENGAGEMENT_DB_PASSWORD; other=user ;;
  *) echo 'Unsupported database service' >&2; exit 1 ;;
esac
test -n "$MYSQL_PWD"
export MYSQL_PWD

database=$(mariadb --protocol=tcp -h127.0.0.1 -u"${service}_app" -D"${service}_db" -N <<'SQL'
SELECT DATABASE();
SQL
)
test "$database" = "${service}_db" || { echo "Database connection failed: $service" >&2; exit 1; }

error_file=$(mktemp)
trap 'rm -f "$error_file"' EXIT
if mariadb --protocol=tcp -h127.0.0.1 -u"${service}_app" -D"${other}_db" > /dev/null 2> "$error_file" <<'SQL'
SELECT 1;
SQL
then
  echo "Cross-database access unexpectedly allowed: $service" >&2
  exit 1
fi
# Only permission denial counts as success; syntax/network errors must fail.
if ! grep -q 'ERROR 1044 ' "$error_file"; then
  cat "$error_file" >&2
  exit 1
fi
echo "PASS: $service database access and isolation"
