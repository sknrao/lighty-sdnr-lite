# YangSchemaServlet — Lighty Migration of ODL `/yang-schema/` Endpoint

## What this does

Replicates the SDNR/ODL custom `GET /yang-schema/{module}?revision=...&node=...`
endpoint in a plain-Java Lighty module with **zero OSGi dependency**.

Your `darpan` dashboard calls this endpoint to fetch raw YANG source text so
it can render the capability configuration editor.  No URL change is needed in
`RestConfRequest.java` — it stays as:

```
GET <lighty-ip>/yang-schema/{module}?node={node}&revision={revision}
```

---

## Files

```
src/main/java/org/iosmcn/sdnr/
├── servlet/
│   └── YangSchemaServlet.java        ← HTTP servlet (pure Java, no OSGi)
└── module/
    └── LightyYangSchemaModule.java   ← AbstractLightyModule that wires everything
```

---

## Integration into your Lighty main class

```java
// ── 1. Start controller and RESTCONF NB as usual ──────────────────────────
LightyController lightyController = new LightyControllerBuilder()
    .from(controllerConfig)
    .build();
lightyController.start().get();

CommunityRestConf communityRestConf = CommunityRestConfBuilder
    .from(RestConfConfigUtils.getDefaultRestConfConfiguration())
    .build();
communityRestConf.start().get();

// ── 2. Register the yang-schema servlet ───────────────────────────────────
LightyYangSchemaModule yangSchemaModule = new LightyYangSchemaModule(
    lightyController.getServices(),
    communityRestConf                  // must be already started
);
yangSchemaModule.start().get();        // registers /yang-schema/* in Jetty

// ── 3. Continue with NETCONF SB, LightyDataProvider, etc. ────────────────
```

Shutdown order (reverse start order):
```java
yangSchemaModule.shutdown().get();
communityRestConf.shutdown().get();
lightyController.shutdown().get();
```

---

## pom.xml dependencies

These are already on your classpath if you have lighty-netconf-sb and
lighty-restconf-nb-community.  Listed here for completeness:

```xml
<!-- Lighty -->
<dependency>
    <groupId>io.lighty.core</groupId>
    <artifactId>lighty-controller</artifactId>
</dependency>
<dependency>
    <groupId>io.lighty.modules</groupId>
    <artifactId>lighty-restconf-nb-community</artifactId>
</dependency>

<!-- yangtools — SchemaRepository, YangTextSchemaSource, SourceIdentifier -->
<dependency>
    <groupId>org.opendaylight.yangtools</groupId>
    <artifactId>yang-model-repo-api</artifactId>
</dependency>
<dependency>
    <groupId>org.opendaylight.yangtools</groupId>
    <artifactId>yang-model-repo-spi</artifactId>
</dependency>

<!-- MD-SAL DOM — DOMSchemaService, DOMMountPointService -->
<dependency>
    <groupId>org.opendaylight.mdsal</groupId>
    <artifactId>mdsal-dom-api</artifactId>
</dependency>

<!-- Network topology binding (for mount path construction) -->
<dependency>
    <groupId>org.opendaylight.mdsal.model</groupId>
    <artifactId>ietf-topology</artifactId>
</dependency>

<!-- Jetty (provided by CommunityRestConf, no version needed here) -->
<dependency>
    <groupId>org.eclipse.jetty</groupId>
    <artifactId>jetty-servlet</artifactId>
    <scope>provided</scope>
</dependency>
```

---

## ODL / yangtools version compatibility

| ODL release         | Lighty version | YangTextSchemaSource base | Action needed          |
|---------------------|----------------|---------------------------|------------------------|
| Magnesium–Silicon   | 13–16          | `ByteSource` (Guava)      | None — default in code |
| Aluminium–Phosphorus| 17–18          | `ByteSource` (Guava)      | None — default in code |
| Scandium+           | 19+            | `CharSource` (Guava)      | See TODO in servlet    |

The **one-line change** for Scandium+, inside `YangSchemaServlet.writeSource()`:

```java
// REMOVE (ByteSource path):
source.copyTo(resp.getOutputStream());
resp.getOutputStream().flush();

// ADD (CharSource path):
resp.setCharacterEncoding("UTF-8");
source.copyTo(resp.getWriter());
resp.getWriter().flush();
```

Also, replace `SchemaContext` with `EffectiveModelContext` in the import if
your yangtools version has renamed it (both are the same interface; just the
class name changed in Scandium).

---

## Request flow

```
darpan GUI
  │  GET /yang-schema/o-ran-uplane-conf?revision=2022-08-24&node=O-RAN-Node-1
  ▼
YangSchemaServlet.doGet()
  │
  ├─ nodeId present?
  │     YES ──► buildMountPath("O-RAN-Node-1")
  │             DOMMountPointService.getMountPoint(path)
  │             mountPoint.getService(DOMSchemaService.class)
  │             mountedSchemaService.getGlobalContext()
  │             findModule(ctx, "o-ran-uplane-conf", "2022-08-24")
  │             mountPoint.getService(SchemaRepository.class)  ← device repo
  │               or fallback → globalSchemaRepository
  │
  └─ nodeId absent?
        ──► globalSchemaRepository.getSchemaSource(sourceId, YangTextSchemaSource.class)
            writeSource(resp, source)
              → source.copyTo(resp.getOutputStream())   (pre-Scandium)
              → source.copyTo(resp.getWriter())          (Scandium+)
```

---

## Troubleshooting

**404 – Module not found in controller schema**
- The module was not on the classpath when LightyController started.
- Add its JAR/artifact to your `schemaServiceConfig.topLevelModels` list in
  `lightyControllerConfig.json` or add the dependency to your pom.xml.

**404 – No mounted device found for nodeId**
- The device is not yet connected (NETCONF session not established).
- Verify with:
  `GET /rests/data/network-topology:network-topology/topology=topology-netconf?content=operational`

**`communityRestConf.getServer()` NoSuchMethodError**
- Your Lighty version exposes the server differently.
- Use the reflection fallback shown in the comment block in `LightyYangSchemaModule`.

**Strategy A/B both fail for SchemaRepository**
- Inject a `YangTextSchemaContextResolver` during controller startup and pass
  it directly to `YangSchemaServlet`'s constructor instead.
  The resolver implements `SchemaRepository` and `SchemaSourceProvider`.
