#!/usr/bin/env bash
set -euo pipefail

connection_name="${DBTOOLS_CONNECTION:-MT ADB Alina Production}"
schema_name="${DB_SCHEMA:-MICRONAUTADMIN}"
default_row_count=20

usage() {
  cat <<'EOF'
Usage:
  ./scripts/explore-db.sh --list
  ./scripts/explore-db.sh --wishlist [ROW_COUNT]
  ./scripts/explore-db.sh TABLE [ROW_COUNT]

Examples:
  ./scripts/explore-db.sh --list
  ./scripts/explore-db.sh HOTELS
  ./scripts/explore-db.sh --wishlist 10

The script uses the saved Oracle SQL Developer connection named
"MT ADB Alina Production" by default. Override it with DBTOOLS_CONNECTION
or override the schema with DB_SCHEMA.
EOF
}

find_sqlcl() {
  if command -v sql >/dev/null 2>&1; then
    command -v sql
    return
  fi

  local candidate
  for candidate in "$HOME"/.vscode-server/extensions/oracle.sql-developer-*/dbtools/sqlcl/bin/sql; do
    if [[ -x "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return
    fi
  done

  echo "SQLcl was not found. Install Oracle SQLcl or the Oracle SQL Developer VS Code extension." >&2
  exit 1
}

if [[ ! "$schema_name" =~ ^[A-Za-z][A-Za-z0-9_$#]*$ ]]; then
  echo "Invalid DB_SCHEMA: $schema_name" >&2
  exit 2
fi

if [[ $# -eq 0 || "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

sqlcl="$(find_sqlcl)"

sqlcl_args=(
  -S
  -L
  -nohistory
  -noupdates
  -name "$connection_name"
  -e "set sqlformat ansiconsole"
  -e "set pagesize 100"
  -e "set linesize 240"
)

if [[ "$1" == "--list" ]]; then
  query="select table_name from all_tables where owner = upper('$schema_name') and table_name in ('ACTIVITIES', 'CHAT_MEMORY', 'DESTINATIONS', 'HOTELS', 'WISHLIST_ITEMS') order by table_name"
  exec "$sqlcl" "${sqlcl_args[@]}" -e "$query"
fi

if [[ "$1" == "--wishlist" ]]; then
  row_count="${2:-$default_row_count}"

  if [[ ! "$row_count" =~ ^[0-9]+$ ]] || ((row_count < 1 || row_count > 200)); then
    echo "ROW_COUNT must be a number from 1 to 200." >&2
    exit 2
  fi

  echo "Showing resolved wishlist items from $schema_name (up to $row_count rows)."
  echo

  query="select w.item_type as type,"
  query+=" case w.item_type when 'destination' then d.name when 'hotel' then h.name when 'activity' then a.name end as item,"
  query+=" case w.item_type when 'destination' then d.name else parent_d.name end as destination,"
  query+=" case w.item_type when 'destination' then d.region when 'hotel' then 'CHF ' || to_char(h.price_per_night, 'FM9999990') || ' / night' when 'activity' then a.season end as detail,"
  query+=" w.conversation_id"
  query+=" from $schema_name.wishlist_items w"
  query+=" left join $schema_name.destinations d on w.item_type = 'destination' and d.id = w.item_id"
  query+=" left join $schema_name.hotels h on w.item_type = 'hotel' and h.id = w.item_id"
  query+=" left join $schema_name.activities a on w.item_type = 'activity' and a.id = w.item_id"
  query+=" left join $schema_name.destinations parent_d on parent_d.id = coalesce(h.destination_id, a.destination_id)"
  query+=" order by w.id desc fetch first $row_count rows only"
  exec "$sqlcl" "${sqlcl_args[@]}" -e "$query"
fi

table_name="${1^^}"
row_count="${2:-$default_row_count}"

if [[ ! "$table_name" =~ ^[A-Z][A-Z0-9_$#]*$ ]]; then
  echo "Invalid table name: $1" >&2
  exit 2
fi

if [[ ! "$row_count" =~ ^[0-9]+$ ]] || ((row_count < 1 || row_count > 200)); then
  echo "ROW_COUNT must be a number from 1 to 200." >&2
  exit 2
fi

column_query="select listagg(chr(34) || replace(column_name, chr(34), chr(34) || chr(34)) || chr(34), ', ') within group (order by column_id)"
column_query+=" from all_tab_columns"
column_query+=" where owner = upper('$schema_name')"
column_query+=" and table_name = upper('$table_name')"
column_query+=" and data_type not in ('VECTOR', 'BFILE', 'BLOB', 'CLOB', 'NCLOB', 'LONG', 'LONG RAW', 'RAW', 'XMLTYPE')"
column_query+=" and data_type_owner is null"

column_list="$({
  "$sqlcl" -S -L -nohistory -noupdates -name "$connection_name" \
    -e "set heading off" \
    -e "set feedback off" \
    -e "set pagesize 0" \
    -e "set linesize 32767" \
    -e "set sqlformat default" \
    -e "$column_query"
} | tr -d '\r' | sed -e '/^[[:space:]]*$/d' -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"

if [[ -z "$column_list" ]]; then
  echo "Table $schema_name.$table_name was not found or has no display-friendly columns." >&2
  exit 3
fi

echo "Showing human-readable columns from $schema_name.$table_name (up to $row_count rows)."
echo "Vector, binary, and Oracle object columns are hidden."
echo

query="select $column_list from $schema_name.$table_name fetch first $row_count rows only"
exec "$sqlcl" "${sqlcl_args[@]}" -e "$query"
