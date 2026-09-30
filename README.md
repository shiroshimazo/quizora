# Quizora

JavaFX quiz application built with NetBeans and Ant. Requires JDK 26, JavaFX SDK
26.0.2. Data is stored in a local SQLite file, `database/quizora.db`, which the app
creates on first run. No database server is needed.

## Run on Windows

On a fresh checkout, see [database setup](database/README.md) to create the demo
accounts after the first run.

From the project directory, run:

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1
```

The script builds the application and launches it with the required JavaFX modules.
It uses the bundled NetBeans JDK and Ant when `JAVA_HOME` and `ANT_HOME` are unset.
JavaFX defaults to the SDK path in `nbproject/project.properties`. Override these
locations with `-JavaHome`, `-AntHome`, and `-JavaFxHome`, or their corresponding
environment variables (`JAVA_HOME`, `ANT_HOME`, `JAVAFX_HOME`).

To build without opening a window, add `-BuildOnly`. In NetBeans, open this project
and choose Run Project (F6).

Keep `src/Resources/fonts`, `src/Resources/icons`, and `src/Resources/images` in
the project: the login screen and dashboards require these bundled assets.

Interaction icons use the bundled Hugeicons Stroke Rounded font, loaded directly
by JavaFX without a network connection. See [icon dependency details](src/Resources/icons/hugeicons/README.md).

Password recovery uses Gmail SMTP. See [SMTP setup](SMTP_SETUP.md) for local
credentials, expiry behavior, and automated recovery checks.
