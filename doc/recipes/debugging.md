for debugging the `fz` command you can set the environment variable `FUZION_JAVA_OPTIONS`.

```bash
    export FUZION_JAVA_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:8000
```

Once `fz` is launched and suspendend you can attach the debugger to port 8000.

An example vscode configuration to work with extension "Debugger for Java" is given here:

```json
"launch": {
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Attach jdb",
      "request": "attach",
      "hostName": "localhost",
      "port": "8000",
    },
  ]
}
```
