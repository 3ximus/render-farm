JAVAC=javac
JAVA=java
JFLAGS=
CLASSPATH=/home/ec2-user/render-farm/aws-java-sdk-1.11.115/lib/aws-java-sdk-1.11.115.jar:/home/ec2-user/render-farm/aws-java-sdk-1.11.115/third-party/lib/*:/home/ec2-user/render-farm/BIT:/home/ec2-user/render-farm/BIT/tools:./:/home/ec2-user/render-farm/raytracer/src/raytracer:/home/ec2-user/render-farm/raytracer/output/raytracer:.
SOURCE=/home/ec2-user/render-farm/raytracer/src/raytracer/*.java /home/ec2-user/render-farm/raytracer/src/raytracer/pigments/*.java /home/ec2-user/render-farm/raytracer/src//raytracer/shapes/*.java
TOOL_OPTION=-dynamic
DIR_IN=/home/ec2-user/render-farm/raytracer/src/raytracer
DIR_OUT=/home/ec2-user/render-farm/raytracer/instr/raytracer

all:
    $(JAVAC) $(JFLAGS) $(SOURCE)
	$(JAVAC) $(JFLAGS) web-server/*.java
	$(JAVAC) $(JFLAGS) -cp $(CLASSPATH) EC2_Measures.java
	$(JAVAC) $(JFLAGS) -cp $(CLASSPATH) LoadBalancer.java
	$(JAVAC) $(JFLAGS)  /home/ec2-user/render-farm/BIT/tools/StatisticsTool.java
    $(JAVA) $(JFLAGS) -cp $(CLASSPATH) StatistiscsTool $(TOOL_OPTION) $(DIR_IN)/. $(DIR_OUT)/.
    $(JAVA) $(JFLAGS) -cp $(CLASSPATH) StatistiscsTool $(TOOL_OPTION)/shapes/. $(DIR_IN) $(DIR_OUT)/shapes/.
    $(JAVA) $(JFLAGS) -cp $(CLASSPATH) StatistiscsTool $(TOOL_OPTION)/pigments/. $(DIR_IN) $(DIR_OUT)/pigments/.
clean:
	rm *.class web-server/*.class raytracer/src/raytracer/*.class raytracer/src/raytracer/shapes/*.class raytracer/src/raytracer/pigments/*.class log.txt raytracer/instr/raytracer/*.class raytracer/instr/raytracer/shapes/*.class raytracer/instr/raytracer/pigments/*.class BIT/tools/*.class

