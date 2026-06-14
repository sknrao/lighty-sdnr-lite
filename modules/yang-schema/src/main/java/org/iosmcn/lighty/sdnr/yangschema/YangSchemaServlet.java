/*
 * YangSchemaServlet.java
 *
 * Serves raw YANG source text for modules known to the Lighty controller,
 * replicating the ODL/SDNR  GET /yang-schema/{module}?revision=...&node=...
 * endpoint without any OSGi dependency.
 *
 * URL pattern:  /yang-schema/{moduleName}
 * Query params:
 *   revision  – optional, e.g. "2019-11-29"
 *   node      – optional nodeId; if present, resolves schema from the
 *               device's mounted SchemaContext instead of the controller's
 *               global context.
 *
 * ─── ODL / Yangtools version notes ───────────────────────────────────────
 *
 * PRE-Scandium (Magnesium / Phosphorus / Silicon):
 *   YangTextSchemaSource extends ByteSource (Guava).
 *   Use:  source.copyTo(resp.getOutputStream())
 *   SchemaSourceProvider returns ListenableFuture<YangTextSchemaSource>
 *
 * Scandium / Potassium / Calcium / Chromium and LATER:
 *   YangTextSchemaSource extends CharSource (Guava).
 *   Use:  source.copyTo(resp.getWriter())
 *   SchemaSourceProvider returns FluentFuture<YangTextSchemaSource>
 *   EffectiveModelContext replaces SchemaContext (same concept, new name).
 *
 * This file targets the PRE-Scandium API (ByteSource) which matches
 * most Lighty SDNR deployments as of 2024-2025.
 * A single TODO comment marks the one line to change for Scandium+.
 *
 * ─── Registration in your LightyModule ───────────────────────────────────
 *
 *   // In LightyYangSchemaModule.initProcedure():
 *   SchemaRepository schemaRepo =
 *       (SchemaRepository) lightyServices.getAdapterContext()
 *                                        .currentSerializer()
 *                                        .getRuntimeContext()
 *                                        .getSchemaContext();
 *   // Simpler: inject via constructor — see LightyYangSchemaModule.java
 *
 * ─────────────────────────────────────────────────────────────────────────
 */
