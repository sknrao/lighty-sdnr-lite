# Cursor Workspace Setup v3 — SDNR Lighty Migration
## Definitive edition — based on full source review of `lighty-sdnr-lite`

> **What changed from v2:** All file paths, package names, version numbers,
> Maven coordinates, and wiring patterns are now confirmed from actual source.
> No placeholders remain. This document is ready to use on day 1.

---

## 1. Confirmed Facts from Source Review

These are no longer assumptions — they are verified facts to encode in
every rule file and prompt.

### Versions (from `pom-root.xml`, `pom-pnf-registration.xml`)
| Artifact | Version |
|---|---|
| Root aggregator / Lighty core | `23.0.0` |
| `yang-data-impl` (yangtools) | `14.0.20` |
| `mdsal-singleton-common-api` | `12.0.8` |
| `yang-binding` | `13.0.6` |
| `kafka-clients` | `4.2.0` |
| `jackson-databind` | `2.21.2` |
| `sdnr-wt-common` / `sdnr-wt-data-provider-model` | `2.2.2` |
| Java | `21` |
| Custom netconf repo | `https://maven.pkg.github.com/ios-mcn-smo/netconf` |

### Key paths (confirmed from source)
| Item | Exact path |
|---|---|
| `Main.java` | `applications/lighty-sdnr-lite/lighty-sdnr-lite-app/src/main/java/org/iosmcn/lighty/sdnr/lite/app/Main.java` |
| `LightyYangSchemaModule.java` | `modules/yang-schema/src/main/java/org/iosmcn/sdnr/module/LightyYangSchemaModule.java` |
| `YangSchemaServlet.java` | `modules/yang-schema/src/main/java/org/iosmcn/sdnr/servlet/YangSchemaServlet.java` |
| `PnfModule.java` | `modules/iosmcn-pnf-registration/src/main/java/io/lighty/modules/pnf/registration/PnfModule.java` |
| `lightyControllerConfig.json` | `applications/lighty-sdnr-lite/lighty-sdnr-lite-app/src/main/resources/lightyControllerConfig.json` |
| modules aggregator pom | `modules/pom.xml` |
| applications aggregator pom | `applications/pom.xml` |

### Package names (confirmed)
| Class | Package |
|---|---|
| `Main` | `org.iosmcn.lighty.sdnr.lite.app` |
| `LightyYangSchemaModule` | `org.iosmcn.sdnr.module` |
| `YangSchemaServlet` | `org.iosmcn.sdnr.servlet` |
| `PnfModule` | `io.lighty.modules.pnf.registration` |
| **New** `LightyDataProvider` | `org.iosmcn.lighty.sdnr.dataprovider` ← follow yang-schema pattern |

### Maven groupId/artifactId pattern (confirmed from `pom-yangschema.xml`)
| Field | Value |
|---|---|
| `groupId` | `org.iosmcn.lighty.sdnr` |
| `artifactId` | `<module-name>-module` |
| `version` | `0.0.1-SNAPSHOT` |
| `parent.groupId` | `io.lighty.core` |
| `parent.artifactId` | `lighty-parent` |
| `parent.version` | `23.0.0` |

### Critical observations from `Main.java`
1. Config is loaded from `lightyControllerConfig.json` passed as `args[0]` —
   all three (controller, restconf, netconf) configs parsed from the **same single file**.
2. `LightyJettyServerProvider` is built explicitly from `restconfConfiguration`
   and passed into `CommunityRestConfBuilder.withLightyServer()`.
3. `restconf.startServer()` is called explicitly AFTER `openApi.start()` —
   **the server is not live until `startServer()` is called**.
4. `LightyYangSchemaModule` receives `(lightyController.getServices(), restconf)` —
   **`restconf` must be started but `startServer()` NOT yet called** when
   YangSchemaModule registers its servlet. The servlet is registered into
   Jetty before the server goes live. This is the correct pattern.
5. `shutdown()` order: `yangSchemaModule` → `pnfModule` → `netconfSBPlugin`
   → `restconf` → `openApi` → `lightyController`
   Note: `callhomePlugin` is NOT in `closeLightyModule()` — check if this is intentional.
6. `this.lightyController` and `this.controller` are BOTH declared — `controller`
   appears to be unused dead code from an older refactor. Do not replicate this.

