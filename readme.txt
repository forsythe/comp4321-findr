Term Project Phase 1 Submission - Readme

The project is built using Gradle. The Gradle Wrapper script is provided so that Gradle does not need to be installed to build the project. 
The files included in this submission already have:
- the database file ("index.db"), containing the indexed result of the first 30 pages crawled from "http://www.cse.ust.hk"
- spider_result.txt 

To make the database file again from scratch, please remove the existing "index.db" file first before running the Phase 1 task program. Otherwise, the indexer will load in the existing database file and if there is no changes in the pages crawled, no change will take place.

To run the phase 1 task:

First change the permission on gradlew by:

'chmod 777 gradlew'

Then exextue:

'./gradlew testClean'

'./gradlew -Dtest.single=SpiderPhase1 test'

After executing those, "index.db" and "spider_result.txt" will be outputted in the project working directory.
