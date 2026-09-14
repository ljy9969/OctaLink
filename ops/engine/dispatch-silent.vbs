Rem OctaLink dispatcher - launch dispatch.cmd hidden (no console window)
Dim sh, here
here = Left(WScript.ScriptFullName, InStrRev(WScript.ScriptFullName, "\"))
Set sh = CreateObject("WScript.Shell")
sh.Run """" & here & "dispatch.cmd""", 0, False
