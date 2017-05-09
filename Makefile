JAVAC=javac
JAVA=java8
JFLAGS=
CLASSPATH=/home/ec2-user/render-farm/aws-java-sdk-1.11.125/lib/aws-java-sdk-1.11.125.jar:/home/ec2-user/render-farm/aws-java-sdk-1.11.125/third-party/lib/*:/home/ec2-user/render-farm/BIT:/home/ec2-user/render-farm/BIT/tools:./:/home/ec2-user/render-farm/raytracer/src/raytracer:/home/ec2-user/render-farm/raytracer/output/raytracer:.
RAYTRACER_SOURCE=/home/ec2-user/render-farm/raytracer/src/raytracer/*.java /home/ec2-user/render-farm/raytracer/src/raytracer/pigments/*.java /home/ec2-user/render-farm/raytracer/src//raytracer/shapes/*.java
TOOL_OPTION=-dynamic
DIR_IN=/home/ec2-user/render-farm/raytracer/src/raytracer
DIR_OUT=/home/ec2-user/render-farm/raytracer/instr/raytracer

all: base load-balancer bit

base:
	$(JAVAC) $(JFLAGS) $(RAYTRACER_SOURCE)
	$(JAVAC) $(JFLAGS) web-server/*.java

load-balancer:
	$(JAVAC) $(JFLAGS) -cp $(CLASSPATH) Amazon_EC2_Interface.java
	$(JAVAC) $(JFLAGS) -cp $(CLASSPATH) LoadBalancer.java

bit:
	$(JAVAC) $(JFLAGS) -cp $(CLASSPATH) /home/ec2-user/render-farm/BIT/tools/StatisticsTool.java
	$(JAVA) $(JFLAGS) -cp $(CLASSPATH) StatisticsTool $(TOOL_OPTION) $(DIR_IN)/. $(DIR_OUT)/.
	$(JAVA) $(JFLAGS) -cp $(CLASSPATH) StatisticsTool $(TOOL_OPTION)/shapes/. $(DIR_IN) $(DIR_OUT)/shapes/.
	$(JAVA) $(JFLAGS) -cp $(CLASSPATH) StatisticsTool $(TOOL_OPTION)/pigments/. $(DIR_IN) $(DIR_OUT)/pigments/.
clean:
	rm *.class web-server/*.class raytracer/src/raytracer/*.class raytracer/src/raytracer/shapes/*.class raytracer/src/raytracer/pigments/*.class log.txt raytracer/instr/raytracer/*.class raytracer/instr/raytracer/shapes/*.class raytracer/instr/raytracer/pigments/*.class BIT/tools/*.class

