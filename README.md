# ProxyChecker
An easy to use open-source, multithreaded Proxy Checker.
Allows you to quickly check HTTP and SOCKS proxies in a user friendly GUI, for Windows, Mac OS, Linux.


![Image of ProxyChecker](showcase/preview_main.gif)




### Features
* GUI based
* Check HTTP, SOCKS4/4a and SOCKS5 Proxies
* Authenticated proxies (HTTP Basic, SOCKS5 username/password, SOCKS4 user id)
* Multithreaded & Multiplatform
* Filters and Removes Duplicate Proxies
* Ability to Load from multiple Proxy Lists
* Proxy Anonymity, Response Time & Country Information
* Export Table (CSV) and Export Based on Anonymity
* Command line mode for servers and scripts (no GUI required)

### Requirements
* Java 11+ (the packaged releases bundle JavaFX)
* Internet Connection :)


### Proxy List Format
One proxy per line, in any of these formats:
```
203.0.113.10:8080
user:password@203.0.113.10:1080
203.0.113.10:1080:user:password
```
Passwords may contain `:` (and `@` in the `user:password@host:port` form). Exports keep each proxy exactly as it was loaded, credentials included.

### Installation & Usage
Simply download the latest packaged release for your system and run!
* [Latest Release](https://github.com/faiqsohail/ProxyChecker/releases/latest)
* Windows (.exe), Mac OS (.dmg), Other (.jar)

Now also available on [Docker Hub](https://hub.docker.com/r/faiqsohail/proxychecker) 🐳


### Command Line
Run the same checker without the GUI (JavaFX is not initialised, so it works on headless servers):
```
java -jar ProxyChecker.jar --cli -i proxies.txt -t socks5 --threads 200 --timeout 5000 -o results.csv --only-working
cat proxies.txt | java -jar ProxyChecker.jar --cli -i - -q --only-working > working.txt
java -jar ProxyChecker.jar --help
```
| Option | Description |
|---|---|
| `-i, --input <file>` | proxy list, repeatable; `-` reads standard input |
| `-o, --output <file>` | write results to a file instead of standard output |
| `-f, --format txt\|csv` | `txt`: one proxy per line as loaded; `csv`: all details. Defaults to `csv` for `*.csv` outputs |
| `-t, --type http\|socks4\|socks5` | proxy protocol (default `http`) |
| `--threads <n>` | concurrent checks, 1-1000 (default 100) |
| `--timeout <ms>` | connect/read timeout, at least 500 (default 5000) |
| `-w, --only-working` | only output working proxies |
| `--my-ip <ip>` | your public IP (skips the lookup) |
| `--api-url <url>` | use another compatible proxy judge API |
| `-q, --quiet` | no progress or summary on standard error |

Progress and a summary are printed to standard error. Exit codes: `0` working proxies found, `1` none working,
`2` invalid arguments or no valid proxies in the input, `3` file or public IP lookup error.
Running the jar without arguments opens the GUI as before.

### Build from Source
This assumes you have Java 11+ (JDK) and [Maven](https://maven.apache.org/download.cgi) set up correctly.
```
git clone https://github.com/faiqsohail/ProxyChecker.git
cd ProxyChecker
mvn package                       # compiles, runs the tests and builds target/ProxyChecker.jar
java -jar target/ProxyChecker.jar
```
During development you can also run the application directly with `mvn javafx:run`, or only the tests with `mvn test`.

Note: the jar bundles the JavaFX native libraries of the platform it was built on, so build it on the
operating system you intend to run it on.


### Contributing
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.



--
<sub><sup>
The application icon is provided by David Cross, from [Webhosting Media](http://webhostingmedia.net/)
</sup></sub>
