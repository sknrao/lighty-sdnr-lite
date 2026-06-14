/*
 * LightyYangSchemaModule.java
 *
 * AbstractLightyModule that:
 *   1. Obtains SchemaRepository and DOMMountPointService from LightyServices.
 *   2. Instantiates YangSchemaServlet (plain Java, no OSGi).
 *   3. Registers the servlet into the CommunityRestConf Jetty server.
 *
 * Plug this module into your Lighty main class AFTER lightyController and
 * communityRestConf have been started, but BEFORE accepting external traffic.
 *
 * ─── Usage in your Lighty main ───────────────────────────────────────────
 *
 *   lightyController.start().get();
 *   communityRestConf.start().get();
 *
 *   LightyYangSchemaModule schemaModule = new LightyYangSchemaModule(
 *       lightyController.getServices(),
 *       communityRestConf
 *   );
 *   schemaModule.start().get();  // registers /yang-schema/* servlet
 *
 *   // ... start NETCONF SB, LightyDataProvider, etc.
 *
 * ─────────────────────────────────────────────────────────────────────────
 *
 * ─── How SchemaRepository is obtained ────────────────────────────────────
 *
 * Lighty does not expose SchemaRepository directly on LightyServices.
 * It is reachable via two routes depending on your Lighty version:
 *
 * Route A (recommended, works on Lighty 18+):
 *   DOMSchemaService exposes a service extension called
 *   DOMYangTextSourceProvider which IS a SchemaSourceProvider<YangTextSchemaSource>.
 *   Cast it to SchemaRepository if it implements that interface (it does in
 *   the opendaylight-yangtools SharedSchemaRepository impl).
 *
 * Route B (older Lighty / ODL Aluminium):
 *   The SharedSchemaRepository is the concrete class behind DOMSchemaService.
 *   It implements both SchemaRepository and SchemaSourceRegistry.
 *   Cast DOMSchemaService → SharedSchemaRepository (package-private friend cast
 *   is cleaner via the extension mechanism shown below).
 *
 * Both routes are shown below; use whichever compiles against your version.
 *
 * ─────────────────────────────────────────────────────────────────────────
 */
package org.iosmcn.lighty.sdnr.yangschema;

import io.lighty.core.controller.api.AbstractLightyModule;
import io.lighty.core.controller.api.LightyServices;
import io.lighty.modules.northbound.restconf.community.impl.CommunityRestConf;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService.YangTextSourceExtension;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Lighty module that registers the {@link YangSchemaServlet} into the running
 * CommunityRestConf Jetty instance.
 */
public class LightyYangSchemaModule extends AbstractLightyModule {

    private static final Logger LOG = LoggerFactory.getLogger(LightyYangSchemaModule.class);

    /** URL pattern that replicates the original ODL endpoint. */
    private static final String SERVLET_PATH = "/yang-schema/*";

    private final LightyServices lightyServices;
    private final CommunityRestConf communityRestConf;

    // Held for potential future cleanup (servlet has no close(), but keep ref)
    private YangSchemaServlet yangSchemaServlet;

    /**
     * @param lightyServices    services from a started {@code LightyController}
     * @param communityRestConf a started {@code CommunityRestConf} instance
     *                          whose Jetty server will host the new servlet
     */
    public LightyYangSchemaModule(final LightyServices lightyServices,
                                   final CommunityRestConf communityRestConf) {
        this.lightyServices   = lightyServices;
        this.communityRestConf = communityRestConf;
    }

    // ─────────────────────────────────────────────────────────────────────
    // AbstractLightyModule contract
    // ─────────────────────────────────────────────────────────────────────

    @Override
    protected boolean initProcedure() {

        LOG.info("Initializing LightyYangSchemaModule …");

        // ── 1. Resolve SchemaRepository ───────────────────────────────────
        final SchemaRepository schemaRepository = resolveSchemaRepository();
        if (schemaRepository == null) {
            LOG.error("Could not obtain SchemaRepository from LightyServices. "
                    + "YangSchemaServlet will NOT be registered.");
            return false;
        }

        // ── 2. Obtain DOMMountPointService ────────────────────────────────
        final DOMMountPointService mountPointService =
                lightyServices.getDOMMountPointService();

        // ── 3. Instantiate the servlet ────────────────────────────────────
        yangSchemaServlet = new YangSchemaServlet(schemaRepository, mountPointService);

        // ── 4. Register into CommunityRestConf's Jetty ───────────────────
        final boolean registered = registerServlet(yangSchemaServlet);
        if (!registered) {
            LOG.error("Failed to register YangSchemaServlet.");
            return false;
        }

        LOG.info("YangSchemaServlet registered at {}", SERVLET_PATH);
        return true;
    }

