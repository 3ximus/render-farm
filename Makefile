JAVAC=javac
JAVA=java8
JFLAGS=

AWS_CLASSPATH=/home/ec2-user/render-farm/aws-java-sdk-1.11.127/lib/aws-java-sdk-1.11.127.jar:/home/ec2-user/render-farm/aws-java-sdk-1.11.127/third-party/lib/*:.
BIT_CLASSPATH=/home/ec2-user/render-farm/BIT/:/home/ec2-user/render-farm/instrument_tools/.

DIR_IN=/home/ec2-user/render-farm/raytracer/src/raytracer
DIR_OUT=/home/ec2-user/render-farm/raytracer/instr/raytracer

all: base load-balancer bit

base:
	cd raytracer && make && cd ..
	$(JAVAC) $(JFLAGS) web-server/*.java

load-balancer:
	$(JAVAC) $(JFLAGS) -cp $(AWS_CLASSPATH) Interface_AmazonEC2.java
	$(JAVAC) $(JFLAGS) -cp $(AWS_CLASSPATH) LoadBalancer.java

bit:
	$(JAVAC) $(JFLAGS) -cp $(BIT_CLASSPATH) instrument_tools/*.java
	$(JAVA) $(JFLAGS) -cp $(BIT_CLASSPATH) StatisticsTool -dynamic $(TOOL_OPTION) $(DIR_IN)/. $(DIR_OUT)/.
	$(JAVA) $(JFLAGS) -cp $(BIT_CLASSPATH) StatisticsTool -dynamic $(TOOL_OPTION) $(DIR_IN)/shapes/. $(DIR_OUT)/shapes/.
	$(JAVA) $(JFLAGS) -cp $(BIT_CLASSPATH) StatisticsTool -dynamic $(TOOL_OPTION) $(DIR_IN)/pigments/. $(DIR_OUT)/pigments/.

clean:
	cd raytracer && make clean && cd ..
	rm *.class web-server/*.class
	rm raytracer/instr/raytracer/*.class raytracer/instr/raytracer/shapes/*.class raytracer/instr/raytracer/pigments/*.class
	rm instrument_tools/*.class

