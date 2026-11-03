param([switch]$InfrastructureOnly)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & docker compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Invalid Compose configuration or missing .env values.' }

    # A PATH can contain several Docker installations. Do not assign an array
    # of command sources to ProcessStartInfo.FileName (becomes System.Object[]).
    $dockerCommand = Get-Command docker.exe -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $dockerCommand) {
        $dockerCommand = Get-Command docker -CommandType Application -ErrorAction Stop |
            Select-Object -First 1
    }
    $dockerExecutable = $dockerCommand.Path
    if (-not $dockerExecutable -or -not (Test-Path -LiteralPath $dockerExecutable -PathType Leaf)) {
        throw 'Docker executable could not be resolved. Run: Get-Command docker -All'
    }

    foreach ($service in @('user', 'policy', 'engagement')) {
        # Pass the script through stdin to avoid Windows PowerShell 5.1 native
        # argument quoting stripping quotes from sh -c and SQL -e arguments.
        $check = (Get-Content -Raw (Join-Path $PSScriptRoot 'mariadb/check-access.sh')).Replace("`r`n", "`n")
        # PowerShell 5.1 pipelines append CRLF even when $check contains only LF.
        # Write UTF-8 bytes directly so sh never receives a trailing CR command.
        $start = New-Object System.Diagnostics.ProcessStartInfo
        $start.FileName = $dockerExecutable
        $start.WorkingDirectory = (Get-Location).Path
        $start.Arguments = "compose exec -T mariadb sh -s -- $service"
        $start.UseShellExecute = $false
        $start.RedirectStandardInput = $true
        $process = [System.Diagnostics.Process]::Start($start)
        try {
            $bytes = [Text.Encoding]::UTF8.GetBytes($check.TrimEnd("`r", "`n") + "`n")
            $process.StandardInput.BaseStream.Write($bytes, 0, $bytes.Length)
            $process.StandardInput.Close()
            $process.WaitForExit()
            if ($process.ExitCode -ne 0) { throw "Database connection/isolation check failed: $service" }
        } finally {
            $process.Dispose()
        }
    }

    $pong = & docker compose exec -T redis redis-cli ping
    if ($LASTEXITCODE -ne 0 -or ($pong -join '').Trim() -ne 'PONG') { throw 'Redis ping failed' }
    Write-Host 'PASS: Redis'

    $topic = 'infra-smoke-' + [Guid]::NewGuid().ToString('N')
    $message = [Guid]::NewGuid().ToString('N')
    try {
        & docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create --topic $topic --partitions 1 --replication-factor 1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Kafka topic creation failed' }
        $message | & docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:9092 --topic $topic
        if ($LASTEXITCODE -ne 0) { throw 'Kafka produce failed' }
        $received = & docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic $topic --from-beginning --max-messages 1 --timeout-ms 15000
        if ($LASTEXITCODE -ne 0 -or ($received -join '').Trim() -ne $message) { throw 'Kafka round trip failed' }
        Write-Host 'PASS: Kafka produce/consume'
    } finally {
        & docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --delete --topic $topic | Out-Null
    }

    if ($InfrastructureOnly) { return }

    # Read the running Gateway key without printing it. This is a temporary test JWT,
    # not a login or an account created in User's database.
    $secret = (& docker compose exec -T gateway-service printenv JWT_SECRET) -join ''
    if ($LASTEXITCODE -ne 0 -or [Text.Encoding]::UTF8.GetByteCount($secret) -lt 32) { throw 'Gateway JWT key unavailable' }
    function ConvertTo-Base64Url([byte[]]$Bytes) {
        [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    }
    $header = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes('{"alg":"HS256","typ":"JWT"}'))
    $claims = @{sub='1'; role='USER'; iat=[DateTimeOffset]::UtcNow.ToUnixTimeSeconds(); exp=[DateTimeOffset]::UtcNow.AddMinutes(5).ToUnixTimeSeconds()} | ConvertTo-Json -Compress
    $payload = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($claims))
    $hmac = New-Object Security.Cryptography.HMACSHA256
    $hmac.Key = [Text.Encoding]::UTF8.GetBytes($secret)
    try { $signature = ConvertTo-Base64Url ($hmac.ComputeHash([Text.Encoding]::ASCII.GetBytes("$header.$payload"))) } finally { $hmac.Dispose() }
    $token = "$header.$payload.$signature"

    # Execute inside the Compose network: host port overrides do not affect checks.
    $configReader = (& docker compose exec -T gateway-service printenv SPRING_CLOUD_CONFIG_USERNAME) -join ''
    if ($LASTEXITCODE -ne 0 -or -not $configReader) { throw 'Config reader username unavailable' }
    $configPassword = (& docker compose exec -T gateway-service printenv SPRING_CLOUD_CONFIG_PASSWORD) -join ''
    if ($LASTEXITCODE -ne 0 -or -not $configPassword) { throw 'Config reader password unavailable' }
    $config = & docker compose exec -T gateway-service curl --fail --silent --user ($configReader + ':' + $configPassword) http://config-service:8888/user-service/default
    $configExitCode = $LASTEXITCODE
    Remove-Variable configPassword
    if ($configExitCode -ne 0 -or -not (($config -join '') | ConvertFrom-Json).propertySources) { throw 'Config Git lookup failed' }
    foreach ($service in @('user', 'policy', 'engagement', 'recommendation')) {
        $response = & docker compose exec -T gateway-service curl --fail --silent -H "Authorization: Bearer $token" "http://localhost:8000/_infra/health/$service"
        if ($LASTEXITCODE -ne 0 -or ($response -join '').Trim() -ne "$service-service OK") { throw "Gateway route failed: $service (wait for Eureka registry refresh and retry)" }
        Write-Host "PASS: Gateway -> $service-service"
    }
    $registered = & docker compose exec -T gateway-service curl --fail --silent -H 'Accept: application/json' http://discovery-service:8761/eureka/apps
    if ($LASTEXITCODE -ne 0) { throw 'Eureka registry lookup failed' }
    $registry = (($registered -join '') | ConvertFrom-Json).applications.application
    foreach ($name in @('CONFIG-SERVICE','GATEWAY-SERVICE','USER-SERVICE','POLICY-SERVICE','ENGAGEMENT-SERVICE','RECOMMENDATION-SERVICE')) {
        $app = $registry | Where-Object name -eq $name
        if (-not ($app.instance | Where-Object status -eq 'UP')) { throw "Missing Eureka UP registration: $name" }
    }
    Write-Host 'PASS: Eureka registrations and Config Git lookup'

    $traces = $null
    for ($attempt = 0; $attempt -lt 15; $attempt++) {
        $raw = & docker compose exec -T gateway-service curl --fail --silent 'http://zipkin:9411/api/v2/traces?serviceName=policy-service&limit=1'
        if ($LASTEXITCODE -eq 0) { $traces = ($raw -join '') | ConvertFrom-Json }
        if ($traces.Count -gt 0) { break }
        Start-Sleep -Seconds 2
    }
    if (-not $traces) { throw 'No policy-service traces in Zipkin yet' }
    Write-Host 'PASS: Zipkin received application traces'
} finally {
    Remove-Variable secret,token -ErrorAction SilentlyContinue
    Pop-Location
}