### Critical observation from `pom-pnf-registration.xml`
`org.osgi:org.osgi.core:6.0.0` and `org.osgi:org.osgi.compendium:5.0.0` are
present as `<scope>provided</scope>`. This is a **residual OSGi dependency**
from the CCSDK code that PnfModule wraps. It does NOT mean OSGi is in use —
`provided` scope means it is not bundled and the code doesn't activate OSGi
at runtime. For `data-provider`, the same pattern applies: OSGi jars may
appear as `provided` compile-time deps for CCSDK code that references
OSGi types, but they must never be runtime deps and must never activate
any OSGi framework.

### `sdnr-wt-data-provider-model:2.2.2` is already a dependency
This is already in `pom-pnf-registration.xml`. This is significant — it means
the YANG binding classes for `data-provider` may already be on the classpath.
Verify whether the generated Java interfaces for the 4 RPCs are already
available before trying to regenerate them.

---

## 2. Workspace Layout

```
sdnr-lighty-migration/
│
├── .cursor/
│   ├── index.mdc
│   └── rules/
│       ├── java-lighty.mdc
│       ├── data-provider.mdc
│       ├── no-osgi.mdc
│       └── existing-codebase.mdc
│
├── context/
│   ├── ARCHITECTURE.md
│   ├── CODEBASE-MAP.md           ← pre-filled below (no longer a template)
│   ├── API-INVENTORY.md
│   ├── DECISIONS.md
│   ├── PROGRESS.md
│   └── CHALLENGES.md
│
├── reference/
│   ├── sdnr-lighty-migration-design.md
│   └── ccsdk-snippets/           ← extract these on day 1
│       ├── data-provider.yang
│       ├── blueprint-data-provider.xml
│       ├── ReadStatusRpc.java
│       ├── ReadInventoryListRpc.java
│       ├── ReadNetworkElementConnectionListRpc.java
│       └── ReadFaultcurrentListRpc.java
│
├── repos/
│   ├── lighty-sdnr-lite/         ← PRIMARY — all new code goes here
│   ├── ccsdk-features/           ← READ ONLY
│   └── darpan/                   ← secondary (RestConfRequest.java only)
│
├── .cursorignore
└── sdnr-lighty-migration.code-workspace
```

### `.code-workspace`
```json
{
  "folders": [
    {
      "name": "lighty-sdnr-lite (PRIMARY)",
      "path": "repos/lighty-sdnr-lite"
    },
    {
      "name": "darpan (secondary)",
      "path": "repos/darpan"
    },
    {
      "name": "ccsdk-features (READ ONLY)",
      "path": "repos/ccsdk-features"
    }
  ],
  "settings": {
    "search.exclude": {
      "**/target/**": true,
      "**/.git/**": true
    },
    "files.exclude": {
      "**/target/**": true
    }
  }
}
```

### `.cursorignore`
```
repos/*/target/
repos/**/.git/
repos/ccsdk-features/sdnr/wt/devicemanager*/
repos/ccsdk-features/sdnr/wt/mountpoint*/
repos/ccsdk-features/sdnr/wt/odlux/
repos/ccsdk-features/sdnr/wt/netconfnode*/
```

---

## 3. Cursor Rules

---

### `.cursor/index.mdc`
```markdown
---
description: SDNR Lighty Migration — always-on project context
globs: ["**/*"]
alwaysApply: true
---

# Project: Adding LightyDataProvider to lighty-sdnr-lite

## Codebase facts (verified from source)
- Lighty version: 23.0.0
- Java: 21
- Root package: org.iosmcn.lighty.sdnr.*
- All new modules: groupId=org.iosmcn.lighty.sdnr, version=0.0.1-SNAPSHOT
- Parent pom: io.lighty.core:lighty-parent:23.0.0
- Config file: lightyControllerConfig.json (single file for controller+restconf+netconf+YANG models)
- YANG model registration: schemaServiceConfig.topLevelModels array in lightyControllerConfig.json
- Custom netconf repo: https://maven.pkg.github.com/ios-mcn-smo/netconf (requires github-packages Maven profile)

## What already exists (DO NOT re-implement)
- Main.java: full startup sequence steps 1–5 + callhome
- LightyYangSchemaModule + YangSchemaServlet: working, tested
- PnfModule: working, tested
- lightyControllerConfig.json: complete, committed

## The ONE thing being added
LightyDataProvider — step 6 in Main.java startLighty() after pnfModule.start()

## Repo roles
- repos/lighty-sdnr-lite/  → PRIMARY — all new code
- repos/darpan/            → Minimal: RestConfRequest.java only
- repos/ccsdk-features/    → READ ONLY reference

## Always read before starting
context/CODEBASE-MAP.md + context/PROGRESS.md
```

