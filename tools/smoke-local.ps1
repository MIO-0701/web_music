param([string]$Base = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'

function JsonBody($value) { return ($value | ConvertTo-Json -Depth 10 -Compress) }
function Call($method, $path, $body = $null, $headers = @{}) {
    $params = @{ Uri = "$Base$path"; Method = $method; Headers = $headers; TimeoutSec = 30 }
    if ($null -ne $body) { $params.ContentType = 'application/json'; $params.Body = JsonBody $body }
    return Invoke-RestMethod @params
}
function Assert($condition, $message) { if (-not $condition) { throw $message }; Write-Output "PASS $message" }
function Status($method, $path, $headers = @{}, $body = $null) {
    $params = @{ Uri = "$Base$path"; Method = $method; Headers = $headers; TimeoutSec = 30; SkipHttpErrorCheck = $true }
    if ($null -ne $body) { $params.ContentType = 'application/json'; $params.Body = JsonBody $body }
    return [int](Invoke-WebRequest @params).StatusCode
}

$settings = @{}
Get-Content (Join-Path $PSScriptRoot '..\.env') | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $settings[$matches[1]] = $matches[2] }
}
$admin = Call 'POST' '/api/auth/login' @{ username = $settings['ADMIN_USERNAME']; password = $settings['ADMIN_PASSWORD'] }
$adminHeaders = @{ Authorization = "Bearer $($admin.token)" }
Assert ($admin.user.role -eq 'ADMIN') 'administrator login'

$catalog = Call 'GET' '/api/songs?page=1&size=1'
Assert ($catalog.total -eq 461) 'only playable songs are public'
$songId = $catalog.items[0].id
Assert ((Status 'GET' "/api/songs/$songId/audio") -eq 401) 'audio requires login'
Assert ((Status 'GET' "/api/songs/$songId/cover") -eq 200) 'cover is available'

$username = 'smoke_' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$user = Call 'POST' '/api/auth/register' @{ username = $username; password = 'SmokeTest2026!'; displayName = '接口测试用户' }
$userHeaders = @{ Authorization = "Bearer $($user.token)" }
Assert ($user.user.role -eq 'USER') 'registration creates USER'
Assert ((Status 'GET' '/api/admin/dashboard' $userHeaders) -eq 403) 'USER cannot access admin dashboard'
Assert ((Call 'GET' '/api/auth/me' $null $userHeaders).username -eq $username) 'authenticated profile'

$audio = Invoke-WebRequest -Uri "$Base/api/songs/$songId/audio" -Headers ($userHeaders + @{ Range = 'bytes=0-31' }) -SkipHttpErrorCheck -TimeoutSec 30
Assert ($audio.StatusCode -eq 206 -and $audio.RawContentLength -eq 32) 'Range audio returns 32 bytes'
Call 'POST' "/api/me/favorites/$songId" $null $userHeaders | Out-Null
Assert ((Call 'GET' '/api/me/favorites' $null $userHeaders).total -eq 1) 'favorite is listed'

$playlist = Call 'POST' '/api/me/playlists' @{ name = '接口测试歌单' } $userHeaders
Call 'POST' "/api/me/playlists/$($playlist.id)/songs/$songId" $null $userHeaders | Out-Null
Assert ((Call 'GET' "/api/me/playlists/$($playlist.id)" $null $userHeaders).songs.Count -eq 1) 'playlist song is listed'
Call 'POST' '/api/me/play-events' @{ songId = $songId; secondsPlayed = 10; completed = $false } $userHeaders | Out-Null
Assert ((Call 'GET' '/api/me/history' $null $userHeaders).total -eq 1) 'play history is recorded'

Call 'POST' "/api/songs/$songId/comments" @{ content = '本机接口冒烟验证' } $userHeaders | Out-Null
$pending = Call 'GET' '/api/admin/comments?status=PENDING' $null $adminHeaders
Assert ($pending.total -ge 1) 'comment awaits moderation'
$commentId = $pending.items[0].id
Call 'PATCH' "/api/admin/comments/$commentId/status?value=APPROVED" $null $adminHeaders | Out-Null
Assert ((Call 'GET' "/api/songs/$songId/comments").total -ge 1) 'approved comment is public'

$rebuilt = Call 'POST' '/api/admin/recommendations/rebuild' $null $adminHeaders
Assert ($rebuilt.usersProcessed -ge 1) 'recommendations rebuild'
Assert ((Call 'GET' '/api/me/recommendations?algorithm=TAG' $null $userHeaders).Count -gt 0) 'tag recommendations'
Assert ((Call 'GET' '/api/me/recommendations?algorithm=ITEM_CF' $null $userHeaders).Count -gt 0) 'item-CF recommendations'

$expires = (Get-Date).AddDays(30).ToString('yyyy-MM-ddTHH:mm:ss')
Call 'PUT' "/api/admin/users/$($user.user.id)/membership" @{ expiresAt = $expires } $adminHeaders | Out-Null
Assert ($null -ne (Call 'GET' '/api/auth/me' $null $userHeaders).membershipExpiresAt) 'membership is applied'
if ([string]::IsNullOrWhiteSpace($settings['DEEPSEEK_API_KEY'])) {
    Assert ((Status 'POST' '/api/me/ai/chat' $userHeaders @{ conversationId = $null; message = '找中文歌' }) -eq 503) 'AI missing-key response is 503'
} else {
    $chat = Call 'POST' '/api/me/ai/chat' @{ conversationId = $null; message = '找中文歌' } $userHeaders
    Assert ($chat.conversationId -gt 0 -and $chat.songs.Count -gt 0) 'AI returns local songs'
}
Assert ((Call 'GET' '/api/me/ai/conversations' $null $userHeaders).Count -ge 1) 'AI conversation is recorded'

$results = Call 'POST' '/api/admin/recommendations/evaluate' $null $adminHeaders
Assert ($results.Count -eq 2) 'offline experiment ran both algorithms'
Assert ((Call 'GET' '/api/admin/dashboard' $null $adminHeaders).songs -eq 461) 'dashboard reports catalog'

Call 'DELETE' "/api/me/playlists/$($playlist.id)" $null $userHeaders | Out-Null
Call 'DELETE' "/api/me/favorites/$songId" $null $userHeaders | Out-Null
Write-Output "Smoke account: $username; song ID: $songId"
