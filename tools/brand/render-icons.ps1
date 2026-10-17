# Renders the Trixx Education mark and app icon to PNG from the same 96-unit grid used by
# app/assets/brand/*.svg. Offline (GDI+), no extra toolchain. Re-run after any mark change:
#   powershell -ExecutionPolicy Bypass -File tools/brand/render-icons.ps1
Add-Type -AssemblyName System.Drawing

$root   = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)   # repo root
$web    = Join-Path $root 'app\web'
$icons  = Join-Path $web 'icons'
$brand  = Join-Path $root 'app\assets\brand'
New-Item -ItemType Directory -Force -Path $icons, $brand | Out-Null

$navy  = [System.Drawing.ColorTranslator]::FromHtml('#172B4D')
$white = [System.Drawing.ColorTranslator]::FromHtml('#FFFFFF')
$amber = [System.Drawing.ColorTranslator]::FromHtml('#E6A23C')
$teal  = [System.Drawing.ColorTranslator]::FromHtml('#0F9F91')
$lteal = [System.Drawing.ColorTranslator]::FromHtml('#52D4C2')

function New-RoundedPath([double]$x, [double]$y, [double]$w, [double]$h, [double]$r) {
  $p = New-Object System.Drawing.Drawing2D.GraphicsPath
  $d = $r * 2
  $p.AddArc($x, $y, $d, $d, 180, 90)
  $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
  $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
  $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
  $p.CloseFigure()
  return $p
}

function New-Poly([double[][]]$pts) {
  $arr = New-Object 'System.Drawing.PointF[]' $pts.Count
  for ($i = 0; $i -lt $pts.Count; $i++) { $arr[$i] = New-Object System.Drawing.PointF($pts[$i][0], $pts[$i][1]) }
  return $arr
}

# Draws the mark (and layers) inside a 96x96 coordinate space already scaled by the caller.
function Draw-Mark($g) {
  $cap = New-Poly @(@(48,19), @(80,36), @(48,53), @(16,36))
  $g.FillPolygon((New-Object System.Drawing.SolidBrush($white)), $cap)

  $cord = New-Object System.Drawing.Pen($amber, 2.5)
  $cord.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
  $cord.EndCap   = [System.Drawing.Drawing2D.LineCap]::Round
  $g.DrawLine($cord, 48, 36, 72, 36)

  $g.FillEllipse((New-Object System.Drawing.SolidBrush($navy)), 45, 33, 6, 6)
  $g.FillEllipse((New-Object System.Drawing.SolidBrush($amber)), 69.6, 32.6, 6.8, 6.8)

  foreach ($layer in @(@($teal, 45.5), @($lteal, 57.5))) {
    $pen = New-Object System.Drawing.Pen($layer[0], 6)
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap   = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $pts = New-Poly @(@(16, $layer[1]), @(48, $layer[1] + 17), @(80, $layer[1]))
    $g.DrawLines($pen, $pts)
  }
}

# Single-colour silhouette of the mark on transparent, for Android 13 themed
# icons. Android tints this layer itself, so only the alpha matters.
function Draw-MarkMono($g, $color) {
  $brush = New-Object System.Drawing.SolidBrush($color)
  $g.FillPolygon($brush, (New-Poly @(@(48,19), @(80,36), @(48,53), @(16,36))))

  $cord = New-Object System.Drawing.Pen($color, 2.5)
  $cord.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
  $cord.EndCap   = [System.Drawing.Drawing2D.LineCap]::Round
  $g.DrawLine($cord, 48, 36, 72, 36)

  $g.FillEllipse($brush, 69.6, 32.6, 6.8, 6.8)

  foreach ($y in @(45.5, 57.5)) {
    $pen = New-Object System.Drawing.Pen($color, 6)
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap   = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $g.DrawLines($pen, (New-Poly @(@(16, $y), @(48, $y + 17), @(80, $y))))
  }
}

# $mode: 'mark' = rounded navy field, transparent corners
#        'app'  = full-bleed navy, content scaled into the central safe zone
#        'mono' = transparent field, single-colour silhouette
function Render([int]$size, [string]$path, [string]$mode, [double]$contentScale = 1.0) {
  $bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.Clear([System.Drawing.Color]::Transparent)

  $s = $size / 96.0
  $g.ScaleTransform($s, $s)

  if ($mode -eq 'mark') {
    $g.FillPath((New-Object System.Drawing.SolidBrush($navy)), (New-RoundedPath 0 0 96 96 24))
  } elseif ($mode -ne 'mono') {
    $g.FillRectangle((New-Object System.Drawing.SolidBrush($navy)), 0, 0, 96, 96)
  }

  if ($contentScale -ne 1.0) {
    $off = (96 - (96 * $contentScale)) / 2.0
    $g.TranslateTransform($off, $off)
    $g.ScaleTransform($contentScale, $contentScale)
  }
  if ($mode -eq 'mono') { Draw-MarkMono $g $navy } else { Draw-Mark $g }

  $g.Dispose()
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
  Write-Output "wrote $path ($size)"
}

Render 48   (Join-Path $web 'favicon.png')            'mark'
Render 192  (Join-Path $icons 'Icon-192.png')          'mark'
Render 512  (Join-Path $icons 'Icon-512.png')          'mark'
Render 192  (Join-Path $icons 'Icon-maskable-192.png') 'app' 0.62
Render 512  (Join-Path $icons 'Icon-maskable-512.png') 'app' 0.62
Render 1024 (Join-Path $brand 'trixx-app-icon-1024.png')     'app' 0.66
Render 1024 (Join-Path $brand 'trixx-mark-1024.png')         'mark'
Render 1024 (Join-Path $brand 'trixx-mark-mono-1024.png')    'mono' 0.66
Write-Output 'done'
