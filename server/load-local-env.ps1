$envPath = Join-Path $PSScriptRoot '.env'
$allowed = @(
    'PORT', 'DB_URL', 'DB_USER', 'DB_PASSWORD',
    'MINI_APP_ID', 'MINI_APP_SECRET', 'OFFICIAL_APP_ID', 'OFFICIAL_APP_SECRET',
    'WECHAT_TOKEN', 'WECHAT_ENCODING_AES_KEY', 'BIND_SECRET',
    'ADMIN_BOOTSTRAP_USER', 'ADMIN_BOOTSTRAP_PASSWORD',
    'MINI_REMINDER_TEMPLATE_ID', 'MINI_REMINDER_TITLE_KEY',
    'MINI_REMINDER_TIME_KEY', 'MINI_REMINDER_CONTENT_KEY', 'MINI_REMINDER_PAGE'
)
if (-not (Test-Path -LiteralPath $envPath)) {
    throw "Local database settings are missing: $envPath"
}

foreach ($line in [System.IO.File]::ReadAllLines($envPath)) {
    if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$' -and $allowed -contains $Matches[1]) {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}

foreach ($name in @('DB_URL', 'DB_USER', 'DB_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
        throw "Missing required setting: $name"
    }
}
