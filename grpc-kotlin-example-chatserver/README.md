# grpc-kotlin-example-chatserver

A simple command line chat server written using bidirectional gRPC.

Build the parent project. From the repo root, run

```sh
mvn package
```

Start the server

```sh
java -jar grpc-kotlin-example-chatserver/target/grpc-kotlin-example-chatserver.jar server
```

From another shell, start a client

```sh
java -jar grpc-kotlin-example-chatserver/target/grpc-kotlin-example-chatserver.jar client
```

---

Big thanks to [Björn Hegerfors](https://github.com/Bj0rnen) and [Emilio Del Tessandoro](https://github.com/emmmile)
for putting together this example!
