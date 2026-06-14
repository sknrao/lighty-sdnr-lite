/*
 * LightyDataProvider.java
 *
 * AbstractLightyModule that registers the 4 darpan-facing data-provider RPCs
 * into MD-SAL's RPC service. Uses a NoDb (no database) backend that reads
 * network element connections from the NETCONF topology in the operational
 * datastore, and returns empty results for other queries.
 *
 * This replaces the OSGi-based DataProviderImpl and DataProviderServiceImpl
 * from ccsdk-features, removing all OSGi, Blueprint, and Karaf dependencies.
 *
 * ─── Usage in Main.java ───────────────────────────────────────────
 *
 *   // After pnfModule.start() (step 5)
 *   LightyDataProvider dataProviderModule = new LightyDataProvider(
 *       lightyController.getServices());
 *   dataProviderModule.start().get();
 *
 * ──────────────────────────────────────────────────────────────────
 */
package org.iosmcn.lighty.sdnr.dataprovider;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.lighty.core.controller.api.AbstractLightyModule;
import io.lighty.core.controller.api.LightyServices;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.ReadFaultcurrentList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.ReadFaultcurrentListInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.ReadFaultcurrentListOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.ReadFaultcurrentListOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.ReadInventoryList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.ReadInventoryListInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.ReadInventoryListOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.ReadInventoryListOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.ReadNetworkElementConnectionList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.ReadNetworkElementConnectionListInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.ReadNetworkElementConnectionListOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.ReadNetworkElementConnectionListOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.ReadStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.ReadStatusInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.ReadStatusOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.ReadStatusOutputBuilder;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lighty module that registers 4 data-provider RPCs used by the darpan dashboard.
 *
 * <p>Registered RPCs:
 * <ul>
 *   <li>{@code data-provider:read-status}</li>
 *   <li>{@code data-provider:read-faultcurrent-list}</li>
 *   <li>{@code data-provider:read-inventory-list}</li>
 *   <li>{@code data-provider:read-network-element-connection-list}</li>
 * </ul>
 */
public class LightyDataProvider extends AbstractLightyModule {

    private static final Logger LOG = LoggerFactory.getLogger(LightyDataProvider.class);

    private final LightyServices lightyServices;
    private Registration rpcRegistration;

    /**
     * @param lightyServices services from a started {@code LightyController}
     */
    public LightyDataProvider(final LightyServices lightyServices) {
        this.lightyServices = lightyServices;
    }

    // ─────────────────────────────────────────────────────────────────
    // AbstractLightyModule contract
    // ─────────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings({"checkstyle:illegalCatch"})
    protected boolean initProcedure() {
        LOG.info("Initializing LightyDataProvider …");

        try {
            // ── Extract MD-SAL services ───────────────────────────────
            final RpcProviderService rpcProviderService = lightyServices.getRpcProviderService();

            // ── Register RPCs ─────────────────────────────────────────
            LOG.info("Registering data-provider RPCs …");
            this.rpcRegistration = rpcProviderService.registerRpcImplementations(
                List.of(
                    new RpcHelper<>(ReadStatus.class,
                        this::readStatus),
                    new RpcHelper<>(ReadFaultcurrentList.class,
                        this::readFaultcurrentList),
                    new RpcHelper<>(ReadInventoryList.class,
                        this::readInventoryList),
                    new RpcHelper<>(ReadNetworkElementConnectionList.class,
                        this::readNetworkElementConnectionList)
                )
            );

            LOG.info("LightyDataProvider initialized — 4 RPCs registered.");
            return true;

        } catch (Exception e) {
            LOG.error("Failed to initialize LightyDataProvider", e);
            return false;
        }
    }

    @Override
    protected boolean stopProcedure() {
        LOG.info("Stopping LightyDataProvider …");
        if (this.rpcRegistration != null) {
            this.rpcRegistration.close();
            LOG.info("data-provider RPC registrations closed.");
        }
        LOG.info("LightyDataProvider stopped.");
        return true;
    }

    // ─────────────────────────────────────────────────────────────────
    // RPC implementations (NoDb backend — returns empty/stub results)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Handles {@code data-provider:read-status}.
     * Returns an empty status output (NoDb — no database to query).
     */
    private ListenableFuture<@NonNull RpcResult<@NonNull ReadStatusOutput>> readStatus(
            final ReadStatusInput input) {
        LOG.debug("RPC Request: readStatus with input {}", input);
        try {
            ReadStatusOutput output = new ReadStatusOutputBuilder().build();
            return Futures.immediateFuture(RpcResultBuilder.success(output).build());
        } catch (Exception e) {
            LOG.error("Error processing readStatus", e);
            return Futures.immediateFuture(
                RpcResultBuilder.<ReadStatusOutput>failed()
                    .withError(ErrorType.APPLICATION, "readStatus failed: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Handles {@code data-provider:read-faultcurrent-list}.
     * Returns an empty fault list (NoDb — no database to query).
     */
    private ListenableFuture<@NonNull RpcResult<@NonNull ReadFaultcurrentListOutput>> readFaultcurrentList(
            final ReadFaultcurrentListInput input) {
        LOG.debug("RPC Request: readFaultcurrentList with input {}", input);
        try {
            ReadFaultcurrentListOutput output = new ReadFaultcurrentListOutputBuilder().build();
            return Futures.immediateFuture(RpcResultBuilder.success(output).build());
        } catch (Exception e) {
            LOG.error("Error processing readFaultcurrentList", e);
            return Futures.immediateFuture(
                RpcResultBuilder.<ReadFaultcurrentListOutput>failed()
                    .withError(ErrorType.APPLICATION, "readFaultcurrentList failed: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Handles {@code data-provider:read-inventory-list}.
     * Returns an empty inventory list (NoDb — no database to query).
     */
    private ListenableFuture<@NonNull RpcResult<@NonNull ReadInventoryListOutput>> readInventoryList(
            final ReadInventoryListInput input) {
        LOG.debug("RPC Request: readInventoryList with input {}", input);
        try {
            ReadInventoryListOutput output = new ReadInventoryListOutputBuilder().build();
            return Futures.immediateFuture(RpcResultBuilder.success(output).build());
        } catch (Exception e) {
            LOG.error("Error processing readInventoryList", e);
            return Futures.immediateFuture(
                RpcResultBuilder.<ReadInventoryListOutput>failed()
                    .withError(ErrorType.APPLICATION, "readInventoryList failed: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Handles {@code data-provider:read-network-element-connection-list}.
     * Returns an empty connection list (NoDb — no database to query).
     * The actual NETCONF topology reading can be added in a follow-up.
     */
    private ListenableFuture<@NonNull RpcResult<@NonNull ReadNetworkElementConnectionListOutput>> readNetworkElementConnectionList(
            final ReadNetworkElementConnectionListInput input) {
        LOG.debug("RPC Request: readNetworkElementConnectionList with input {}", input);
        try {
            ReadNetworkElementConnectionListOutput output =
                new ReadNetworkElementConnectionListOutputBuilder().build();
            return Futures.immediateFuture(RpcResultBuilder.success(output).build());
        } catch (Exception e) {
            LOG.error("Error processing readNetworkElementConnectionList", e);
            return Futures.immediateFuture(
                RpcResultBuilder.<ReadNetworkElementConnectionListOutput>failed()
                    .withError(ErrorType.APPLICATION,
                        "readNetworkElementConnectionList failed: " + e.getMessage())
                    .build());
        }
    }
}
