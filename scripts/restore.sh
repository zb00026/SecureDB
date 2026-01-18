#!/bin/bash
set -e

################################################################################
# Restore Script for Database and Keycloak
# 
# This script restores backups from S3:
# - Downloads backup from S3
# - Restores MySQL/PostgreSQL database
# - Restores Keycloak database
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
    
    # Note: KEYCLOAK_HOME is checked in restore_keycloak_h2() function, not here
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
    
    # S3 variables (for downloading backup)
    [ -z "$S3_BACKUP_PATH" ] && missing_vars+=("S3_BACKUP_PATH")
    [ -z "$AWS_ACCESS_KEY_ID" ] && missing_vars+=("AWS_ACCESS_KEY_ID")
    [ -z "$AWS_SECRET_ACCESS_KEY" ] && missing_vars+=("AWS_SECRET_ACCESS_KEY")
    
    # Keycloak variables will be checked after we know the backup type from metadata
    
    if [ ${#missing_vars[@]} -ne 0 ]; then
        log_error "Missing required environment variables:"
        for var in "${missing_vars[@]}"; do
            echo "  - $var"
        done
        exit 1
    fi
}

# Function to prompt for confirmation
confirm_restore() {
    log_warn "=========================================="
    log_warn "WARNING: This will REPLACE existing data!"
    log_warn "=========================================="
    echo ""
    echo "Database: $DB_NAME on $DB_HOST:$DB_PORT"
    echo "Keycloak DB: $KEYCLOAK_DB_NAME on $KEYCLOAK_DB_HOST:$KEYCLOAK_DB_PORT"
    echo "Backup source: $S3_BACKUP_PATH"
    echo ""
    
    if [ "$FORCE_RESTORE" == "true" ]; then
        log_warn "FORCE_RESTORE=true, skipping confirmation"
        return 0
    fi
    
    read -p "Are you sure you want to proceed? (yes/no): " response
    if [ "$response" != "yes" ]; then
        log_info "Restore cancelled by user"
        exit 0
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

# Function to download backup from S3
download_from_s3() {
    log_info "Downloading backup from S3..."
    
    # Ensure AWS CLI is installed
    ensure_aws_cli
    
    RESTORE_DIR="/tmp/dam_restore_$(date +%Y%m%d_%H%M%S)"
    mkdir -p "$RESTORE_DIR"
    
    BACKUP_ARCHIVE="$RESTORE_DIR/backup.tar.gz"
    
    aws s3 cp "$S3_BACKUP_PATH" "$BACKUP_ARCHIVE" --region "${S3_REGION:-us-east-1}"
    
    if [ $? -eq 0 ]; then
        log_info "Backup downloaded: $BACKUP_ARCHIVE"
        log_info "Backup size: $(du -h "$BACKUP_ARCHIVE" | cut -f1)"
    else
        log_error "Failed to download backup from S3"
        log_error "Check your AWS credentials and S3 backup path"
        exit 1
    fi
}

# Function to extract backup
extract_backup() {
    log_info "Extracting backup archive..."
    
    tar -xzf "$BACKUP_ARCHIVE" -C "$RESTORE_DIR"
    
    # Find the extracted directory
    BACKUP_CONTENT_DIR=$(find "$RESTORE_DIR" -type d -name "dam_backup_*" | head -1)
    
    if [ -z "$BACKUP_CONTENT_DIR" ]; then
        log_error "Could not find backup content directory"
        exit 1
    fi
    
    log_info "Backup extracted to: $BACKUP_CONTENT_DIR"
    
    # Display metadata if available
    if [ -f "$BACKUP_CONTENT_DIR/backup_metadata.txt" ]; then
        log_info "Backup metadata:"
        cat "$BACKUP_CONTENT_DIR/backup_metadata.txt"
        
        # Extract Keycloak DB type from metadata
        KEYCLOAK_BACKUP_DB_TYPE=$(grep "^Keycloak DB Type:" "$BACKUP_CONTENT_DIR/backup_metadata.txt" | cut -d':' -f2 | tr -d ' ' || echo "")
    fi
}

# Function to restore Keycloak H2 database files
restore_keycloak_h2() {
    log_info "Restoring Keycloak H2 database files..."
    
    local keycloak_backup_dir="$BACKUP_CONTENT_DIR/keycloak_h2"
    
    if [ ! -d "$keycloak_backup_dir" ]; then
        log_error "Keycloak H2 backup directory not found: $keycloak_backup_dir"
        exit 1
    fi
    
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
        # Try to read from backup first
        if [ -f "$keycloak_backup_dir/keycloak_path.txt" ]; then
            kc_path=$(cat "$keycloak_backup_dir/keycloak_path.txt")
            # Resolve symlinks
            kc_path=$(readlink -f "$kc_path" 2>/dev/null || echo "$kc_path")
            if [ -d "$kc_path" ] && [ -d "$kc_path/data" ]; then
                log_info "Using Keycloak path from backup: $kc_path"
            else
                log_warn "Keycloak path from backup doesn't exist: $kc_path"
                log_warn "Attempting to find Keycloak installation automatically..."
                kc_path=""
            fi
        fi
        
        # If still not found, try automatic detection
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
    fi
    
    log_info "Keycloak installation path: $kc_path"
    
    # First check if the data directory exists at all
    if [ ! -d "$kc_path/data" ]; then
        log_warn "Keycloak data directory not found: $kc_path/data"
        log_warn "This might mean Keycloak is using an external database or hasn't created data directory yet"
        log_warn "Skipping Keycloak H2 restore (no data directory)"
        return 0
    fi
    
    local h2_data_dir="$kc_path/data/h2"
    local keycloak_user="${KEYCLOAK_USER:-keycloak}"
    
    # Check if we need sudo for accessing H2 directory
    local use_sudo=false
    local sudo_cmd=""
    
    # Test if we can access the directory
    if [ ! -r "$h2_data_dir" ] 2>/dev/null || [ ! -w "$(dirname "$h2_data_dir")" ] 2>/dev/null; then
        # Check if sudo is available
        if command -v sudo >/dev/null 2>&1; then
            # Test if we can use sudo to access as keycloak user
            if sudo -u "$keycloak_user" test -r "$h2_data_dir" 2>/dev/null || sudo -u "$keycloak_user" test -w "$(dirname "$h2_data_dir")" 2>/dev/null; then
                use_sudo=true
                sudo_cmd="sudo -u $keycloak_user"
                log_info "Using sudo to access Keycloak H2 directory as user: $keycloak_user"
            else
                log_warn "Cannot access $h2_data_dir even with sudo"
                log_warn "You may need to configure sudoers to allow access"
                log_warn "Example: echo '$USER ALL=(keycloak) NOPASSWD: /bin/cp, /bin/mkdir, /bin/chown' | sudo tee /etc/sudoers.d/keycloak-backup"
            fi
        else
            log_warn "Cannot access $h2_data_dir and sudo is not available"
        fi
    fi
    
    # Stop Keycloak if running (recommended for H2 restore)
    if systemctl is-active --quiet keycloak 2>/dev/null; then
        log_warn "Keycloak service is running. Stopping it for safe restore..."
        if [ "$FORCE_RESTORE" != "true" ]; then
            read -p "Stop Keycloak service now? (yes/no): " stop_response
            if [ "$stop_response" == "yes" ]; then
                sudo systemctl stop keycloak
                KEYCLOAK_WAS_RUNNING=true
            else
                log_warn "Proceeding with Keycloak running (not recommended for H2)"
            fi
        else
            sudo systemctl stop keycloak
            KEYCLOAK_WAS_RUNNING=true
        fi
    fi
    
    # Create backup of existing H2 files if they exist
    local dir_check_cmd="test -d"
    local file_check_cmd="test -f"
    local mkdir_cmd="mkdir -p"
    
    # Helper function to restore file (reads as normal user, writes as keycloak user)
    restore_h2_file() {
        local src_file="$1"
        local dst_file="$2"
        local file_name="$3"
        
        if [ "$use_sudo" = true ]; then
            # Read as normal user, write as keycloak user
            if cat "$src_file" | sudo -u "$keycloak_user" tee "$dst_file" > /dev/null 2>&1; then
                return 0
            else
                return 1
            fi
        else
            # Normal copy if we have direct access
            if cp "$src_file" "$dst_file" 2>/dev/null; then
                return 0
            else
                return 1
            fi
        fi
    }
    
    # Helper function to backup existing file (reads as keycloak user, writes as keycloak user in backup dir)
    backup_existing_h2_file() {
        local src_file="$1"
        local dst_file="$2"
        local file_name="$3"
        
        if [ "$use_sudo" = true ]; then
            # Read as keycloak user, write to backup dir (backup dir is owned by keycloak)
            # Use sh -c to ensure the redirection happens as keycloak user
            if sudo -u "$keycloak_user" sh -c "cat '$src_file' > '$dst_file'" 2>/dev/null; then
                return 0
            else
                return 1
            fi
        else
            # Normal copy
            if cp "$src_file" "$dst_file" 2>/dev/null; then
                return 0
            else
                return 1
            fi
        fi
    }
    
    if [ "$use_sudo" = true ]; then
        dir_check_cmd="sudo -u $keycloak_user test -d"
        file_check_cmd="sudo -u $keycloak_user test -f"
        mkdir_cmd="sudo -u $keycloak_user mkdir -p"
    fi
    
    if $dir_check_cmd "$h2_data_dir" 2>/dev/null; then
        local backup_timestamp=$(date +%Y%m%d_%H%M%S)
        local h2_backup_dir="$h2_data_dir/backup_before_restore_$backup_timestamp"
        $mkdir_cmd "$h2_backup_dir" 2>/dev/null || sudo mkdir -p "$h2_backup_dir"
        
        # Backup existing files - check both naming patterns
        if $file_check_cmd "$h2_data_dir/keycloak.mv.db" 2>/dev/null; then
            if backup_existing_h2_file "$h2_data_dir/keycloak.mv.db" "$h2_backup_dir/keycloak.mv.db" "keycloak.mv.db"; then
                log_info "Backed up existing keycloak.mv.db"
            else
                log_warn "Failed to backup existing keycloak.mv.db"
            fi
        fi
        if $file_check_cmd "$h2_data_dir/keycloakdb.mv.db" 2>/dev/null; then
            if backup_existing_h2_file "$h2_data_dir/keycloakdb.mv.db" "$h2_backup_dir/keycloakdb.mv.db" "keycloakdb.mv.db"; then
                log_info "Backed up existing keycloakdb.mv.db"
            else
                log_warn "Failed to backup existing keycloakdb.mv.db"
            fi
        fi
        if $file_check_cmd "$h2_data_dir/keycloak.h2.db" 2>/dev/null; then
            if backup_existing_h2_file "$h2_data_dir/keycloak.h2.db" "$h2_backup_dir/keycloak.h2.db" "keycloak.h2.db"; then
                log_info "Backed up existing keycloak.h2.db"
            else
                log_warn "Failed to backup existing keycloak.h2.db"
            fi
        fi
        if $file_check_cmd "$h2_data_dir/keycloak.trace.db" 2>/dev/null; then
            backup_existing_h2_file "$h2_data_dir/keycloak.trace.db" "$h2_backup_dir/keycloak.trace.db" "keycloak.trace.db" 2>/dev/null || true
        fi
        if $file_check_cmd "$h2_data_dir/keycloakdb.trace.db" 2>/dev/null; then
            backup_existing_h2_file "$h2_data_dir/keycloakdb.trace.db" "$h2_backup_dir/keycloakdb.trace.db" "keycloakdb.trace.db" 2>/dev/null || true
        fi
    else
        # Create directory if it doesn't exist (may need sudo)
        if [ "$use_sudo" = true ]; then
            sudo mkdir -p "$h2_data_dir"
            sudo chown "$keycloak_user:$keycloak_user" "$h2_data_dir"
        else
            mkdir -p "$h2_data_dir"
        fi
    fi
    
    # Restore H2 database files - handle both naming patterns
    # Pattern 1: keycloak.mv.db (standard)
    if [ -f "$keycloak_backup_dir/keycloak.mv.db" ]; then
        if restore_h2_file "$keycloak_backup_dir/keycloak.mv.db" "$h2_data_dir/keycloak.mv.db" "keycloak.mv.db"; then
            log_info "Restored: keycloak.mv.db"
        else
            log_error "Failed to restore keycloak.mv.db"
        fi
    fi
    
    # Pattern 2: keycloakdb.mv.db (alternative naming)
    if [ -f "$keycloak_backup_dir/keycloakdb.mv.db" ]; then
        if restore_h2_file "$keycloak_backup_dir/keycloakdb.mv.db" "$h2_data_dir/keycloakdb.mv.db" "keycloakdb.mv.db"; then
            log_info "Restored: keycloakdb.mv.db"
        else
            log_error "Failed to restore keycloakdb.mv.db"
        fi
    fi
    
    # Pattern 3: Any other *.mv.db files
    for mv_file in "$keycloak_backup_dir"/*.mv.db; do
        if [ -f "$mv_file" ]; then
            local basename_mv=$(basename "$mv_file")
            if [ "$basename_mv" != "keycloak.mv.db" ] && [ "$basename_mv" != "keycloakdb.mv.db" ]; then
                if restore_h2_file "$mv_file" "$h2_data_dir/$basename_mv" "$basename_mv"; then
                    log_info "Restored: $basename_mv"
                else
                    log_error "Failed to restore $basename_mv"
                fi
            fi
        fi
    done
    
    # Older format: keycloak.h2.db
    if [ -f "$keycloak_backup_dir/keycloak.h2.db" ]; then
        if restore_h2_file "$keycloak_backup_dir/keycloak.h2.db" "$h2_data_dir/keycloak.h2.db" "keycloak.h2.db"; then
            log_info "Restored: keycloak.h2.db"
        else
            log_error "Failed to restore keycloak.h2.db"
        fi
    fi
    
    # Trace files - both patterns
    if [ -f "$keycloak_backup_dir/keycloak.trace.db" ]; then
        if restore_h2_file "$keycloak_backup_dir/keycloak.trace.db" "$h2_data_dir/keycloak.trace.db" "keycloak.trace.db"; then
            log_info "Restored: keycloak.trace.db"
        else
            log_error "Failed to restore keycloak.trace.db"
        fi
    fi
    
    if [ -f "$keycloak_backup_dir/keycloakdb.trace.db" ]; then
        if restore_h2_file "$keycloak_backup_dir/keycloakdb.trace.db" "$h2_data_dir/keycloakdb.trace.db" "keycloakdb.trace.db"; then
            log_info "Restored: keycloakdb.trace.db"
        else
            log_error "Failed to restore keycloakdb.trace.db"
        fi
    fi
    
    # Set proper permissions (Keycloak usually runs as keycloak user)
    # chown requires root privileges, so always use sudo
    local chown_user="${KEYCLOAK_USER:-}"
    if [ -z "$chown_user" ]; then
        # Try to detect Keycloak user from service file
        local service_file="${KEYCLOAK_SERVICE_FILE:-/etc/systemd/system/keycloak.service}"
        if [ -f "$service_file" ]; then
            chown_user=$(grep "^User=" "$service_file" | cut -d'=' -f2 | tr -d '"' | tr -d "'" || echo "")
        fi
    fi
    
    if [ -n "$chown_user" ]; then
        sudo chown -R "$chown_user:$chown_user" "$h2_data_dir"
        log_info "Set ownership to $chown_user for H2 files"
    else
        log_warn "Could not determine Keycloak user for setting ownership"
        log_warn "Files may have incorrect permissions"
    fi
    
    # Ensure proper file permissions (readable/writable by owner, readable by group)
    # chmod may need sudo if files are owned by keycloak user
    if [ "$use_sudo" = true ] || [ -n "$chown_user" ]; then
        sudo chmod 640 "$h2_data_dir"/*.db 2>/dev/null || true
    else
        chmod 640 "$h2_data_dir"/*.db 2>/dev/null || true
    fi
    
    # Restart Keycloak if it was running
    if [ "${KEYCLOAK_WAS_RUNNING:-false}" == "true" ]; then
        log_info "Starting Keycloak service..."
        sudo systemctl start keycloak
    fi
    
    log_info "Keycloak H2 database restore completed"
}

# Function to restore MySQL database
restore_mysql() {
    local db_host=$1
    local db_port=$2
    local db_name=$3
    local db_user=$4
    local db_password=$5
    local backup_file=$6
    
    log_info "Restoring MySQL database: $db_name"
    
    # Check if database exists (more reliable method using INFORMATION_SCHEMA)
    local db_exists=$(MYSQL_PWD="$db_password" mysql \
        --host="$db_host" \
        --port="$db_port" \
        --user="$db_user" \
        -e "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = '$db_name';" 2>/dev/null | tail -n 1 | tr -d ' ' || echo "0")
    
    # Drop and recreate database (optional, based on RECREATE_DB flag)
    if [ "$RECREATE_DB" == "true" ]; then
        log_warn "Dropping and recreating database: $db_name"
        MYSQL_PWD="$db_password" mysql \
            --host="$db_host" \
            --port="$db_port" \
            --user="$db_user" \
            -e "DROP DATABASE IF EXISTS \`$db_name\`; CREATE DATABASE \`$db_name\`;"
        if [ $? -ne 0 ]; then
            log_error "Failed to drop and recreate database"
            exit 1
        fi
    elif [ "$db_exists" -eq 0 ]; then
        # Database doesn't exist, create it
        log_info "Database '$db_name' does not exist. Creating it..."
        MYSQL_PWD="$db_password" mysql \
            --host="$db_host" \
            --port="$db_port" \
            --user="$db_user" \
            -e "CREATE DATABASE IF NOT EXISTS \`$db_name\`;"
        if [ $? -ne 0 ]; then
            log_error "Failed to create database '$db_name'"
            exit 1
        fi
        log_info "Database '$db_name' created successfully"
    else
        log_info "Database '$db_name' already exists"
    fi
    
    # Restore database
    log_info "Restoring data to database '$db_name'..."
    MYSQL_PWD="$db_password" mysql \
        --host="$db_host" \
        --port="$db_port" \
        --user="$db_user" \
        "$db_name" < "$backup_file"
    
    if [ $? -eq 0 ]; then
        log_info "MySQL database restored successfully"
    else
        log_error "MySQL restore failed"
        exit 1
    fi
}

# Function to restore PostgreSQL database
restore_postgresql() {
    local db_host=$1
    local db_port=$2
    local db_name=$3
    local db_user=$4
    local db_password=$5
    local backup_file=$6
    
    log_info "Restoring PostgreSQL database: $db_name"
    
    # Check if database exists
    local db_exists=$(PGPASSWORD="$db_password" psql \
        --host="$db_host" \
        --port="$db_port" \
        --username="$db_user" \
        --dbname="postgres" \
        -tAc "SELECT 1 FROM pg_database WHERE datname='$db_name';" 2>/dev/null | tr -d ' ' || echo "0")
    
    # Drop and recreate database (optional, based on RECREATE_DB flag)
    if [ "$RECREATE_DB" == "true" ]; then
        log_warn "Dropping and recreating database: $db_name"
        
        # Terminate existing connections
        PGPASSWORD="$db_password" psql \
            --host="$db_host" \
            --port="$db_port" \
            --username="$db_user" \
            --dbname="postgres" \
            -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$db_name' AND pid <> pg_backend_pid();" \
            > /dev/null 2>&1
        
        # Drop and create database
        PGPASSWORD="$db_password" psql \
            --host="$db_host" \
            --port="$db_port" \
            --username="$db_user" \
            --dbname="postgres" \
            -c "DROP DATABASE IF EXISTS \"$db_name\"; CREATE DATABASE \"$db_name\";"
        if [ $? -ne 0 ]; then
            log_error "Failed to drop and recreate database"
            exit 1
        fi
    elif [ "$db_exists" != "1" ]; then
        # Database doesn't exist, create it
        log_info "Database '$db_name' does not exist. Creating it..."
        PGPASSWORD="$db_password" psql \
            --host="$db_host" \
            --port="$db_port" \
            --username="$db_user" \
            --dbname="postgres" \
            -c "CREATE DATABASE \"$db_name\";"
        if [ $? -ne 0 ]; then
            log_error "Failed to create database '$db_name'"
            exit 1
        fi
        log_info "Database '$db_name' created successfully"
    else
        log_info "Database '$db_name' already exists"
    fi
    
    # Restore database
    log_info "Restoring data to database '$db_name'..."
    PGPASSWORD="$db_password" pg_restore \
        --host="$db_host" \
        --port="$db_port" \
        --username="$db_user" \
        --dbname="$db_name" \
        --clean \
        --if-exists \
        --no-owner \
        --no-acl \
        "$backup_file"
    
    if [ $? -eq 0 ]; then
        log_info "PostgreSQL database restored successfully"
    else
        log_error "PostgreSQL restore failed"
        exit 1
    fi
}

# Function to restore database (router function)
restore_database() {
    local db_type=$1
    local db_host=$2
    local db_port=$3
    local db_name=$4
    local db_user=$5
    local db_password=$6
    local backup_file=$7
    
    if [ ! -f "$backup_file" ]; then
        log_error "Backup file not found: $backup_file"
        exit 1
    fi
    
    case "$db_type" in
        mysql|MYSQL)
            restore_mysql "$db_host" "$db_port" "$db_name" "$db_user" "$db_password" "$backup_file"
            ;;
        postgresql|POSTGRESQL|postgres|POSTGRES)
            restore_postgresql "$db_host" "$db_port" "$db_name" "$db_user" "$db_password" "$backup_file"
            ;;
        *)
            log_error "Unsupported database type: $db_type"
            exit 1
            ;;
    esac
}

# Function to cleanup
cleanup() {
    log_info "Cleaning up temporary files..."
    
    if [ "$KEEP_DOWNLOADED_BACKUP" != "true" ] && [ -d "$RESTORE_DIR" ]; then
        rm -rf "$RESTORE_DIR"
        log_info "Removed temporary restore directory"
    else
        log_info "Temporary files kept in: $RESTORE_DIR"
    fi
}

# Function to send notification (optional)
send_notification() {
    if [ -n "$SLACK_WEBHOOK_URL" ]; then
        local message="✅ DAM Restore completed successfully\nDatabase: $DB_NAME\nKeycloak DB: $KEYCLOAK_DB_NAME\nSource: $S3_BACKUP_PATH"
        
        curl -X POST "$SLACK_WEBHOOK_URL" \
            -H 'Content-Type: application/json' \
            -d "{\"text\":\"$message\"}" \
            > /dev/null 2>&1
    fi
}

# Main execution
main() {
    log_info "=== DAM Restore Script Started ==="
    log_info "Timestamp: $(date)"
    
    # Check required environment variables
    check_required_vars
    
    # Confirm restore operation
    confirm_restore
    
    # Download backup from S3
    download_from_s3
    
    # Extract backup
    extract_backup
    
    # Restore application database
    log_info "Starting application database restore..."
    if [ "$DB_TYPE" == "mysql" ] || [ "$DB_TYPE" == "MYSQL" ]; then
        restore_database "$DB_TYPE" "$DB_HOST" "$DB_PORT" "$DB_NAME" "$DB_USER" "$DB_PASSWORD" \
            "$BACKUP_CONTENT_DIR/dam_database.sql"
    else
        restore_database "$DB_TYPE" "$DB_HOST" "$DB_PORT" "$DB_NAME" "$DB_USER" "$DB_PASSWORD" \
            "$BACKUP_CONTENT_DIR/dam_database.dump"
    fi
    
    # Restore Keycloak database
    log_info "Starting Keycloak restore..."
    
    # Determine backup type from metadata or file presence
    if [ -d "$BACKUP_CONTENT_DIR/keycloak_h2" ]; then
        # H2 backup detected
        restore_keycloak_h2
    elif [ -f "$BACKUP_CONTENT_DIR/keycloak_database.sql" ] || [ -f "$BACKUP_CONTENT_DIR/keycloak_database.dump" ]; then
        # External database backup
        if [ -z "$KEYCLOAK_DB_TYPE" ] || [ -z "$KEYCLOAK_DB_HOST" ] || [ -z "$KEYCLOAK_DB_NAME" ]; then
            log_error "Keycloak database credentials required for restore"
            log_error "Please set KEYCLOAK_DB_TYPE, KEYCLOAK_DB_HOST, KEYCLOAK_DB_PORT, KEYCLOAK_DB_NAME, KEYCLOAK_DB_USER, KEYCLOAK_DB_PASSWORD"
            exit 1
        fi
        
        log_info "Restoring Keycloak external database..."
        if [ "$KEYCLOAK_DB_TYPE" == "mysql" ] || [ "$KEYCLOAK_DB_TYPE" == "MYSQL" ]; then
            restore_database "$KEYCLOAK_DB_TYPE" "$KEYCLOAK_DB_HOST" "$KEYCLOAK_DB_PORT" \
                "$KEYCLOAK_DB_NAME" "$KEYCLOAK_DB_USER" "$KEYCLOAK_DB_PASSWORD" \
                "$BACKUP_CONTENT_DIR/keycloak_database.sql"
        else
            restore_database "$KEYCLOAK_DB_TYPE" "$KEYCLOAK_DB_HOST" "$KEYCLOAK_DB_PORT" \
                "$KEYCLOAK_DB_NAME" "$KEYCLOAK_DB_USER" "$KEYCLOAK_DB_PASSWORD" \
                "$BACKUP_CONTENT_DIR/keycloak_database.dump"
        fi
    else
        log_warn "No Keycloak backup files found in backup"
        log_warn "This is normal if Keycloak wasn't backed up (e.g., H2 files don't exist yet)"
        log_warn "Skipping Keycloak restore (restore will continue with application database only)"
    fi
    
    # Cleanup
    cleanup
    
    # Send notification
    send_notification
    
    log_info "=== Restore completed successfully ==="
}

# Trap errors and cleanup
trap 'log_error "Restore failed. Cleaning up..."; cleanup; exit 1' ERR

# Run main function
main

