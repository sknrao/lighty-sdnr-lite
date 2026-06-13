# Cursor Workspace Setup — SDNR Lighty Migration
## Everything to organise before writing the first line of code

---

## 1. Workspace Layout

Create a single root folder that acts as your **mission control**.
All repos sit inside it as subfolders; Cursor indexes the entire tree.

```
sdnr-lighty-migration/               ← open THIS in Cursor as workspace root
│
├── .cursor/
│   ├── index.mdc                    ← always-on project rules
│   └── rules/
│       ├── java-lighty.mdc          ← Java + Lighty conventions
│       ├── yang-schema.mdc          ← yang-schema servlet context
│       ├── data-provider.mdc        ← data-provider migration context
│       └── no-osgi.mdc              ← anti-pattern guard
│
├── context/                         ← your hand-written AI knowledge base
│   ├── ARCHITECTURE.md              ← overall design (this migration)
│   ├── API-INVENTORY.md             ← darpan call inventory
│   ├── DECISIONS.md                 ← ADRs — why you chose what you chose
│   ├── PROGRESS.md                  ← running log; update after each session
│   ├── CHALLENGES.md                ← known hard problems, gotchas
│   └── YANG-MODELS.md               ← list of YANG modules, versions, imports
│
├── reference/                       ← read-only reference material
│   ├── sdnr-lighty-migration-design.md   ← the design doc from this session
│   ├── yang-schema-servlet/              ← the generated implementation files
│   │   ├── YangSchemaServlet.java
│   │   ├── LightyYangSchemaModule.java
│   │   └── README.md
│   ├── lighty-rnc-app-snippets/     ← key snippets extracted from lighty-rnc-app
│   │   ├── LightyRncMain.java       ← how the main class is wired
│   │   └── RncLightyModule.java     ← example AbstractLightyModule
│   └── odl-transportpce-lighty/     ← key snippets from transportpce migration
│       └── TransportPCEImpl.java
│
├── repos/                           ← actual git clones
│   ├── ccsdk-features/              ← git clone onap/ccsdk-features
│   ├── lighty/                      ← git clone PANTHEONtech/lighty
│   ├── lighty-sdnr/                 ← YOUR customised Lighty SDNR
│   └── darpan/                      ← YOUR dashboard (udb-darpan)
│
├── scratch/                         ← throw-away experiments, never committed
│   └── .gitkeep
│
└── sdnr-lighty-migration.code-workspace   ← Cursor multi-root workspace file
```

### The `.code-workspace` file

Save this as `sdnr-lighty-migration.code-workspace` and open it in Cursor
(`File → Open Workspace from File`). Cursor will index all four repos together.

```json
{
  "folders": [
    { "name": "lighty-sdnr (yours)",  "path": "repos/lighty-sdnr"   },
    { "name": "darpan (yours)",        "path": "repos/darpan"         },
    { "name": "ccsdk-features (ref)",  "path": "repos/ccsdk-features" },
    { "name": "lighty (ref)",          "path": "repos/lighty"         }
  ],
  "settings": {
    "search.exclude": {
      "**/target/**":       true,
      "**/.git/**":         true,
      "**/node_modules/**": true
    },
    "files.exclude": {
      "**/target/**": true
    }
  }
}
```

---

## 2. Cursor Rules (`.cursor/rules/`)

Project Rules are stored in `.cursor/rules/` as `.mdc` files and are
version-controlled per project. Each file targets specific file globs so
Cursor only loads the rule when it is relevant — saving tokens.

---

### `.cursor/index.mdc` — Always-on project rule

This is loaded on every prompt regardless of which file is open.
Think of rules as a compressed representation of your project — thousands
of lines of context distilled into focused guidelines. Keep this short.

