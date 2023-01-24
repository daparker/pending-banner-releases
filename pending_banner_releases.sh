#!/bin/bash

JAVA_HOME="/usr/lib/jvm/java"
JAVA="/usr/bin/java"

ESM_LIB_DIR="/u01/apache-tomcat-8.5.20/webapps/admin/WEB-INF/lib"
ESM_H2_FILE="/u01/adminApp/ESMAdminProdDb.h2.db"
TMP_FILE="/var/tmp/ESMAdminProdDb.h2.db"

if [ ! -f ${ESM_H2_FILE} ] ; then
    mvfile=$(echo "${ESM_H2_FILE}" | sed -e 's/\.h2\.db/\.mv\.db/')

    echo
    echo "Couldn't find ${ESM_H2_FILE}"
    echo "Trying ${mvfile} ..."
    echo

    if [ -f ${mvfile} ] ; then
        ESM_H2_FILE=${mvfile}
        TMP_FILE="/var/tmp/ESMAdminProdDb.mv.db"
    else
        echo "ERROR: No H2 database file could be found."
        echo
        exit 1
    fi
fi

cp ${ESM_H2_FILE} ${TMP_FILE}

CLASSPATH="${ESM_LIB_DIR}/*:."
${JAVA} -cp ${CLASSPATH} PendingBannerReleases

rm -rf ${TMP_FILE}

echo
exit 0