---

### `.cursor/rules/existing-codebase.mdc`
```markdown
---
description: What NOT to touch in lighty-sdnr-lite
globs: ["**/lighty-sdnr-lite/**/*.java", "**/Main.java", "**/pom*.xml"]
alwaysApply: false
---

# Existing Codebase — Do Not Modify

## Frozen files (never change these)
- applications/lighty-sdnr-lite/lighty-sdnr-lite-app/src/main/java/org/iosmcn/lighty/sdnr/lite/app/Main.java
  → Only addition: step 6 wiring after line 276 (pnfModule started)
- modules/yang-schema/** (LightyYangSchemaModule, YangSchemaServlet)
- modules/iosmcn-pnf-registration/** (PnfModule and sub-classes)
- applications/lighty-sdnr-lite/lighty-sdnr-lite-app/src/main/resources/lightyControllerConfig.json
  → Only addition: new entries in schemaServiceConfig.topLevelModels

## Exact wiring pattern from Main.java (replicate this for step 6)

// FIELD DECLARATION (top of Main class, with other module fields):
private LightyModule dataProviderModule;

// STARTUP (in startLighty(), after pnfModule block ending at line 276):
//6. start DataProvider module
LOG.info("Starting DataProvider module...");
this.dataProviderModule = new LightyDataProvider(
    this.lightyController.getServices());
final boolean dataProviderStartOk = this.dataProviderModule.start()
        .get(modulesConfig.getModuleTimeoutSeconds(), TimeUnit.SECONDS);
if (!dataProviderStartOk) {
    throw new ModuleStartupException("DataProvider module startup failed!");
}
LOG.info("DataProvider module started successfully.");

// SHUTDOWN (in shutdown(), FIRST in the list — before pnfModule):
closeLightyModule(this.dataProviderModule);

## Key startup ordering facts
- restconf.start() → openApi.start() → restconf.startServer() → yangSchemaModule.start()
- Server goes live ONLY after restconf.startServer() — YangSchema registers BEFORE this
- dataProviderModule starts AFTER netconfSBPlugin (devices may already be connecting)
- shutdown() is reverse of startup order

## New module location
modules/iosmcn-data-provider/
  → mirrors modules/yang-schema/ structure exactly
  → pom.xml: groupId=org.iosmcn.lighty.sdnr, artifactId=data-provider-module, version=0.0.1-SNAPSHOT
  → parent: io.lighty.core:lighty-parent:23.0.0
```

---

### `.cursor/rules/java-lighty.mdc`
```markdown
---
description: Java and Lighty coding conventions — confirmed from source
globs: ["**/*.java", "**/pom.xml"]
alwaysApply: false
---

# Java & Lighty Conventions (confirmed from lighty-sdnr-lite source)

## Constructor pattern (from LightyYangSchemaModule — use this, not PnfModule)
public LightyDataProvider(final LightyServices lightyServices) {
    this.lightyServices = lightyServices;
}
// PnfModule also stores lightyServices but doesn't use MD-SAL services.
// LightyDataProvider WILL use them — pass lightyServices only, extract inside initProcedure.

## Services to extract in initProcedure()
final DataBroker dataBroker = lightyServices.getBindingDataBroker();
final RpcProviderService rpcService = lightyServices.getRpcProviderService();
// Do NOT extract in constructor — extract in initProcedure() only.

## RPC registration pattern (NOT in PnfModule — use lighty docs pattern)
private Registration readStatusRegistration;

// In initProcedure():
this.readStatusRegistration = lightyServices.getRpcProviderService()
    .registerRpcImplementation(ReadStatus.class, new ReadStatusRpcImpl(dblib));

// In stopProcedure():
if (this.readStatusRegistration != null) {
    this.readStatusRegistration.close();
}

## initProcedure() rules
- Returns boolean: false on failure (not throw)
- LOG.info at start and end
- @SuppressWarnings({"checkstyle:illegalCatch"}) if catching Exception broadly

## stopProcedure() rules
- Close all Registration objects obtained in initProcedure()
- Close dblib connection pool
- Return true unless critical failure

## Maven rules (from pom-yangschema.xml)
- <packaging>jar</packaging> always
- No maven-bundle-plugin, no bnd
- Parent: io.lighty.core:lighty-parent:23.0.0
- Do NOT specify versions for ODL/lighty artifacts — inherited from parent BOM
- DO specify versions for non-ODL artifacts (kafka, jackson, onap, etc.)
- OSGi artifacts as <scope>provided</scope> ONLY if CCSDK code compiles against them
  — never runtime, never without scope

## Logging
- SLF4J only: private static final Logger LOG = LoggerFactory.getLogger(X.class);
- Never System.out.println

## Java version
- JDK 21 — use records, var, pattern matching instanceof freely
```

