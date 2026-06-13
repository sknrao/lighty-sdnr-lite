## Build and Run
build the project: ```mvn clean install```

### Start this demo example
* build the project using ```mvn clean install```
* go to target directory ```cd applications/iosmcn-pnf-registration-aggregator```
* build the docker using ```mvn clean install -P docker```
* run the docker image using ```docker run -d   --name pnfreg-container   --network smo   --network oam   -p 8888:8888   iosmcn-pnfreg```
* enable logging ```docker run -d   --name pnfreg-container   --network smo   --network oam   -p 8888:8888   -e JAVA_OPTS=-Dlog4j2.debug=true   iosmcn-pnfreg```
* docker logs ```dokcer logs -f pnfreg-container```