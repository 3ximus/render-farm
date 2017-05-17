JAVAC=javac
JAVA=java7
JFLAGS=

CLASSPATH=/home/ec2-user/render-farm/amazon:/home/ec2-user/render-farm/instrument-tools:/home/ec2-user/render-farm/BIT:/home/ec2-user/render-farm/aws-java-sdk-1.11.130/lib/aws-java-sdk-1.11.130.jar:/home/ec2-user/render-farm/aws-java-sdk-1.11.130/third-party/lib/*:.

TOOL=DynamicStats
RAYTRACER_DIR=raytracer/src/raytracer

all: base bit loadbal
	$(JAVAC) $(JFLAGS) web-server/WebServer.java

base: amazon/Interface_AmazonEC2.java
	$(JAVAC) $(JFLAGS) -cp $(CLASSPATH) amazon/Interface_AmazonEC2.java

bit: raytracer/
	cd raytracer && make && cd ..
	$(JAVAC) $(JFLAGS) -cp $(CLASSPATH) instrument-tools/*.java
	$(JAVA) $(JFLAGS) -cp $(CLASSPATH) $(TOOL) $(RAYTRACER_DIR) $(RAYTRACER_DIR)
	$(JAVA) $(JFLAGS) -cp $(CLASSPATH) $(TOOL) $(RAYTRACER_DIR)/shapes $(RAYTRACER_DIR)/shapes
	$(JAVA) $(JFLAGS) -cp $(CLASSPATH) $(TOOL) $(RAYTRACER_DIR)/pigments $(RAYTRACER_DIR)/pigments


loadbal: load-balancer/LoadBalancer.java
	$(JAVAC) $(JFLAGS) -cp $(CLASSPATH) load-balancer/LoadBalancer.java

run-webserver:
	java -classpath /home/ec2-user/render-farm/web-server WebServer

run-raytracer: # example run of the standalone raytracer
	JAVA_HOME=/etc/alternatives/java_sdk_1.7.0 \
	JAVA_ROOT=/etc/alternatives/java_sdk_1.7.0 \
	JDK_HOME=/etc/alternatives/java_sdk_1.7.0 \
	JRE_HOME=/etc/alternatives/java_sdk_1.7.0/jre \
	PATH=/etc/alternatives/java_sdk_1.7.0/bin \
	SDK_HOME=/etc/alternatives/java_sdk_1.7.0 \
	_JAVA_OPTIONS='-XX:-UseSplitVerifier ' \
	java -Djava.awt.headless=true -cp $(CLASSPATH):/home/ec2-user/render-farm/raytracer/src raytracer.Main raytracer/test05.txt raytracer/test05.bmp 400 300 400 300 400 300

clean:
	cd raytracer && make clean && cd ..
	rm load-balancer/*.class web-server/*.class instrument-tools/*.class amazon/*.class
