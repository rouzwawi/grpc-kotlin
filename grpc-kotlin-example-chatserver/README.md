# grpc-kotlin-example-chatserver

A simple command line chat server written using both bidirectional & server streaming gRPC.

Build the parent project. From the repo root, run

```sh
./mvnw clean package
```

Start the server

```sh
java -jar grpc-kotlin-example-chatserver/target/grpc-kotlin-example-chatserver.jar server
```

From another shell, start a bidirectional streaming client

```sh
java -jar grpc-kotlin-example-chatserver/target/grpc-kotlin-example-chatserver.jar client
```

From the third shell, start a server streaming client

```sh
java -jar grpc-kotlin-example-chatserver/target/grpc-kotlin-example-chatserver.jar clientSS
```

---

Big thanks to [Björn Hegerfors](https://github.com/Bj0rnen) and [Emilio Del Tessandoro](https://github.com/emmmile)
for putting together this example!