```markdown
---
description: SDNR Lighty Migration — always-on project context
globs: ["**/*"]
alwaysApply: true
---

# Project: SDNR data-provider & yang-schema migration to Lighty

## What this is
Migrating two ODL/CCSDK OSGi bundles to plain Java Lighty modules:
1. `data-provider` — persistence + 4 YANG RPCs (ES/MariaDB backend)
2. `yang-schema` servlet — raw YANG source text over HTTP

## Repo map
- `lighty-sdnr/`    → OUR Lighty SDNR (O-RAN customised) — PRIMARY EDIT TARGET
- `darpan/`         → OUR dashboard (RESTCONF client) — secondary edits only
- `ccsdk-features/` → READ ONLY reference. Never modify.
- `lighty/`         → READ ONLY reference. Never modify.

## Non-negotiable constraints
- ZERO OSGi. No blueprint XML, no @Component, no BundleContext, no Karaf.
- ZERO `<packaging>bundle</packaging>`. All output is plain JAR.
- Constructor injection only — no field injection, no service locator.
- Every new class must compile against the ODL version already in lighty-sdnr/pom.xml.
- Always read context/ARCHITECTURE.md and context/PROGRESS.md before starting work.
```

---

### `.cursor/rules/java-lighty.mdc` — Java + Lighty conventions

```markdown
---
description: Java and Lighty coding conventions for this project
globs: ["**/*.java", "**/pom.xml"]
alwaysApply: false
---

# Java & Lighty Conventions

## Module lifecycle
- All new modules extend `io.lighty.core.controller.api.AbstractLightyModule`.
- `initProcedure()` returns boolean. Return false (not throw) on non-fatal failures.
- `stopProcedure()` must close every resource opened in `initProcedure()`.
- Never call `System.exit()` inside a module.

## Service wiring
- Obtain all services from `LightyServices` passed into the constructor.
- Key accessors:
    - `lightyServices.getBindingDataBroker()`
    - `lightyServices.getRpcProviderService()`
    - `lightyServices.getDOMMountPointService()`
    - `lightyServices.getDOMSchemaService()`
    - `lightyServices.getNotificationPublishService()`

## YANG / yangtools
- Use `EffectiveModelContext` (Scandium+) or `SchemaContext` (pre-Scandium).
  Check which the existing lighty-sdnr code uses and match it.
- `SourceIdentifier(name, revision)` is the lookup key for SchemaRepository.
- Never hard-code revision strings; derive them from the SchemaContext module.

## RPC registration
- Use `RpcProviderService.registerRpcImplementation(Interface.class, impl)`.
- Store the returned `Registration` and close it in `stopProcedure()`.

## Logging
- Always use SLF4J: `LoggerFactory.getLogger(YourClass.class)`.
- Never use `System.out.println`.

## Maven
- `<packaging>jar</packaging>` only.
- Never add `maven-bundle-plugin` or `bnd` to pom.xml.
- Match `<version>` of mdsal, yangtools, netconf artifacts to the BOM
  already declared in `lighty-sdnr/pom.xml`.
```

---

### `.cursor/rules/yang-schema.mdc` — yang-schema servlet context

```markdown
---
description: Context for the yang-schema servlet implementation
globs: ["**/YangSchemaServlet.java", "**/LightyYangSchemaModule.java", "**/yang-schema/**"]
alwaysApply: false
---

# yang-schema Servlet Context

## What it does
Serves raw YANG source text at GET /yang-schema/{module}?revision=...&node=...
Replicates the ODL-specific endpoint — darpan's URL does not change.

## Key design decisions
- Plain HttpServlet registered into CommunityRestConf's Jetty instance.
- Two resolution paths:
    1. No `node` param → globalSchemaRepository (controller-global)
    2. `node` param → DOMMountPointService → per-device DOMSchemaService → SchemaContext
       → fallback to globalSchemaRepository if module not in device context
- SchemaRepository resolution strategy order:
    A. DOMSchemaService.getExtensions() — look for SchemaRepository impl
    B. Direct cast of DOMSchemaService to SchemaRepository
    C. Fail loudly in logs; do NOT silently return 500.

## Version split (IMPORTANT)
- Pre-Scandium: YangTextSchemaSource extends ByteSource → write to OutputStream
- Scandium+:    YangTextSchemaSource extends CharSource  → write to Writer
  Check lighty-sdnr yangtools version before touching writeSource().

## Files
- reference/yang-schema-servlet/YangSchemaServlet.java     ← base implementation
- reference/yang-schema-servlet/LightyYangSchemaModule.java ← module + Jetty wiring
- reference/yang-schema-servlet/README.md                   ← integration guide
```

