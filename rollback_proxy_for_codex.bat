@echo off
setlocal
chcp 65001 >nul

set "LOG=%~dp0rollback_proxy_for_codex.log"
echo ===== rollback_proxy_for_codex started at %date% %time% ===== > "%LOG%"

echo [1/5] Remove user proxy environment variables...
echo [1/5] Remove user proxy environment variables...>>"%LOG%"
reg delete "HKCU\Environment" /v HTTP_PROXY /f >>"%LOG%" 2>&1
reg delete "HKCU\Environment" /v HTTPS_PROXY /f >>"%LOG%" 2>&1
reg delete "HKCU\Environment" /v ALL_PROXY /f >>"%LOG%" 2>&1
reg delete "HKCU\Environment" /v http_proxy /f >>"%LOG%" 2>&1
reg delete "HKCU\Environment" /v https_proxy /f >>"%LOG%" 2>&1
reg delete "HKCU\Environment" /v all_proxy /f >>"%LOG%" 2>&1
reg delete "HKCU\Environment" /v GIT_HTTP_PROXY /f >>"%LOG%" 2>&1
reg delete "HKCU\Environment" /v GIT_HTTPS_PROXY /f >>"%LOG%" 2>&1
reg delete "HKCU\Environment" /v GIT_ASKPASS /f >>"%LOG%" 2>&1
reg delete "HKCU\Environment" /v git_http_proxy /f >>"%LOG%" 2>&1
reg delete "HKCU\Environment" /v git_https_proxy /f >>"%LOG%" 2>&1
reg delete "HKCU\Environment" /v git_askpass /f >>"%LOG%" 2>&1

echo [2/5] Clear current shell proxy variables...
echo [2/5] Clear current shell proxy variables...>>"%LOG%"
set "HTTP_PROXY="
set "HTTPS_PROXY="
set "ALL_PROXY="
set "http_proxy="
set "https_proxy="
set "all_proxy="
set "GIT_HTTP_PROXY="
set "GIT_HTTPS_PROXY="
set "GIT_ASKPASS="
set "git_http_proxy="
set "git_https_proxy="
set "git_askpass="
set "VSCODE_GIT_ASKPASS_MAIN="
set "VSCODE_GIT_ASKPASS_NODE="
set "VSCODE_GIT_ASKPASS_EXTRA_ARGS="

echo [3/5] Reset WinHTTP proxy...
echo [3/5] Reset WinHTTP proxy...>>"%LOG%"
netsh winhttp reset proxy >>"%LOG%" 2>&1

echo [4/5] Remove Git global proxy and ssl override...
echo [4/5] Remove Git global proxy and ssl override...>>"%LOG%"
git config --global --unset http.proxy >>"%LOG%" 2>&1
git config --global --unset https.proxy >>"%LOG%" 2>&1
git config --global --unset http.sslBackend >>"%LOG%" 2>&1

echo [5/5] Show final state...
echo [5/5] Show final state...>>"%LOG%"
echo ----- user env ----- >>"%LOG%"
reg query "HKCU\Environment" /v HTTP_PROXY >>"%LOG%" 2>&1
reg query "HKCU\Environment" /v HTTPS_PROXY >>"%LOG%" 2>&1
reg query "HKCU\Environment" /v ALL_PROXY >>"%LOG%" 2>&1
reg query "HKCU\Environment" /v GIT_HTTP_PROXY >>"%LOG%" 2>&1
reg query "HKCU\Environment" /v GIT_HTTPS_PROXY >>"%LOG%" 2>&1
reg query "HKCU\Environment" /v GIT_ASKPASS >>"%LOG%" 2>&1
echo ----- winhttp ----- >>"%LOG%"
netsh winhttp show proxy >>"%LOG%" 2>&1
echo ----- git proxy ----- >>"%LOG%"
git config --global --get http.proxy >>"%LOG%" 2>&1
git config --global --get https.proxy >>"%LOG%" 2>&1
git config --global --get http.sslBackend >>"%LOG%" 2>&1

echo ===== finished at %date% %time% ===== >> "%LOG%"
echo.
echo DONE. Restart VS Code/Codex after running this script.
echo Log file:
echo %LOG%
start "" notepad "%LOG%"
pause
