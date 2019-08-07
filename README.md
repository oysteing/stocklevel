# Stocklevel

Stocklevel is a proof-of-concept service for offering a high-performance, low footprint standalone stock level service typically used by eCommerce sites.

## Background
SAP Hybris eCommerce platform out of the box handles stock levels persisted in a relational database, and cached in the entity cache (EHCache). For a scenario where stock levels source-of-truth is external, for example an ERP system, loading a high number of stock levels puts a high toll on the relational database (MySQL or similar) and requires large on-heap caches for quick access. Additionally, caches are often invalidated due to frequent updates.

Typical scenario:
* 300 stores
* 10000 products
* Stock snapshot of 3M positions every 5 minutes

Issues:
* Slow page view - requires MySQL access or cache hit
* High on-heap memory usage - with Hybris stock level data structure averaging to just over 1KB on EHCache, the cache size required for all data would be 3GB, and would have to be duplicated on each node in the cluster (assuming non-distributed clustering)
* Very heavy loading of stock levels

There are many solutions to this challenge like transferring deltas instead of full snapshots, reducing cache sizes, distributed caching and others. None of them address all the issues. I feel externalising the stock level service (microservice style) would give the most performant and manageable service.

## Solution

Standalone stock level service with responsibility for receiving stock level updates and providing a REST API for consumers to integrate stock levels into web sites and applications. The prototype is based on Spring Boot and in-memory only storage. For large inventories, this on-heap solution would not fit into manageable heaps. Alternatives could be to use Redis or build some kind of partitioning into the service.

Low-footprint optimized data structures are used to reduce memory footprint (FastUtil's Int2ObjectOpenHashMap).

Measurements show that the 3GB memory usage is reduced to ~100MB, with effective max heap sizes x3. Access latency is very low due to HashMaps.