---

### `.cursor/rules/data-provider.mdc` — data-provider migration context

```markdown
---
description: Context for migrating the CCSDK data-provider OSGi bundle to Lighty
globs: ["**/data-provider/**", "**/LightyDataProvider*", "**/dblib/**"]
alwaysApply: false
---

# data-provider Migration Context

## Source location (READ ONLY)
ccsdk-features/sdnr/wt/data-provider/
  ├── model/    ← data-provider.yang and imported YANG files
  ├── provider/ ← RPC implementations, YangToolsMapper2
  └── dblib/    ← Elasticsearch / MariaDB abstraction (pure Java, migrate first)

## Four RPCs to migrate (the only data-provider calls darpan makes)
POST rests/operations/data-provider:read-inventory-list
POST rests/operations/data-provider:read-network-element-connection-list
POST rests/operations/data-provider:read-faultcurrent-list
POST rests/operations/data-provider:read-status

## Migration order
1. dblib first — no ODL deps, pure Java. Get it compiling as plain JAR.
2. LightyDataProvider stub — extend AbstractLightyModule, wire into main.
3. One RPC end-to-end (start with read-status, simplest).
4. Remaining three RPCs once the first is proven.
5. YANG model registration in lightyControllerConfig.json.

## YANG schema registration
All modules imported by data-provider.yang must be listed in:
lighty-sdnr/src/main/resources/lightyControllerConfig.json
under schemaServiceConfig.topLevelModels

## Do NOT migrate
- ODLUX Connect app coupling (WebSocket notifications) — out of scope for now.
- NetworkElementConnectionListener — fed by devicemanager, separate concern.
- YangToolsMapper2 internal ODL casts — replace with plain GSON/Jackson if simpler.
```

---

### `.cursor/rules/no-osgi.mdc` — anti-pattern guard

```markdown
---
description: Hard stop on any OSGi pattern introduction
globs: ["**/*.java", "**/*.xml", "**/pom.xml"]
alwaysApply: true
---

# OSGi Anti-Pattern Guard

NEVER introduce any of the following. If you find yourself about to write one,
stop and find the plain Java / constructor injection equivalent instead.

## Forbidden imports
- `org.osgi.*`
- `org.apache.felix.*`
- `org.eclipse.equinox.*`
- `org.apache.karaf.*`

## Forbidden annotations
- `@Component` (DS)
- `@Activate` / `@Deactivate` (DS)
- `@Reference` (DS)
- `@OsgiServiceProvider`

## Forbidden XML patterns
- `<blueprint ...>` in any XML file
- `<reference ...>` in blueprint XML
- `<osgi:service ...>`
- `Bundle-Activator` in manifest

## Forbidden Maven patterns
- `<packaging>bundle</packaging>`
- `maven-bundle-plugin`
- `bnd-maven-plugin`

## The correct alternative
Replace OSGi service lookup with constructor injection.
Replace bundle lifecycle with AbstractLightyModule.initProcedure/stopProcedure.
Replace blueprint XML with a Java factory or builder class.
```

---

## 3. Context Folder — Hand-Written Knowledge Base

Maintain a running log of patterns and issues, milestones for current
goals and completed work, and an instructions file for project overview.
Reference these with @ to give the AI persistent project knowledge that
evolves with your codebase.

These files are your persistent memory across Cursor sessions.

---

### `context/ARCHITECTURE.md`

Paste the full contents of `reference/sdnr-lighty-migration-design.md` here
and trim it to essentials as work progresses. This is the document the AI
reads first. Keep it current — outdated architecture docs actively mislead.

---

### `context/API-INVENTORY.md`

