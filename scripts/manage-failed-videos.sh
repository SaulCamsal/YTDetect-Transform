#!/usr/bin/env bash
# =====================================================================
# Script para auditar y reintentar descargas fallidas en YTDetect (Linux/macOS)
# =====================================================================

ACTION="${1:-help}"
VIDEO_ID="$2"
BASE_URL="${API_BASE_URL:-http://localhost:8080}"
API_KEY="${API_KEY:-default-secret-key}"

case "$ACTION" in
    "list")
        echo "=========================================================="
        echo "         VIDEOS CON DESCARGA FALLIDA (STATUS: FAILED)     "
        echo "=========================================================="
        echo "Consultando: $BASE_URL/api/v1/youtube/videos?status=FAILED"
        echo ""
        curl -s -H "X-API-Key: $API_KEY" -H "Accept: application/json" \
            "$BASE_URL/api/v1/youtube/videos?status=FAILED&size=100" | \
            python3 -c '
import sys, json
try:
    data = json.load(sys.stdin)
    videos = data.get("content", [])
    if not videos:
        print("No hay videos caídos o en estado FAILED actualmente.")
        sys.exit(0)
    print(f"Total de videos con fallas: {data.get(\"totalElements\", len(videos))}\n")
    print(f"{ \"VIDEO ID\":<14} | { \"CANAL\":<20} | { \"INTENTOS\":<8} | { \"ERROR\":<15} | { \"MOTIVO\":<40} | { \"FECHA\" }")
    print("-" * 115)
    for v in videos:
        vid = v.get("videoId", "")
        chan = (v.get("channelName") or v.get("channelId") or "")[:20]
        att = str(v.get("attemptCount", 0))
        err = (v.get("lastErrorCode") or "")[:15]
        msg = (v.get("lastErrorMessage") or "")[:38]
        if len(v.get("lastErrorMessage") or "") > 38: msg += ".."
        date = (v.get("updatedAt") or "")[:19]
        print(f"{vid:<14} | {chan:<20} | {att:<8} | {err:<15} | {msg:<40} | {date}")
except Exception as e:
    print(f"Error procesando respuesta: {e}")
'
        ;;

    "retry-all")
        echo "Reintentando todos los videos en estado FAILED..."
        curl -s -X POST -H "X-API-Key: $API_KEY" -H "Accept: application/json" \
            "$BASE_URL/api/v1/youtube/videos/retry-failed" | \
            python3 -c '
import sys, json
try:
    data = json.load(sys.stdin)
    count = data.get("retriedCount", 0)
    vids = data.get("videoIds", [])
    print(f"\nSe re-encolaron exitosamente {count} videos.")
    if vids:
        print("IDs reintentados:")
        for vid in vids:
            print(f" - {vid}")
        print("\nEl worker los procesará automáticamente en su siguiente ciclo.")
except Exception as e:
    print(f"Error procesando respuesta: {e}")
'
        ;;

    "retry")
        if [ -z "$VIDEO_ID" ]; then
            echo "Error: Debes especificar el videoId a reintentar."
            echo "Uso: ./manage-failed-videos.sh retry <videoId>"
            exit 1
        fi
        echo "Reintentando video: $VIDEO_ID..."
        curl -s -X POST -H "X-API-Key: $API_KEY" -H "Accept: application/json" \
            "$BASE_URL/api/v1/youtube/videos/$VIDEO_ID/retry" | \
            python3 -c '
import sys, json
try:
    data = json.load(sys.stdin)
    if "videoId" in data:
        print(f"\nVideo {data.get(\"videoId\")} cambiado a estado {data.get(\"status\")} con {data.get(\"attemptCount\")} intentos.")
        print("El worker lo procesará en breve.")
    else:
        print(f"Respuesta: {data}")
except Exception as e:
    print(f"Error procesando respuesta: {e}")
'
        ;;

    *)
        echo "Uso de manage-failed-videos.sh:"
        echo "  ./manage-failed-videos.sh list                -> Muestra la lista de videos fallidos con motivo y reintentos."
        echo "  ./manage-failed-videos.sh retry-all           -> Re-encola todos los videos fallidos a QUEUED_DOWNLOAD."
        echo "  ./manage-failed-videos.sh retry <videoId>     -> Re-encola un video específico."
        ;;
esac
