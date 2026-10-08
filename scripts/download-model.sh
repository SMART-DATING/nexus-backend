#!/bin/sh
set -eu
directory="${1:-$(dirname "$0")/../models/rubert-tiny2}"
mkdir -p "$directory"
base='https://huggingface.co/iki13/rubert-tiny2-onnx/resolve/5e6f7f225ff590999fc409e3e747c382d79d94b5'
download() {
  source="$1"; name="$2"; checksum="$3"
  if test -f "$directory/$name" && echo "$checksum  $directory/$name" | sha256sum -c - >/dev/null 2>&1; then return; fi
  curl --fail --location --retry 3 "$base/$source" --output "$directory/$name.partial"
  echo "$checksum  $directory/$name.partial" | sha256sum -c -
  mv "$directory/$name.partial" "$directory/$name"
}
download model_optimized.onnx model.onnx 39dcd6f039c9dfa269795259cec99ca85c75a2002ba72efe186b8068a8d7ad03
download vocab.txt vocab.txt f056a69b097422652053bf87565c35543e5d81540ca4b7dddd28de4157a969e0
echo 'Local semantic model verified.'