```markdown
# darpan → SDNR API Call Inventory

## Category 1 — Native Lighty RESTCONF (no migration needed)
All use rests/ prefix (RFC 8040) after RestConfRequest.java update.

| Intent                  | Method | Path                                                             |
|-------------------------|--------|------------------------------------------------------------------|
| All config nodes        | GET    | rests/data/network-topology:network-topology?content=config      |
| Specific config node    | GET    | rests/data/.../node={nodeId}?content=config                      |
| All operational nodes   | GET    | rests/data/network-topology:network-topology?content=nonconfig   |
| Specific op node        | GET    | rests/data/.../node={nodeId}?content=nonconfig                   |
| Node data               | GET    | rests/data/.../topology=topology-netconf/node={nodeId}           |
| Capability config read  | GET    | rests/data/.../yang-ext:mount/{path}?content=config              |
| Capability config write | POST   | rests/data/.../yang-ext:mount/{module}:{path}                    |
| YANG source text        | GET    | /yang-schema/{module}?revision=...&node=...  ← custom servlet    |

## Category 2 — Requires data-provider migration
| RPC                                | Method | Path                                                           |
|------------------------------------|--------|----------------------------------------------------------------|
| read-inventory-list                | POST   | rests/operations/data-provider:read-inventory-list             |
| read-network-element-connection-list | POST | rests/operations/data-provider:read-network-element-connection-list |
| read-faultcurrent-list             | POST   | rests/operations/data-provider:read-faultcurrent-list          |
| read-status                        | POST   | rests/operations/data-provider:read-status                     |

## darpan files involved
- RestConfRequest.java   ← URL builder; update config/operational paths here
- RestConfManager.java   ← HTTP executor; no change needed
- RestCallExecutor.java  ← injects auth; no change needed
- RestConfController.java ← Spring endpoints; no change needed
```

---

### `context/DECISIONS.md`

```markdown
# Architecture Decision Records

## ADR-001: Normalise darpan to rests/ prefix
**Decision:** Update RestConfRequest.java to use rests/data?content=config
instead of restconf/config/ paths.
**Reason:** lighty-restconf-nb-community serves only RFC 8040 rests/ paths.
Running both NB modules is unnecessary complexity.
**Impact:** Two lines changed in RestConfRequest.java.

## ADR-002: Migrate yang-schema as plain HttpServlet
**Decision:** Implement YangSchemaServlet as a plain Java HttpServlet registered
into CommunityRestConf's Jetty. Not as a RESTCONF RPC.
**Reason:** The endpoint returns raw YANG text, not JSON/XML RESTCONF response.
Keeping the same URL means darpan needs no change.

## ADR-003: Migrate dblib before provider
**Decision:** Migrate the Elasticsearch/MariaDB abstraction layer (dblib) first,
before touching any ODL / MD-SAL code.
**Reason:** dblib has zero ODL dependencies. Getting it building as a plain JAR
provides a stable persistence foundation before tackling the more complex
RPC registration work.

## ADR-004: Migrate only the 4 darpan-facing RPCs initially
**Decision:** Do not migrate the full data-provider bundle. Migrate only the
four RPC implementations that darpan actually calls.
**Reason:** Full migration is a large scope. The four RPCs cover all darpan
functionality. ODLUX Connect app coupling is out of scope for this phase.
```

---

### `context/PROGRESS.md`

```markdown
# Migration Progress Log
Update this file at the end of every Cursor session.

## Status legend
✅ Done   🔄 In Progress   ⏳ Not Started   ❌ Blocked

## Phase 1 — Scaffolding
| Task                                           | Status |
|------------------------------------------------|--------|
| Workspace folder structure created             | ✅     |
| Cursor rules written                           | ✅     |
| All repos cloned                               | ⏳     |
| Lighty version confirmed                       | ⏳     |
| ODL dependency tree audited (data-provider)    | ⏳     |

## Phase 2 — yang-schema servlet
| Task                                           | Status |
|------------------------------------------------|--------|
| YangSchemaServlet.java integrated              | ⏳     |
| LightyYangSchemaModule wired into main         | ⏳     |
| SchemaRepository resolution strategy confirmed | ⏳     |
| GET /yang-schema tested from darpan            | ⏳     |

## Phase 3 — RESTCONF prefix normalisation
| Task                                           | Status |
|------------------------------------------------|--------|
| RestConfRequest.java updated                   | ⏳     |
| Category 1 calls tested against Lighty         | ⏳     |

## Phase 4 — data-provider dblib
| Task                                           | Status |
|------------------------------------------------|--------|
| dblib compiled as plain JAR                    | ⏳     |
| ES/MariaDB connection lifecycle managed        | ⏳     |

## Phase 5 — data-provider RPCs
| Task                                           | Status |
|------------------------------------------------|--------|
| YANG models registered in lightyControllerConfig.json | ⏳ |
| LightyDataProvider stub compiles and starts    | ⏳     |
| read-status RPC working end-to-end             | ⏳     |
| read-inventory-list working                    | ⏳     |
| read-network-element-connection-list working   | ⏳     |
| read-faultcurrent-list working                 | ⏳     |

## Session log
<!-- Add a line after each session -->
<!-- 2026-06-12: Initial setup, context files created, rules written -->
```

