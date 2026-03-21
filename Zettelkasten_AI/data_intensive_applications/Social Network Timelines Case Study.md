---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 1: Foundations"
status: pending
---
To illustrate performance and scalability, consider a Twitter-like system where users post messages and follow others.
*   **Scale:** 500 million posts/day (avg 5,800/sec, peak 150,000/sec).
*   **Graph:** Average user follows 200 people; celebrities have millions.

#### Approach 1: Relational Schema (Pull Model)
Data is stored in standard relational tables: `Users`, `Posts`, and `Follows`.

*   **Description**: This diagram shows a simple relational database schema for a social network. It consists of three tables: `Users` (storing user profiles), `Posts` (storing messages sent by users), and `Follows` (a many-to-many join table linking a follower to a followee).
![Figure 2-1: Simple relational schema for a social network in which users can follow each other.](figure-2-1.png)

To generating a home timeline:
```sql
SELECT posts.*, users.* FROM posts
JOIN follows ON posts.sender_id = follows.followee_id
JOIN users
ON posts.sender_id = users.id
WHERE follows.follower_id = current_user
ORDER BY posts.timestamp DESC
LIMIT 1000
```
*   **Problem:** This approach requires looking up recent posts for *every* person a user follows and merging them at read time. With 2 million active users polling, this results in huge database load.

#### Approach 2: Materialized Timelines (Push Model / Fan-out)
Instead of computing the timeline on read, we precompute it on write.
*   **Mechanism:** Each user has a "mailbox" (timeline cache). When a user posts, the system looks up all their followers and inserts the new post ID into each follower's timeline cache.
*   **Fan-out:** One write request (the post) becomes many writes to followers' timelines.
*   **Trade-off:** Fast reads (O(1)) but expensive writes. A celebrity with 100 million followers causes a massive write spike ("fan-out").

*   **Description**: This diagram illustrates the "fan-out" process. When a User posts a message, the Load Balancer sends it to a Web Server, which then inserts the post into the sender's timeline. Crucially, it also looks up the user's followers and pushes the new post into the Home Timeline Cache of *every* follower (User 2, User 3, etc.), pre-computing their view.
![Figure 2-2: Fan-out: delivering new posts to every follower of the user who made the post.](figure-2-2.png)

*   **Hybrid Solution:** Most users use the push model. For celebrities (users with huge follower counts), the system falls back to the pull model (merging their posts in at read time) to avoid the massive write penalty.
---
## Related Concepts
* [[Data Intensive Applications]]
