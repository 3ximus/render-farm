JAVAC=javac
JAVA=java7
JFLAGS=

AWS_CLASSPATH=/home/ec2-user/render-farm/aws-java-sdk-1.11.127/lib/aws-java-sdk-1.11.127.jar:/home/ec2-user/render-farm/aws-java-sdk-1.11.127/third-party/lib/*:/home/ec2-user/render-farm/amazon:.
BIT_CLASSPATH=/home/ec2-user/render-farm/BIT:/home/ec2-user/render-farm/instrument-tools:./

TOOL=DynamicStats
DIR=raytracer/src/raytracer

all: base bit loadbal
	$(JAVAC) $(JFLAGS) web-server/WebServer.java

base: amazon/Interface_AmazonEC2.java
	$(JAVAC) $(JFLAGS) -cp $(AWS_CLASSPATH) amazon/Interface_AmazonEC2.java

bit: raytracer/
	cd raytracer && make && cd ..
	$(JAVAC) $(JFLAGS) -cp $(BIT_CLASSPATH) instrument-tools/*.java
	$(JAVA) $(JFLAGS) -cp $(BIT_CLASSPATH) $(TOOL) $(DIR) $(DIR)
	$(JAVA) $(JFLAGS) -cp $(BIT_CLASSPATH) $(TOOL) $(DIR)/shapes $(DIR)/shapes
	$(JAVA) $(JFLAGS) -cp $(BIT_CLASSPATH) $(TOOL) $(DIR)/pigments $(DIR)/pigments

loadbal: load-balancer/LoadBalancer.java
	$(JAVAC) $(JFLAGS) -cp $(AWS_CLASSPATH) load-balancer/LoadBalancer.java

run-raytracer: bit
# example run of the standalone raytracer
	JAVA_HOME=/etc/alternatives/java_sdk_1.7.0 \
	JAVA_ROOT=/etc/alternatives/java_sdk_1.7.0 \
	JDK_HOME=/etc/alternatives/java_sdk_1.7.0 \
	JRE_HOME=/etc/alternatives/java_sdk_1.7.0/jre \
	PATH=/etc/alternatives/java_sdk_1.7.0/bin \
	SDK_HOME=/etc/alternatives/java_sdk_1.7.0 \
	_JAVA_OPTIONS='-XX:-UseSplitVerifier ' \
	java -Djava.awt.headless=true -cp instrument-tools:raytracer/src raytracer.Main raytracer/test05.txt raytracer/test05.bmp 400 300 400 300 400 300

clean:
	cd raytracer && make clean && cd ..
	rm load-balancer/*.class web-server/*.class instrument-tools/*.class amazon/*.class
