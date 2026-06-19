/*
 * LightyDataProvider.java
 *
<<<<<<< HEAD
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
=======
 * AbstractLightyModule that registers the darpan-facing data-provider RPCs
 * into MD-SAL's RPC service. Uses a Live NETCONF backend that reads
 * network element connections from the NETCONF topology in the operational
 * datastore, and implements create/update/delete by modifying the config datastore.
>>>>>>> 1671bff (successfull test of data-provider)
 */
package org.iosmcn.lighty.sdnr.dataprovider;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.lighty.core.controller.api.AbstractLightyModule;
import io.lighty.core.controller.api.LightyServices;
<<<<<<< HEAD
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
=======
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.ReadTransaction;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251028.ConnectionOper.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251028.credentials.credentials.LoginPwUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251028.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251103.NetconfNodeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251103.NetconfNodeAugmentBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251103.netconf.node.augment.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251103.netconf.node.augment.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.status.entity.FaultsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.status.entity.NetworkElementConnectionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.read.status.output.DataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.read.network.element.connection.list.output.PaginationBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

>>>>>>> 1671bff (successfull test of data-provider)
public class LightyDataProvider extends AbstractLightyModule {

    private static final Logger LOG = LoggerFactory.getLogger(LightyDataProvider.class);

    private final LightyServices lightyServices;
    private Registration rpcRegistration;

<<<<<<< HEAD
    /**
     * @param lightyServices services from a started {@code LightyController}
     */
=======
    private static final InstanceIdentifier<Topology> NETCONF_TOPOLOGY_IID =
            InstanceIdentifier.builder(NetworkTopology.class)
                    .child(Topology.class, new TopologyKey(new TopologyId("topology-netconf")))
                    .build();

>>>>>>> 1671bff (successfull test of data-provider)
    public LightyDataProvider(final LightyServices lightyServices) {
        this.lightyServices = lightyServices;
    }

<<<<<<< HEAD
    // ─────────────────────────────────────────────────────────────────
    // AbstractLightyModule contract
    // ─────────────────────────────────────────────────────────────────

=======
>>>>>>> 1671bff (successfull test of data-provider)
    @Override
    @SuppressWarnings({"checkstyle:illegalCatch"})
    protected boolean initProcedure() {
        LOG.info("Initializing LightyDataProvider …");

        try {
<<<<<<< HEAD
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
=======
            final RpcProviderService rpcProviderService = lightyServices.getRpcProviderService();

            LOG.info("Registering data-provider RPCs …");
            this.rpcRegistration = rpcProviderService.registerRpcImplementations(
                List.of(
                    new RpcHelper<>(ReadStatus.class, this::readStatus),
                    new RpcHelper<>(ReadNetworkElementConnectionList.class, this::readNetworkElementConnectionList),
                    new RpcHelper<>(CreateNetworkElementConnection.class, this::createNetworkElementConnection),
                    new RpcHelper<>(UpdateNetworkElementConnection.class, this::updateNetworkElementConnection),
                    new RpcHelper<>(DeleteNetworkElementConnection.class, this::deleteNetworkElementConnection),
                    
                    // Stubs for remaining to prevent 500 errors
                    new RpcHelper<>(ReadFaultcurrentList.class, input -> stubList(new ReadFaultcurrentListOutputBuilder().build())),
                    new RpcHelper<>(ReadInventoryList.class, input -> stubList(new ReadInventoryListOutputBuilder().build())),
                    new RpcHelper<>(ReadInventoryDeviceList.class, input -> stubList(new ReadInventoryDeviceListOutputBuilder().build())),
                    new RpcHelper<>(ReadFaultlogList.class, input -> stubList(new ReadFaultlogListOutputBuilder().build())),
                    new RpcHelper<>(ReadCmlogList.class, input -> stubList(new ReadCmlogListOutputBuilder().build())),
                    new RpcHelper<>(ReadEventlogList.class, input -> stubList(new ReadEventlogListOutputBuilder().build())),
                    new RpcHelper<>(ReadConnectionlogList.class, input -> stubList(new ReadConnectionlogListOutputBuilder().build())),
                    new RpcHelper<>(ReadMaintenanceList.class, input -> stubList(new ReadMaintenanceListOutputBuilder().build())),
                    new RpcHelper<>(ReadMediatorServerList.class, input -> stubList(new ReadMediatorServerListOutputBuilder().build())),
                    new RpcHelper<>(ReadPmdata15mList.class, input -> stubList(new ReadPmdata15mListOutputBuilder().build())),
                    new RpcHelper<>(ReadPmdata15mLtpList.class, input -> stubList(new ReadPmdata15mLtpListOutputBuilder().build())),
                    new RpcHelper<>(ReadPmdata15mDeviceList.class, input -> stubList(new ReadPmdata15mDeviceListOutputBuilder().build())),
                    new RpcHelper<>(ReadPmdata24hList.class, input -> stubList(new ReadPmdata24hListOutputBuilder().build())),
                    new RpcHelper<>(ReadPmdata24hLtpList.class, input -> stubList(new ReadPmdata24hLtpListOutputBuilder().build())),
                    new RpcHelper<>(ReadPmdata24hDeviceList.class, input -> stubList(new ReadPmdata24hDeviceListOutputBuilder().build())),
                    new RpcHelper<>(ReadGuiCutThroughEntry.class, input -> stubList(new ReadGuiCutThroughEntryOutputBuilder().build())),
                    new RpcHelper<>(ReadTlsKeyEntry.class, input -> stubList(new ReadTlsKeyEntryOutputBuilder().build()))
                )
            );

            LOG.info("LightyDataProvider initialized — RPCs registered.");
>>>>>>> 1671bff (successfull test of data-provider)
            return true;

        } catch (Exception e) {
            LOG.error("Failed to initialize LightyDataProvider", e);
            return false;
        }
    }

