param([string]$Directory=(Join-Path $PSScriptRoot '../models/rubert-tiny2'))
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Path $Directory -Force | Out-Null
$base='https://huggingface.co/iki13/rubert-tiny2-onnx/resolve/5e6f7f225ff590999fc409e3e747c382d79d94b5'
$files=@(
 @{Source='model_optimized.onnx';Name='model.onnx';Hash='39dcd6f039c9dfa269795259cec99ca85c75a2002ba72efe186b8068a8d7ad03'},
 @{Source='vocab.txt';Name='vocab.txt';Hash='f056a69b097422652053bf87565c35543e5d81540ca4b7dddd28de4157a969e0'}
)
foreach($file in $files){
 $target=Join-Path $Directory $file.Name
 if((Test-Path -LiteralPath $target) -and ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant() -eq $file.Hash)){continue}
 $partial=$target+'.partial'
 Write-Host ('Downloading '+$file.Name+' (local semantic model)...')
 Invoke-WebRequest ($base+'/'+$file.Source) -OutFile $partial
 if((Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash.ToLowerInvariant() -ne $file.Hash){throw ('Checksum mismatch: '+$file.Name)}
 Move-Item -LiteralPath $partial -Destination $target -Force
}
Write-Host 'RuBERT-tiny2 model and vocabulary verified. Private texts are processed locally.'
