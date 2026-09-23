# Policy Service Integration

Storage service now supports data authorization and compliance checks with [Policy Service](https://osdu.pages.opengroup.org/platform/security-and-compliance/policy/) via [OPA(Open Policy Agent)](https://www.openpolicyagent.org/) in create or update record workflow.

OPA has data authorization policies replicating existing create/update workflow system behaviors persisted and allows dynamic policy evaluation on user requests.

By default, Storage service utilizes [Entitlement](https://osdu.pages.opengroup.org/platform/security-and-compliance/entitlements/) and [Legal](https://osdu.pages.opengroup.org/platform/security-and-compliance/legal/) service for data authorization and compliance checks respectively. CSP must opt-in to delegate data access and compliance to Policy Service.      

To enable OPA integration for a provider:

- Add and provide values for following runtime configuration in `application.properties`

```
   OPA_API=${opa_endpoint}
   opa.enabled=true
```

For more information see [Policy Service](https://osdu.pages.opengroup.org/platform/security-and-compliance/policy/).