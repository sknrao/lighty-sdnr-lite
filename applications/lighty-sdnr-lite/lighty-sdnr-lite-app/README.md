# Lighty NETCONF/RESTCONF Application
This application provides a RESTCONF northbound interface and utilizes a NETCONF southbound plugin to manage NETCONF devices on the network. It operates as a standalone SDN controller, capable of connecting to NETCONF devices and exposing them through RESTCONF northbound APIs.

In addition, the application supports PNF (Physical Network Function) registration and can handle NETCONF Call Home functionality, enabling devices to initiate connections to the controller and be automatically discovered, registered, and managed.

This application starts:
* Lighty Controller
* OpenDaylight RESTCONF plugin
* OpenDaylight OpenApi servlet
* NETCONF south-bound plugin
* PNF registration
* Callhome

## Build and Run
build the project: ```mvn clean install```

### Start this demo example
* build the project using ```mvn clean install```
* go to target directory ```cd applications/iosmcn-pnf-registration-app/target``` 
* unzip example application bundle ```unzip  iosmcn-pnf-registration-app-23.0.0-SNAPSHOT-bin.zip```
* go to unzipped application directory ```cd iosmcn-pnf-registration-app-23.0.0-SNAPSHOT```
* start controller example controller application ```java -jar iosmcn-pnf-registration-app-23.0.0-SNAPSHOT.jar``` 

### Test example application
Once example application has been started using command ```java -jar iosmcn-pnf-registration-app-23.0.0-SNAPSHOT.jar``` 
RESTCONF web interface is available at URL ```http://localhost:8888/restconf/*```

##### URLs to start with
* __GET__ ```http://localhost:8888/restconf/operations```
* __GET__ ```http://localhost:8888/restconf/data/network-topology:network-topology?content=config```
* __GET__ ```http://localhost:8888/restconf/data/network-topology:network-topology?content=nonconfig```

##### OpenApi UI
This application example has active [OpenApi](https://swagger.io/) UI for RESTCONF.

URLs for OpenApi: https://datatracker.ietf.org/doc/html/rfc8040
* __OpenApi UI__ ``http://localhost:8888/openapi/explorer/index.html``

### Use custom config files
```
  Path configPath = Paths.get("/path/to/lightyControllerConfig.json");
  InputStream is = Files.newInputStream(configPath);
  RestConfConfiguration restConfConfig
      = RestConfConfigUtils.getRestConfConfiguration(is);
```
`java -jar iosmcn-pnf-registration-app-23.0.0-SNAPSHOT.jar /path/to/restConfConfig.json`

Example configuration is [here](applications/iosmcn-pnf-registration-aggregator/iosmcn-pnf-registration-app-docker/src/main/docker/restConfConfig.json)

## Setup Logging
Default logging configuration may be overwritten by JVM option
```-Dlog4j.configurationFile=path/to/log4j2.xml```

Content of ```log4j2.xml``` is described [here](https://logging.apache.org/log4j/2.x/manual/configuration.html).

Example log file is [here](/home/pavanashree/Pavana/iosmcn_lighty_pnfreg/applications/iosmcn-pnf-registration-aggregator/iosmcn-pnf-registration-app/src/main/resources/log4j2.xml).

## Mountpoint-registrar config file
Sample config file related to mountpoint-registrar is [here](mountpoint-registrar.properties)

## Connect a device manually[ssh]
```
  curl --request PUT \
  --url http://localhost:8888/rests/data/network-topology:network-topology/topology=topology-netconf/node=node-name \
  --header 'content-type: application/json' \
  --data '{
  "node": [
    {
      "node-id": "node-name",
      "netconf-node-topology:netconf-node": {
        "schemaless": false,
        "tcp-only": false,
        "port": 830,
        "keepalive-delay": 20,
        "login-password-unencrypted": {
                    "username": "username",
                    "password": "password"
                },
        "host": "host-ip"
      }
    }
  ]
}'
```

## Fetch connected devices
```
curl --request GET   --url http://localhost:8888/rests/data/network-topology:network-topology/topology=topology-netconf
```