    @Override
    protected boolean stopProcedure() {
        // YangSchemaServlet is stateless — nothing to close.
        LOG.info("LightyYangSchemaModule stopped.");
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────
    // SchemaRepository resolution strategies
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Tries two strategies to obtain a {@link SchemaRepository}:
     *
     * <ol>
     *   <li><b>Extension route</b> — Lighty 18+ / ODL Silicon+:
     *       {@code DOMSchemaService} provides a
     *       {@code DOMYangTextSourceProvider} extension which is also a
     *       {@code SchemaSourceProvider<YangTextSchemaSource>}.
     *       If the underlying impl also implements {@link SchemaRepository}
     *       (SharedSchemaRepository does), cast directly.</li>
     *
     *   <li><b>Direct cast route</b> — ODL Aluminium / older Lighty:
     *       The concrete class behind {@code DOMSchemaService} in yangtools
     *       is {@code GlobalSchemaContextHolder} or
     *       {@code SharedSchemaRepository}, both of which implement
     *       {@link SchemaRepository}.</li>
     * </ol>
     *
     * @return a {@link SchemaRepository}, or {@code null} if neither strategy works
     */
    private SchemaRepository resolveSchemaRepository() {

        final DOMSchemaService domSchemaService = lightyServices.getDOMSchemaService();

        // ── Strategy A: extension route (preferred) ───────────────────────
        //
        // DOMSchemaService.getExtensions() returns a Map keyed by extension class.
        // DOMYangTextSourceProvider is the extension that provides
        // SchemaSourceProvider<YangTextSchemaSource>.
        //
        // The class name changed between ODL releases:
        //   ODL Silicon/Aluminium: org.opendaylight.mdsal.dom.spi.DOMSchemaServiceExtension
        //   Lighty 18+:            same package, look for the text-source extension
        //
        try {
            final YangTextSourceExtension yangTextSchemaSourceExtension = lightyServices.getDOMSchemaService().extension(YangTextSourceExtension.class);
            final Map<DOMSchemaServiceExtension,
                      DOMSchemaServiceExtension> extensions =
                    (Map<DOMSchemaServiceExtension, DOMSchemaServiceExtension>)
                            (Map<?, ?>) domSchemaService.getExtensions();

            for (final DOMSchemaServiceExtension ext : extensions.values()) {
                if (ext instanceof SchemaRepository) {
                    LOG.debug("SchemaRepository obtained via DOMSchemaService extension: {}",
                              ext.getClass().getName());
                    return (SchemaRepository) ext;
                }
                if (ext instanceof SchemaSourceProvider) {
                    // SchemaSourceProvider that is also a SchemaRepository —
                    // SharedSchemaRepository implements both.
                    if (ext instanceof SchemaRepository) {
                        return (SchemaRepository) ext;
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("Extension route failed (may be expected on older Lighty): {}", e.getMessage());
        }

        // ── Strategy B: direct cast ───────────────────────────────────────
        //
        // On many Lighty versions the DOMSchemaService implementation class
        // IS a SchemaRepository.  This works when the impl is
        // GlobalSchemaContextHolder or SharedSchemaRepository.
        //
        if (domSchemaService instanceof SchemaRepository) {
            LOG.debug("SchemaRepository obtained via direct DOMSchemaService cast.");
            return (SchemaRepository) domSchemaService;
        }

        // ── Strategy C: Adapter context (Lighty 15–17) ───────────────────
        //
        // Some Lighty versions expose a BindingToNormalizedNodeCodec / AdapterContext
        // from which the RuntimeContext and thus the SchemaContext can be obtained.
        // However, SchemaContext ≠ SchemaRepository, so this route doesn't apply here.
        //
        // If you are on a version where neither A nor B works, register a
        // custom YangTextSchemaContextResolver during controller startup and
        // inject it here directly.
        //
        LOG.error("Could not resolve SchemaRepository from DOMSchemaService ({}). "
                + "Tried extension route and direct cast. "
                + "Consider injecting a YangTextSchemaContextResolver directly.",
                  domSchemaService.getClass().getName());
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Servlet registration into CommunityRestConf's Jetty
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Registers the servlet against Lighty's running Jetty server.
     *
     * <p>CommunityRestConf wraps a Jetty {@code Server}. The exact API to reach
     * its {@link ServletContextHandler} differs slightly between Lighty versions:
     *
     * <ul>
     *   <li>Lighty 18–21: {@code communityRestConf.getServer()} returns the
     *       Jetty {@code Server}; walk its handlers to find the context.</li>
     *   <li>Lighty 22+: a {@code getServletContextHandler()} helper may be
     *       available (check your build).</li>
     * </ul>
     *
     * <p>In both cases the approach below works: locate the first
     * {@link ServletContextHandler} in the handler tree and add to it.
     *
     * <p><b>Important</b>: Jetty allows adding servlets to a started context
     * only if the context was started with {@code allowNullPathInfo(true)} or
     * if servlet security constraints allow dynamic addition. For most Lighty
     * configurations this works out of the box. If it does not, stop/start the
     * context after adding (see the commented block below).
     */
    private boolean registerServlet(final YangSchemaServlet servlet) {
        try {
            // ── Obtain the Jetty Server from CommunityRestConf ─────────────
            //
            // CommunityRestConf exposes getServer() in Lighty 18+.
            // If your version does not have this method, use reflection:
            //
            //   Field f = CommunityRestConf.class.getDeclaredField("server");
            //   f.setAccessible(true);
            //   Server server = (Server) f.get(communityRestConf);
            //
            final org.eclipse.jetty.server.Server jettyServer =
                    communityRestConf.getServer();

            // ── Walk handler tree to find ServletContextHandler ────────────
            final ServletContextHandler ctx =
                    findServletContextHandler(jettyServer.getHandler());

            if (ctx == null) {
                LOG.error("Could not locate a ServletContextHandler in the "
                        + "CommunityRestConf Jetty server.");
                return false;
            }

            // ── Add servlet to the running context ─────────────────────────
            //
            // ServletContextHandler.addServlet() is safe on a running context
            // in Jetty 9/10/11 when the context was started with
            // setAllowNullPathInfo(true) (Lighty default).
            //
            ctx.addServlet(new ServletHolder("yang-schema", servlet), SERVLET_PATH);

            // If the above silently fails (no exception but servlet not active),
            // stop and restart the context:
            //
            //   ctx.stop();
            //   ctx.addServlet(new ServletHolder("yang-schema", servlet), SERVLET_PATH);
            //   ctx.start();
            //
            // This is safe only if no requests are in-flight. In practice,
            // Lighty's NETCONF SB starts before external traffic arrives, so
            // this window is fine.

            LOG.info("Registered {} at path {}", servlet.getClass().getSimpleName(),
                     SERVLET_PATH);
            return true;

        } catch (NoSuchMethodError | NoSuchMethodException e) {
            LOG.error("communityRestConf.getServer() is not available on this Lighty version. "
                    + "Add a direct Jetty server reference or use reflection fallback.", e);
            return false;
        } catch (Exception e) {
            LOG.error("Unexpected error registering YangSchemaServlet", e);
            return false;
        }
    }

    /**
     * Walks the Jetty handler tree (depth-first) and returns the first
     * {@link ServletContextHandler} found.
     *
     * @param handler root handler of the Jetty server
     * @return the context handler, or {@code null} if not found
     */
    private static ServletContextHandler findServletContextHandler(
            final org.eclipse.jetty.server.Handler handler) {

        if (handler == null) {
            return null;
        }
        if (handler instanceof ServletContextHandler) {
            return (ServletContextHandler) handler;
        }
        if (handler instanceof org.eclipse.jetty.server.handler.HandlerWrapper) {
            return findServletContextHandler(
                    ((org.eclipse.jetty.server.handler.HandlerWrapper) handler).getHandler());
        }
        if (handler instanceof org.eclipse.jetty.server.handler.HandlerCollection) {
            for (final org.eclipse.jetty.server.Handler child :
                    ((org.eclipse.jetty.server.handler.HandlerCollection) handler).getHandlers()) {
                final ServletContextHandler result = findServletContextHandler(child);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
