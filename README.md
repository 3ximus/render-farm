## Render Farm

Cloud Computing and Virtualization

This project is a Render Farm hosted on AWS servers. It consists of a raytracer application to render 3D scenes. The requests are done via HTTP to a Load Balancer that chooses the WebServer node to handle the raytracing based on a number of heuristics. The raytracer code is instrumented to collect metricts of its execution and help the Load Balancer do its job.

### Instructions
The application can be setup by running the script `setup.sh` wich will download the needed dependencies (BIT and aws-java-sdk)

Then the project can be compiled by running `make`.

The WebServer node can be executed by running `make run-webserver` and the LoadBalancer with `make run-loadbalancer`


