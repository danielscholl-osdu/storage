Two New API in Storage service as part of Replay flow will be introduce in the storage service. 

* [] Proposed
* [ ] Trialing
* [ ] Under review
* [x] Approved
* [ ] Retired

## Context & Scope
This ADR is centered around the design of the new replay API within OSDU's storage service which is introduced as the part of the [Replay ADR](https://community.opengroup.org/osdu/platform/system/storage/-/issues/186). The purpose of this Replay API is to publish messages that indicate changes to records, which are subsequently received and processed by consumers. It's important to note that the handling of these messages follows an idempotent process.


## Terminology

<table>
   <tr>
      <td><strong>                                                        Name</strong>
      </td>
      <td><strong>                                          Explanation</strong>
      </td>
   </tr>
   <tr>
      <td><strong>                                                     Record</strong>
      </td>
      <td>The record is stored in OSDU Data Platform in two parts, i.e., document database, which contains basic data (id, kind, legal information, and access permissions), and file storage in a Java Script Object Notation (JSON) format, which contains other relevant information of the record. We are interested in the document database part.
      </td>
   </tr>
</table>


## Tradeoff Analysis

The new APIs does not represent a breaking change of any other API, and consequently neither for the consuming applications. Only concerned-consuming applications would benefit from this new feature, while it remains entirely transparent for others.

## Additional Requirement 
The newly introduced APIs must facilitate [Collaboration workflows](https://community.opengroup.org/osdu/platform/system/storage/-/issues/149) through the utilization of the x-collaboration header. Additionally, the replay mechanism should ensure the accurate publication of collaboration context information in the corresponding event.

## Decision

The proposal is to provide POST and GET Replay API - 

The new APIs does not represent a breaking change of any other API, and consequently neither for the consuming applications. Only concerned-consuming applications would benefit from this new feature, while it remains entirely transparent for others.


<table>
   <tr>
      <td><strong> API fields </strong>
      </td>
      <td><strong>Explanation</strong>
      </td>
   </tr>
   <tr>
      <td><strong>kind</strong>
      </td>
      <td>It specifies to what Kind the schema belongs to. [optional]
      </td>
   </tr>
   <tr>
      <td><strong>repalyId</strong>
      </td>
      <td>It represents status id. [required]
      </td>
   </tr>
   <tr>
      <td><strong>operation</strong>
      </td>
      <td> Define the replay operation to be carried out. [required]
      </td>
   </tr>
   <tr>
      <td><strong>filter</strong>
      </td>
      <td> Define based on which field the record is selected. [optional]
      </td>
   </tr>
</table>
<strong>Allowed roles for API access</strong> : users.datalake.ops
<br>
<table>
   <tr>
      <td>
         <strong>Method</strong>
      </td>
      <td>
         <strong> API Endpoint</strong>
      </td>
      <td>
         <strong>Design</strong>
      </td>
   </tr>
   <tr>
      <td> POST
      </td>
      <td>v1/replay
      </td>
      <td>
         <strong>Request Example - </strong>
         <p>
            <strong> </strong>
         <p>
            1.   	<strong>Description</strong> - This API request will reindex all the storage records.
         <p>
            This phase we will pass empty body for reindexall
         <p>
            {
         <p>
            }
         <p>
 In next phase -
<p>
    ![operationrepaly](/operationrepaly.png)     	
<p>
2.   	<strong>Description</strong> - This API request will reindex the specific kinds of storage records in this operationName is optional by default, it will reindex specific kinds with filter field. Currently we will replay for single kind only so the array of kind will be restricted to size one.
<p>
![operationrepaly](/operationrepaly.png) 
<p>
         <p>
<strong>Response example – </strong>
![responsepostreplay](/responsepostreplay.png)
<p>
<strong> 
      </td>
   </tr>
   <tr>
      <td>             	GET
      </td>
      <td>
         replay/status/{id}</em>
         <p>
      </td>
      <td>
         <strong>Request:</strong>
         <p>
         <p>
         <p>
            1. <strong>Response Replay in Progress:</strong> <br>
         <p>
            a) <b>Scenario</b> - In Replay All <br><br>
           ![replaystatusAllKind](/replaystatusAllKind.png) <br>
            b) <b>Scenario</b> - In Replay single kind <br><br> ![replaystatusforsinglekind](/replaystatusforsinglekind.png)
     <br>
         <p>
         <p>
            2. <strong>Response Replay in Failed:</strong> <br>
         <p>
            a) <b>Scenario</b> - In Replay All <br><br>
           ![replayFailedForAllKind](/replayFailedForAllKind.png)
            <br>
            b) <b>Scenario</b> - In Replay single kind  <br><br>
  ![replayfailedforsinglekind](/replayfailedforsinglekind.png)           
         <p>
         <p>
      </td>
   </tr>
</table>
<br>
API spec swagger yaml -[ReplayAPISpecs.yaml](/ReplayAPISpecs.yaml)