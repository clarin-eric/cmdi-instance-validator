#!/bin/sh

PRG=`readlink -f $0`
DIRNAME=`dirname ${PRG}`
DIRNAME=`readlink -f "${DIRNAME}/../lib/"`

CP=""
for JAR in ${DIRNAME}/*.jar; do
    JAR=`readlink -f ${JAR}`
    if [ -z "${CP}" ]; then
        CP="${JAR}"
    else 
        CP="${CP}":"${JAR}"
    fi
done

if [ ! -z "${SAXON_HOME}" ]; then
    for JAR in ${SAXON_HOME}/*.jar; do
        JAR=`readlink -f ${JAR}`
        CP="${JAR}":"${CP}"
    done
fi

if [ -d ${DIRNAME}/endorsed ]; then
    for JAR in ${DIRNAME}/endorsed/*.jar; do
        JAR=`readlink -f ${JAR}`
        CP="${JAR}":"${CP}"
    done
fi

VM_OPTS=""
if java -version 2>&1 | grep -q -i '64-bit'; then
  VM_OPTS="-d64 $VM_OPTS"
fi
exec java ${VM_OPTS} -cp "${CP}" eu.clarin.cmdi.validator.tool.CMDIValidatorTool "$@"
