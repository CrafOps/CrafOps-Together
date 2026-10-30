# Tiles the dev game windows on the primary monitor so they never overlap:
#   Dev = top-left, Dev2 = top-right, Dev3 = bottom-left; bottom-right stays free for the IDE/terminal.
# Windows are found by their "[Dev2] Minecraft..." title, which the mod sets in the dev environment.
# Usage: powershell -File scripts/arrange-windows.ps1
Add-Type -AssemblyName System.Windows.Forms
Add-Type @'
using System; using System.Runtime.InteropServices;
public static class Win {
    [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr h, int x, int y, int w, int hgt, bool repaint);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
}
'@

$screen = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
$w = [int]($screen.Width / 2); $h = [int]($screen.Height / 2)
$slots = @{
    'Dev'  = @($screen.X, $screen.Y)
    'Dev2' = @(($screen.X + $w), $screen.Y)
    'Dev3' = @($screen.X, ($screen.Y + $h))
}

$found = 0
foreach ($proc in Get-Process java, javaw -ErrorAction SilentlyContinue) {
    if ($proc.MainWindowTitle -notmatch '^\[(Dev\d?)\]') { continue }
    $name = $Matches[1]
    if (-not $slots.ContainsKey($name)) { continue }
    [Win]::ShowWindow($proc.MainWindowHandle, 9) | Out-Null   # SW_RESTORE, in case it is maximised
    [Win]::MoveWindow($proc.MainWindowHandle, $slots[$name][0], $slots[$name][1], $w, $h, $true) | Out-Null
    Write-Output "$name -> $($slots[$name][0]),$($slots[$name][1]) ${w}x${h}"
    $found++
}
if ($found -eq 0) { Write-Output 'No dev game windows found (titles should start with [Dev], [Dev2] or [Dev3]).' }
