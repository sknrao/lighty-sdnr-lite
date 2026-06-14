/*
 * Copyright (c) 2018 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.iosmcn.lighty.sdnr.lite.app;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.Stopwatch;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.lighty.applications.util.ModulesConfig;
import io.lighty.core.common.exceptions.ModuleStartupException;
import io.lighty.core.common.models.YangModuleUtils;
import io.lighty.core.controller.api.LightyController;
import io.lighty.core.controller.api.LightyModule;
import io.lighty.core.controller.impl.LightyControllerBuilder;
import io.lighty.core.controller.impl.config.ConfigurationException;
import io.lighty.core.controller.impl.config.ControllerConfiguration;
import io.lighty.core.controller.impl.util.ControllerConfigUtils;
import io.lighty.modules.pnf.registration.PnfModule;
import io.lighty.modules.northbound.restconf.community.impl.CommunityRestConf;
import io.lighty.modules.northbound.restconf.community.impl.CommunityRestConfBuilder;
import io.lighty.modules.northbound.restconf.community.impl.config.RestConfConfiguration;
import io.lighty.modules.northbound.restconf.community.impl.util.RestConfConfigUtils;
import io.lighty.modules.southbound.netconf.impl.NetconfCallhomePlugin;
import io.lighty.modules.southbound.netconf.impl.NetconfCallhomePluginBuilder;
import io.lighty.modules.southbound.netconf.impl.NetconfTopologyPluginBuilder;
import io.lighty.modules.southbound.netconf.impl.config.NetconfConfiguration;
import io.lighty.modules.southbound.netconf.impl.util.NetconfConfigUtils;
import io.lighty.openapi.OpenApiLighty;
import io.lighty.server.LightyJettyServerProvider;
import org.iosmcn.lighty.sdnr.dataprovider.LightyDataProvider;
import org.iosmcn.lighty.sdnr.yangschema.LightyYangSchemaModule;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.opendaylight.yangtools.binding.meta.YangModuleInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private LightyController lightyController;
    private LightyController controller;
    private OpenApiLighty openApi;
    private CommunityRestConf restconf;
    private LightyModule netconfSBPlugin;
    private LightyModule pnfModule;
    private LightyModule dataProviderModule;
    private LightyModule yangSchemaModule;
    private ModulesConfig modulesConfig = ModulesConfig.getDefaultModulesConfig();
    private NetconfCallhomePlugin callhomePlugin;

    public static void main(final String[] args) {
        Main app = new Main();
        app.start(args, true);
    }

    public void start() {
        start(new String[] {}, false);
        System.out.println("Starting Lighty PNF Registration Application...");

        try {
        // 1️⃣ Create default controller configuration
        ControllerConfiguration controllerConfiguration =
                ControllerConfigUtils.getDefaultSingleNodeConfiguration();

        // 2️⃣ Build controller
        controller = new LightyControllerBuilder()
                .from(controllerConfiguration)
                .build();

        boolean controllerStarted = controller.start()
                .get(modulesConfig.getModuleTimeoutSeconds(), TimeUnit.SECONDS);

        if (!controllerStarted) {
            throw new ModuleStartupException("Controller startup failed!");
        }

        } catch (Exception e) {
            LOG.error("pnf module error ", e);
            shutdown();
        }
    }

    @SuppressWarnings("IllegalCatch")
    @SuppressFBWarnings("SLF4J_SIGN_ONLY_FORMAT")
    public void start(final String[] args, final boolean registerShutdownHook) {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        LOG.info(".__  .__       .__     __              .__           _________________    _______");
        LOG.info("|  | |__| ____ |  |___/  |_ ___.__.    |__| ____    /   _____/\\______ \\   \\      \\");
        LOG.info("|  | |  |/ ___\\|  |  \\   __<   |  |    |  |/  _ \\   \\_____  \\  |    |  \\  /   |   \\");
        LOG.info("|  |_|  / /_/  >   Y  \\  |  \\___  |    |  (  <_> )  /        \\ |    `   \\/    |    \\");
        LOG.info("|____/__\\___  /|___|  /__|  / ____| /\\ |__|\\____/  /_______  //_______  /\\____|__  /");
        LOG.info("        /_____/     \\/      \\/      \\/                     \\/         \\/         \\/");
        LOG.info("Starting lighty.io RESTCONF-NETCONF example application ...");
        LOG.info("https://lighty.io/");
        LOG.info("https://github.com/PANTHEONtech/lighty");

        try {
            final ControllerConfiguration singleNodeConfiguration;
            final RestConfConfiguration restconfConfiguration;
            final NetconfConfiguration netconfSBPConfiguration;
            if (args.length > 0) {
                Path configPath = Paths.get(args[0]);
                LOG.info("using configuration from file {} ...", configPath);
                //1. get controller configuration
                singleNodeConfiguration = ControllerConfigUtils.getConfiguration(Files.newInputStream(configPath));
                LOG.info("Loaded config from {}", configPath);
                LOG.info("Loaded schema config: {}",
                singleNodeConfiguration.getSchemaServiceConfig());
                //2. get RESTCONF NBP configuration
                restconfConfiguration = RestConfConfigUtils.getRestConfConfiguration(Files.newInputStream(configPath));
                //3. NETCONF SBP configuration
                netconfSBPConfiguration =
                    NetconfConfigUtils.createNetconfConfiguration(Files.newInputStream(configPath));
                //4. Load modules app configuration
                modulesConfig = ModulesConfig.getModulesConfig(Files.newInputStream(configPath));
                Set<YangModuleInfo> modelPaths = Stream.of(RestConfConfigUtils.YANG_MODELS.stream(),
                    NetconfConfigUtils.NETCONF_TOPOLOGY_MODELS.stream(),
                    NetconfConfigUtils.NETCONF_CALLHOME_MODELS.stream())
                    .flatMap(s -> s).collect(Collectors.toSet());
                ArrayNode arrayNode = YangModuleUtils
                        .generateJSONModelSetConfiguration(
                                Stream.concat(ControllerConfigUtils.YANG_MODELS.stream(), modelPaths.stream())
                                        .collect(Collectors.toSet())
                        );
                //0. print the list of schema context models
                LOG.info("JSON model config snippet: {}", arrayNode.toString());
            } else {
                LOG.info("using default configuration ...");
                Set<YangModuleInfo> modelPaths = Stream.of(RestConfConfigUtils.YANG_MODELS.stream(),
                    NetconfConfigUtils.NETCONF_TOPOLOGY_MODELS.stream(),
                    NetconfConfigUtils.NETCONF_CALLHOME_MODELS.stream())
                    .flatMap(s -> s).collect(Collectors.toSet());
                ArrayNode arrayNode = YangModuleUtils
                        .generateJSONModelSetConfiguration(
                                Stream.concat(ControllerConfigUtils.YANG_MODELS.stream(), modelPaths.stream())
                                        .collect(Collectors.toSet())
                        );
                //0. print the list of schema context models
                LOG.info("JSON model config snippet: {}", arrayNode.toString());
                //1. get controller configuration
                singleNodeConfiguration = ControllerConfigUtils.getDefaultSingleNodeConfiguration(modelPaths);
                //2. get RESTCONF NBP configuration
                restconfConfiguration = RestConfConfigUtils.getDefaultRestConfConfiguration();
                //3. NETCONF SBP configuration
                netconfSBPConfiguration = NetconfConfigUtils.createDefaultNetconfConfiguration();
            }
            //Register shutdown hook for graceful shutdown.
            if (registerShutdownHook) {
                Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
            }
            startLighty(singleNodeConfiguration, restconfConfiguration, netconfSBPConfiguration);
            LOG.info("lighty.io and RESTCONF-NETCONF started in {}", stopwatch.stop());
        } catch (IOException e) {
            LOG.error("Main RESTCONF-NETCONF application - error reading config file: ", e);
            shutdown();
        } catch (Exception e) {
            LOG.error("Main RESTCONF-NETCONF application exception: ", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            shutdown();
        }
    }

    private void startLighty(final ControllerConfiguration controllerConfiguration,
                             final RestConfConfiguration restconfConfiguration,
                             NetconfConfiguration netconfSBPConfiguration)
        throws ConfigurationException, ExecutionException, InterruptedException, TimeoutException,
               ModuleStartupException {

        //1. initialize and start Lighty controller (MD-SAL, Controller, YangTools, Pekko)
        LightyControllerBuilder lightyControllerBuilder = new LightyControllerBuilder();
        this.lightyController = lightyControllerBuilder.from(controllerConfiguration).build();
        final boolean controllerStartOk = this.lightyController.start()
                .get(modulesConfig.getModuleTimeoutSeconds(), TimeUnit.SECONDS);
        if (!controllerStartOk) {
            throw new ModuleStartupException("Lighty.io Controller startup failed!");
        }

        //2. build RestConf server
        final LightyJettyServerProvider jettyServerBuilder = new LightyJettyServerProvider(new InetSocketAddress(
                restconfConfiguration.getInetAddress(), restconfConfiguration.getHttpPort()));
        this.restconf = CommunityRestConfBuilder
                .from(RestConfConfigUtils.getRestConfConfiguration(restconfConfiguration,
                    this.lightyController.getServices()))
                .withLightyServer(jettyServerBuilder)
                .build();

        //3. start openApi and RestConf server
        final boolean restconfStartOk = this.restconf.start()
            .get(modulesConfig.getModuleTimeoutSeconds(), TimeUnit.SECONDS);
        if (!restconfStartOk) {
            throw new ModuleStartupException("Community Restconf startup failed!");
        }
        lightyController.getServices().withJaxRsEndpoint(restconf.getJaxRsEndpoint());

        this.openApi =
            new OpenApiLighty(restconfConfiguration, jettyServerBuilder, this.lightyController.getServices(), null);
        final boolean openApiStartOk = this.openApi.start()
                .get(modulesConfig.getModuleTimeoutSeconds(), TimeUnit.SECONDS);
        if (!openApiStartOk) {
            throw new ModuleStartupException("Lighty.io OpenApi startup failed!");
        }
        this.restconf.startServer();

        //3.5 start YangSchema module
        LOG.info("Starting YangSchema module...");
        this.yangSchemaModule = new LightyYangSchemaModule(
            this.lightyController.getServices(),
            this.restconf
        );
        final boolean yangSchemaStartOk = this.yangSchemaModule.start()
                .get(modulesConfig.getModuleTimeoutSeconds(), TimeUnit.SECONDS);
        if (!yangSchemaStartOk) {
            throw new ModuleStartupException("YangSchema module startup failed!");
        }
        LOG.info("YangSchema module started successfully.");

        //4. start NetConf SBP
        netconfSBPConfiguration = NetconfConfigUtils.injectServicesToTopologyConfig(
                netconfSBPConfiguration, this.lightyController.getServices());
        this.netconfSBPlugin = NetconfTopologyPluginBuilder
                .from(netconfSBPConfiguration, this.lightyController.getServices())
                .build();
        final boolean netconfSBPStartOk = this.netconfSBPlugin.start()
                .get(modulesConfig.getModuleTimeoutSeconds(), TimeUnit.SECONDS);
        if (!netconfSBPStartOk) {
            throw new ModuleStartupException("NetconfSB plugin startup failed!");
        }

        //4.1 Start CallHome
        LOG.debug("Loading default lighty.io NETCONF module configuration...");
        // final NetconfConfiguration netconfConfig = NetconfConfigUtils.createDefaultNetconfConfiguration();
        final NetconfConfiguration netconfConfig = netconfSBPConfiguration;
        LOG.debug("Default lighty.io NETCONF module configuration loaded!");
        this.callhomePlugin = new NetconfCallhomePluginBuilder(lightyController.getServices(),
        		netconfConfig,
                restconfConfiguration.getInetAddress().getHostAddress(),
                4334).build();
        this.callhomePlugin.start();

        //5. start PNF module
        LOG.info("Starting PNF Registration module...");

        this.pnfModule = new PnfModule(this.lightyController.getServices());

        final boolean pnfStartOk = this.pnfModule.start()
                .get(modulesConfig.getModuleTimeoutSeconds(), TimeUnit.SECONDS);

        if (!pnfStartOk) {
            throw new ModuleStartupException("PNF module startup failed!");
        }

        LOG.info("PNF Registration module started successfully.");

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
    }

    private void closeLightyModule(final LightyModule module) {
        if (module != null) {
            module.shutdown(modulesConfig.getModuleTimeoutSeconds(), TimeUnit.SECONDS);
        }
    }

    public void shutdown() {
        LOG.info("Lighty.io and RESTCONF-NETCONF shutting down ...");
        final Stopwatch stopwatch = Stopwatch.createStarted();
        closeLightyModule(this.dataProviderModule);
        closeLightyModule(this.yangSchemaModule);
        closeLightyModule(this.pnfModule);
        closeLightyModule(this.netconfSBPlugin);
        closeLightyModule(this.restconf);
        closeLightyModule(this.openApi);
        closeLightyModule(this.lightyController);
        LOG.info("Lighty.io and RESTCONF-NETCONF stopped in {}", stopwatch.stop());
    }

}