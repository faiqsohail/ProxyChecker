# ProxyChecker
An easy to use open-source, multithreaded Proxy Checker.
Allows you to quickly check HTTP and SOCKS proxies in a user friendly GUI, for Windows, Mac OS, Linux.


![Image of ProxyChecker](showcase/preview_main.gif)




### Features
* GUI based
* Check SOCKS or HTTP Proxies
* Multithreaded & Multiplatform
* Filters and Removes Duplicate Proxies
* Ability to Load from multiple Proxy Lists
* Proxy Anonymity, Response Time & Country Information
* Export Table (CSV) and Export Based on Anonymity

### Requirements
* Java 1.8+
* Internet Connection :)


### Installation & Usage
Simply download the latest packaged release for your system and run!
* [Latest Release](https://github.com/faiqsohail/ProxyChecker/releases/latest)
* Windows (.exe), Mac OS (.dmg), Other (.jar)

Now also available on [Docker Hub](https://hub.docker.com/r/faiqsohail/proxychecker) 🐳


### Build from Source
This assumes you have both Java and [Maven](https://maven.apache.org/download.cgi) setup correctly.
```
git clone https://github.com/faiqsohail/ProxyChecker.git
mvn package -f ProxyChecker/pom.xml
java -jar ProxyChecker/target/ProxyChecker.jar
```

### Contributing
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.



--
<sub><sup>
The application icon is provided by David Cross, from [Webhosting Media](http://webhostingmedia.net/)
</sup></sub>
