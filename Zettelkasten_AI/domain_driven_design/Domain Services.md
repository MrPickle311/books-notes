---
aliases:
tags:
  - domaindrivendesign
source_book: "Domain-Driven Design"
status: pending
---
Domain services host business logic that doesn't naturally belong to any aggregate or value object.

#### When to Use Domain Services
*   Logic that spans multiple aggregates
*   Calculations requiring data from multiple sources
*   Business processes that don't fit within a single aggregate

#### Example: Multi-Aggregate Coordination
```csharp
public class ResponseTimeFrameCalculationService
{
    public ResponseTimeframe CalculateAgentResponseDeadline(
        UserId agentId, Priority priority, bool escalated, DateTime startTime)
    {
        var policy = departmentRepository.GetDepartmentPolicy(agentId);
        var maxProcessingTimeSpan = policy.GetMaxResponseTimeFor(priority);
        
        if (escalated) 
        {
            maxProcessingTimeSpan = maxProcessingTimeSpan * policy.EscalationFactor;
        }
        
        var shifts = departmentRepository.GetUpcomingShifts(agentId, startTime, 
            startTime.Add(policy.MaxAgentResponseTime));
        return CalculateTargetTime(maxProcessingTimeSpan, shifts);
    }
}
```

**Important:** Domain services are stateless and have nothing to do with microservices or SOA.
---
## Related Concepts
* [[Domain Driven Design]]
