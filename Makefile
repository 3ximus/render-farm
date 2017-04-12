JAVAC=javac
JFLAGS=

all:
	$(JAVAC) $(JFLAGS) *.java web-server/*.java

clean:
	rm *.class web-server/*.class
