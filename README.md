# Pending Banner Releases

## About
This is a small Java program that will show the releases in the Ellucian Solution Manager (ESM) database which have not yet been installed in the specified Banner instances.  It is intended to be compiled and run on the ESM server, as it requires access to the OJDBC driver, H2 driver, and H2 database used by ESM.  You can specity the connection details for up to three separate Banner instances, and this program will show the pending releases which are not yet installed in each instance.  This makes it useful for quickly comparing which releases are installed in different Banner environments.

***Please follow the instructions below carefully before running this program for the first time.***

**Note:** This program does not currently recognize dependencies.  Therefore, the output may show some product versions which are not installed in Banner because they were superseded by a newer release.

## Compiling
You are welcome to try using the **PendingBannerReleases.class** file provided in this repo, following the instructions under **Running** (below).  If it does not work correctly, you may need to recompile the class file for your own platform and ESM version.  In order to compile the **PendingBannerReleases.class** file, you will need to specify the full path to **ojdbc7.jar** as the classpath.  Something like this should work:

```
$ javac -Xlint:unchecked -cp <path>/ojdbc7.jar PendingBannerReleases.java
```

Where `<path>` is the full path to the ESM application's lib directory (e.g., `/u01/apache-tomcat-8.5.20/webapps/admin/WEB-INF/lib`).

## Configuring
You can configure the connection details for both the ESM H2 database and Banner database in the **config.properties** file.  This file must reside in the same directory as the PendingBannerReleases.class file.  In order to allow access to the H2 database used by ESM, you will need the file password, username, and user password which were configured when ESM was first installed.  The configuration properties are described below:

| Property | Description |
| --- | --- |
| h2.db.file | Full path to a *copy* of the ESM H2 database file |
| h2.db.file.pass | File password for the ESM H2 database |
| h2.db.user | Username for accessing the ESM H2 database |
| h2.db.user.pass | User password for accessing the ESM H2 database |
| orcl.db1.host | 1st Banner database hostname |
| orcl.db1.port | 1st Banner database port |
| orcl.db1.name | 1st Banner database service name |
| orcl.db1.user | 1st Banner database username |
| orcl.db1.pass | 1st Banner database password |
| orcl.db2.host | *(Optional)* 2nd Banner database hostname |
| orcl.db2.port | *(Optional)* 2nd Banner database port |
| orcl.db2.name | *(Optional)* 2nd Banner database service name |
| orcl.db2.user | *(Optional)* 2nd Banner database username |
| orcl.db2.pass | *(Optional)* 2nd Banner database password |
| orcl.db3.host | *(Optional)* 3rd Banner database hostname |
| orcl.db3.port | *(Optional)* 3rd Banner database port |
| orcl.db3.name | *(Optional)* 3rd Banner database service name |
| orcl.db3.user | *(Optional)* 3rd Banner database username |
| orcl.db3.pass | *(Optional)* 3rd Banner database password |
| ga_releases_only | Only show releases with a status of 'GA' *(default = false)* |

**Note:** The value of `h2.db.file` must be the full path of a *copy* of the H2 database file, not the production file in use by ESM.  Making a copy will allow the program to safely read the H2 database without the need to shut down ESM.  The copy must exist at the path specified by `h2.db.file` before running the program.  If you are using the **pending_banner_releases.sh** script, it will create the copy for you and delete it after the run.

## Running
If your ESM server is running on Linux, you can simply use the included shell script **pending_banner_releases.sh** to run the program:

```
$ ./pending_banner_releases.sh
```

** **IMPORTANT NOTE** ** You must ensure that BOTH the config file AND the script are configured properly.  If you are using the shell script to run this program, you must first ensure that the values of `h2.db.file` in **config.properites** and `ESM_H2_FILE` in the **pending_banner_releases.sh** script are the same.  The script will copy the live H2 database file to the filename specified by `ESM_H2_FILE`.  DO NOT specify the full path of the live file as the value for this variable, or the script will fail.

Alternatively, you may run the `PendingBannerReleases` class directly.  There are a few things you must do in order for this to work:

1. Copy the live H2 database file to a temporary file and ensure that the `h2.db.file` value in **config.properties** points to the temporary copy.
2. Specify the H2 and OJDBC drivers in the classpath.

Assuming you have set **/var/tmp/ESMAdminProdDb.mv.db** as the value of `h2.db.file` in **config.properties**, you would do something like this:

```
$ cp /u01/adminApp/ESMAdminProdDb.mv.db /var/tmp/ESMAdminProdDb.mv.db
$ java -cp <path>/*:. PendingBannerReleases
```

Where `<path>` is the full path to the ESM webapp's lib directory (e.g., `/u01/apache-tomcat-8.5.20/webapps/admin/WEB-INF/lib`).

## Change Log
**Version 1.4.0** - January 24, 2023
* Changed the default Oracle JDBC connection string to the format supported by pluggable databases RAC.
* If the first connection attempt to an Oracle database fails, retry with the old JDBC connection string format.

**Version 1.3.0** - December 23, 2021
* Added checks for missing config values for Oracle databases.
* Added code to handle database connection failures for H2 and Oracle.

**Version 1.2.0** - August 25, 2021
* Removed obsolete releases from the results.
* Added ga_releases_only config parameter.

**Version 1.1.0** - November 8, 2019
* Added side-by-side output for up to three Banner database instances.

**Version 1.0.0** - August 19, 2019
* Initial version.