    @Override
    protected boolean stopProcedure() {
<<<<<<< HEAD
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
=======
        if (this.rpcRegistration != null) {
            this.rpcRegistration.close();
        }
        return true;
    }

    private <O> ListenableFuture<@NonNull RpcResult<@NonNull O>> stubList(O output) {
        return Futures.immediateFuture(RpcResultBuilder.success(output).build());
    }

    private ListenableFuture<@NonNull RpcResult<@NonNull ReadStatusOutput>> readStatus(final ReadStatusInput input) {
        try {
            long total = 0, connected = 0, connecting = 0, unable = 0, disconnected = 0;
            
            try (ReadTransaction tx = lightyServices.getBindingDataBroker().newReadOnlyTransaction()) {
                Optional<Topology> optTopo = tx.read(LogicalDatastoreType.OPERATIONAL, NETCONF_TOPOLOGY_IID.toIdentifier()).get();
                if (optTopo.isPresent() && optTopo.get().getNode() != null) {
                    for (Node node : optTopo.get().getNode().values()) {
                        if (node.getNodeId().getValue().equals("controller-config")) continue;
                        total++;
                        NetconfNodeAugment augment = node.augmentation(NetconfNodeAugment.class);
                        if (augment != null && augment.getNetconfNode() != null) {
                            ConnectionStatus status = augment.getNetconfNode().getConnectionStatus();
                            if (status == ConnectionStatus.Connected) connected++;
                            else if (status == ConnectionStatus.Connecting) connecting++;
                            else if (status == ConnectionStatus.UnableToConnect) unable++;
                        } else {
                            disconnected++;
                        }
                    }
                }
            }

            ReadStatusOutput output = new ReadStatusOutputBuilder()
                .setData(List.of(new DataBuilder()
                    .setFaults(new FaultsBuilder().setCriticals(Uint32.valueOf(0)).setMajors(Uint32.valueOf(0)).setMinors(Uint32.valueOf(0)).setWarnings(Uint32.valueOf(0)).build())
                    .setNetworkElementConnections(new NetworkElementConnectionsBuilder()
                        .setTotal(Uint32.valueOf(total))
                        .setConnected(Uint32.valueOf(connected))
                        .setConnecting(Uint32.valueOf(connecting))
                        .setUnableToConnect(Uint32.valueOf(unable))
                        .setDisconnected(Uint32.valueOf(disconnected))
                        .setMounted(Uint32.valueOf(0))
                        .setUnmounted(Uint32.valueOf(0))
                        .setUndefined(Uint32.valueOf(0))
                        .build())
                    .build()))
                .build();
            return Futures.immediateFuture(RpcResultBuilder.success(output).build());
        } catch (Exception e) {
            LOG.error("Error processing readStatus", e);
            return Futures.immediateFuture(RpcResultBuilder.<ReadStatusOutput>failed().withError(ErrorType.APPLICATION, e.getMessage()).build());
        }
    }