---

### `context/CHALLENGES.md`

```markdown
# Known Challenges & Gotchas

## C1 — SchemaRepository not directly on LightyServices
LightyServices has no getSchemaRepository() method.
Resolution: try extensions map → direct cast → fail loudly.
See: LightyYangSchemaModule.resolveSchemaRepository()

## C2 — YangTextSchemaSource API split at Scandium
Pre-Scandium: ByteSource → write to OutputStream
Scandium+:    CharSource  → write to Writer
Resolution: Check the yangtools version in lighty-sdnr/pom.xml first.
See: YangSchemaServlet.writeSource() TODO comment

## C3 — communityRestConf.getServer() may not exist on all versions
Resolution: Use reflection fallback shown in LightyYangSchemaModule comments.

## C4 — YANG schema context completeness
Missing imports in lightyControllerConfig.json cause silent binding failure.
Resolution: Run full transitive import closure check on data-provider.yang
before starting Phase 5.

## C5 — ES/MariaDB has no ODL lifecycle hook in Lighty
Resolution: Manage connection retry loop explicitly in initProcedure().
Do not assume dependencies are ready — Lighty has no resolver.

## C6 — Old vs new RPC registration API
Old: RpcProviderRegistry.addRpcImplementation()
New: RpcProviderService.registerRpcImplementation()
Resolution: Check which one lighty-sdnr's existing modules use.
```

---

### `context/YANG-MODELS.md`

```markdown
# YANG Models Reference

## data-provider.yang imports (to verify)
Run: grep "^import" ccsdk-features/sdnr/wt/data-provider/model/src/main/yang/data-provider.yang

Expected imports (check revisions against your branch):
- ietf-yang-types        (RFC 6991)
- ietf-inet-types        (RFC 6991)
- network-topology       (RFC 8345)
- (any O-RAN or OpenROADM specific imports — check your branch)

## Registration in lightyControllerConfig.json
All imported modules must appear under:
  schemaServiceConfig → topLevelModels

## Lighty version → ODL core version mapping
(Fill in after checking lighty-sdnr/pom.xml)
Lighty version:   ______
ODL core:         ______
yangtools:        ______
md-sal:           ______
netconf:          ______
```

---

## 4. Reference Snippets to Extract Manually

Before your first Cursor session, extract these key snippets from the
reference repos and drop them in `reference/lighty-rnc-app-snippets/`.
The AI cannot browse GitHub at depth but can read files you have locally.

| File to copy | From repo | Why |
|---|---|---|
| `lighty-applications/lighty-rnc-app/src/main/java/.../LightyRncMain.java` | PANTHEONtech/lighty | Shows exact startup sequence: controller → restconf → modules |
| `lighty-applications/lighty-rnc-app/src/main/java/.../RncLightyModule.java` | PANTHEONtech/lighty | AbstractLightyModule example with NETCONF SB |
| `lighty-applications/lighty-rnc-app/src/main/resources/lightyControllerConfig.json` | PANTHEONtech/lighty | YANG model registration format |
| `sdnr/wt/data-provider/model/src/main/yang/data-provider.yang` | ccsdk-features | The YANG source you are implementing |
| `sdnr/wt/data-provider/provider/src/main/resources/OSGI-INF/blueprint/*.xml` | ccsdk-features | Blueprint to audit; maps to your initProcedure() |
| `lighty/lighty-core/.../AbstractLightyModule.java` | PANTHEONtech/lighty | Read this before writing any module |

---

