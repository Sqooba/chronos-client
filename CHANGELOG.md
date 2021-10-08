# CHANGELOG
## 13.09.2021
### Query result labels

Query results will be labeled as the given,expected label, if one is provided. The returned __name__ tag will only be used as a fallback.
This should make function calls more predictable to changes in Prom/Victoria result presentation logic, that can come
up with surprising labels depending on query complexity and query engine version.
