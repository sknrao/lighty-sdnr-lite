# RFC 8040 Migration Diff

Here is the diff of the changes made to `RestConfRequest.java` and `RestConfController.java` to unify the ODL/SDNR API calls on the newer RFC 8040 standard (`rests/data`).

### `core-ran-management/.../RestConfRequest.java`
```diff
--- a/core-ran-management/src/main/java/org/iosmcn/nr/ran/sdnc/RestConfRequest.java
+++ b/core-ran-management/src/main/java/org/iosmcn/nr/ran/sdnc/RestConfRequest.java
@@ -100,7 +100,7 @@
 
     /* RESTCONF/ODL DataStore enum */
     public enum DataStore {
-	CONFIG("restconf/config"), OPERATIONAL("restconf/operational"), DATA("rests/data"), OPERATIONS("rests/operations"), SCHEMA("yang-schema");
+	DATA("rests/data"), OPERATIONS("rests/operations"), SCHEMA("yang-schema");
 
 	public String path;
 
```

### `web-visualizer/.../RestConfController.java`
```diff
--- a/web-visualizer/src/main/java/org/iosmcn/nr/smo/web/controller/ran/RestConfController.java
+++ b/web-visualizer/src/main/java/org/iosmcn/nr/smo/web/controller/ran/RestConfController.java
@@ -69,7 +69,7 @@
     @GetMapping("/nodes/operational")
     public ResponseEntity<?> getOperationalNodes() {
 	LOG.info("====Inside RestConfController#listNodes#operational");
-	return executor.get(DataStore.OPERATIONAL, "network-topology", "network-topology", null, null, "json");
+	return executor.get(DataStore.DATA, "network-topology", "network-topology", null, "content=nonconfig", "json");
     }
 
@@ -80,8 +80,8 @@
     @GetMapping("/node/{nodeId}/operational")
     public ResponseEntity<?> getOperationalNodeInfo(@PathVariable("nodeId") String nodeId) {
 	LOG.info("====Inside RestConfController#getOperationalNodeInfo for " + nodeId);
-	String path = "topology/topology-netconf/node/" + nodeId;
-	return executor.get(DataStore.OPERATIONAL, "network-topology", "network-topology", path, null, "json");
+	String path = "topology=topology-netconf/node=" + nodeId;
+	return executor.get(DataStore.DATA, "network-topology", "network-topology", path, "content=nonconfig", "json");
     }
 
@@ -91,7 +91,7 @@
     @GetMapping("/nodes/config")
     public ResponseEntity<?> getConfigNodes() {
 	LOG.info("====Inside RestConfController#listNodes#config");
-	return executor.get(DataStore.CONFIG, "network-topology", "network-topology", null, null, "json");
+	return executor.get(DataStore.DATA, "network-topology", "network-topology", null, "content=config", "json");
     }
 
@@ -102,8 +102,8 @@
     @GetMapping("/node/{nodeId}/config")
     public ResponseEntity<?> getConfigNodeInfo(@PathVariable("nodeId") String nodeId) {
 	LOG.info("====Inside RestConfController#getConfigNodeInfo for " + nodeId);
-	String path = "topology/topology-netconf/node/" + nodeId;
-	return executor.get(DataStore.CONFIG, "network-topology", "network-topology", path, null, "json");
+	String path = "topology=topology-netconf/node=" + nodeId;
+	return executor.get(DataStore.DATA, "network-topology", "network-topology", path, "content=config", "json");
     }
```
