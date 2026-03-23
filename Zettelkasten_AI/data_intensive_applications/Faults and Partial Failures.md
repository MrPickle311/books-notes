When you write software for a single computer, the operating system goes out of its way to present an idealized, mathematically perfect reality. **Single-node computers are deterministic:** they either work perfectly, or they crash completely (kernel panic, blue screen of death). They are specifically designed *not* to operate in a halfway-broken state because that causes confusing errors.

**Distributed systems are fundamentally different.**
Because they span multiple machines connected by cables in the physical world, they are subject to **Partial Failures**. 
In a partial failure, one part of your system breaks in an unpredictable way while the rest of the system continues functioning fine. 

Because partial failures are entirely **nondeterministic**, a request might work, it might fail, or—worst of all—you might not even know if it succeeded or not. However, if we accept and design around partial failures, we can achieve something incredible: **Fault Tolerance**. We can build a perfectly reliable system constructed entirely out of inherently unreliable physical hardware.