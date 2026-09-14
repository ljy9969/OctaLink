@echo off
REM OctaLink trigger dispatcher - runs every 15 min via Task Scheduler. Log: ops/state/dispatch.log
"C:\Program Files\nodejs\node.exe" "%~dp0dispatch.mjs" >> "%~dp0..\state\dispatch.log" 2>&1
