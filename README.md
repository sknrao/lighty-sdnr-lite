It utilizes core [OpenDaylight](https://www.opendaylight.org/) components, which are available as a set of libraries and are adapted to run in a __plain Java SE environment__ and __lighty.io 24__.

## Modules & Apps
| Modules | Applications |
| :---: | :---:|
| [PNF Registration](modules/iosmcn-pnf-registration) | [RESTCONF-NETCONF-PNFREGISTRATION Application](/applications/iosmcn-pnf-registration-aggregator/) | |

## Build & Install
In order to build and install lighty.io artifacts locally, follow the steps below:
1. __Install JDK__ - make sure [JDK 21](https://openjdk.java.net/projects/jdk/21/) is installed (For example: https://adoptium.net/temurin/releases/)
2. __Install maven__ - make sure you have maven 3.9.5 or later installed
3. __Setup maven__ - make sure you have the proper [settings.xml](https://github.com/opendaylight/odlparent/blob/master/settings.xml) in your ```~/.m2``` directory
4. __Build & Install locally__ - by running command: ``mvn clean install -DskipTests``