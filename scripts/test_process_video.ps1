<#
.SYNOPSIS
    Script de prueba para descargar un video de YouTube, normalizar/fragmentar en chunks de 5 min
    y preparar la estructura de rutas S3 correspondiente a MonitorLatino.

.DESCRIPTION
    Emula el pipeline completo de yt-worker:
    1. Obtiene metadatos con yt-dlp.
    2. Descarga el audio original en MP3.
    3. Normaliza a 16kHz mono 64kbps y divide en chunks de 300s con ffmpeg.
    4. Calcula la fecha/hora local y construye la ruta S3 exacta para el bucket.

.PARAMETER VideoUrl
    URL del video de YouTube a procesar. Por defecto: https://youtu.be/UwQx93tpnk4

.PARAMETER S3Folder
    Carpeta de estación asignada en Reporting/S3. Por defecto: CHART/YT/MILENIO

.PARAMETER OutputDir
    Directorio temporal de trabajo. Por defecto: ./test_output/video_test
#>

param (
    [string]$VideoUrl = "https://youtu.be/UwQx93tpnk4",
    [string]$S3Folder = "CHART/YT/MILENIO",
    [string]$OutputDir = ".\test_output\video_test",
    [string]$Bucket = "monitorlatino"
)

if ($PSVersionTable.PSVersion.Major -ge 7) {
    $PSNativeCommandUseErrorActionPreference = $false
}
$ErrorActionPreference = "Continue"

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "   TEST DE PROCESAMIENTO DE VIDEO YOUTUBE -> CHUNKS S3   " -ForegroundColor Cyan
Write-Host "========================================================`n" -ForegroundColor Cyan

# 1. Validar herramientas requeridas
Write-Host "[1/4] Verificando dependencias locales (yt-dlp y ffmpeg)..." -ForegroundColor Yellow
$ytDlp = Get-Command yt-dlp -ErrorAction SilentlyContinue
$ffmpeg = Get-Command ffmpeg -ErrorAction SilentlyContinue

if (-not $ytDlp) {
    Write-Error "yt-dlp no está instalado o no se encuentra en el PATH."
}
if (-not $ffmpeg) {
    Write-Error "ffmpeg no está instalado o no se encuentra en el PATH."
}

Write-Host " -> yt-dlp: $($ytDlp.Source)" -ForegroundColor Green
Write-Host " -> ffmpeg: $($ffmpeg.Source)" -ForegroundColor Green

# 2. Consultar información del video
Write-Host "`n[2/4] Consultando metadatos del video..." -ForegroundColor Yellow
$cleanUrl = $VideoUrl.Split('?')[0]
$meta = & yt-dlp --print "%(id)s|%(title)s|%(duration)s|%(channel)s|%(channel_id)s|%(upload_date)s|%(timestamp)s" $cleanUrl
$metaParts = $meta.Split('|')

$videoId     = $metaParts[0]
$title       = $metaParts[1]
$durationSec = [int]$metaParts[2]
$channelName = $metaParts[3]
$channelId   = $metaParts[4]
$uploadDate  = $metaParts[5]
$timestamp   = [long]$metaParts[6]

Write-Host " -> Video ID:    $videoId" -ForegroundColor White
Write-Host " -> Título:      $title" -ForegroundColor White
Write-Host " -> Canal:       $channelName ($channelId)" -ForegroundColor White
Write-Host " -> Duración:    $durationSec segundos ($([math]::Round($durationSec / 60, 2)) minutos)" -ForegroundColor White

# 3. Descarga del audio completo
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
}

$rawAudioPath = Join-Path $OutputDir "full_audio.mp3"
Write-Host "`n[3/4] Descargando audio original con yt-dlp..." -ForegroundColor Yellow
& yt-dlp -f bestaudio -x --audio-format mp3 --no-playlist -o $rawAudioPath $cleanUrl | Out-Null

if (-not (Test-Path $rawAudioPath)) {
    Write-Error "Error: No se pudo generar el archivo de audio $rawAudioPath"
}
$rawSize = (Get-Item $rawAudioPath).Length
Write-Host " -> Audio descargado: $rawAudioPath ($([math]::Round($rawSize / 1MB, 2)) MB)" -ForegroundColor Green

