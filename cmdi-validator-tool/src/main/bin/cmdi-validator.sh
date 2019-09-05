#!/bin/sh

VM_OPTS=""

#script depends on GNU readlink, which is different from BSD readlink...
READLINK=`which greadlink || which readlink`
if ! ${READLINK} -f . >/dev/null 2>/dev/null
then
	echo "Incompatible readlink version found, probably BSD. On MacOS, please  install brew."
	echo "Then run"
	echo ""
	echo "   brew install coreutils"
	echo ""
	echo "to fix this"	
	exit 1
fi

PRG=`${READLINK} -f $0`
DIRNAME=`dirname ${PRG}`
DIRNAME=`${READLINK} -f "${DIRNAME}/../lib/"`

CP=""
for JAR in ${DIRNAME}/*.jar; do
    JAR=`${READLINK} -f ${JAR}`
    if [ -z "${CP}" ]; then
        CP="${JAR}"
    else 
        CP="${CP}":"${JAR}"
    fi
done

if [ ! -z "${SAXON_HOME}" ]; then
    for JAR in ${SAXON_HOME}/*.jar; do
        JAR=`${READLINK} -f ${JAR}`
        CP="${JAR}":"${CP}"
    done
fi

if [ -d ${DIRNAME}/endorsed ]; then
    for JAR in ${DIRNAME}/endorsed/*.jar; do
        JAR=`${READLINK} -f ${JAR}`
        CP="${JAR}":"${CP}"
    done
fi

exec java ${VM_OPTS} -cp "${CP}" eu.clarin.cmdi.validator.tool.CMDIValidatorTool "$@"
