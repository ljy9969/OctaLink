@echo off
REM OctaLink 트리거 디스패처 — 작업 스케줄러가 15분마다 호출. 로그: ops/state/dispatch.log
"C:\Program Files\nodejs\node.exe" "%~dp0dispatch.mjs" >> "%~dp0..\state\dispatch.log" 2>&1
