JAVAC=javac
JFLAGS=

# XXX SET CLASSPATH
# export CLASSPATH=/home/ec2-user/render-farm/aws-java-sdk-1.11.115/lib/aws-java-sdk-1.11.115:/home/ec2-user/render-farm/aws-java-sdk-1.11.115/third-party/lib/*:.

all:
	$(JAVAC) $(JFLAGS) *.java web-server/*.java

clean:
	rm *.class web-server/*.class
