#!/bin/sh
while :
do
	curl http://istio-ingressgateway-istio-system.apps.ocp4.lab.unixnerd.org/todos
	sleep 0.8
done