## 5. Prompt Templates

Use these in Cursor Chat (`Cmd+L`) or Composer (`Cmd+I`).
Always use `@file` references so Cursor loads the exact context.
If you repeat a prompt pattern in chat, promote it to a reusable rule.

---

### 5.1 Session Start Prompt (use every time you open Cursor)

```
Read @context/ARCHITECTURE.md, @context/PROGRESS.md, and @context/CHALLENGES.md
to understand where we are.

Today's goal: [describe what you want to achieve in this session]

Do not write any code yet. Summarise your understanding of the current state
and propose a plan for today's session. Wait for my approval before starting.
```

---

### 5.2 Audit Blueprint XML → initProcedure() Mapping

```
Read @ccsdk-features/sdnr/wt/data-provider/provider/src/main/resources/OSGI-INF/blueprint/
and all .xml files in it.

For each <reference> and <bean> element, produce a table with these columns:
  - OSGi element type (reference / bean)
  - bean id / ref
  - interface / class
  - init-method (if any)
  - destroy-method (if any)
  - Lighty equivalent (which LightyServices accessor replaces it)

Do not write any Java yet. Just the table.
```

---

### 5.3 Maven Dependency Audit

```
Run this in the terminal for me:
  cd repos/ccsdk-features/sdnr/wt/data-provider/provider
  mvn dependency:tree -Dincludes=org.osgi,org.apache.karaf,org.eclipse.osgi > /tmp/osgi-deps.txt
  cat /tmp/osgi-deps.txt

Then list every OSGi/Karaf artifact found and for each one tell me:
  - What it provides
  - Whether it can simply be removed, or needs a plain Java replacement
  - What the plain Java replacement is
```

---

### 5.4 Integrate YangSchemaServlet

```
I want to integrate the YangSchemaServlet into my lighty-sdnr main class.

Read these files first:
  @reference/yang-schema-servlet/YangSchemaServlet.java
  @reference/yang-schema-servlet/LightyYangSchemaModule.java
  @reference/yang-schema-servlet/README.md
  @reference/lighty-rnc-app-snippets/LightyRncMain.java

Then read my current main class:
  @lighty-sdnr/src/main/java/[path-to-main]/SdnrMain.java

Show me exactly:
  1. Where to add the LightyYangSchemaModule instantiation (after which line)
  2. The exact constructor call
  3. Where to add the shutdown call
  4. Any import statements needed

Do not rewrite the whole main class. Show only the diffs.
```

---

### 5.5 Verify SchemaRepository Resolution

```
Read my Lighty main class and the lighty-sdnr pom.xml to determine
the exact Lighty and yangtools versions in use.

  @lighty-sdnr/pom.xml
  @reference/yang-schema-servlet/LightyYangSchemaModule.java

Then tell me:
  1. Which SchemaRepository resolution strategy (A, B, or C) in
     LightyYangSchemaModule.resolveSchemaRepository() will work for
     my exact Lighty version.
  2. Whether YangTextSchemaSource in my version is ByteSource or CharSource
     (pre-Scandium vs Scandium+).
  3. What the exact class name is of the DOMSchemaService implementation
     in my version.

Give me the simplified resolveSchemaRepository() body after removing
the strategies that don't apply.
```

---

### 5.6 Migrate dblib

```
Read the dblib module:
  @ccsdk-features/sdnr/wt/data-provider/dblib/

I want to migrate it to a plain Java module (no OSGi).

  1. List every OSGi-specific import, annotation, or XML file in dblib.
  2. For each one, provide the plain Java replacement.
  3. Show me the modified pom.xml (packaging change + removed plugins only).
  4. If any class has @Activate or @Deactivate, show me how to convert it
     to a constructor + explicit init()/close() methods.

Do not touch the ES/MariaDB query logic. Only remove OSGi plumbing.
```

---

### 5.7 Implement a Single data-provider RPC