---

### `.cursor/rules/data-provider.mdc`
```markdown
---
description: data-provider migration context and constraints
globs: ["**/data-provider/**", "**/LightyDataProvider*", "**/ReadStatus*", "**/ReadInventory*", "**/ReadFault*", "**/ReadNetwork*"]
alwaysApply: false
---

# data-provider Migration

## Source (READ ONLY)
repos/ccsdk-features/sdnr/wt/data-provider/
  model/    → data-provider.yang (YANG contract)
  provider/ → RPC implementations to migrate
  dblib/    → ES/MariaDB abstraction — migrate first

## IMPORTANT: sdnr-wt-data-provider-model:2.2.2 is ALREADY a dependency
It is in pom-pnf-registration.xml. Check whether the generated Java RPC
interfaces already exist on the classpath before regenerating them.
Command to check: jar tf ~/.m2/repository/org/onap/ccsdk/features/sdnr/wt/sdnr-wt-data-provider-model/2.2.2/*.jar | grep -i "ReadStatus\|ReadInventory\|ReadFault\|ReadNetwork"

## Target location
modules/iosmcn-data-provider/
  src/main/java/org/iosmcn/lighty/sdnr/dataprovider/
    LightyDataProvider.java      ← AbstractLightyModule
    rpc/
      ReadStatusRpcImpl.java
      ReadInventoryListRpcImpl.java
      ReadNetworkElementConnectionListRpcImpl.java
      ReadFaultcurrentListRpcImpl.java
    dblib/                       ← migrated dblib (OSGi removed)

## The 4 RPCs darpan calls
POST rests/operations/data-provider:read-status
POST rests/operations/data-provider:read-inventory-list
POST rests/operations/data-provider:read-network-element-connection-list
POST rests/operations/data-provider:read-faultcurrent-list

## Migration order
1. Check if RPC interfaces already exist in sdnr-wt-data-provider-model:2.2.2
2. Audit blueprint XML → map to initProcedure()
3. Migrate dblib as plain Java
4. LightyDataProvider stub → wire into Main.java as step 6
5. Register read-status (simplest) end-to-end
6. Add remaining 3 RPCs
7. Add YANG entries to lightyControllerConfig.json

## lightyControllerConfig.json entries to add
These go in schemaServiceConfig.topLevelModels with "usedBy":"RESTCONF":
  data-provider.yang and ALL its transitive imports.
Run: grep "^import" data-provider.yang to find direct imports.
The namespace for data-provider is: urn:ietf:params:xml:ns:yang:data-provider
(verify from the actual yang file header)

## OSGi in CCSDK deps — expected, not a problem
Some CCSDK artifacts reference OSGi types. Use <scope>provided</scope> for
org.osgi artifacts as done in pom-pnf-registration.xml. Never runtime scope.
```

---

### `.cursor/rules/no-osgi.mdc`
```markdown
---
description: OSGi anti-pattern guard
globs: ["**/*.java", "**/*.xml"]
alwaysApply: true
---

# OSGi Anti-Pattern Guard

## Forbidden imports (in NEW code — not in migrated CCSDK code)
org.osgi.framework.BundleContext
org.osgi.framework.BundleActivator
org.apache.felix.*
org.eclipse.equinox.*
org.apache.karaf.*

## Forbidden annotations (anywhere)
@Component, @Activate, @Deactivate, @Reference (DS annotations)
@OsgiServiceProvider

## Forbidden XML (anywhere)
<blueprint ...>, <reference ...> in XML, Bundle-Activator in manifest

## Forbidden Maven
<packaging>bundle</packaging>
maven-bundle-plugin, bnd-maven-plugin

## OSGi as provided scope — ALLOWED
org.osgi:org.osgi.core and org.osgi:org.osgi.compendium as <scope>provided</scope>
are ALLOWED when CCSDK code compiles against OSGi types.
This does NOT activate OSGi at runtime. It is the same pattern as pom-pnf-registration.xml.

## Replace with
OSGi service lookup → constructor injection from LightyServices
Bundle lifecycle → AbstractLightyModule.initProcedure/stopProcedure
Blueprint XML → Java constructor or factory
```