# 4. Segmentación y normalización a MP3 16kHz mono 64kbps (300 seg)
$chunksDir = Join-Path $OutputDir "chunks"
if (-not (Test-Path $chunksDir)) {
    New-Item -ItemType Directory -Force -Path $chunksDir | Out-Null
}

Write-Host "`n[4/4] Normalizando y dividiendo en fragmentos de 300s con ffmpeg..." -ForegroundColor Yellow
$chunkPattern = Join-Path $chunksDir "chunk_%04d.mp3"

& ffmpeg -nostats -loglevel error -y -i $rawAudioPath -f segment -segment_time 300 -c:a libmp3lame -ac 1 -ar 16000 -b:a 64k -reset_timestamps 1 $chunkPattern

$chunks = Get-ChildItem -Path $chunksDir -Filter "chunk_*.mp3" | Sort-Object Name
Write-Host " -> Total de chunks generados: $($chunks.Count)" -ForegroundColor Green

# 5. Cálculo de timestamp local México y ruta en S3
$localAiredAt = [DateTimeOffset]::FromUnixTimeSeconds($timestamp).ToOffset([TimeSpan]::FromHours(-6)).DateTime
$cleanFolder = $S3Folder.Trim("/").Trim("\")

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "          ESTRUCTURA DE SUBIDA PREPARADA PARA S3        " -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

for ($i = 0; $i -lt $chunks.Count; $i++) {
    $chunkFile = $chunks[$i]
    $chunkTime = $localAiredAt.AddSeconds($i * 300)
    $dayFolder = $chunkTime.ToString("yyyyMMdd")
    $fileName  = $chunkTime.ToString("yyyyMMdd_HHmmss") + ".mp3"
    $s3Key     = "$cleanFolder/$dayFolder/$fileName"
    $s3Uri     = "s3://$Bucket/$s3Key"
    $sizeKb    = [math]::Round($chunkFile.Length / 1KB, 1)

    Write-Host "`nChunk #$($i):" -ForegroundColor Yellow
    Write-Host "  Archivo local:  $($chunkFile.FullName)" -ForegroundColor White
    Write-Host "  Tamaño:         $sizeKb KB" -ForegroundColor White
    Write-Host "  Hora emitida:   $($chunkTime.ToString('yyyy-MM-dd HH:mm:ss')) (America/Mexico_City)" -ForegroundColor White
    Write-Host "  S3 Key:         $s3Key" -ForegroundColor Green
    Write-Host "  S3 URI:         $s3Uri" -ForegroundColor Cyan
}

# 6. Sincronización con Base de Datos SQL Server (MonitorTranscription)
$envFile = Join-Path $PSScriptRoot "..\.env"
if (Test-Path $envFile) {
    Write-Host "`n[5/5] Registrando video y fragmentos en la base de datos MonitorTranscription..." -ForegroundColor Yellow
    $envLines = Get-Content $envFile
    $dbUrl = ($envLines | Where-Object { $_ -match "^SPRING_DATASOURCE_URL=(.*)$" } | ForEach-Object { $matches[1].Trim() })
    $dbUser = ($envLines | Where-Object { $_ -match "^SPRING_DATASOURCE_USERNAME=(.*)$" } | ForEach-Object { $matches[1].Trim() })
    $dbPass = ($envLines | Where-Object { $_ -match "^SPRING_DATASOURCE_PASSWORD=(.*)$" } | ForEach-Object { $matches[1].Trim() })

    if ($dbUrl -and $dbUser -and $dbPass) {
        try {
            if ($dbUrl -match "jdbc:sqlserver://([^:;]+)(?::(\d+))?;databaseName=([^;]+)") {
                $serverHost = $matches[1]
                $serverPort = if ($matches[2]) { $matches[2] } else { "1433" }
                $dbName = $matches[3]
                $connStr = "Server=$serverHost,$serverPort;Database=$dbName;User Id=$dbUser;Password=$dbPass;Encrypt=False;TrustServerCertificate=True;Timeout=10;"
                $conn = New-Object System.Data.SqlClient.SqlConnection($connStr)
                $conn.Open()

                $escapedTitle = $title.Replace("'", "''")
                $sql = @"
MERGE youtube.youtube_video AS target
USING (VALUES (
    '$videoId',
    '$channelId',
    N'$escapedTitle',
    '$($localAiredAt.ToString('yyyy-MM-dd HH:mm:ss'))',
    $durationSec,
    'NONE',
    'AUDIO_READY',
    'MANUAL',
    '$Bucket',
    '$cleanFolder',
    $($chunks.Count),
    0
)) AS src (video_id, channel_id, title, published_at, duration_seconds, live_status, status, discovered_via, s3_bucket, s3_prefix, chunk_count, attempt_count)
ON target.video_id = src.video_id
WHEN MATCHED THEN
    UPDATE SET 
        status = src.status,
        chunk_count = src.chunk_count,
        updated_at = SYSUTCDATETIME()
WHEN NOT MATCHED THEN
    INSERT (video_id, channel_id, title, published_at, duration_seconds, live_status, status, discovered_via, s3_bucket, s3_prefix, chunk_count, attempt_count, created_at, version)
    VALUES (src.video_id, src.channel_id, src.title, src.published_at, src.duration_seconds, src.live_status, src.status, src.discovered_via, src.s3_bucket, src.s3_prefix, src.chunk_count, src.attempt_count, SYSUTCDATETIME(), 0);
"@
                $cmd = $conn.CreateCommand()
                $cmd.CommandText = $sql
                $cmd.ExecuteNonQuery() | Out-Null

                for ($i = 0; $i -lt $chunks.Count; $i++) {
                    $chunkTime = $localAiredAt.AddSeconds($i * 300)
                    $dayFolder = $chunkTime.ToString("yyyyMMdd")
                    $fileName  = $chunkTime.ToString("yyyyMMdd_HHmmss") + ".mp3"
                    $s3Key     = "$cleanFolder/$dayFolder/$fileName"
                    $chunkSql = @"
MERGE youtube.youtube_audio_chunk AS target
USING (VALUES (
    '$videoId',
    $i,
    CAST($($i * 300).000 AS DECIMAL(12,3)),
    CAST(300.000 AS DECIMAL(12,3)),
    '$($chunkTime.ToString('yyyy-MM-dd HH:mm:ss'))',
    '$s3Key',
    'QUEUED'
)) AS src (video_id, chunk_index, offset_seconds, duration_seconds, aired_at_local, s3_key, transcription_status)
ON target.video_id = src.video_id AND target.chunk_index = src.chunk_index
WHEN MATCHED THEN
    UPDATE SET 
        s3_key = src.s3_key,
        transcription_status = src.transcription_status,
        updated_at = SYSUTCDATETIME()
WHEN NOT MATCHED THEN
    INSERT (video_id, chunk_index, offset_seconds, duration_seconds, aired_at_local, s3_key, transcription_status, created_at)
    VALUES (src.video_id, src.chunk_index, src.offset_seconds, src.duration_seconds, src.aired_at_local, src.s3_key, src.transcription_status, SYSUTCDATETIME());
"@
                    $cmd.CommandText = $chunkSql
                    $cmd.ExecuteNonQuery() | Out-Null
                }

                $conn.Close()
                Write-Host " -> Video y chunks registrados exitosamente en la base de datos (Estado: AUDIO_READY)!" -ForegroundColor Green
            }
        } catch {
            Write-Host " -> No se pudo registrar en la base de datos: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}

Write-Host "`nEl prefijo S3 base es '$cleanFolder/', el cual coincide exactamente" -ForegroundColor Gray
Write-Host "con el 's3_prefix' configurado en MonitorTranscription.transcription.station_config." -ForegroundColor Gray
Write-Host "Al depositar los archivos en S3, AWS iniciará la transcripción automáticamente.`n" -ForegroundColor Gray
