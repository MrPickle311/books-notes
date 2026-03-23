Only one entity in the aggregate hierarchy serves as the public interface - the aggregate root.

![Figure 6-5: A diagram showing the aggregate root as the single entry point to the aggregate.](figure-6-5.png)

All external access goes through the root:
```csharp
public class Ticket  // Aggregate Root
{
    private List<Message> messages;
    
    public void Execute(AcknowledgeMessage cmd)
    {
        var message = messages.Where(x => x.Id == cmd.id).First();
        message.WasRead = true;  // Modifying internal entity through root
    }
}