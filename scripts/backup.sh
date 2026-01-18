#!/bin/bash
set -e

################################################################################
# Backup Script for Database and Keycloak
# 
# This script creates timestamped backups of:
# - MySQL/PostgreSQL database
# - Keycloak data (database export)
# - Uploads to S3
#
# All configuration is done via environment variables
################################################################################

# Auto-load .env file if it exists in the script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/.env" ]; then
    set -a  # Automatically export all variables
    # Source .env file, handling inline comments and quoted values
    while IFS= read -r line || [ -n "$line" ]; do
        # Skip comments and empty lines
        [[ "$line" =~ ^[[:space:]]*# ]] && continue
        [[ -z "${line// }" ]] && continue
        
        # Extract variable name and value
        if [[ "$line" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
            var_name="${BASH_REMATCH[1]}"
            var_value="${BASH_REMATCH[2]}"
            
            # Strip inline comments (everything after # that's not in quotes)
            # This is a simple approach - remove # and everything after if not in quotes
            if [[ "$var_value" =~ ^\"(.*)\" ]]; then
                # Value is in double quotes
                var_value="${BASH_REMATCH[1]}"
            elif [[ "$var_value" =~ ^\'(.*)\' ]]; then
                # Value is in single quotes
                var_value="${BASH_REMATCH[1]}"
            else
                # Remove inline comment (everything after #)
                var_value=$(echo "$var_value" | sed 's/#.*$//' | sed 's/[[:space:]]*$//')
            fi
            
            # Remove leading/trailing whitespace and quotes
            var_value=$(echo "$var_value" | sed 's/^[[:space:]]*//' | sed 's/[[:space:]]*$//' | sed 's/^"\(.*\)"$/\1/' | sed "s/^'\(.*\)'$/\1/")
            
            # Export the variable
            export "$var_name=$var_value"
        fi
    done < "$SCRIPT_DIR/.env"
    set +a  # Turn off automatic export
fi

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored messages
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to find Keycloak installation path from systemd service
find_keycloak_path() {
    local service_file="${KEYCLOAK_SERVICE_FILE:-/etc/systemd/system/keycloak.service}"
    
    if [ -f "$service_file" ]; then
        # Extract WorkingDirectory or ExecStart path
        local work_dir=$(grep "^WorkingDirectory=" "$service_file" | cut -d'=' -f2 | tr -d '"' | tr -d "'")
        local exec_start=$(grep "^ExecStart=" "$service_file" | cut -d'=' -f2- | awk '{print $1}' | tr -d '"' | tr -d "'")
        
        if [ -n "$work_dir" ] && [ -d "$work_dir" ]; then
            # Resolve symlinks to get actual path
            local resolved_path=$(readlink -f "$work_dir" 2>/dev/null || echo "$work_dir")
            if [ -d "$resolved_path" ] && [ -d "$resolved_path/data" ]; then
                log_info "Found Keycloak path from systemd WorkingDirectory: $resolved_path" >&2
                echo "$resolved_path"
                return 0
            fi
        elif [ -n "$exec_start" ]; then
            # Extract directory from ExecStart (usually /opt/keycloak-26.1.0/bin/kc.sh -> /opt/keycloak-26.1.0)
            local kc_dir=$(dirname "$(dirname "$exec_start")")
            # Resolve symlinks to get actual path
            local resolved_path=$(readlink -f "$kc_dir" 2>/dev/null || echo "$kc_dir")
            if [ -d "$resolved_path" ] && [ -d "$resolved_path/data" ]; then
                log_info "Found Keycloak path from systemd ExecStart: $resolved_path" >&2
                echo "$resolved_path"
                return 0
            fi
        fi
    fi
    
    # Note: KEYCLOAK_HOME is checked in backup_keycloak_h2() function, not here
    # This function is for automatic discovery when KEYCLOAK_HOME is not set
    
    # Fallback to common locations (including versioned directories)
    # Check versioned directories FIRST (more specific)
    local found_path=""
    shopt -s nullglob
    for path in /opt/keycloak-*; do
        if [ -d "$path" ] && [ -d "$path/data" ]; then
            found_path="$path"
            log_info "Found Keycloak versioned directory: $found_path" >&2
            break
        fi
    done
    shopt -u nullglob
    
    if [ -n "$found_path" ]; then
        echo "$found_path"
        return 0
    fi
    
    # Check /opt/keycloak (non-versioned, might be symlink)
    if [ -d "/opt/keycloak" ]; then
        # Resolve symlink if it exists
        local resolved_path=$(readlink -f "/opt/keycloak" 2>/dev/null || echo "/opt/keycloak")
        if [ -d "$resolved_path" ] && [ -d "$resolved_path/data" ]; then
            log_info "Found Keycloak path (resolved from /opt/keycloak): $resolved_path" >&2
            echo "$resolved_path"
            return 0
        fi
    fi
    
    # Check /usr/local/keycloak-* (versioned)
    found_path=""
    shopt -s nullglob
    for path in /usr/local/keycloak-*; do
        if [ -d "$path" ] && [ -d "$path/data" ]; then
            found_path="$path"
            log_info "Found Keycloak versioned directory: $found_path" >&2
            break
        fi
    done
    shopt -u nullglob
    
    if [ -n "$found_path" ]; then
        echo "$found_path"
        return 0
    fi
    
    # Check /usr/local/keycloak (non-versioned)
    if [ -d "/usr/local/keycloak" ]; then
        local resolved_path=$(readlink -f "/usr/local/keycloak" 2>/dev/null || echo "/usr/local/keycloak")
        if [ -d "$resolved_path" ] && [ -d "$resolved_path/data" ]; then
            log_info "Found Keycloak path (resolved from /usr/local/keycloak): $resolved_path" >&2
            echo "$resolved_path"
            return 0
        fi
    fi
    
    return 1
}

# Function to detect Keycloak database type
detect_keycloak_db_type() {
    # Check if explicitly set to h2
    if [ "${KEYCLOAK_DB_TYPE:-}" == "h2" ] || [ "${KEYCLOAK_DB_TYPE:-}" == "H2" ]; then
        echo "h2"
        return 0
    fi
    
    # Check if Keycloak data directory has H2 files
    local kc_path=$(find_keycloak_path 2>/dev/null)
    if [ -n "$kc_path" ] && [ -d "$kc_path/data/h2" ]; then
        if [ -f "$kc_path/data/h2/keycloak.mv.db" ] || [ -f "$kc_path/data/h2/keycloakdb.mv.db" ] || [ -f "$kc_path/data/h2/keycloak.h2.db" ]; then
            echo "h2"
            return 0
        fi
    fi
    
    # Default to external database (mysql/postgresql)
    echo "${KEYCLOAK_DB_TYPE:-mysql}"
    return 0
}

# Function to check required environment variables
check_required_vars() {
    local missing_vars=()
    
    # Database variables
    [ -z "$DB_TYPE" ] && missing_vars+=("DB_TYPE")
    [ -z "$DB_HOST" ] && missing_vars+=("DB_HOST")
    [ -z "$DB_PORT" ] && missing_vars+=("DB_PORT")
    [ -z "$DB_NAME" ] && missing_vars+=("DB_NAME")
    [ -z "$DB_USER" ] && missing_vars+=("DB_USER")
    [ -z "$DB_PASSWORD" ] && missing_vars+=("DB_PASSWORD")
    
    # Detect Keycloak database type
    KEYCLOAK_ACTUAL_DB_TYPE=$(detect_keycloak_db_type)
    
    if [ "$KEYCLOAK_ACTUAL_DB_TYPE" == "h2" ]; then
        # For H2, we need Keycloak path instead of database credentials
        if [ -z "${KEYCLOAK_HOME:-}" ] && ! find_keycloak_path >/dev/null 2>&1; then
            log_warn "Keycloak using H2 but path not found. Trying to detect..."
            local kc_path=$(find_keycloak_path)
            if [ -z "$kc_path" ]; then
                missing_vars+=("KEYCLOAK_HOME or KEYCLOAK_SERVICE_FILE")
            fi
        fi
    else
        # For external database, require database credentials
        [ -z "$KEYCLOAK_DB_TYPE" ] && missing_vars+=("KEYCLOAK_DB_TYPE")
        [ -z "$KEYCLOAK_DB_HOST" ] && missing_vars+=("KEYCLOAK_DB_HOST")
        [ -z "$KEYCLOAK_DB_PORT" ] && missing_vars+=("KEYCLOAK_DB_PORT")
        [ -z "$KEYCLOAK_DB_NAME" ] && missing_vars+=("KEYCLOAK_DB_NAME")
        [ -z "$KEYCLOAK_DB_USER" ] && missing_vars+=("KEYCLOAK_DB_USER")
        [ -z "$KEYCLOAK_DB_PASSWORD" ] && missing_vars+=("KEYCLOAK_DB_PASSWORD")
    fi
    
    # S3 variables
    [ -z "$S3_BUCKET" ] && missing_vars+=("S3_BUCKET")
    [ -z "$S3_REGION" ] && missing_vars+=("S3_REGION")
    [ -z "$AWS_ACCESS_KEY_ID" ] && missing_vars+=("AWS_ACCESS_KEY_ID")
    [ -z "$AWS_SECRET_ACCESS_KEY" ] && missing_vars+=("AWS_SECRET_ACCESS_KEY")
    
    if [ ${#missing_vars[@]} -ne 0 ]; then
        log_error "Missing required environment variables:"
        for var in "${missing_vars[@]}"; do
            echo "  - $var"
        done
        exit 1
    fi
}

# Function to create backup directory
create_backup_dir() {
    local timestamp=$(date +%Y%m%d_%H%M%S)
    BACKUP_DIR="/tmp/dam_backup_${timestamp}"
    mkdir -p "$BACKUP_DIR"
    log_info "Created backup directory: $BACKUP_DIR"
}

# Function to backup MySQL database
backup_mysql() {
    local db_type=$1
    local db_host=$2
    local db_port=$3
    local db_name=$4
    local db_user=$5
    local db_password=$6
    local output_file=$7
    
    log_info "Backing up MySQL database: $db_name"
    
    MYSQL_PWD="$db_password" mysqldump \
        --host="$db_host" \
        --port="$db_port" \
        --user="$db_user" \
        --single-transaction \
        --routines \
        --triggers \
        --events \
        --databases "$db_name" \
        > "$output_file"
    
    if [ $? -eq 0 ]; then
        log_info "MySQL backup completed: $output_file"
    else
        log_error "MySQL backup failed"
        exit 1
    fi
}

# Function to backup PostgreSQL database
backup_postgresql() {
    local db_type=$1
    local db_host=$2
    local db_port=$3
    local db_name=$4
    local db_user=$5
    local db_password=$6
    local output_file=$7
    
    log_info "Backing up PostgreSQL database: $db_name"
    
    PGPASSWORD="$db_password" pg_dump \
        --host="$db_host" \
        --port="$db_port" \
        --username="$db_user" \
        --dbname="$db_name" \
        --format=custom \
        --compress=9 \
        --file="$output_file"
    
    if [ $? -eq 0 ]; then
        log_info "PostgreSQL backup completed: $output_file"
    else
        log_error "PostgreSQL backup failed"
        exit 1
    fi
}

# Function to backup database (router function)
backup_database() {
    local db_type=$1
    local db_host=$2
    local db_port=$3
    local db_name=$4
    local db_user=$5
    local db_password=$6
    local output_file=$7
    
    case "$db_type" in
        mysql|MYSQL)
            backup_mysql "$db_type" "$db_host" "$db_port" "$db_name" "$db_user" "$db_password" "$output_file"
            ;;
        postgresql|POSTGRESQL|postgres|POSTGRES)
            backup_postgresql "$db_type" "$db_host" "$db_port" "$db_name" "$db_user" "$db_password" "$output_file"
            ;;
        *)
            log_error "Unsupported database type: $db_type"
            exit 1
            ;;
    esac
}

# Function to backup Keycloak H2 database files
backup_keycloak_h2() {
    log_info "Backing up Keycloak H2 database files..."
    
    # Find Keycloak installation path
    local kc_path="${KEYCLOAK_HOME:-}"
    
    if [ -n "$kc_path" ]; then
        # Resolve symlinks if KEYCLOAK_HOME is set
        local resolved_path=$(readlink -f "$kc_path" 2>/dev/null || echo "$kc_path")
        
        # Check if resolved path exists and has data directory
        if [ -d "$resolved_path" ] && [ -d "$resolved_path/data" ]; then
            kc_path="$resolved_path"
            log_info "Using KEYCLOAK_HOME (resolved): $kc_path"
        else
            log_warn "KEYCLOAK_HOME is set to '$kc_path' but path doesn't exist or has no data directory"
            log_warn "Resolved path: $resolved_path"
            log_warn "Attempting to find Keycloak installation automatically..."
            kc_path=""
        fi
    fi
    
    # If KEYCLOAK_HOME wasn't set or didn't work, try to find it automatically
    if [ -z "$kc_path" ]; then
        # Use a temp file to separate logs (stderr) from path (stdout)
        local temp_file=$(mktemp)
        kc_path=$(find_keycloak_path 2>"$temp_file")
        # Display the logs
        if [ -s "$temp_file" ]; then
            cat "$temp_file" >&2
        fi
        rm -f "$temp_file"
        if [ -z "$kc_path" ] || [ ! -d "$kc_path" ]; then
            log_error "Could not find Keycloak installation path"
            log_error "Please set KEYCLOAK_HOME to the correct path (e.g., /opt/keycloak-26.1.0)"
            log_error "Or ensure Keycloak is installed in /opt/keycloak-* or /usr/local/keycloak-*"
            exit 1
        fi
    fi
    
    log_info "Keycloak installation path: $kc_path"
    
    local h2_data_dir="$kc_path/data/h2"
    local keycloak_user="${KEYCLOAK_USER:-keycloak}"
    
    # Check if we need sudo for accessing H2 directory
    local use_sudo=false
    local sudo_cmd=""
    
    # Test if we can access the directory
    if [ ! -r "$h2_data_dir" ] 2>/dev/null; then
        # Check if sudo is available
        if command -v sudo >/dev/null 2>&1; then
            # Test if we can use sudo to access as keycloak user
            if sudo -u "$keycloak_user" test -r "$h2_data_dir" 2>/dev/null; then
                use_sudo=true
                sudo_cmd="sudo -u $keycloak_user"
                log_info "Using sudo to access Keycloak H2 directory as user: $keycloak_user"
            else
                log_warn "Cannot access $h2_data_dir even with sudo"
                log_warn "You may need to configure sudoers to allow access"
                log_warn "Example: echo '$USER ALL=(keycloak) NOPASSWD: /bin/cp' | sudo tee /etc/sudoers.d/keycloak-backup"
            fi
        else
            log_warn "Cannot access $h2_data_dir and sudo is not available"
        fi
    fi
    
    # Create directory if it doesn't exist (might be first run)
    # Use sudo if needed to check directory existence
    local dir_check_cmd="test -d"
    if [ "$use_sudo" = true ]; then
        dir_check_cmd="sudo -u $keycloak_user test -d"
    fi
    
    if ! $dir_check_cmd "$h2_data_dir" 2>/dev/null; then
        log_warn "Keycloak H2 data directory not found: $h2_data_dir"
        log_warn "This might mean Keycloak is using in-memory H2 database (dev mode default)"
        log_warn "To enable file-based H2, add: --db=h2-file to Keycloak start command"
        log_warn "Or configure Keycloak to use: --db-url=jdbc:h2:file:${kc_path}/data/h2/keycloak"
        log_warn "Skipping Keycloak H2 backup (no files to backup)"
        return 0
    fi
    
    # Check if Keycloak is running (for production safety)
    local keycloak_running=false
    if systemctl is-active --quiet keycloak 2>/dev/null; then
        keycloak_running=true
        log_warn "Keycloak service is running during backup"
        log_info "H2 files can be backed up while running, but ensure Keycloak is in a stable state"
        
        # Optional: Wait a moment for any pending writes (production safety)
        if [ "${KEYCLOAK_BACKUP_WAIT:-false}" == "true" ]; then
            log_info "Waiting 2 seconds for Keycloak to flush writes..."
            sleep 2
        fi
    fi
    
    # Create Keycloak backup directory
    local keycloak_backup_dir="$BACKUP_DIR/keycloak_h2"
    mkdir -p "$keycloak_backup_dir"
    
    # Backup H2 database files
    local h2_files_found=0
    local file_check_cmd="test -f"
    
    # Helper function to copy file (reads as keycloak user, writes as normal user)
    copy_h2_file() {
        local src_file="$1"
        local dst_file="$2"
        local file_name="$3"
        
        if [ "$use_sudo" = true ]; then
            # Use sudo to read as keycloak user, but write as normal user
            if sudo -u "$keycloak_user" cat "$src_file" > "$dst_file" 2>/dev/null; then
                # Preserve timestamp if possible (may not work with cat, but try)
                if [ -f "$src_file" ]; then
                    local timestamp=$(sudo -u "$keycloak_user" stat -c %y "$src_file" 2>/dev/null || echo "")
                    if [ -n "$timestamp" ]; then
                        touch -d "$timestamp" "$dst_file" 2>/dev/null || true
                    fi
                fi
                return 0
            else
                return 1
            fi
        else
            # Normal copy if we have direct access
            if cp -p "$src_file" "$dst_file" 2>/dev/null; then
                return 0
            else
                return 1
            fi
        fi
    }
    
    if [ "$use_sudo" = true ]; then
        file_check_cmd="sudo -u $keycloak_user test -f"
    fi
    
    # Keycloak H2 database files - check for common naming patterns
    # Pattern 1: keycloak.mv.db (standard Keycloak naming)
    if $file_check_cmd "$h2_data_dir/keycloak.mv.db" 2>/dev/null; then
        if copy_h2_file "$h2_data_dir/keycloak.mv.db" "$keycloak_backup_dir/keycloak.mv.db" "keycloak.mv.db"; then
            h2_files_found=1
            log_info "Backed up: keycloak.mv.db ($(du -h "$keycloak_backup_dir/keycloak.mv.db" | cut -f1))"
        else
            log_error "Failed to copy keycloak.mv.db"
        fi
    fi
    
    # Pattern 2: keycloakdb.mv.db (alternative naming)
    if $file_check_cmd "$h2_data_dir/keycloakdb.mv.db" 2>/dev/null; then
        if copy_h2_file "$h2_data_dir/keycloakdb.mv.db" "$keycloak_backup_dir/keycloakdb.mv.db" "keycloakdb.mv.db"; then
            h2_files_found=1
            log_info "Backed up: keycloakdb.mv.db ($(du -h "$keycloak_backup_dir/keycloakdb.mv.db" | cut -f1))"
        else
            log_error "Failed to copy keycloakdb.mv.db"
        fi
    fi
    
    # Pattern 3: Any other *.mv.db files (fallback)
    if [ "$use_sudo" = true ]; then
        for mv_file in $(sudo -u "$keycloak_user" find "$h2_data_dir" -maxdepth 1 -name "*.mv.db" -type f 2>/dev/null); do
            local basename_mv=$(basename "$mv_file")
            if [ "$basename_mv" != "keycloak.mv.db" ] && [ "$basename_mv" != "keycloakdb.mv.db" ]; then
                if copy_h2_file "$mv_file" "$keycloak_backup_dir/$basename_mv" "$basename_mv"; then
                    h2_files_found=1
                    log_info "Backed up: $basename_mv ($(du -h "$keycloak_backup_dir/$basename_mv" | cut -f1))"
                else
                    log_error "Failed to copy $basename_mv"
                fi
            fi
        done
    else
        for mv_file in "$h2_data_dir"/*.mv.db; do
            if [ -f "$mv_file" ]; then
                local basename_mv=$(basename "$mv_file")
                if [ "$basename_mv" != "keycloak.mv.db" ] && [ "$basename_mv" != "keycloakdb.mv.db" ]; then
                    if copy_h2_file "$mv_file" "$keycloak_backup_dir/$basename_mv" "$basename_mv"; then
                        h2_files_found=1
                        log_info "Backed up: $basename_mv ($(du -h "$keycloak_backup_dir/$basename_mv" | cut -f1))"
                    else
                        log_error "Failed to copy $basename_mv"
                    fi
                fi
            fi
        done
    fi
    
    # Older versions use keycloak.h2.db
    if $file_check_cmd "$h2_data_dir/keycloak.h2.db" 2>/dev/null; then
        if copy_h2_file "$h2_data_dir/keycloak.h2.db" "$keycloak_backup_dir/keycloak.h2.db" "keycloak.h2.db"; then
            h2_files_found=1
            log_info "Backed up: keycloak.h2.db ($(du -h "$keycloak_backup_dir/keycloak.h2.db" | cut -f1))"
        else
            log_error "Failed to copy keycloak.h2.db"
        fi
    fi
    
    # Backup trace files - check for both naming patterns
    if $file_check_cmd "$h2_data_dir/keycloak.trace.db" 2>/dev/null; then
        if copy_h2_file "$h2_data_dir/keycloak.trace.db" "$keycloak_backup_dir/keycloak.trace.db" "keycloak.trace.db"; then
            log_info "Backed up: keycloak.trace.db"
        else
            log_error "Failed to copy keycloak.trace.db"
        fi
    fi
    
    if $file_check_cmd "$h2_data_dir/keycloakdb.trace.db" 2>/dev/null; then
        if copy_h2_file "$h2_data_dir/keycloakdb.trace.db" "$keycloak_backup_dir/keycloakdb.trace.db" "keycloakdb.trace.db"; then
            log_info "Backed up: keycloakdb.trace.db"
        else
            log_error "Failed to copy keycloakdb.trace.db"
        fi
    fi
    
    if [ $h2_files_found -eq 0 ]; then
        log_warn "No H2 database files found in $h2_data_dir"
        log_warn "This is normal if Keycloak hasn't created any data yet, or if using external database"
        log_warn "Skipping Keycloak H2 backup (backup will continue with application database only)"
        # Remove the empty directory
        rmdir "$keycloak_backup_dir" 2>/dev/null || true
        return 0
    fi
    
    # Save Keycloak path info for restore
    echo "$kc_path" > "$keycloak_backup_dir/keycloak_path.txt"
    
    # Save backup metadata
    cat > "$keycloak_backup_dir/backup_info.txt" << EOF
Keycloak Path: $kc_path
H2 Data Directory: $h2_data_dir
Backup Timestamp: $(date -u +"%Y-%m-%d %H:%M:%S UTC")
Keycloak Running During Backup: $keycloak_running
EOF
    
    log_info "Keycloak H2 database backup completed"
}

# Function to compress backups
compress_backups() {
    log_info "Compressing backup directory..."
    
    local timestamp=$(basename "$BACKUP_DIR" | sed 's/dam_backup_//')
    BACKUP_ARCHIVE="/tmp/dam_backup_${timestamp}.tar.gz"
    
    tar -czf "$BACKUP_ARCHIVE" -C "$(dirname "$BACKUP_DIR")" "$(basename "$BACKUP_DIR")"
    
    if [ $? -eq 0 ]; then
        log_info "Backup compressed: $BACKUP_ARCHIVE"
        log_info "Backup size: $(du -h "$BACKUP_ARCHIVE" | cut -f1)"
    else
        log_error "Compression failed"
        exit 1
    fi
}

# Function to check if AWS CLI is working
check_aws_cli_working() {
    if ! command -v aws >/dev/null 2>&1; then
        return 1
    fi
    
    # Test if AWS CLI actually works (not just installed)
    if aws --version >/dev/null 2>&1; then
        return 0
    fi
    
    return 1
}

# Function to fix broken AWS CLI installation
fix_broken_aws_cli() {
    log_warn "AWS CLI is installed but not working (likely Python compatibility issue)"
    log_info "Attempting to fix by installing AWS CLI v2..."
    
    # Try to uninstall broken AWS CLI first
    if command -v apt-get >/dev/null 2>&1 && [ -f /usr/bin/aws ]; then
        log_info "Removing broken system AWS CLI..."
        sudo apt-get remove -y awscli 2>/dev/null || true
    fi
    
    # Install AWS CLI v2 (works on all systems and doesn't have Python dependency issues)
    local original_dir=$(pwd)
    cd /tmp || return 1
    
    if ! command -v curl >/dev/null 2>&1; then
        log_error "curl is required but not installed"
        cd "$original_dir" || true
        return 1
    fi
    
    if ! command -v unzip >/dev/null 2>&1; then
        log_info "unzip not found, installing..."
        if command -v apt-get >/dev/null 2>&1; then
            sudo apt-get update -qq && sudo apt-get install -y unzip
        elif command -v yum >/dev/null 2>&1; then
            sudo yum install -y unzip
        else
            log_error "Cannot install unzip automatically"
            cd "$original_dir" || true
            return 1
        fi
    fi
    
    log_info "Downloading AWS CLI v2..."
    curl -s "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
    if [ $? -ne 0 ]; then
        log_error "Failed to download AWS CLI v2"
        cd "$original_dir" || true
        return 1
    fi
    
    log_info "Installing AWS CLI v2..."
    unzip -q awscliv2.zip
    sudo ./aws/install --bin-dir /usr/local/bin --install-dir /usr/local/aws-cli
    local install_status=$?
    
    rm -rf awscliv2.zip aws
    cd "$original_dir" || true
    
    if [ $install_status -eq 0 ] && check_aws_cli_working; then
        log_info "✓ AWS CLI v2 installed and working"
        return 0
    fi
    
    log_error "Failed to install AWS CLI v2"
    return 1
}

# Function to install AWS CLI based on OS
install_aws_cli() {
    log_info "Installing AWS CLI..."
    local original_dir=$(pwd)
    
    # Always prefer AWS CLI v2 (more reliable, no Python dependency issues)
    log_info "Installing AWS CLI v2 (recommended method)..."
    cd /tmp || return 1
    
    # Check prerequisites
    if ! command -v curl >/dev/null 2>&1; then
        log_error "curl is required but not installed"
        if command -v apt-get >/dev/null 2>&1; then
            log_info "Installing curl..."
            sudo apt-get update -qq && sudo apt-get install -y curl
        elif command -v yum >/dev/null 2>&1; then
            log_info "Installing curl..."
            sudo yum install -y curl
        else
            log_error "Please install curl manually"
            cd "$original_dir" || true
            return 1
        fi
    fi
    
    if ! command -v unzip >/dev/null 2>&1; then
        log_info "Installing unzip..."
        if command -v apt-get >/dev/null 2>&1; then
            sudo apt-get update -qq && sudo apt-get install -y unzip
        elif command -v yum >/dev/null 2>&1; then
            sudo yum install -y unzip
        else
            log_error "Please install unzip manually"
            cd "$original_dir" || true
            return 1
        fi
    fi
    
    log_info "Downloading AWS CLI v2..."
    curl -s "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
    if [ $? -ne 0 ]; then
        log_error "Failed to download AWS CLI v2"
        cd "$original_dir" || true
        return 1
    fi
    
    log_info "Installing AWS CLI v2..."
    unzip -q awscliv2.zip
    sudo ./aws/install --bin-dir /usr/local/bin --install-dir /usr/local/aws-cli
    local install_status=$?
    
    rm -rf awscliv2.zip aws
    cd "$original_dir" || true
    
    if [ $install_status -eq 0 ] && check_aws_cli_working; then
        log_info "✓ AWS CLI v2 installed successfully"
        return 0
    fi
    
    log_error "Failed to install AWS CLI v2"
    return 1
}

# Function to check and install AWS CLI if needed
ensure_aws_cli() {
    # Check if AWS CLI is installed and working
    if check_aws_cli_working; then
        return 0
    fi
    
    # If AWS CLI is installed but broken, try to fix it
    if command -v aws >/dev/null 2>&1; then
        if ! check_aws_cli_working; then
            log_warn "AWS CLI is installed but not working properly"
            if [ "${AUTO_FIX_AWSCLI:-true}" == "true" ]; then
                fix_broken_aws_cli
                if [ $? -eq 0 ]; then
                    return 0
                fi
            else
                log_error "Set AUTO_FIX_AWSCLI=true in .env to automatically fix broken AWS CLI"
                exit 1
            fi
        fi
    fi
    
    log_warn "AWS CLI is not installed"
    
    # Check if auto-install is enabled (default: true)
    if [ "${AUTO_INSTALL_AWSCLI:-true}" != "true" ]; then
        log_error "Auto-installation is disabled. Please install AWS CLI manually:"
        if [ -f /etc/os-release ]; then
            . /etc/os-release
            case "$ID" in
                amzn|amazon)
                    log_error "  curl \"https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip\" -o \"awscliv2.zip\" && unzip awscliv2.zip && sudo ./aws/install"
                    ;;
                ubuntu|debian)
                    log_error "  sudo apt-get update && sudo apt-get install -y awscli"
                    ;;
                *)
                    log_error "  pip install awscli"
                    ;;
            esac
        else
            log_error "  pip install awscli"
        fi
        exit 1
    fi
    
    # Ask for confirmation unless FORCE_INSTALL is set
    if [ "${FORCE_INSTALL_AWSCLI:-false}" != "true" ]; then
        log_warn "This script will attempt to install AWS CLI automatically"
        if [ -t 0 ]; then
            read -p "Continue with installation? (yes/no): " response
            if [ "$response" != "yes" ] && [ "$response" != "y" ]; then
                log_info "Installation cancelled. Set FORCE_INSTALL_AWSCLI=true to skip this prompt."
                exit 1
            fi
        fi
    fi
    
    install_aws_cli
    if [ $? -ne 0 ]; then
        log_error "Failed to install AWS CLI. Please install it manually."
        exit 1
    fi
}

# Function to upload to S3
upload_to_s3() {
    log_info "Uploading backup to S3..."
    
    # Ensure AWS CLI is installed
    ensure_aws_cli
    
    local s3_path="s3://${S3_BUCKET}/${S3_PREFIX}$(basename "$BACKUP_ARCHIVE")"
    
    aws s3 cp "$BACKUP_ARCHIVE" "$s3_path" \
        --region "$S3_REGION" \
        --storage-class "${S3_STORAGE_CLASS:-STANDARD_IA}"
    
    if [ $? -eq 0 ]; then
        log_info "Backup uploaded successfully to: $s3_path"
        echo "$s3_path" > /tmp/last_backup_location.txt
    else
        log_error "S3 upload failed"
        log_error "Check your AWS credentials and S3 bucket permissions"
        exit 1
    fi
}

# Function to cleanup local backups
cleanup() {
    log_info "Cleaning up local backup files..."
    
    if [ -d "$BACKUP_DIR" ]; then
        rm -rf "$BACKUP_DIR"
        log_info "Removed backup directory"
    fi
    
    if [ "$KEEP_LOCAL_BACKUP" != "true" ] && [ -f "$BACKUP_ARCHIVE" ]; then
        rm -f "$BACKUP_ARCHIVE"
        log_info "Removed local backup archive"
    else
        log_info "Local backup archive kept: $BACKUP_ARCHIVE"
    fi
}

# Function to send notification (optional)
send_notification() {
    if [ -n "$SLACK_WEBHOOK_URL" ]; then
        local message="✅ DAM Backup completed successfully\nBackup location: s3://${S3_BUCKET}/${S3_PREFIX}$(basename "$BACKUP_ARCHIVE")\nSize: $(du -h "$BACKUP_ARCHIVE" 2>/dev/null | cut -f1 || echo 'N/A')"
        
        curl -X POST "$SLACK_WEBHOOK_URL" \
            -H 'Content-Type: application/json' \
            -d "{\"text\":\"$message\"}" \
            > /dev/null 2>&1
    fi
}

# Main execution
main() {
    log_info "=== DAM Backup Script Started ==="
    log_info "Timestamp: $(date)"
    
    # Check required environment variables
    check_required_vars
    
    # Create backup directory
    create_backup_dir
    
    # Backup application database
    log_info "Starting application database backup..."
    if [ "$DB_TYPE" == "mysql" ] || [ "$DB_TYPE" == "MYSQL" ]; then
        backup_database "$DB_TYPE" "$DB_HOST" "$DB_PORT" "$DB_NAME" "$DB_USER" "$DB_PASSWORD" \
            "$BACKUP_DIR/dam_database.sql"
    else
        backup_database "$DB_TYPE" "$DB_HOST" "$DB_PORT" "$DB_NAME" "$DB_USER" "$DB_PASSWORD" \
            "$BACKUP_DIR/dam_database.dump"
    fi
    
    # Backup Keycloak database
    log_info "Starting Keycloak backup..."
    if [ "$KEYCLOAK_ACTUAL_DB_TYPE" == "h2" ]; then
        backup_keycloak_h2
    else
        log_info "Backing up Keycloak external database..."
        if [ "$KEYCLOAK_DB_TYPE" == "mysql" ] || [ "$KEYCLOAK_DB_TYPE" == "MYSQL" ]; then
            backup_database "$KEYCLOAK_DB_TYPE" "$KEYCLOAK_DB_HOST" "$KEYCLOAK_DB_PORT" \
                "$KEYCLOAK_DB_NAME" "$KEYCLOAK_DB_USER" "$KEYCLOAK_DB_PASSWORD" \
                "$BACKUP_DIR/keycloak_database.sql"
        else
            backup_database "$KEYCLOAK_DB_TYPE" "$KEYCLOAK_DB_HOST" "$KEYCLOAK_DB_PORT" \
                "$KEYCLOAK_DB_NAME" "$KEYCLOAK_DB_USER" "$KEYCLOAK_DB_PASSWORD" \
                "$BACKUP_DIR/keycloak_database.dump"
        fi
    fi
    
    # Create metadata file
    cat > "$BACKUP_DIR/backup_metadata.txt" << EOF
Backup Timestamp: $(date -u +"%Y-%m-%d %H:%M:%S UTC")
Database Type: $DB_TYPE
Database Name: $DB_NAME
Keycloak DB Type: $KEYCLOAK_ACTUAL_DB_TYPE
Keycloak DB Name: ${KEYCLOAK_DB_NAME:-N/A (H2)}
Backup Script Version: 2.0
EOF
    
    # Compress backups
    compress_backups
    
    # Upload to S3
    upload_to_s3
    
    # Cleanup
    cleanup
    
    # Send notification
    send_notification
    
    log_info "=== Backup completed successfully ==="
}

# Trap errors and cleanup
trap 'log_error "Backup failed. Cleaning up..."; cleanup; exit 1' ERR

# Run main function
main

