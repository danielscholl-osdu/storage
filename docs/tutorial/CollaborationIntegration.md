## Collaboration Integration
All APIs in storage service are collaboration context-aware in Azure CSP when the collaboration context feature flag is set to true.
Meaning that any interface changes (that are implemented by all CSPs) have a "dummy" implementation that ignores the functionality related to collaboration.
Other CSPs can implement this functionality at their own pace.

### Sample implementation of Azure
Please refer to this MR for [implementation of Azure](https://community.opengroup.org/osdu/platform/system/storage/-/merge_requests/546).

There are few things to consider when implementing APIs to support collaboration context.
1. The current record changed topic is not triggered by any changes when a collaboration context is provided (please refer to this Azure implementation [MR](https://community.opengroup.org/osdu/platform/system/storage/-/merge_requests/582)).
2. There should be a new topic that is triggered for all requests regardless of collaboration context. Note that collaboration context should be sent as part of the message if provided (please refer to this Azure implementation [MR](https://community.opengroup.org/osdu/platform/system/storage/-/merge_requests/553)).
3. The old functionality of storage should not be changed if collaboration context is not provided.