---

## 4. `context/CODEBASE-MAP.md` — Pre-filled (no longer a template)

```markdown
# lighty-sdnr-lite Codebase Map (verified from source)

## Directory layout
lighty-sdnr-lite/
  pom.xml                                     ← root aggregator, version 23.0.0
  applications/
    pom.xml                                   ← applications aggregator
    lighty-sdnr-lite/
      lighty-sdnr-lite-app/
        pom.xml                               ← app pom (has all runtime deps)
        src/main/java/org/iosmcn/lighty/sdnr/lite/app/
          Main.java                           ← startup/shutdown wiring
        src/main/resources/
          lightyControllerConfig.json         ← ALL config: controller+restconf+netconf+YANG
  modules/
    pom.xml                                   ← modules aggregator (lists: iosmcn-pnf-registration, yang-schema)
    yang-schema/
      pom.xml                                 ← groupId=org.iosmcn.lighty.sdnr, artifactId=yang-schema-module
      src/main/java/org/iosmcn/sdnr/
        module/LightyYangSchemaModule.java    ← AbstractLightyModule, Jetty servlet registration
        servlet/YangSchemaServlet.java        ← HttpServlet serving /yang-schema/*
    iosmcn-pnf-registration/
      pom.xml                                 ← groupId=org.iosmcn.lighty.applications, artifactId=mountpoint-registrar
      src/main/java/io/lighty/modules/pnf/registration/
        PnfModule.java                        ← AbstractLightyModule, thread-based (no RPC)

## Versions
Lighty / ODL core:    23.0.0
yangtools:            14.0.20 (yang-data-impl)
mdsal-singleton:      12.0.8
yang-binding:         13.0.6
Java:                 21
Custom netconf:       ios-mcn-smo/netconf via github-packages Maven profile

## Startup sequence (Main.java startLighty())
Step 1:  LightyController.start()                          line ~196
Step 2:  CommunityRestConf (built with LightyJettyServerProvider) line ~203
Step 3:  restconf.start() + openApi.start()                line ~212
         restconf.startServer()  ← server goes live here   line ~226
Step 3.5: LightyYangSchemaModule(services, restconf).start() line ~230
Step 4:  NetconfTopologyPlugin.start()                     line ~244
Step 4.1: NetconfCallhomePlugin.start()                    line ~258
Step 5:  PnfModule(services).start()                       line ~267
[Step 6]: LightyDataProvider(services).start()             ← INSERT HERE

## Shutdown sequence (Main.java shutdown())
closeLightyModule(yangSchemaModule)     ← first
closeLightyModule(pnfModule)
closeLightyModule(netconfSBPlugin)
closeLightyModule(restconf)
closeLightyModule(openApi)
closeLightyModule(lightyController)    ← last
// Note: callhomePlugin NOT in shutdown — may be a bug worth fixing separately

## Config file format — YANG model entry
{ "usedBy":"RESTCONF","name":"<module-name>","revision":"YYYY-MM-DD","nameSpace":"<urn>"}
// usedBy options seen: CONTROLLER, NETCONF, RESTCONF, CONTROLLER/NETCONF, RESTCONF/NETCONF
// For data-provider.yang use "RESTCONF"
// For its imported IETF models already in file, do NOT duplicate
```

---

## 5. `context/DECISIONS.md`

```markdown
# Architecture Decision Records

## ADR-001: Normalise darpan to rests/?content= prefix
Change RestConfRequest.java: restconf/config → rests/data?content=config
Reason: lighty-restconf-nb-community serves only rests/ (RFC 8040).

## ADR-002: yang-schema as plain HttpServlet in Jetty
Already implemented. YangSchemaServlet + LightyYangSchemaModule — done.

## ADR-003: Migrate dblib before provider RPCs
dblib has no ODL deps — get it compiling as plain JAR first.

## ADR-004: Migrate only 4 darpan-facing RPCs initially
read-status, read-inventory-list, read-network-element-connection-list, read-faultcurrent-list.

## ADR-005: Follow yang-schema module as pattern template (NOT PnfModule)
PnfModule is thread-based and does not register RPCs.
LightyYangSchemaModule shows the correct AbstractLightyModule + LightyServices pattern.
For RPC registration specifically, use lighty 23.x documentation/examples.

## ADR-006: New module at modules/iosmcn-data-provider/
Mirrors yang-schema/ structure.
pom.xml: groupId=org.iosmcn.lighty.sdnr, artifactId=data-provider-module, version=0.0.1-SNAPSHOT
parent: io.lighty.core:lighty-parent:23.0.0

## ADR-007: YANG registration in lightyControllerConfig.json
Single config file for everything. data-provider.yang → "usedBy":"RESTCONF".
Check transitive imports — many IETF models already present, do not duplicate.

## ADR-008: Check sdnr-wt-data-provider-model:2.2.2 for existing RPC bindings
Already in pom-pnf-registration.xml. RPC interfaces may already be generated.
Verify before writing any binding generation code.

## ADR-009: OSGi as provided scope is acceptable for CCSDK transitive deps
Same pattern as pom-pnf-registration.xml. Not a runtime concern.

## ADR-010: dataProviderModule shuts down BEFORE pnfModule
PNF registration writes to data-provider. Stop the writer (pnf) before the store (data-provider).
Wait — actually PnfModule writes to NETCONF topology, not data-provider directly.
Re-evaluate order: data-provider can shut down after pnf safely.
```

