param(
    [string]$GatewayUrl = 'http://localhost:8000',
    [string]$ZipkinUrl = 'http://localhost:9411',
    [string]$PrometheusUrl = 'http://localhost:9090',
    [string]$PolicyId,
    [Security.SecureString]$AccessToken
)
$ErrorActionPreference = 'Stop'

function Send-ObservationRequest {
    <# .SYNOPSIS Sends a request without printing authentication credentials or response bodies. #>
    param([string]$Url, [hashtable]$Headers = @{})
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -Headers $Headers -TimeoutSec 15
        return @{ Status = [int]$response.StatusCode; TraceId = [string]$response.Headers['X-Trace-Id'] }
    } catch {
        if (-not $_.Exception.Response) { throw }
        $response = $_.Exception.Response
        return @{ Status = [int]$response.StatusCode; TraceId = [string]$response.Headers['X-Trace-Id'] }
    }
}

function Wait-Observation {
    <# .SYNOPSIS Waits for asynchronous span export or Prometheus scrapes within a bounded timeout. #>
    param([scriptblock]$Check, [string]$Name)
    $deadline = (Get-Date).AddSeconds(60)
    do {
        try { if (& $Check) { Write-Host "PASS: $Name"; return } } catch { }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    throw "Timed out: $Name"
}

Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & docker compose exec -T prometheus promtool check config /etc/prometheus/prometheus.yml
    if ($LASTEXITCODE -ne 0) { throw 'Prometheus configuration or recording rules are invalid.' }
    $since = (Get-Date).ToUniversalTime().ToString('o')
    $headers = @{}
    $expectedServices = @('gateway-service', 'policy-service')
    $path = '/api/policies/housing'
    if ($PolicyId -or $AccessToken) {
        if (-not $PolicyId -or -not $AccessToken) {
            throw 'For the three-service member flow, supply both PolicyId and AccessToken.'
        }
        $headers.Authorization = 'Bearer ' + (New-Object Net.NetworkCredential('', $AccessToken)).Password
        $path = '/api/policies/' + [Uri]::EscapeDataString($PolicyId)
        $expectedServices += 'user-service'
    }
    $response = Send-ObservationRequest -Url ($GatewayUrl + $path) -Headers $headers
    if ($response.Status -ne 200) { throw "Expected HTTP 200; received $($response.Status). Check seeded policy/member data." }
    if ($response.TraceId -notmatch '^[0-9a-f]{16}([0-9a-f]{16})?$') { throw 'Missing X-Trace-Id response header.' }
    $traceId = $response.TraceId
    Write-Host "Trace ID: $traceId"
    Wait-Observation -Name 'Zipkin spans for the actual service flow' -Check {
        $spans = Invoke-RestMethod -Uri "$ZipkinUrl/api/v2/trace/$traceId" -TimeoutSec 10
        $names = @($spans | ForEach-Object { $_.localEndpoint.serviceName } | Select-Object -Unique)
        return @($expectedServices | Where-Object { $_ -notin $names }).Count -eq 0
    }
    foreach ($service in $expectedServices) {
        $lines = & docker compose logs --no-color --since $since $service 2>&1
        if ($LASTEXITCODE -ne 0) { throw "Unable to read logs for $service" }
        if (-not ($lines | Select-String -SimpleMatch $traceId)) { throw "No correlated log found for $service" }
        & docker compose exec -T $service test -s "/app/logs/$service.log"
        if ($LASTEXITCODE -ne 0) { throw "Missing persistent file log for $service" }
        Write-Host "PASS: $service console correlation and file logging"
    }
    $denied = Send-ObservationRequest -Url "$GatewayUrl/actuator/prometheus"
    if ($denied.Status -ne 401) { throw 'Unauthenticated metrics must return HTTP 401.' }
    $failure = Send-ObservationRequest -Url "$GatewayUrl/api/members/me"
    if ($failure.Status -ne 401 -or -not $failure.TraceId) { throw 'Rejected business request must have HTTP 401 and a trace header.' }
    Wait-Observation -Name 'All five authenticated Prometheus targets UP' -Check {
        $query = [Uri]::EscapeDataString('up{job="hyeja-services"}')
        $result = Invoke-RestMethod -Uri "$PrometheusUrl/api/v1/query?query=$query" -TimeoutSec 10
        $values = @($result.data.result)
        return $values.Count -eq 5 -and @($values | Where-Object { [double]$_.value[1] -ne 1 }).Count -eq 0
    }
    Wait-Observation -Name 'HTTP count and latency histogram samples' -Check {
        $countQuery = [Uri]::EscapeDataString('sum(http_server_requests_seconds_count{application="gateway-service",status="200"})')
        $bucketQuery = [Uri]::EscapeDataString('sum(http_server_requests_seconds_bucket{application="gateway-service"})')
        $count = Invoke-RestMethod -Uri "$PrometheusUrl/api/v1/query?query=$countQuery" -TimeoutSec 10
        $buckets = Invoke-RestMethod -Uri "$PrometheusUrl/api/v1/query?query=$bucketQuery" -TimeoutSec 10
        return $count.data.result.Count -gt 0 -and [double]$count.data.result[0].value[1] -gt 0 -and $buckets.data.result.Count -gt 0
    }
    Write-Host "PASS: observability checks. Zipkin: $ZipkinUrl/zipkin/traces/$traceId"
    Write-Host 'PromQL: hyeja:http_requests:rate5m, hyeja:http_latency_seconds:p95, hyeja:http_5xx:ratio5m'
} finally {
    if ($headers) { $headers.Clear() }
    Pop-Location
}
