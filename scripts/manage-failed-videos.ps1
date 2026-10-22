# =====================================================================
# Script para auditar y reintentar descargas fallidas en YTDetect
# =====================================================================
[CmdletBinding()]
param (
    [Parameter(Position = 0, Mandatory = $true)]
    [ValidateSet("list", "retry-all", "retry", "help")]
    [string]$Action,

    [Parameter(Position = 1)]
    [string]$VideoId,

    [string]$BaseUrl = $(if ($env:API_BASE_URL) { $env:API_BASE_URL } else { "http://localhost:8080" }),
    [string]$ApiKey  = $(if ($env:API_KEY) { $env:API_KEY } else { "default-secret-key" })
)

$headers = @{
    "X-API-Key" = $ApiKey
    "Accept"    = "application/json"
}

switch ($Action.ToLower()) {
    "list" {
        Write-Host "==========================================================" -ForegroundColor Cyan
        Write-Host "         VIDEOS CON DESCARGA FALLIDA (STATUS: FAILED)     " -ForegroundColor Cyan
        Write-Host "==========================================================" -ForegroundColor Cyan
        Write-Host "Consultando: $BaseUrl/api/v1/youtube/videos?status=FAILED" -ForegroundColor Gray

        try {
            $response = Invoke-RestMethod -Uri "$BaseUrl/api/v1/youtube/videos?status=FAILED&size=100" -Headers $headers -Method Get
            $videos = $response.content

            if (-not $videos -or $videos.Count -eq 0) {
                Write-Host "`nNo hay videos caídos o en estado FAILED actualmente." -ForegroundColor Green
                return
            }

            Write-Host "`nTotal de videos con fallas: $($response.totalElements)`n" -ForegroundColor Yellow

            $formatted = $videos | ForEach-Object {
                [PSCustomObject]@{
                    "VideoId"      = $_.videoId
                    "Canal"        = if ($_.channelName) { $_.channelName.Substring(0, [Math]::Min($_.channelName.Length, 20)) } else { $_.channelId }
                    "Intentos"     = $_.attemptCount
                    "Cod_Error"    = $_.lastErrorCode
                    "Motivo_Fallo" = if ($_.lastErrorMessage) { $_.lastErrorMessage.Substring(0, [Math]::Min($_.lastErrorMessage.Length, 45)) + "..." } else { "N/A" }
                    "Ultimo_Fallo" = $_.updatedAt
                }
            }

            $formatted | Format-Table -AutoSize
        }
        catch {
            Write-Host "Error al conectar con la API: $($_.Exception.Message)" -ForegroundColor Red
        }
    }

    "retry-all" {
        Write-Host "Reintentando todos los videos en estado FAILED..." -ForegroundColor Cyan
        try {
            $response = Invoke-RestMethod -Uri "$BaseUrl/api/v1/youtube/videos/retry-failed" -Headers $headers -Method Post
            Write-Host "`nSe re-encolaron exitosamente $($response.retriedCount) videos." -ForegroundColor Green
            if ($response.videoIds -and $response.videoIds.Count -gt 0) {
                Write-Host "IDs reintentados:" -ForegroundColor Gray
                $response.videoIds | ForEach-Object { Write-Host " - $_" -ForegroundColor DarkCyan }
                Write-Host "`nEl worker los procesará automáticamente en su siguiente ciclo." -ForegroundColor Yellow
            }
        }
        catch {
            Write-Host "Error al ejecutar el reintento masivo: $($_.Exception.Message)" -ForegroundColor Red
        }
    }

    "retry" {
        if (-not $VideoId) {
            Write-Host "Error: Debes especificar el videoId a reintentar." -ForegroundColor Red
            Write-Host "Uso: .\manage-failed-videos.ps1 retry <videoId>" -ForegroundColor Gray
            return
        }

        Write-Host "Reintentando video: $VideoId..." -ForegroundColor Cyan
        try {
            $response = Invoke-RestMethod -Uri "$BaseUrl/api/v1/youtube/videos/$VideoId/retry" -Headers $headers -Method Post
            Write-Host "`nVideo $($response.videoId) cambiado a estado $($response.status) con $($response.attemptCount) intentos." -ForegroundColor Green
            Write-Host "El worker lo procesará en breve." -ForegroundColor Yellow
        }
        catch {
            Write-Host "Error al reintentar el video ${VideoId}: $($_.Exception.Message)" -ForegroundColor Red
        }
    }

    "help" {
        Write-Host "Uso de manage-failed-videos.ps1:" -ForegroundColor Cyan
        Write-Host "  .\manage-failed-videos.ps1 list                -> Muestra la lista de videos fallidos con motivo y reintentos."
        Write-Host "  .\manage-failed-videos.ps1 retry-all           -> Re-encola todos los videos fallidos a QUEUED_DOWNLOAD."
        Write-Host "  .\manage-failed-videos.ps1 retry <videoId>     -> Re-encola un video específico."
    }
}