---

## 6. `context/CHALLENGES.md`

```markdown
# Known Challenges & Gotchas

## C1 — SchemaRepository resolution (resolved in LightyYangSchemaModule)
Already solved. Strategy A (extensions map) → Strategy B (direct cast).
Do not re-solve — reuse resolveSchemaRepository() if needed in data-provider.

## C2 — restconf.startServer() ordering
Server goes live AFTER openApi.start() and AFTER startServer().
YangSchemaModule registers its servlet AFTER startServer() is called (line ~230 in Main).
dataProviderModule registers RPCs in initProcedure() — this is fine at any point
after LightyController is started, because RPCs register into MD-SAL, not Jetty.

## C3 — sdnr-wt-data-provider-model:2.2.2 version match
The CCSDK RPC interfaces are versioned. The YANG revision in data-provider.yang
must match the version of the JAR. If the JAR has 2.2.2 bindings, the YANG
revision date must match what was used to generate them.

## C4 — Duplicate YANG models in lightyControllerConfig.json
Many imports of data-provider.yang (ietf-yang-types, ietf-inet-types,
network-topology) are already in the file. Adding duplicates causes startup
failure. Check before adding each entry.

## C5 — dblib ES/MariaDB connection lifecycle
No OSGi resolver to delay startup. Connection retry must be explicit in initProcedure().

## C6 — callhomePlugin not in shutdown() — potential existing bug
Main.java shutdown() does not call closeLightyModule(callhomePlugin).
Do not copy this pattern. Add dataProviderModule.shutdown() correctly.
Consider raising a PR to fix callhome shutdown separately.

## C7 — pom-pnf-registration.xml has no parent BOM
It declares lighty-parent as a <dependency> not as <parent>.
This means it manages its own versions explicitly.
The yang-schema pom.xml uses the correct pattern (<parent> declaration).
Follow yang-schema pattern for data-provider, NOT pnf-registration pattern.

## C8 — Two duplicate controller fields in Main.java
Both `lightyController` and `controller` are declared. `controller` is dead code.
Do not reference `controller` — use `lightyController` consistently.

## C9 — github-packages Maven profile required for iosmcn/netconf
Building requires `-P github-packages` and GitHub credentials in `~/.m2/settings.xml`.
Data-provider should NOT depend on iosmcn/netconf directly — it's a controller dep.
```

---

## 7. `context/PROGRESS.md`

