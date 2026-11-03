param([string]$OutputDirectory = (Join-Path $PSScriptRoot 'config-tls'))
$ErrorActionPreference = 'Stop'
$keytoolPath = (Get-Command keytool -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
$target = [IO.Path]::GetFullPath($OutputDirectory)
foreach ($path in @('private/ca.p12', 'server/server.p12', 'trust/truststore.p12')) {
    if (Test-Path -LiteralPath (Join-Path $target $path)) { throw 'TLS files already exist; refusing to overwrite keys.' }
}
$savedKeyPassword = $env:CONFIG_TLS_KEY_STORE_PASSWORD
$savedTrustPassword = $env:CONFIG_TLS_TRUST_STORE_PASSWORD
function Invoke-Keytool([string[]]$Arguments) {
    $start = New-Object Diagnostics.ProcessStartInfo
    $start.FileName = $keytoolPath
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    # No shell is used. Passwords are read by keytool from the process environment.
    $start.Arguments = ($Arguments | ForEach-Object { '"' + $_.Replace('"', '\"') + '"' }) -join ' '
    $process = [Diagnostics.Process]::Start($start)
    try {
        $outTask = $process.StandardOutput.ReadToEndAsync()
        $errTask = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $null = $outTask.GetAwaiter().GetResult()
        $diagnostic = $errTask.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { throw ('keytool failed: ' + $diagnostic) }
    } finally { $process.Dispose() }
}
try {
    if (-not $env:CONFIG_TLS_KEY_STORE_PASSWORD) {
        $secure = Read-Host 'Server/CA keystore password (same as CONFIG_TLS_KEY_STORE_PASSWORD in .env)' -AsSecureString
        $env:CONFIG_TLS_KEY_STORE_PASSWORD = ([PSCredential]::new('tls', $secure)).GetNetworkCredential().Password
    }
    if (-not $env:CONFIG_TLS_TRUST_STORE_PASSWORD) {
        $secure = Read-Host 'Truststore password (same as CONFIG_TLS_TRUST_STORE_PASSWORD in .env)' -AsSecureString
        $env:CONFIG_TLS_TRUST_STORE_PASSWORD = ([PSCredential]::new('tls', $secure)).GetNetworkCredential().Password
    }
    if ($env:CONFIG_TLS_KEY_STORE_PASSWORD.Length -lt 12 -or $env:CONFIG_TLS_TRUST_STORE_PASSWORD.Length -lt 12) {
        throw 'Use TLS store passwords of at least 12 characters.'
    }
    foreach ($directory in @('private', 'server', 'trust')) {
        New-Item -ItemType Directory -Force (Join-Path $target $directory) | Out-Null
    }
    $ca = Join-Path $target 'private/ca.p12'
    $server = Join-Path $target 'server/server.p12'
    $cert = Join-Path $target 'trust/ca.crt'
    $csr = Join-Path $target 'private/server.csr'
    $signed = Join-Path $target 'private/server.crt'
    $trust = Join-Path $target 'trust/truststore.p12'
    Invoke-Keytool -Arguments @('-genkeypair','-alias','config-ca','-keyalg','RSA','-keysize','3072','-dname','CN=Hyeja Config Development CA','-validity','3650','-ext','bc:c','-ext','ku=keyCertSign,cRLSign','-keystore',$ca,'-storetype','PKCS12','-storepass:env','CONFIG_TLS_KEY_STORE_PASSWORD')
    Invoke-Keytool -Arguments @('-exportcert','-rfc','-alias','config-ca','-keystore',$ca,'-storepass:env','CONFIG_TLS_KEY_STORE_PASSWORD','-file',$cert)
    Invoke-Keytool -Arguments @('-genkeypair','-alias','config-service','-keyalg','RSA','-keysize','3072','-dname','CN=config-service','-validity','365','-keystore',$server,'-storetype','PKCS12','-storepass:env','CONFIG_TLS_KEY_STORE_PASSWORD')
    Invoke-Keytool -Arguments @('-certreq','-alias','config-service','-keystore',$server,'-storepass:env','CONFIG_TLS_KEY_STORE_PASSWORD','-file',$csr)
    Invoke-Keytool -Arguments @('-gencert','-alias','config-ca','-keystore',$ca,'-storepass:env','CONFIG_TLS_KEY_STORE_PASSWORD','-infile',$csr,'-outfile',$signed,'-rfc','-validity','365','-ext','SAN=dns:config-service,dns:localhost,ip:127.0.0.1','-ext','EKU=serverAuth','-ext','KU=digitalSignature,keyEncipherment')
    Invoke-Keytool -Arguments @('-importcert','-noprompt','-alias','config-ca','-file',$cert,'-keystore',$server,'-storepass:env','CONFIG_TLS_KEY_STORE_PASSWORD')
    Invoke-Keytool -Arguments @('-importcert','-noprompt','-alias','config-service','-file',$signed,'-keystore',$server,'-storepass:env','CONFIG_TLS_KEY_STORE_PASSWORD')
    Invoke-Keytool -Arguments @('-importcert','-noprompt','-alias','config-ca','-file',$cert,'-keystore',$trust,'-storetype','PKCS12','-storepass:env','CONFIG_TLS_TRUST_STORE_PASSWORD')
    Write-Output "Development TLS material generated in $target"
    Write-Output 'Import trust/ca.crt as a CA in Postman. Keep SSL certificate verification enabled.'
    Write-Output 'Never commit these files or distribute private/ca.p12 or server/server.p12 to clients.'
} finally {
    $env:CONFIG_TLS_KEY_STORE_PASSWORD = $savedKeyPassword
    $env:CONFIG_TLS_TRUST_STORE_PASSWORD = $savedTrustPassword
}
