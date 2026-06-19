# Building and Running lighty-sdnr-lite

This guide covers the steps from compiling the Java code to building the Docker image and running it within the `iosmcn-oam` environment.

## 1. Build the Docker Image

The project is configured to build the Docker image automatically during the Maven `package`/`install` phase via the `docker-maven-plugin`.

Navigate to the root directory of your project and run:

```bash
mvn clean install
```
*Alternatively, you can just run this inside the `applications/lighty-sdnr-lite/lighty-sdnr-lite-docker` directory.*

This command will:
1. Compile the code and package the application jar.
2. Unzip the packaged distribution.
3. Use the `Dockerfile` to create the Docker image named `iosmcn-sdnrlite:latest`.

## 2. Set Up the Docker Network

If the `iosmcn-oam` network doesn't already exist, you'll need to create it before starting the container:

```bash
docker network create iosmcn-oam
```
*(If you have a `smo` network as well, you can create that too: `docker network create smo`)*

## 3. Run the Docker Container

You can now start the container. Based on standard usage, we will connect it to the required networks, map port `8888`, and start it in detached mode (`-d`).

```bash
docker run -d \
  --name lighty-sdnr-lite \
  --network iosmcn-oam \
  -p 8888:8888 \
  iosmcn-sdnrlite:latest
```

> [!TIP]
> If you also need to connect to an `smo` network (as referenced in previous configs), you can attach the running container to a second network using:  
> `docker network connect smo lighty-sdnr-lite`

## 4. Enable Debug Logging (Optional)

If you need to troubleshoot, you can pass JVM arguments to enable debug logging when running the container:

```bash
docker run -d \
  --name lighty-sdnr-lite \
  --network iosmcn-oam \
  -p 8888:8888 \
  -e JAVA_OPTS="-Dlog4j2.debug=true" \
  iosmcn-sdnrlite:latest
```

## 5. View Logs

To check that the container started correctly and watch the application logs in real-time, use:

```bash
docker logs -f lighty-sdnr-lite
```
