In batch processing, the primitive unit of work is a *File*. In stream processing, the primitive unit of work is an **Event**.

### What is an Event?
An event is a small, self-contained, completely immutable object that describes something that happened at a specific point in time. 
*   **Contents:** It usually contains a timestamp (recorded by a time-of-day clock) and data describing an action (e.g., a user clicked a button, made a purchase, or a thermostat recorded a temperature). 
*   **Encoding:** Events are easily encoded as text strings, JSON, or compact binary formats (like Avro or Protobuf), making them easy to append to databases or send over a network.

### Producers and Consumers
Just like how a batch file is written once and read by many downstream batch jobs, an event is:
*   Generated once by a **Producer** (also known as a Publisher or Sender).
*   Processed by one or more **Consumers** (also known as Subscribers or Recipients).
*   Grouped together logically: Instead of a filesystem directory, related streams of events are grouped together into a **Topic** (or Stream).

### Polling vs Notification
How do you physically connect the Producer to the Consumer? 
Technically, you could just use a standard relational database: The Producer inserts event rows, and the Consumer runs a `SELECT` query every morning to check for new data. 

However, as you move towards low-latency continuous processing, polling the database becomes a massive performance bottleneck. If the Consumer runs a `SELECT` query every second, but new events only happen every 10 seconds, you are wasting 90% of your queries and actively overwhelming the database overhead.

Instead of the Consumer continuously asking *"Are we there yet?"*, the architecture must shift to **Notifications**: The system must actively push a notification directly to the Consumer the exact millisecond a new Event arrives. 
While relational databases have basic mechanisms for this (like Triggers), they are highly limited and notoriously fragile. To solve this, the industry designed specialized, dedicated infrastructure purely to deliver high-speed event notifications.
