on error resume next         '这行使脚本不提示错误 想查看错误请删除这一行
set shell=CreateObject("WScript.shell")
shell.run "javaw -jar C:\Users\loubth\Documents\自定义启动项\Java开机时发邮件到QQ邮箱\emailPrompt.jar"