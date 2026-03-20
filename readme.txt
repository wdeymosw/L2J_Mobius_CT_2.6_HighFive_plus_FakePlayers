Запуск в VS Code
Требования
BellSoft Liberica JDK 25 (или другой JDK 25+)
VS Code
Расширение Extension Pack for Java в VS Code
Настройка
Открой папку L2J_Mobius_CT_2.6_HighFive в VS Code
Создай папку .vscode в корне проекта
Создай файл .vscode/settings.json:

{
    "java.project.sourcePaths": ["java"],
    "java.project.outputPath": "bin",
    "java.project.referencedLibraries": ["dist/libs/**/*.jar"]
}
Создай файл .vscode/launch.json:

{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "GameServer",
            "request": "launch",
            "mainClass": "org.l2jmobius.gameserver.GameServer",
            "projectName": "L2J_Mobius_CT_2.6_HighFive",
            "classPaths": ["bin", "dist/libs/**/*.jar"],
            "vmArgs": "-server -Dfile.encoding=UTF-8 -Djava.util.logging.manager=org.l2jmobius.log.ServerLogManager -Dorg.slf4j.simpleLogger.log.com.zaxxer.hikari=warn -XX:+UseZGC -Xmx4g -Xms2g",
            "cwd": "${workspaceFolder}/dist/game"
        }
    ]
}
Запуск
Нажми F5 — VS Code скомпилирует проект и запустит сервер.
