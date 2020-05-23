on error resume next         '这行使脚本不提示错误 想查看错误请删除这一行
set shell=CreateObject("WScript.shell")
shell.run "javaw -jar emailPrompt.jar"