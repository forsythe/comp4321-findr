Term Project Phase 1 Submission - Readme

The project is built using Gradle. The Gradle Wrapper script is provided so that Gradle does not need to be installed to build the project. 
The files included in this submission already have:
- the database file ("index.db"), containing the indexed result of the first 30 pages crawled from "http://www.cse.ust.hk"
- spider_result.txt 

To make the database file again from scratch, please remove the existing "index.db" file first before running the Phase 1 task program. Otherwise, the indexer will load in the existing database file and if there is no changes in the pages crawled, no change will take place.

To run the phase 1 task:

Move to the project directory, and 

1. First change the permission on gradlew by:

'chmod 755 gradlew'

2. Then exextue:

'./gradlew cleanTest'

'./gradlew -Dtest.single=SpiderPhase1 test'

After executing those, "index.db" and "spider_result.txt" will be outputted in the project working directory.
Please make sure to execute the "gradlew" commands inside the project directory. 

--------------------------------------------------------------------------------------------------------
Remarks

- The zipped file containing this "readme.txt" does not only contain the necessary source codes for the spider, indexer and the test program, but also some other files that are not used in phase 1. These files are for the final submission and they were included in this submission for completeness only. 
- Although the other files are not used for phase 1 task, please do not remove them for running the test program.
- The files used for phase 1 tasks are marked with (*) :

comp4321-findr 
|
+- src 
|   |
|   +- main
|      |
|      +- java 
|      |  |
|      |  +-com
|      |    |
|      |    +- object
|      |    |  |
|      |    |  +- Webpage.java *
|      |    |
|      |    +- service
|      |       |
|      |       +- crawler *
|      |       |
|      |       +- indexer *
|      |       |
|      |       +- parser * 
|      |       |
|      |       +- spider *
|      |       |
|      |       +- stemming *
|      |
|      +- resources
|         |
|         +- stopwords.txt *
|
+- bin * 
|
+- build * 
|
+- gradle *
|
+- build.gradle * 
|
+- COMP4321 Phase1 Database Design.pdf * 
|
+- gradlew * 
|
+- gradlew.bat *
|
+- index.db * 
|
+- readme.txt * 
|
+- spider_result.txt * 

