## <a name="_toc119676063"></a>Context
This ADR is centered around the design of the new replay flow within OSDU's storage service. The purpose of this Replay flow is to publish messages that indicate changes to records, which are subsequently received and processed by consumers. It's important to note that the handling of these messages follows an idempotent process.

The Replay flow will address following- 

1.  In case of disaster, this replay flow will help us to rebuild the indexes that to RPO.[Out of Scope of ADR]
2.  Reindexing the records by publishing the record change messages to consumer Indexer service.	
3.  Correction of indices after changes to structure of the storage records of a particular kind.

**Replay rate** - It is the rate at which storage publish the record changes message to service bus.

## <a name="_toc119676075"></a>Problems with Current Reindex All Solution

|**Problem**|**Details**|**What is Required?**|
| :- | :- | :- |
|Reliability |<p>**Operation is Synchronous.**</p><p>- Very long HTTP call is never reliable</p><p></p><p></p><p>The Reindex is a synchronous operation, making the operation Unreliable and not resilient to failures. If there is any interruption to the connection, all the status and progress could be lost.</p><p></p>|The operation must be reliable. If the operation is triggered, it must either succeed or it must fail and in both the cases, the user must be diligently informed with the right reasons for success/failures. The system should not be in a state where the user has no clue what’s happening.|
|Resiliency|Abrupt disturbance of the reindex-process leaves the system in an inconsistent state. For example, if there is any exception or if the process crashes, then the system is left entirely in an inconsistent state.|The system must be resilient to failure and must always succeed. If the operation fails, then the system must be left in the previous state.|
|Scale|Due to the synchronous and non-resilient nature of the current implementation, the scale is very limited. It cannot ingest more than a couple of million records reliably.|The reindex operation must scale to any number of records|
|Speed|The speed is very slow. It’s known to take close to an hour for 1 million records.|Faster rate of reindexing is required. For example, 100 million records should not take more than a few hours. |
|Tracking/Observability|There is no way for the user to know about the progress.||
|Pausing/Resuming reindex|Today, there is no capability to pause and resume reindex. Given that this will be a long running operation, having pause and resume will be good to have.||
|No Delta Reindexing|For some Disaster Recovery Scenarios, there may be partial backups available. So reindexing only a subset of records of a kind can prove to be useful. This functionality is not available today.||
|Parallelization|Currently, the reindex is a procedural process. This has impact on both scale as well as speed.||


## <a name="_toc119676077"></a>Requirements to address

To be able to address these issues, we need to re-design the way reindex works, addressing various functional and non-functional aspects like speed, scale, reliability, observability, etc. The below table outlines what is expected out of the new Reindex design.

