# tcp-echo-server

A TCP Echo Server ([RFC862](https://tools.ietf.org/html/rfc862)) built with
Vert.x for training purposes.

# Pre-requisites

For building the code:

* JDK 8

For running the server:

* JRE 8

# Building the code

The following command will build the project and generate a runnable jar:

```bash
$ ./gradlew build
```

# Running the server

**ATTENTION:** One must specify the path to a configuration file via the
`PATH_TO_CONFIG` environment variable when starting `tcp-echo-server`. An
example configuration file can be found [here](./example/config.json).

**ATTENTION:** RFC862 specifies that a TCP echo server listens on port `7`, and
the [example configuration file]() tells  `tcp-echo-server` to bind to this
port by default. However, one cannot bind to ports ≤ `1024` without adequate
permissions, so you should probably pick an alternative port in your
configuration file.

To run the server run:

```bash
$ PATH_TO_CONFIG=/path/to/config.json java -jar tcp-echo-server.jar
14:51:52.688 [INFO ] tcp echo server is listening at 0.0.0.0:2007
14:51:54.576 [DEBUG] open connections: 0
14:51:54.951 [DEBUG] connection from 192.168.1.10:52304 is now open
14:51:56.569 [DEBUG] open connections: 1
14:51:58.566 [DEBUG] open connections: 1
14:52:00.568 [DEBUG] open connections: 1
14:52:02.567 [DEBUG] open connections: 1
14:52:04.566 [DEBUG] open connections: 1
14:52:06.569 [DEBUG] open connections: 1
14:52:06.780 [DEBUG] connection from 192.168.1.10:52304 is now closed
14:52:08.571 [DEBUG] open connections: 0
14:52:10.568 [DEBUG] open connections: 0
```

## Configuration

The configuration file accepts three properties:

* `debug` — whether to enable connection logging.
* `host` — the interface which to bind to.
* `port` — the port which to bind to.

# Interacting with the server

To interact with the server one can use `telnet`:

```bash
$ telnet 192.168.1.90 2007
Trying 192.168.1.90...
Connected to 192.168.1.90.
Escape character is '^]'.
HELLO
HELLO
BYE
BYE
^]

telnet> quit
Connection closed.
```

As one sends messages to the server one will see them being echoed back.

<a id="environment-variables"></a>

# Environment Variables

| Name             | Default                            | Description                         |
|------------------|------------------------------------|-------------------------------------|
| `PATH_TO_CONFIG` | `/etc/tcp-echo-server/config.json` | The path to the configuration file. |