```
I want to implement the `read-status` RPC from data-provider in Lighty.

Read these files:
  @ccsdk-features/sdnr/wt/data-provider/model/src/main/yang/data-provider.yang
  @ccsdk-features/sdnr/wt/data-provider/provider/src/main/java/[path]/ReadStatusRpc.java
  @context/DECISIONS.md

Write a class `LightyReadStatusRpc` that:
  1. Implements the correct Lighty RPC interface (derive from the YANG model)
  2. Calls the dblib layer to fetch status from ES/MariaDB
  3. Has no OSGi imports
  4. Takes all dependencies via constructor injection

Also show me how to register it in LightyDataProvider.initProcedure().
```

---

### 5.8 Register YANG Models

```
Read:
  @ccsdk-features/sdnr/wt/data-provider/model/src/main/yang/data-provider.yang
  @reference/lighty-rnc-app-snippets/lightyControllerConfig.json

Produce the topLevelModels entries I need to add to my:
  @lighty-sdnr/src/main/resources/lightyControllerConfig.json

Include data-provider.yang and every module it imports (transitive closure).
For each entry show: moduleName, revision, nameSpace.
If you are unsure of a revision, say so — do not guess.
```

---

### 5.9 Update RestConfRequest.java

```
Read:
  @darpan/core-ran-management/src/main/java/org/iosmcn/nr/ran/sdnc/RestConfRequest.java
  @context/API-INVENTORY.md  (ADR-001 section)

Show me the exact diff (unified format) to change the CONFIG and OPERATIONAL
datastore URL patterns from the ODL restconf/config and restconf/operational
prefix style to the RFC 8040 rests/data?content= style.

Do not change anything else in the file.
```

---

### 5.10 End-of-Session Update

```
We finished the following today: [brief description]

Please:
  1. Update @context/PROGRESS.md — mark completed tasks ✅ and add a session log line.
  2. Update @context/CHALLENGES.md — add any new gotchas we discovered.
  3. Update @context/DECISIONS.md — add any new ADR if we made a significant choice.
  4. Summarise in 3 bullet points what the NEXT session should start with.
```

---

## 6. Token Efficiency Tips for Opus

Since you're paying per token with Opus, these habits matter:

- **Always use `@file` references** rather than pasting code into the prompt.
  Cursor fetches only what it needs; pasting duplicates tokens.
- **One concern per prompt.** Don't ask Opus to audit + migrate + test in one
  shot. Break it into the templates above.
- **Use Composer for code edits, Chat for questions.**
  Composer (Cmd+I) applies diffs directly. Chat (Cmd+L) is for analysis.
- **Prefix prompts with context files**, not full file dumps.
  `Read @context/PROGRESS.md first` costs far fewer tokens than pasting it.
- **The session start prompt matters most.** Without it, Opus re-discovers
  context every session, wasting your budget.
- If you repeat a prompt in chat three times, promote it to a `.mdc` rule
  — it becomes free context instead of charged tokens.
- **`.cursorignore`:** tell Cursor not to index the Maven `target/` directories
  across all repos — they add massive noise to the index.

  ```
  # .cursorignore (place in workspace root)
  repos/*/target/
  repos/**/.git/
  repos/**/node_modules/
  scratch/
  ```

---

## 7. Quick-Start Checklist

Before your first coding session:

- [ ] Create `sdnr-lighty-migration/` root folder
- [ ] Create `.cursor/` rules folder and write the four `.mdc` files above
- [ ] Clone the four repos into `repos/`
- [ ] Copy `sdnr-lighty-migration-design.md` into `context/ARCHITECTURE.md`
- [ ] Fill `context/API-INVENTORY.md` from the darpan analysis
- [ ] Write `context/DECISIONS.md` with ADR-001 through ADR-004
- [ ] Create blank `context/PROGRESS.md` and `context/CHALLENGES.md`
- [ ] Extract the six reference snippets into `reference/lighty-rnc-app-snippets/`
- [ ] Copy `YangSchemaServlet.java`, `LightyYangSchemaModule.java`, `README.md`
      into `reference/yang-schema-servlet/`
- [ ] Save the `.code-workspace` file and open it in Cursor
- [ ] Add `.cursorignore` in workspace root
- [ ] Run the session start prompt and confirm Opus understands the project
      before touching any code

---

*This setup guide is designed to be the single source of truth for your Cursor
workspace. Update `context/PROGRESS.md` after every session.*