|**Requirement**|**Details**|**Technical Implications** | **Scope** |
| :- | :- | :- | :- |
|1. Scalability|<p>The Replay operation must be scalable; it should be able to handle infinitely large amounts of records.</p><p><br>A realistic goal to target can be 100m records in 4-5 hours.</p>|<p>Need to ensure Elasticsearch storage can be scaled up.</p><p></p><p>For achieving a higher scale, the following must be done: -</p><p>- The whole operation must be **Asynchronous** in nature</p><p>- It must be resilient to failures due to pod crashes, 429s due to high Database/Service Bus/Elasticsearch load.</p><p>- We can leverage Message Broker to divide and conquer and have the framework.</p><p>- We can also look at job schedulers like QUARTZ to achieve a reliable reindex.</p><p>- Need to evaluate which is the best service to perform this reindexing. </p><p>- Can also try to leverage **Airflow**</p><p></p>| In Scope of ADR |
|2. Reliable Responses|<p>When the operation is triggered, the response must be reliable. </p><p></p><p>There could be some pre-validation done to check whether the reindex process can be completed either successfully or not.</p><p>The result of whether the operation is success or fail, should be communicated via response to the user properly.</p>|Today, we don’t return anything apart from 200 OK in the response even if things fail. <br><br>The entire response should be revamped and reworked on how the status can be conveyed to the user in a useful way.| In Scope of ADR |
|3. Observability and Monitoring|<p>Given the fact that reindex is a long running operation, the User triggering the reindex must have insights into what is going on, using a track status API.</p><p></p><p>Some of the details should include:</p><p>- **Status:** Validating, Stopping-Ingestion, In-progress, Finalizing, Complete, Error, etc.</p><p>- **Progress:** Overall percentage, per index progress, remaining records count, ETA</p><p></p>|We could store the progress in a Redis cache or elsewhere that can be used to report back to the user on the progress.| In Scope of ADR |
|4. Reliable System State – Consistency before/after operation in case of failure|<p>Guarantee to reindex valid storage records – **Must have**</p><p><br>**(depends on message broker reliability)**</p><p></p><p><br>**Rollbacks** – nice to have</p>|<p></p><p>If there are unrecoverable errors during reindexing a particular kind, then that leaves the system in an inconsistent state. It would be good to “**rollback**” the operation to restore the system to the state before the operation was triggered for that kind.</p><p></p><p>There should also be **no concurrent “reindexAll” operation** running. There can however, be concurrent reindex of different kinds happening at the same time.</p><p>It can be a configurable parameter on whether the rollback should be done in case of unrecoverable failures, due to internal system errors.<br><br>How this can be achieved is that, all the reindexed records for a kind, should be indexed into a new “secondary index” for that kind, and only if that is succeeds completely, the index can be renamed and replace the primary index.<br><br>Elasticsearch’s clone index feature can be utilized to achieve this.</p><p></p><p>- Reindex failed record IDs</p>|Out of Scope of ADR |
|5. Stop Ingestion/Search during Reindex|<p>During **Reindex**, the normal ingestion should stop. This is because:</p><p>- There are some edge cases which could end up the system in an inconsistent state. Edge Cases: **<TODO>**</p><p>- Load on Elasticsearch</p><p></p>| | Out of Scope of ADR|
|6. Speed</p>|<p></p><p>The operation is quite slow today. It takes almost an hour to reindex a million records. This means it will take a few days to reindex 100m records, which is not practical.</p><p></p><p>Two Issues:</p><p>1. Finding Unique Kinds</p><p>2. Reindexing – Database load</p>|<p></p><p>This is **directly dependent on the scalability of the underlying infra like Database** and Elasticsearch. </p><p>Database can be scaled up/out on demand, by either the UI by customer (i.e., a via CP), or some other means. </p><p></p><p>Auto scaling-out of Elasticsearch is currently not possible, so we may be limited in speed due to Elasticsearch. We can, however, scale up Elasticsearch and this can help in higher speed.</p><p></p><p>How this scale up is triggered automatically or manually is something we need to evaluate and do a POC.</p><p></p><p>Storage Service’s queries can also be revisited – there was a change done in some service which had a more efficient implementation of paginated queries - [Performance improvement on paginated query for CosmosDB (!244) · Merge requests · Open Subsurface Data Universe Software / Platform / System / Lib / cloud / azure / OS Core Lib Azure · GitLab (opengroup.org)](https://community.opengroup.org/osdu/platform/system/lib/cloud/azure/os-core-lib-azure/-/merge_requests/244/diffs)</p><p></p><p></p>| |Out Scope of ADR |
|7. **Delta Reindex** and **Consistency Checker/Enforcer**|<p>Doing a delta reindex can be useful if there is restoration of backups during a disaster recovery. This will result in faster recovery times.</p><p></p><p>Delta Reindex = reindex only those records that are not present in Backup.<br><br>When we talk about delta reindex, we need to ensure there is consistency across all 3 components – storage blob, storage records and Elasticsearch.</p><p></p>|<p>Need to explore feasibility. The operation can be something like Reindex All records whose create/update time > X.</p><p></p><p>A consistency enforcer should be built that will ensure that the 3 entities are in consistent state.</p>| Out Scope of ADR |
|8. Snapshot Backup/Cluster replication|<p>Backup Elasticsearch storage Snapshots frequently, and in case of disaster, restore the snapshot and then perform the delta reindex.<br><br>This will make the recovery times much faster| |Out Scope of ADR |
|9. Source of trigger|During a recovery process, who will make the call to reindex? Is it the user or internal system? |Will need to design and account for this fact in the reindex design.| Out Scope of ADR |
|10. Pause/Resume Reindex|Since reindex is a long running operation, having the ability to pause and resume reindex operation would be nice to have|<p>We need to ensure system consistency when the operation is paused and resumed. </p><p></p><p>Also, any new records ingested after the pause must be included in the reindex process when it’s resumed.</p><p></p>| Out Scope of ADR |

## <a name="_toc119676078"></a> Architectural Options:
<br>

|**Options**|**Pro**|**Cons**|**Work Required**|
| :- | :- | :- | :- |
|1. Using **Airflow** + Message Broker + StorageService + Workflow Service|<p>- Proven Workflow Engine</p><p>- Lesser new implementations in storage services, so lesser work required by other CSPs.</p>|<p>- Process becomes slower and inefficient.</p><p>- Lot of HTTP calls from Airflow <-> AKS</p><p>- Airflow will require access to internal Infrastructure to operate in the most efficient manner.</p><p>- Some required features are not yet available in ADF Airflow </p><p>- Parallelization may spawn up 1000s of tasks waiting to be scheduled. **Scalability can be issue.**</p><p>- Concurrency and Safety guarantee is tricky – allowing no more than one reindex for a kind</p><p></p>|<p>**Airflow**</p><p>- DAG using TaskGroups, Dynamic Task Mapping, Concurrency handling.</p><p>- Build pipelines to integrate new DAG.</p><p></p><p>**Storage Service**</p><p>- Implement new APIs to publish messages to message broker.</p><p></p><p>**Indexer Service**</p><p></p><p>**Workflow Service**</p><p>- Have new APIs to support observability</p><p>- Design for checkpointing</p>|
|2. Using **StorageService** + **Message Broker**|<p>- Simple, Lesser moving parts</p><p>- Fast & Efficient</p>|- Parallelization may require state management.|<p>**Storage Service**</p><p>- New APIs for exposing Replay functionality (ReplayAll, ReplayKind, GetReplayStatus)</p><p>- New Modules for replay message processing</p><p></p><p>**Indexer Service**</p><p>- Delete ALL kinds API</p>|

## <a name="_toc119676079"></a> Decision:
We chose design option 2 using storage service and message broker as the advantage is to persist the replay status and allows to re-play and return the status and simpler implementation. 
- **[Decision]** What led us to select the Storage service for the Replay API decision? <br>
* The source of truth for the storage records is – Storage Service. It is the storage service, that publishes the record change messages, which are then consumed by the consumers. This processing of those messages is idempotent.So, it’s fair to say that to trigger reindexing, we must invoke some procedure in storage service, that will make it emit record change messages onto the message broker.<br>
* Indexer is just a consumer of the recordChange messages, and there could be other consumers who require this replay functionality as well. In those cases, instead of letting each consumer build their own replay logic, if we have it in one common place, it would benefit all the consumers. <br>
     * This way, one consumer doesn’t have to depend on indexer, which is also just another consumer<br>
     *  Reindex is just one-use cases, that uses this new Replay functionality. Other consumers can have their own use case for consuming those replayed messages.
<br>

**Design Approach for option 2:**

![Aspose.Words.71972436-70f7-48df-8f1c-d2035f55ce34.004](/uploads/5a573b82493315f91adeee547fd97fee/Aspose.Words.71972436-70f7-48df-8f1c-d2035f55ce34.004.png)

**Note** 
The ADR also helps to address following issues - <br>
- **[Issue]** https://community.opengroup.org/osdu/platform/system/indexer-service/-/issues/91 <br>
* The Replay flow will include a Service Bus topic for every event. If we need to introduce new events in the future that necessitate message publishing, we can easily do so by introducing a new topic and associated logic. This approach can help prevent unintended consequences that may arise from triggering other listeners on the same topic, as they can be resolved accordingly. <br>
- **[Issue]** https://community.opengroup.org/osdu/platform/system/indexer-service/-/issues/66
* Utilizing the service bus and tracking its progress assists us in achieving a reliable design, including the built-in reliability of message queuing. <br>
- **[Issue]** https://community.opengroup.org/osdu/platform/system/indexer-service/-/issues/80
* With the flexibility to introduce new topics in the Reindex