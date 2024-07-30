#! /bin/bash

java $JAVA_OPTS -cp $( cat /app/jib-classpath-file ) $( cat /app/jib-main-class-file ) /var/opentripplanner/ $@
