# Pending Banner Releases

This program will show the releases in the Ellucian Solution Manager database which have not yet been installed in the specified Banner instances.

## Compiling
In order to compile the PendingBannerReleases.class file, you will need to specify the full path to **ojdbc7.jar** as the classpath.  Something like this should work: 

```
$ javac -Xlint:unchecked -cp <path>/ojdbc7.jar PendingBannerReleases.java
```

Where `<path>` is the full path to the ESM webapp's lib directory (e.g., /u01/apache-tomcat-8.5.20/webapps/admin/WEB-INF/lib).

## Configuring
You can configure the connection details for both the ESM H2 database and Banner database in the **config.properties** file.  This file must reside in the same directory as the PendingBannerReleases.class file.

## Running
If your ESM server is running on Linux, you can simply use the included shell script **pending_banner_releases.sh** to run the program, like so:

```
$ ./pending_banner_releases.sh
```

**IMPORTANT:** You must ensure that both the config.properties file AND the script are configured properly.  See NOTES section below.

Alternatively, you may run the `PendingBannerReleases` class directly.  There are a few things you must do in order for this to work:

  1. Copy the live H2 database file to a temporary file and ensure that the h2.db.file value in **config.properties** points to the temporary copy.

  2. Specify the H2 and OJDBC drivers in the classpath.

Assuming you have set **/var/tmp/ESMAdminProdDb.h2.db** as the value of `h2.db.file` in **config.properties**, you would do something like this:

```
$ cp /u01/adminApp/ESMAdminProdDb.h2.db /var/tmp/ESMAdminProdDb.h2.db
$ java -cp <path>/h2-1.4.196.jar:<path>/ojdbc7.jar:. PendingBannerReleases
```

Where `<path>` is the full path to the ESM webapp's lib directory (e.g., /u01/apache-tomcat-8.5.20/webapps/admin/WEB-INF/lib).

## Notes
* If you are using the shell script to run this program, you must first ensure that the values of `h2.db.file` in **config.properites** and `ESM_H2_FILE` in the **pending_banner_releases.sh** script are the same.
* The script will copy the live H2 database file to the filename specified by `ESM_H2_FILE`.  DO NOT specify the full path of the live file as the value for this variable, or the script will fail.
* This program does not currently recognize dependencies.  Therefore, the output may show some product versions which are not installed in Banner because they were superseded by a newer release.