package org.iosmcn.lighty.sdnr.yangschema;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;   // pre-Scandium
// import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext; // Scandium+
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servlet that serves raw YANG module source text.
 *
 * <p>It mirrors the behaviour of the ODL/SDNR {@code /yang-schema/} endpoint,
 * pulling sources from:
 * <ol>
 *   <li>The controller's global {@link SchemaRepository} (no {@code node} param), or</li>
 *   <li>The per-device mounted {@link DOMSchemaService} (with {@code node} param).</li>
 * </ol>
 *
 * <p>Register this servlet in your {@code LightyYangSchemaModule.initProcedure()}
 * against the URL pattern {@code /yang-schema/*}.
 */
public class YangSchemaServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(YangSchemaServlet.class);

    /** Timeout waiting for SchemaRepository to resolve a source. */
    private static final long SOURCE_TIMEOUT_SECONDS = 10L;

    /** topology-netconf — the standard NETCONF mount topology. */
    private static final String TOPOLOGY_NETCONF = "topology-netconf";

    // ── Injected services (no OSGi; plain constructor injection) ──────────

    /**
     * Controller-level schema repository.
     *
     * <p>In Lighty, obtain this from:
     * <pre>
     *   lightyController.getServices()
     *                   .getDOMSchemaService()      // DOMSchemaService
     *                   // then cast or adapt to SchemaRepository —
     *                   // see LightyYangSchemaModule for the correct cast path
     * </pre>
     *
     * <p>SchemaRepository is the read side of the SharedSchemaRepository that
     * yangtools builds during startup.  It holds YangTextSchemaSource entries
     * for every module on the classpath.
     */
    private final SchemaRepository globalSchemaRepository;

    /**
     * Gives access to per-device mounted schema contexts.
     *
     * <p>From LightyServices: {@code lightyServices.getDOMMountPointService()}.
     */
    private final DOMMountPointService mountPointService;

    // ─────────────────────────────────────────────────────────────────────

    /**
     * Constructs the servlet.
     *
     * @param globalSchemaRepository controller-global schema repo from LightyServices
     * @param mountPointService      DOM mount-point service from LightyServices
     */
    public YangSchemaServlet(final SchemaRepository globalSchemaRepository,
                             final DOMMountPointService mountPointService) {
        this.globalSchemaRepository = globalSchemaRepository;
        this.mountPointService      = mountPointService;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Servlet entry point
    // ─────────────────────────────────────────────────────────────────────

    @Override
    protected void doGet(final HttpServletRequest req,
                         final HttpServletResponse resp) throws IOException {

        // ── 1. Parse URL and query parameters ────────────────────────────
        //
        // Expected path info:  /{moduleName}
        // Query string:        ?revision=2019-11-29&node=O-RAN-Node-1
        //
        final String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.length() <= 1) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Module name is required: /yang-schema/{moduleName}");
            return;
        }

        // Strip leading "/"
        final String moduleName = pathInfo.substring(1);
        if (moduleName.isBlank()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Empty module name");
            return;
        }

        final String revisionParam = req.getParameter("revision");  // nullable
        final String nodeId        = req.getParameter("node");       // nullable

        LOG.debug("yang-schema request: module={} revision={} node={}",
                  moduleName, revisionParam, nodeId);

        // ── 2. Build SourceIdentifier ─────────────────────────────────────
        //
        // SourceIdentifier(name, revision) is yangtools' primary lookup key.
        // If no revision is given we request the latest available revision.
        //
        final SourceIdentifier sourceId = buildSourceId(moduleName, revisionParam);

        // ── 3. Resolve: device-mounted OR controller-global ───────────────
        if (nodeId != null && !nodeId.isBlank()) {
            serveDeviceModuleSource(resp, nodeId, moduleName, revisionParam, sourceId);
        } else {
            serveControllerModuleSource(resp, sourceId);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Controller-global source resolution
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Fetches YANG source from the controller's global SchemaRepository and
     * writes it directly to the HTTP response.
     */
    private void serveControllerModuleSource(final HttpServletResponse resp,
                                             final SourceIdentifier sourceId)
            throws IOException {

        LOG.debug("Resolving controller-global source for {}", sourceId);

        try {
            // getSource() is asynchronous; block with a reasonable timeout.
            // Pre-Scandium return type: ListenableFuture<YangTextSchemaSource>
            // Scandium+:               FluentFuture<YangTextSchemaSource>
            // Both implement Future<YangTextSchemaSource>, so .get() works for both.
            YangTextSource source = globalSchemaRepository.getSource(sourceId).get(SOURCE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            writeSource(resp, source);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Interrupted while fetching schema source for: " + sourceId);
        } catch (TimeoutException e) {
            sendError(resp, HttpServletResponse.SC_GATEWAY_TIMEOUT,
                    "Timeout resolving schema source for: " + sourceId);
        } catch (ExecutionException e) {
            // MissingSchemaSourceException is the typical cause when the module
            // name / revision doesn't match anything in the schema repository.
            LOG.warn("Schema source not found in controller context: {}", sourceId, e);
            sendError(resp, HttpServletResponse.SC_NOT_FOUND,
                    "Module not found in controller schema: " + sourceId
                    + ". Cause: " + rootCauseMessage(e));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Device-mounted source resolution
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Resolves YANG source from a NETCONF-mounted device's own SchemaContext.
     *
     * <p>The device may have loaded its models from the controller's schema
     * repository (via classpath) OR fetched them live via
     * {@code ietf-netconf-monitoring:get-schema}.  Either way, they are stored
     * in the device's per-mount SchemaContext, accessible via
     * {@link DOMSchemaService} from the mount point.
     *
     * <p>Strategy:
     * <ol>
     *   <li>Locate the mount point for {@code nodeId} in topology-netconf.</li>
     *   <li>Obtain {@link DOMSchemaService} from the mount point's services.</li>
     *   <li>Use the device's {@code SchemaContext} to look up the module.</li>
     *   <li>If found, build a {@link SourceIdentifier} with the actual revision
     *       and fetch via the mount point's {@link SchemaRepository}; otherwise
     *       fall back to the controller-global repository.</li>
     * </ol>
     */
    private void serveDeviceModuleSource(final HttpServletResponse resp,
                                         final String nodeId,
                                         final String moduleName,
                                         final String revisionParam,
                                         final SourceIdentifier sourceId)
            throws IOException {

        LOG.debug("Resolving device source for node={} module={}", nodeId, sourceId);

        // ── 3a. Locate mount point ────────────────────────────────────────
        final YangInstanceIdentifier mountPath = buildMountPath(nodeId);
        final Optional<DOMMountPoint> mountPointOpt =
                mountPointService.getMountPoint(mountPath);

        if (mountPointOpt.isEmpty()) {
            LOG.warn("Mount point not found for node={}", nodeId);
            sendError(resp, HttpServletResponse.SC_NOT_FOUND,
                    "No mounted device found for nodeId: " + nodeId
                    + ". Ensure the device is connected in topology-netconf.");
            return;
        }

        final DOMMountPoint mountPoint = mountPointOpt.get();

        // ── 3b. Get DOMSchemaService from the mount point ─────────────────
        final Optional<DOMSchemaService> mountedSchemaServiceOpt =
                mountPoint.getService(DOMSchemaService.class);

        if (mountedSchemaServiceOpt.isEmpty()) {
            LOG.warn("DOMSchemaService not available on mount point for node={}", nodeId);
            sendError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Schema service unavailable for node: " + nodeId);
            return;
        }

        final DOMSchemaService mountedSchemaService = mountedSchemaServiceOpt.get();
        DOMYangTextSourceProvider extension = mountedSchemaService.extension(DOMYangTextSourceProvider.class);

        // ── 3c. Resolve the module inside the device SchemaContext ─────────
        //
        // pre-Scandium: getGlobalContext() returns SchemaContext
        // Scandium+:    getGlobalContext() returns EffectiveModelContext
        //               (cast is safe; EffectiveModelContext extends SchemaContext)
        //
        final SchemaContext deviceContext = mountedSchemaService.getGlobalContext();

        final Optional<Module> moduleOpt = findModule(deviceContext, moduleName, revisionParam);

        if (moduleOpt.isEmpty()) {
            // Module not in device context — try the controller-global repo
            // as a fallback (common for IETF/standard models that are bundled
            // in the controller classpath).
            LOG.info("Module {} not found in device context for node={}; "
                     + "falling back to controller-global repository",
                     moduleName, nodeId);
            serveControllerModuleSource(resp, sourceId);
            return;
        }

        final Module module = moduleOpt.get();

        // Reconstruct SourceIdentifier with the concrete revision from the module
        // (handles the case where revisionParam was null / "latest")
        final SourceIdentifier resolvedId = module.getRevision()
                .map(rev -> new SourceIdentifier(moduleName, rev))
                .orElse(new SourceIdentifier(moduleName));

        // ── 3d. Fetch YANG text via the device's SchemaRepository ──────────
        //
        // The device mount point may expose a SchemaRepository via its services.
        // If not, fall back to the controller-global repository which caches
        // all sources it has loaded, including device-fetched ones.
        //
        final Optional<SchemaRepository> deviceRepoOpt =
                mountPoint.getService(SchemaRepository.class);

        if (deviceRepoOpt.isPresent()) {
            LOG.debug("Using device-specific SchemaRepository for node={}", nodeId);
            serveFromRepository(resp, deviceRepoOpt.get(), resolvedId);
        } else {
            // Fall back to the controller global repo — the source was downloaded
            // and registered there during device schema negotiation.
            LOG.debug("Device SchemaRepository not available; using global repo for node={}",
                      nodeId);
            serveControllerModuleSource(resp, resolvedId);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Shared repository fetch + write
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Fetches YANG source from {@code repository} and writes it to the response.
     * Identical logic to {@link #serveControllerModuleSource} but parameterised
     * on the repository to use.
     */
    private void serveFromRepository(final HttpServletResponse resp,
                                     final SchemaRepository repository,
                                     final SourceIdentifier sourceId)
            throws IOException {
        try {
            final YangTextSchemaSource source =
                    repository.getSchemaSource(sourceId, YangTextSchemaSource.class)
                              .get(SOURCE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            writeSource(resp, source);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Interrupted while fetching schema source for: " + sourceId);
        } catch (TimeoutException e) {
            sendError(resp, HttpServletResponse.SC_GATEWAY_TIMEOUT,
                    "Timeout resolving schema source for: " + sourceId);
        } catch (ExecutionException e) {
            LOG.warn("Schema source not found in repository: {}", sourceId, e);
            sendError(resp, HttpServletResponse.SC_NOT_FOUND,
                    "Module not found: " + sourceId + ". Cause: " + rootCauseMessage(e));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Write YANG source to HTTP response
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Writes the YANG source to the HTTP response.
     *
     * <p><b>Pre-Scandium</b> ({@code YangTextSchemaSource extends ByteSource}):
     * writes to {@code OutputStream} with content-type {@code text/plain}.
     *
     * <p><b>Scandium+</b> ({@code YangTextSchemaSource extends CharSource}):
     * change the TODO line below to write to {@code Writer} instead.
     */
    private static void writeSource(final HttpServletResponse resp,
                                    final YangTextSchemaSource source)
            throws IOException {

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("text/plain; charset=UTF-8");

        // ── PRE-SCANDIUM (ByteSource) ─────────────────────────────────────
        // YangTextSchemaSource.copyTo(OutputStream) streams raw bytes.
        source.copyTo(resp.getOutputStream());
        resp.getOutputStream().flush();

        // ── SCANDIUM+ (CharSource) — replace the two lines above with: ────
        // TODO: if upgrading to Scandium/Potassium/Calcium yangtools,
        //       comment out the ByteSource block above and uncomment this:
        //
        // resp.setCharacterEncoding("UTF-8");
        // source.copyTo(resp.getWriter());
        // resp.getWriter().flush();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper: build SourceIdentifier
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Builds a {@link SourceIdentifier} from the module name and optional
     * revision string (format: {@code "YYYY-MM-DD"}).
     */
    private static SourceIdentifier buildSourceId(final String moduleName,
                                                   final String revisionParam) {
        if (revisionParam != null && !revisionParam.isBlank()) {
            try {
                return new SourceIdentifier(moduleName,
                                            Revision.of(revisionParam));
            } catch (IllegalArgumentException e) {
                LOG.warn("Ignoring unparseable revision '{}'; using name-only lookup",
                         revisionParam);
            }
        }
        // Name-only: yangtools will return the latest available revision.
        return new SourceIdentifier(moduleName);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper: find Module in SchemaContext
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Looks up a {@link Module} by name and optional revision inside a
     * {@link SchemaContext}.
     */
    private static Optional<Module> findModule(final SchemaContext ctx,
                                                final String moduleName,
                                                final String revisionParam) {
        if (revisionParam != null && !revisionParam.isBlank()) {
            try {
                final Revision rev = Revision.of(revisionParam);
                return ctx.findModule(moduleName, rev);
            } catch (IllegalArgumentException e) {
                LOG.warn("Could not parse revision '{}', falling back to name-only lookup",
                         revisionParam);
            }
        }
        // Name-only: return the first (latest) module with that name.
        return ctx.findModules(moduleName).stream().findFirst();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper: build NETCONF topology mount path for a node
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Constructs the {@link YangInstanceIdentifier} for a node in
     * {@code topology-netconf}, i.e.:
     * <pre>
     *   /network-topology/topology[topology-id='topology-netconf']/node[node-id='{nodeId}']
     * </pre>
     *
     * <p>This is the DOM-layer path used by {@link DOMMountPointService#getMountPoint}.
     */
    private static YangInstanceIdentifier buildMountPath(final String nodeId) {
        return YangInstanceIdentifier.builder()
                .node(NetworkTopology.QNAME)
                .node(Topology.QNAME)
                .nodeWithKey(Topology.QNAME,
                             TopologyKey.class,
                             new TopologyKey(new TopologyId(TOPOLOGY_NETCONF)))
                .node(Node.QNAME)
                .nodeWithKey(Node.QNAME,
                             NodeKey.class,
                             new NodeKey(new NodeId(nodeId)))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper: error response
    // ─────────────────────────────────────────────────────────────────────

    private static void sendError(final HttpServletResponse resp,
                                   final int status,
                                   final String message) throws IOException {
        resp.setStatus(status);
        resp.setContentType("text/plain; charset=UTF-8");
        final PrintWriter w = resp.getWriter();
        w.println(message);
        w.flush();
        LOG.debug("Responded {} – {}", status, message);
    }

    private static String rootCauseMessage(final ExecutionException e) {
        Throwable cause = e.getCause();
        while (cause != null && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause != null ? cause.getMessage() : e.getMessage();
    }
}
