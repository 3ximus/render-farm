JAVAC=javac
JAVA=java7
JFLAGS=

AWS_CLASSPATH=/home/ec2-user/render-farm/aws-java-sdk-1.11.127/lib/aws-java-sdk-1.11.127.jar:/home/ec2-user/render-farm/aws-java-sdk-1.11.127/third-party/lib/*:.
BIT_CLASSPATH=/home/ec2-user/render-farm/BIT:/home/ec2-user/render-farm/instrument-tools:./

TOOL=ICount
DIR_IN=/home/ec2-user/render-farm/raytracer/src/raytracer

all: web-server load-balancer

web-server: raytracer bit
	$(JAVAC) $(JFLAGS) web-server/*.java

bit: raytracer
	$(JAVAC) $(JFLAGS) -cp $(BIT_CLASSPATH) instrument-tools/*.java
	$(JAVA) $(JFLAGS) -cp $(BIT_CLASSPATH) $(TOOL) $(DIR_IN)/. $(DIR_IN)/.
	$(JAVA) $(JFLAGS) -cp $(BIT_CLASSPATH) $(TOOL) $(DIR_IN)/shapes/. $(DIR_IN)/shapes/.
	$(JAVA) $(JFLAGS) -cp $(BIT_CLASSPATH) $(TOOL) $(DIR_IN)/pigments/. $(DIR_IN)/pigments/.

raytracer:
	cd raytracer && make && cd ..

load-balancer:
	$(JAVAC) $(JFLAGS) -cp $(AWS_CLASSPATH) Interface_AmazonEC2.java
	$(JAVAC) $(JFLAGS) -cp $(AWS_CLASSPATH) LoadBalancer.java

clean:
	cd raytracer && make clean && cd ..
	rm *.class web-server/*.class
	rm raytracer/instr/raytracer/*.class raytracer/instr/raytracer/shapes/*.class raytracer/instr/raytracer/pigments/*.class
	rm instrument-tools/*.class
