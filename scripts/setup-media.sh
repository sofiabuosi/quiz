#!/usr/bin/env bash
set -euo pipefail

PLUGIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSET_DIR="$PLUGIN_DIR/src/quizamigos/assets"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
mkdir -p "$ASSET_DIR"

if ! command -v yt-dlp >/dev/null 2>&1; then
  echo "Erro: yt-dlp não encontrado. Instale com: pipx install 'yt-dlp[default]'" >&2
  exit 1
fi
if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "Erro: ffmpeg não encontrado. Instale com: sudo apt install ffmpeg" >&2
  exit 1
fi

# se a música de fundo anterior não estiver mais disponível ou não houver permissão
# para redistribuí-la no grupo, execute com music_url='url_autorizada' ./scripts/setup-media.sh
MUSIC_URL="${MUSIC_URL:-https://youtu.be/apxEJpyJ7fo}"

download_audio() {
  local filename="$1"
  local url="$2"
  printf "\nBaixando e convertendo: %s\n" "$filename"
  yt-dlp --no-playlist --extract-audio --audio-format wav --audio-quality 0 \
    --output "$TMP_DIR/${filename}.%(ext)s" "$url"
  if [[ ! -s "$TMP_DIR/${filename}.wav" ]]; then
    echo "Erro: yt-dlp não gerou $filename.wav" >&2
    exit 1
  fi
  cp "$TMP_DIR/${filename}.wav" "$ASSET_DIR/${filename}.wav"
}

download_audio "curtain" "https://youtu.be/Ebu6ROdNY5w"
download_audio "reveal-to-score" "https://youtube.com/shorts/JoAnglKUbiE"
download_audio "scoreboard" "https://youtu.be/zsrWXMfEogY"
download_audio "music" "$MUSIC_URL"

printf "\nBaixando arte principal do Quiz...\n"
curl --fail --location --retry 3 \
  "https://img.magnific.com/vetores-gratis/ilustracao-de-um-jogo-vibrante_1308-181062.jpg?semt=ais_hybrid&w=740&q=80" \
  --output "$ASSET_DIR/quiz-cover.jpg"

printf "\nBaixando capa de Olivia Rodrigo (uso somente no quiz dela)...\n"
curl --fail --location --retry 3 \
  "https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c4/Olivia_Rodrigo_2.jpeg/500px-Olivia_Rodrigo_2.jpeg" \
  --output "$ASSET_DIR/olivia-rodrigo.jpg"

file "$ASSET_DIR"/*
printf "\nMídia instalada em: %s\n" "$ASSET_DIR"
echo "Agora compile o plugin: cd '$PLUGIN_DIR' && ant clean jar test"
echo "Os WAVs e a foto serão incorporados automaticamente ao JAR."
