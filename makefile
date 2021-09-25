JCC = javac
JCR = java
JFLAGS =-XDignore.symbol.file \
	-cp ".:gson-2.8.5.jar" \
	-d out

main: all clean

all:
	mkdir -p out
	mkdir -p artifact
	wget -N https://repo1.maven.org/maven2/com/google/code/gson/gson/2.8.5/gson-2.8.5.jar
	find . -name "*.java" -print | xargs $(JCC) $(JFLAGS)
	jar xf gson-2.8.5.jar
	cp -R com out/
	cp -R src/META-INF out/
	cp -R src/co/proxychecker/ProxyChecker/assets out/co/proxychecker/ProxyChecker/
	cp src/co/proxychecker/ProxyChecker/gui/*.fxml out/co/proxychecker/ProxyChecker/gui/
	cd out; jar -cvfm ../artifact/ProxyChecker.jar META-INF/MANIFEST.MF .

clean:
	rm -rf out/
	rm -rf gson-2.8.5.jar
	rm -rf META-INF/
	rm -rf com/