    private ListenableFuture<@NonNull RpcResult<@NonNull ReadNetworkElementConnectionListOutput>> readNetworkElementConnectionList(final ReadNetworkElementConnectionListInput input) {
        try {
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.read.network.element.connection.list.output.Data> dataList = new ArrayList<>();
            
            try (ReadTransaction tx = lightyServices.getBindingDataBroker().newReadOnlyTransaction()) {
                Optional<Topology> optTopo = tx.read(LogicalDatastoreType.OPERATIONAL, NETCONF_TOPOLOGY_IID.toIdentifier()).get();
                if (optTopo.isPresent() && optTopo.get().getNode() != null) {
                    for (Node node : optTopo.get().getNode().values()) {
                        String nodeId = node.getNodeId().getValue();
                        if (nodeId.equals("controller-config")) continue;

                        String host = "unknown";
                        long port = 0;
                        String status = "Disconnected";

                        NetconfNodeAugment augment = node.augmentation(NetconfNodeAugment.class);
                        if (augment != null && augment.getNetconfNode() != null) {
                            NetconfNode netconfNode = augment.getNetconfNode();
                            if (netconfNode.getHost() != null) host = netconfNode.getHost().stringValue();
                            if (netconfNode.getPort() != null) port = netconfNode.getPort().getValue().longValue();
                            if (netconfNode.getConnectionStatus() != null) status = netconfNode.getConnectionStatus().getName();
                        }

                        dataList.add(new org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.read.network.element.connection.list.output.DataBuilder()
                            .setId(nodeId)
                            .setNodeId(nodeId)
                            .setHost(host)
                            .setPort(Uint32.valueOf(port))
                            .setStatus(org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.data.provider.rev201110.ConnectionLogStatus.forName(status))
                            .build());
                    }
                }
            }

            long totalElements = dataList.size();
            long page = 1;
            long size = totalElements > 0 ? totalElements : 1;
            
            if (input != null && input.getPagination() != null) {
                 if (input.getPagination().getPage() != null) page = input.getPagination().getPage().longValue();
                 if (input.getPagination().getSize() != null) size = input.getPagination().getSize().longValue();
            }

            ReadNetworkElementConnectionListOutput output = new ReadNetworkElementConnectionListOutputBuilder()
                .setData(dataList)
                .setPagination(new PaginationBuilder()
                    .setPage(Uint64.valueOf(page))
                    .setSize(Uint32.valueOf(size))
                    .setTotal(Uint64.valueOf(totalElements))
                    .build())
                .build();
            return Futures.immediateFuture(RpcResultBuilder.success(output).build());
        } catch (Exception e) {
            LOG.error("Error processing readNetworkElementConnectionList", e);
            return Futures.immediateFuture(RpcResultBuilder.<ReadNetworkElementConnectionListOutput>failed().withError(ErrorType.APPLICATION, e.getMessage()).build());
        }
    }

    private ListenableFuture<@NonNull RpcResult<@NonNull CreateNetworkElementConnectionOutput>> createNetworkElementConnection(final CreateNetworkElementConnectionInput input) {
        return Futures.transform(
            writeMountPoint(input.getNodeId(), input.getHost(), input.getPort() != null ? input.getPort().intValue() : 830, input.getUsername(), input.getPassword(), false),
            v -> RpcResultBuilder.success(new CreateNetworkElementConnectionOutputBuilder().build()).build(),
            com.google.common.util.concurrent.MoreExecutors.directExecutor()
        );
    }

