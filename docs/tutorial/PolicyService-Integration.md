Storage service now supports data authorization and compliance checks via Policy service. Policy service allows dynamic policy evaluation on user requests and can
be configured per partition. 

By default, Storage service utilizes Entitlement and Legal service for data authorization and compliance checks respectively. CSP must opt-in to delegate data access and compliance to Policy Service.      

Here are steps to enable Policy service for a provider:

- Enable policy configuration for desired partition:
  ```
  PATCH /api/partition/v1/partitions/{partitionId}
  {
    "properties": {
        "policy-service-enabled": {
            "sensitive": false,
            "value": "true"
        }
    }
  }
  ```

- Register policy for Storage service, please look at policy service [documentation](https://community.opengroup.org/osdu/platform/security-and-compliance/policy#add-policy) for more details.  

- Add and provide values for following runtime configuration in `application.properties`
  ```
   service.policy.enabled=true
  service.policy.id=storage //policy_id from ${policy_service_endpoint}/api/policy/v1/policies.
  service.policy.endpoint=${policy_service_endpoint}
  policy.cache.timeout=<timeout_in_minutes>
  PARTITION_API=${partition_service_endpoint}
  ```
