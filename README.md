# Lighty SDN-R Lite

**Lighty SDN-R Lite** is an optimized, plain-Java SDN-R controller powered by [lighty.io](https://lighty.io/) (version 24) and core [OpenDaylight](https://www.opendaylight.org/) components. 

Originally built as a standalone PNF-Registration application, it has been refactored into a generalized, extensible SDN-R platform. It operates entirely without an OSGi container, providing a lightweight, high-performance execution environment for RAN configuration and OAM infrastructure management.

---

## 🚀 Key Features and Modules

The controller wires together independent Lighty modules to provide a seamless SDN-R environment:

### 1. PNF Registration Module
Handles O-RAN PNF (Physical Network Function) registration events. 
- Listens to Kafka for VES `PnfRegistration` events via the `StrimziKafkaPNFRegVESMsgConsumer`.
- Upon receiving a valid event, it automatically issues a RESTCONF `PUT` request to mount the device into the local NETCONF topology.

### 2. YangSchema Endpoint (`/yang-schema/`)
A pure Java servlet that replaces the ODL-native `/yang-schema/` OSGi bundle.
- **Purpose**: Exposes raw YANG source text over HTTP so client dashboards (like the `darpan` editor) can render capability editors dynamically.
- **Endpoint**: `GET /yang-schema/{moduleName}?revision={revision}&node={nodeId}`
- **How it works**:
  - If a `nodeId` is provided, the servlet resolves the schema from the device's mounted `SchemaContext`.
  - If no node is provided, it falls back to the controller's global schema repository.
- **Troubleshooting**:
  - **404 Module not found**: Ensure the module is bundled in the classpath and listed in `lightyControllerConfig.json`.
  - **404 No mounted device**: Ensure the NETCONF session to the node is successfully established in `topology-netconf`.

### 3. IETF NETCONF 2013 Upgrade
The application utilizes an upgraded NETCONF implementation (`iosmcn/netconf`) that migrates from the legacy 2011 IETF Netconf revision to the 2013 revision, ensuring compatibility with modern O-RAN elements. The controller actively loads the `ietf-netconf-acm` (Access Control Model) as required by the 2013 schemas.

---

## 📂 Architecture and Integration

The controller lifecycle is fully managed within `Main.java` located at:
`applications/lighty-sdnr-lite/lighty-sdnr-lite-app/src/main/java/org/iosmcn/lighty/sdnr/lite/app/Main.java`

Modules are wired in the following sequence:
1. `LightyController` (MD-SAL datastore)
2. `CommunityRestConf` (Jetty Server and REST endpoints)
3. `LightyYangSchemaModule` (YANG schema servlet)
4. `NetconfTopologyPlugin` (NETCONF southbound)
5. `PnfModule` (PNF Kafka Listener)

*(Note: The upcoming `data-provider` migration for persistence will also plug directly into this module flow).*

---

## 🛠️ Build and Install

To build and run the `lighty-sdnr-lite` artifacts locally:

1. **Install JDK**: Ensure [JDK 21](https://adoptium.net/temurin/releases/) is installed.
2. **Install Maven**: Ensure you have Maven 3.9.5 or later.
3. **Setup Maven**: Use the standard ODL [settings.xml](https://github.com/opendaylight/odlparent/blob/master/settings.xml) in your `~/.m2/` directory.
4. **Compile**: Run the following command from the root directory:
   ```bash
   mvn clean install -DskipTests
   ```

## 🐳 Docker Deployment

A lightweight Alpine-based Java runtime Docker image is generated during the build.
To package the Docker image locally, navigate to the applications directory and run:

```bash
cd applications/lighty-sdnr-lite
mvn clean package -P docker -DskipTests
```

This will build the `iosmcn-sdnrlite:latest` image containing the compiled jar, `lightyControllerConfig.json`, and the startup entrypoint scripts.