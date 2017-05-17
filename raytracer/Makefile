JAVAC = javac
JFLAGS =

CLASSPATH=/home/ec2-user/render-farm/amazon:/home/ec2-user/render-farm/instrument-tools:/home/ec2-user/render-farm/BIT:/home/ec2-user/render-farm/aws-java-sdk-1.11.130/lib/aws-java-sdk-1.11.130.jar:/home/ec2-user/render-farm/aws-java-sdk-1.11.130/third-party/lib/*:.

all:
	$(JAVAC) $(JFLAGS) -cp $(CLASSPATH) src/raytracer/*.java src/raytracer/pigments/*.java src/raytracer/shapes/*.java

clean:
	$(RM) src/raytracer/*.class src/raytracer/pigments/*.class src/raytracer/shapes/*.class
