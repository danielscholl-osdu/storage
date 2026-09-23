# Collaboration Integration

All APIs in storage service are [collaboration context-aware](CollaborationContext.md) in Azure CSP when the collaboration context feature flag is set to true. More details about the feature flag are provided in this [Wiki](https://community.opengroup.org/groups/osdu/platform/-/wikis/Feature-Flag).

CSPs that support collaborations functionality yet are indicated below-

| CSP   | Supports Collaboration ? |
|:------|:-------------------------|
| Azure | Yes                      |
| IBM   | No                       |
| GCP   | No                       |
| AWS   | No                       |

For a CSP who wants to support x-collaboration namespaces, it is necessary to upgrade its messaging infrastructure by adding the "records changed V2" topic in addition to the existing "records changed (V1)". The new topic is to be dedicated to the messages sent by the Storage service for record changes in the x-collaboration context. So the V1 topic is for SOR (default) namespace messages only, and the V2 topic is for x-collaboration messages only.

The Storage service common code and several CSP sections have already been upgraded for sending these two groups messages to the different topics. Please check whether the feature has been implemented for your CSP and if not, implement the necessary upgrades to direct x-collaboration namespaces records changes events to the V2 topic.

The Indexer-Queue service needs to be upgraded to subscribe to the V2 messages and queue them for indexing the Indexer service the same way it is happening for the V1 messages. Thus, both V1 and V2 messages on their arrival at the Index-Queue service should be queued in the single queue and then sent to the Indexer service push API.

The Indexer service must be upgraded to distinguish x-collaboration (V2) messages (by their x-collaboration property) from the SOR messages and treat them differently. The resulting index documents must go to the same record kind's index but with additional prefixing in the indexed document IDs and the additional "Collaboration" property.

The Search service must be upgraded for optional usage of the special "x-collaboration" header. When this is set, it leads to searching for records in the specified "x-collaboration" namespace (and not in the SOR namespace, which is searched when the header is not set).

Known limitation: reindex operations do not work properly when the x-collaboration indexed documents are present. Such reindexing will remove all the indexed x-collaboration documents from the index and they will not be rebuilt. To address this issue, ADR114 has been proposed, but it is not yet implemented.
