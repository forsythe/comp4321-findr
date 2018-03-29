# findr search engine

Final project for COMP4321 Search Engines

## Phase 1 Testing

The output files from the Phase 1 tasks:
* database file ("index.db") containing the first 30 pages crawled from "http://www.cse.ust.hk/"
* "spider_result.txt"

are already included.

To make the database file again from scratch, please remove the existing "index.db" file first before running the Phase 1 task program. Otherwise, the indexer will load in the existing database file and if there is no changes in the pages crawled, no change will take place.

To run the phase 1 task:

First change the permission on gradlew:

```
chmod 755 gradlew
```

then execute:

```
./gradlew cleanTest
./gradlew -Dtest.single=SpiderPhase1 test
```

"index.db" and "spider_result.txt" will be outputted in the project working directory. 

## Getting Started

Launch the `bootRun` gradle task to run, and go to [http://localhost:8080](http://localhost:8080)

## Packages
Package name 		| Contents
------------- 		| -------------
`controller`  		| Holds the classes each responsible for a URL (e.g. `/`, `/search`), which determine what content to show to the user given the query parameters
`object`		| Common plain objects go here, e.g. `Webpage`, `Posting`.
`scheduled`		| Holds classes which contains functions that are scheduled to run regularly. Good for scheduled crawling/indexing
`service.crawler`	| Holds the crawler interface, and any implementations. Takes a starting URL and returns a list of pages
`service.indexer`	| Holds the indexer interface, and any implementations. Responsible for interacting with the database, e.g. mapdb
`service.parser`	| Holds the parser interface, and any implementations. Classes which take a URL and return a Webpage object
`service.searcher`	| Holds the searcher interface, and any implementations. Classes which take a query and return a list of results
`service.spider`	| Holds the spider interface, and any implementations. Does crawling and indexing together as an action
`services.stemming`	| Utility functions for stopword removal and removing.

## TODO
search for TODO in the code

