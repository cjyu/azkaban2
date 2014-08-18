#!/bin/bash

azkaban_dir=$(dirname $0)/..

if [[ -z "$tmpdir" ]]; then
tmpdir=/tmp
fi

for file in $azkaban_dir/lib/*.jar;
do
  CLASSPATH=$CLASSPATH:$file
done

for file in $azkaban_dir/extlib/*.jar;
do
  CLASSPATH=$CLASSPATH:$file
done

for file in $azkaban_dir/plugins/*/*.jar;
do
  CLASSPATH=$CLASSPATH:$file
done

HADOOP_LIB_DIR=/usr/lib/hadoop/client
HADOOP_CONF_DIR=/etc/hadoop/conf
HADOOP_NATIVE_LIB_DIR=/usr/lib/hadoop/lib/native
CLASSPATH=$CLASSPATH:$HADOOP_LIB_DIR/*:$HADOOP_CONF_DIR
JAVA_LIB_PATH="-Djava.library.path=$HADOOP_NATIVE_LIB_DIR"

echo $azkaban_dir;
echo $CLASSPATH;

executorport=`cat $azkaban_dir/conf/azkaban.properties | grep executor.port | cut -d = -f 2`
serverpath=`pwd`

if [ -z $AZKABAN_OPTS ]; then
  AZKABAN_OPTS="-Xmx3G"
fi
AZKABAN_OPTS="$AZKABAN_OPTS -server -Dcom.sun.management.jmxremote -Djava.io.tmpdir=$tmpdir -Dexecutorport=$executorport -Dserverpath=$serverpath"

java $AZKABAN_OPTS $JAVA_LIB_PATH -cp $CLASSPATH azkaban.webapp.AzkabanSingleServer -conf $azkaban_dir/conf $@ &

echo $! > currentpid