```markdown
# Migration Progress Log

## Status legend
✅ Done   🔄 In Progress   ⏳ Not Started   ❌ Blocked

## Phase 0 — Already complete in lighty-sdnr-lite
| Component                                        | Status |
|--------------------------------------------------|--------|
| LightyController + CommunityRestConf + OpenApi   | ✅     |
| LightyYangSchemaModule + YangSchemaServlet        | ✅     |
| NetconfTopologyPlugin + CallhomePlugin            | ✅     |
| PnfModule (Kafka VES PNF registration)           | ✅     |
| IETF NETCONF 2013 upgrade                        | ✅     |
| lightyControllerConfig.json                      | ✅     |

## Phase 1 — Setup
| Task                                             | Status |
|--------------------------------------------------|--------|
| Repos cloned, workspace opened in Cursor         | ⏳     |
| .mdc rule files created                          | ⏳     |
| ccsdk-snippets extracted to reference/           | ⏳     |
| Verify RPC interfaces in sdnr-wt-data-provider-model:2.2.2 | ⏳ |

## Phase 2 — RESTCONF prefix (darpan)
| Task                                             | Status |
|--------------------------------------------------|--------|
| RestConfRequest.java updated to rests/?content=  | ⏳     |
| Category 1 calls tested against lighty-sdnr-lite | ⏳     |

## Phase 3 — dblib migration
| Task                                             | Status |
|--------------------------------------------------|--------|
| Blueprint XML audited                            | ⏳     |
| dblib OSGi elements identified and removed       | ⏳     |
| dblib compiles as plain JAR                      | ⏳     |
| ES/MariaDB connection lifecycle standalone       | ⏳     |

## Phase 4 — LightyDataProvider module
| Task                                             | Status |
|--------------------------------------------------|--------|
| modules/iosmcn-data-provider/pom.xml created     | ⏳     |
| modules/pom.xml updated (new module added)       | ⏳     |
| LightyDataProvider stub compiles                 | ⏳     |
| Wired into Main.java as step 6                   | ⏳     |
| data-provider.yang added to lightyControllerConfig.json | ⏳ |
| read-status RPC end-to-end working               | ⏳     |
| read-inventory-list working                      | ⏳     |
| read-network-element-connection-list working     | ⏳     |
| read-faultcurrent-list working                   | ⏳     |

## Session log
<!-- Add a line here after each Cursor session -->
```

---

## 8. Prompt Templates

---

### 8.1 Session Start (every session — non-negotiable)
```
Read these before doing anything:
  @context/CODEBASE-MAP.md
  @context/PROGRESS.md
  @context/CHALLENGES.md

Today's goal: [describe goal]

Summarise current state and propose a concrete plan. Wait for my approval.
```

---

### 8.2 Check Existing RPC Bindings (do this in Phase 1, before writing any RPC code)
```
Run this in the terminal:
  jar tf ~/.m2/repository/org/onap/ccsdk/features/sdnr/wt/sdnr-wt-data-provider-model/2.2.2/sdnr-wt-data-provider-model-2.2.2.jar \
    | grep -iE "ReadStatus|ReadInventory|ReadFaultcurrent|ReadNetworkElementConnection"

Then tell me:
  1. Do the 4 RPC Java interfaces already exist in the JAR?
  2. What are their exact fully-qualified class names?
  3. Do they follow the Lighty 23 / mdsal RPC interface pattern
     (implementing org.opendaylight.yangtools.yang.binding.Rpc)?

This determines whether I need to add a YANG-to-Java generation step or can
use the existing classes directly.
```

---

### 8.3 Blueprint Audit
```
Read:
  @reference/ccsdk-snippets/blueprint-data-provider.xml
  @context/CODEBASE-MAP.md  (LightyYangSchemaModule pattern)

For each <reference> and <bean> in the blueprint XML, produce a table:
  - Element type | ID / interface | init-method | destroy-method | LightyServices equivalent

Then write the skeleton of LightyDataProvider.initProcedure() with:
  - Constructor taking only LightyServices (following LightyYangSchemaModule)
  - Service extraction inside initProcedure(), not in constructor
  - Empty stubs for each provider/rpc that needs to be started
  - @SuppressWarnings({"checkstyle:illegalCatch"}) on initProcedure()
```

---

### 8.4 Create Module pom.xml
```
Read:
  @repos/lighty-sdnr-lite/modules/yang-schema/pom.xml
  @context/CODEBASE-MAP.md

Create modules/iosmcn-data-provider/pom.xml following the EXACT structure
of yang-schema/pom.xml with these changes:
  - artifactId: data-provider-module
  - description: iosmcn :: data-provider-module
  - Add dependencies needed for data-provider but NOT already in parent BOM:
      sdnr-wt-data-provider-model:2.2.2  (already confirmed on classpath)
      sdnr-wt-common:2.2.2
      Any ES/MariaDB client libs from dblib
  - OSGi artifacts as <scope>provided</scope> if needed by CCSDK code
  - Add ONAP nexus repository (same as pom-pnf-registration.xml)

Also show the one-line addition to modules/pom.xml:
  <module>iosmcn-data-provider</module>
```

---