    private ListenableFuture<@NonNull RpcResult<@NonNull UpdateNetworkElementConnectionOutput>> updateNetworkElementConnection(final UpdateNetworkElementConnectionInput input) {
        return Futures.transform(
            writeMountPoint(input.getNodeId(), input.getHost(), input.getPort() != null ? input.getPort().intValue() : 830, input.getUsername(), input.getPassword(), true),
            v -> RpcResultBuilder.success(new UpdateNetworkElementConnectionOutputBuilder().build()).build(),
            com.google.common.util.concurrent.MoreExecutors.directExecutor()
        );
    }

    private ListenableFuture<Void> writeMountPoint(String nodeId, String host, int port, String username, String password, boolean isUpdate) {
        try {
            NodeId domNodeId = new NodeId(nodeId);
            InstanceIdentifier<Node> nodeIid = NETCONF_TOPOLOGY_IID.child(Node.class, new NodeKey(domNodeId));

            NetconfNodeBuilder netconfNodeBuilder = new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address(host))))
                .setPort(new PortNumber(Uint16.valueOf(port)))
                .setReconnectOnChangedSchema(true)
                .setDefaultRequestTimeoutMillis(Uint32.valueOf(60000))
                .setMaxConnectionAttempts(Uint32.valueOf(0))
                .setKeepaliveDelay(Uint32.valueOf(120));

            if (username != null && password != null) {
                netconfNodeBuilder.setCredentials(new LoginPwUnencryptedBuilder()
                    .setLoginPasswordUnencrypted(new LoginPasswordUnencryptedBuilder()
                        .setUsername(username)
                        .setPassword(password)
                        .build())
                    .build());
            }

            Node node = new NodeBuilder()
                .withKey(new NodeKey(domNodeId))
                .setNodeId(domNodeId)
                .addAugmentation(new NetconfNodeAugmentBuilder().setNetconfNode(netconfNodeBuilder.build()).build())
                .build();

            WriteTransaction tx = lightyServices.getBindingDataBroker().newWriteOnlyTransaction();
            if (isUpdate) {
                tx.merge(LogicalDatastoreType.CONFIGURATION, nodeIid.toIdentifier(), node);
            } else {
                tx.put(LogicalDatastoreType.CONFIGURATION, nodeIid.toIdentifier(), node);
            }
            return tx.commit().transform(commitInfo -> null, com.google.common.util.concurrent.MoreExecutors.directExecutor());
        } catch (Exception e) {
            LOG.error("Failed to write mountpoint", e);
            return Futures.immediateFailedFuture(e);
        }
    }

    private ListenableFuture<@NonNull RpcResult<@NonNull DeleteNetworkElementConnectionOutput>> deleteNetworkElementConnection(final DeleteNetworkElementConnectionInput input) {
        try {
            NodeId domNodeId = new NodeId(input.getNodeId());
            InstanceIdentifier<Node> nodeIid = NETCONF_TOPOLOGY_IID.child(Node.class, new NodeKey(domNodeId));

            WriteTransaction tx = lightyServices.getBindingDataBroker().newWriteOnlyTransaction();
            tx.delete(LogicalDatastoreType.CONFIGURATION, nodeIid.toIdentifier());
            
            return Futures.transform(
                tx.commit(),
                v -> RpcResultBuilder.success(new DeleteNetworkElementConnectionOutputBuilder().build()).build(),
                com.google.common.util.concurrent.MoreExecutors.directExecutor()
            );
        } catch (Exception e) {
            LOG.error("Failed to delete mountpoint", e);
            return Futures.immediateFuture(RpcResultBuilder.<DeleteNetworkElementConnectionOutput>failed().withError(ErrorType.APPLICATION, e.getMessage()).build());
>>>>>>> 1671bff (successfull test of data-provider)
        }
    }
}
