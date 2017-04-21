JAVAC=javac
JFLAGS=
CLASSPATH=/home/ec2-user/render-farm/aws-java-sdk-1.11.115/lib/aws-java-sdk-1.11.115.jar:/home/ec2-user/render-farm/aws-java-sdk-1.11.115/third-party/lib/*:.

all:
	$(JAVAC) $(JFLAGS) web-server/*.java
	$(JAVAC) $(JFLAGS) -cp $(CLASSPATH) *.java

clean:
	rm *.class web-server/*.class