### 8.5 Wire into Main.java
```
Read:
  @repos/lighty-sdnr-lite/applications/lighty-sdnr-lite/lighty-sdnr-lite-app/src/main/java/org/iosmcn/lighty/sdnr/lite/app/Main.java
  @context/CODEBASE-MAP.md  (exact line numbers for step 5 and shutdown)

Show me a unified diff to add LightyDataProvider as step 6:
  1. Field declaration at top of class (after pnfModule field)
  2. Import statement
  3. Startup block in startLighty() after pnfModule starts (after line ~276)
  4. Shutdown entry in shutdown() — FIRST line, before yangSchemaModule

Do not change any existing lines. Additions only.
```

---

### 8.6 Add YANG entries to lightyControllerConfig.json
```
Read:
  @reference/ccsdk-snippets/data-provider.yang
  @repos/lighty-sdnr-lite/applications/lighty-sdnr-lite/lighty-sdnr-lite-app/src/main/resources/lightyControllerConfig.json

Step 1: List all imports in data-provider.yang (direct and transitive).
Step 2: For each imported module, check if it is ALREADY in lightyControllerConfig.json.
Step 3: Produce ONLY the entries that are NOT already present, in the correct JSON format:
  { "usedBy":"RESTCONF","name":"data-provider","revision":"YYYY-MM-DD","nameSpace":"<urn>"}
Do not duplicate entries already in the file — duplicates cause startup failure.
```

---

### 8.7 Implement read-status RPC
```
Read:
  @reference/ccsdk-snippets/ReadStatusRpc.java
  @reference/ccsdk-snippets/data-provider.yang  (find read-status definition)
  @context/CHALLENGES.md  (C3 — version match)

Confirm the exact Java interface name for read-status from sdnr-wt-data-provider-model:2.2.2
(use result from prompt 8.2).

Write ReadStatusRpcImpl.java that:
  1. Implements the confirmed interface
  2. Takes dblib as constructor parameter
  3. Calls dblib to fetch status
  4. Zero OSGi imports

Show registration in LightyDataProvider.initProcedure():
  this.readStatusRegistration = lightyServices.getRpcProviderService()
      .registerRpcImplementation(ReadStatus.class, new ReadStatusRpcImpl(this.dblib));

Show stopProcedure() cleanup:
  if (this.readStatusRegistration != null) { this.readStatusRegistration.close(); }
```

---

### 8.8 End-of-Session Update
```
We completed: [brief description]

Update these files:
1. @context/PROGRESS.md — mark done ✅, add session log line with today's date
2. @context/CHALLENGES.md — add any new gotchas discovered
3. @context/DECISIONS.md — add ADR if a significant architectural choice was made

Give me 3 bullet points for what the NEXT session should start with.
```

---

## 9. Day 1 Checklist

- [ ] Clone `sknrao/lighty-sdnr-lite` into `repos/lighty-sdnr-lite/`
- [ ] Clone `onap/ccsdk-features` into `repos/ccsdk-features/`
- [ ] Create all `.cursor/rules/*.mdc` files from Section 3
- [ ] Create `context/` files from Sections 4–7
- [ ] Create `sdnr-lighty-migration.code-workspace` and open in Cursor
- [ ] Add `.cursorignore`
- [ ] Extract 6 ccsdk-snippets into `reference/ccsdk-snippets/`
- [ ] Run `mvn clean install -DskipTests -P github-packages` to populate local `.m2`
- [ ] Run **Prompt 8.2** to check existing RPC bindings — do this before anything else
- [ ] Run **Session Start prompt (8.1)** and confirm Opus understands the project

---

## 10. Token Efficiency Notes

- **Prompt 8.2 first.** If the RPC interfaces already exist in `sdnr-wt-data-provider-model:2.2.2`, you skip a significant chunk of binding generation work. Find out before spending tokens on it.
- **`CODEBASE-MAP.md` replaces exploration.** All file paths are now pre-filled. Use `@context/CODEBASE-MAP.md` in every session-start prompt and Opus won't need to search the repo tree.
- **Exact line references matter.** The wiring prompts (8.5) cite the actual startup method and shutdown sequence — Opus will produce a clean diff rather than rewriting the whole method.
- **One RPC per session in Phase 4.** Implement, test, commit, then move to the next. Don't batch all four.
- **C4 (duplicate YANG entries) will cost you tokens if ignored.** Run Prompt 8.6's step 2 carefully — a startup failure from duplicates is easy to avoid and expensive to debug.

---

*v3 — All content verified from actual source files: Main.java, LightyYangSchemaModule.java,
PnfModule.java, pom-root.xml, pom-modules.xml, pom-applications.xml, pom-yangschema.xml,
pom-pnf-registration.xml, lightyControllerConfig.json, README.md.*
