/*
 * Copyright (c) 2026 OPENNETS.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package io.lighty.modules.pnf.registration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import io.lighty.core.controller.api.AbstractLightyModule;
import io.lighty.core.controller.api.LightyServices;

import org.onap.ccsdk.features.sdnr.wt.common.configuration.ConfigurationFileRepresentation;
import org.onap.ccsdk.features.sdnr.wt.common.configuration.filechange.IConfigChangedListener;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;

import io.lighty.modules.pnf.registration.mountpointregistrar.config.FaultConfig;
import io.lighty.modules.pnf.registration.mountpointregistrar.config.GeneralConfig;
import io.lighty.modules.pnf.registration.mountpointregistrar.config.MessageConfig;
import io.lighty.modules.pnf.registration.mountpointregistrar.config.PNFRegistrationConfig;
import io.lighty.modules.pnf.registration.mountpointregistrar.config.ProvisioningConfig;
import io.lighty.modules.pnf.registration.mountpointregistrar.config.StndDefinedFaultConfig;
import io.lighty.modules.pnf.registration.mountpointregistrar.config.StrimziKafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PnfModule extends AbstractLightyModule 
    implements IConfigChangedListener, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(PnfModule.class);
    private static final String APPLICATION_NAME = "mountpoint-registrar";
    private static final String CONFIGURATIONFILE = "config/mountpoint-registrar.properties";

    private Thread sKafkaVESMsgConsumerMain = null;

    private GeneralConfig generalConfig;
    private boolean strimziEnabled = false;
    private Map<String, MessageConfig> configMap = new HashMap<>();
    private StrimziKafkaVESMsgConsumerMain sKafkaConsumerMain = null;
    private StrimziKafkaConfig strimziKafkaConfig;
    private final LightyServices lightyServices;

    // Blueprint 1
	public PnfModule(final LightyServices lightyServices/* , final ExecutorService executorService */) {
    	  LOG.info("Creating provider class for {}", APPLICATION_NAME);
          this.lightyServices = lightyServices;
    }

    @Override
    @SuppressWarnings({"checkstyle:illegalCatch"})
    public boolean initProcedure() {
        LOG.info("Init call for {}", APPLICATION_NAME);

        ConfigurationFileRepresentation configFileRepresentation =
                new ConfigurationFileRepresentation(CONFIGURATIONFILE);
        configFileRepresentation.registerConfigChangedListener(this);

        generalConfig = new GeneralConfig(configFileRepresentation);
        strimziKafkaConfig = new StrimziKafkaConfig(configFileRepresentation);
        PNFRegistrationConfig pnfRegConfig = new PNFRegistrationConfig(configFileRepresentation);
        FaultConfig faultConfig = new FaultConfig(configFileRepresentation);
        ProvisioningConfig provisioningConfig = new ProvisioningConfig(configFileRepresentation);
        StndDefinedFaultConfig stndFaultConfig = new StndDefinedFaultConfig(configFileRepresentation);

        configMap.put("pnfRegistration", pnfRegConfig);
        configMap.put("fault", faultConfig);
        configMap.put("provisioning", provisioningConfig);
        configMap.put("stndDefinedFault", stndFaultConfig);

        strimziEnabled = strimziKafkaConfig.getEnabled();
        if (strimziEnabled) { // start Kafka consumer thread only if strimziEnabled=true
            LOG.info("Strimzi Kafka seems to be enabled, starting consumer(s)");
            sKafkaConsumerMain = new StrimziKafkaVESMsgConsumerMain(configMap, generalConfig, strimziKafkaConfig);
            sKafkaVESMsgConsumerMain = new Thread(sKafkaConsumerMain);
            sKafkaVESMsgConsumerMain.start();
        } else {
            LOG.info("Strimzi Kafka seems to be disabled, not starting any consumer(s)");
        }
        return true;
    }

    /**
     * Reflect status for Unit Tests
     *
     * @return Text with status
     */
    public String isInitializationOk() {
        return "No implemented";
    }

    @Override
    public void onConfigChanged() {
        if (generalConfig == null) { // Included as NullPointerException observed once in docker logs
            LOG.warn("onConfigChange cannot be handled. Unexpected Null for generalConfig");
            return;
        }
        if (strimziKafkaConfig == null) { // Included as NullPointerException observed once in docker logs
            LOG.warn("onConfigChange cannot be handled. Unexpected Null for strimziKafkaConfig");
            return;
        }
        LOG.info("Service configuration state changed. Enabled: {}", strimziKafkaConfig.getEnabled());
        boolean strimziEnabledNewVal = strimziKafkaConfig.getEnabled();
        if (!strimziEnabled && strimziEnabledNewVal) { // Strimzi kafka disabled earlier (or during bundle startup) but enabled later, start Consumer(s)
            LOG.info("Strimzi Kafka is enabled, starting consumer(s)");
            sKafkaConsumerMain = new StrimziKafkaVESMsgConsumerMain(configMap, generalConfig, strimziKafkaConfig);
            sKafkaVESMsgConsumerMain = new Thread(sKafkaConsumerMain);
            sKafkaVESMsgConsumerMain.start();
        } else if (strimziEnabled && !strimziEnabledNewVal) { // Strimzi kafka enabled earlier (or during bundle startup) but disabled later, stop consumer(s)
            LOG.info("Strimzi Kafka is disabled, stopping consumer(s)");
            List<StrimziKafkaVESMsgConsumer> consumers = sKafkaConsumerMain.getConsumers();
            for (StrimziKafkaVESMsgConsumer consumer : consumers) {
                // stop all consumers
                consumer.stopConsumer();
            }
        }
        strimziEnabled = strimziEnabledNewVal;
    }

    @Override
    public void close() throws Exception {
        LOG.info("{} closing ...", this.getClass().getName());
        LOG.info("{} closing done", APPLICATION_NAME);
    }

    /**
     * Used to close all Services, that should support AutoCloseable Pattern
     *
     * @param toClose
     * @throws Exception
     */
    @SuppressWarnings("unused")
    private void close(AutoCloseable... toCloseList) throws Exception {
        for (AutoCloseable element : toCloseList) {
            if (element != null) {
                element.close();
            }
        }
    }

    @Override
    @SuppressWarnings({"checkstyle:illegalCatch"})
    protected boolean stopProcedure() {
        boolean closeSuccess = true;
        return closeSuccess;
    }
}
