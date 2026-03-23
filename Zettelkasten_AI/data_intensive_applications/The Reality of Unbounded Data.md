In the real world, user data is never "complete." Users generated data yesterday, they are generating data right now, and they will continue generating data tomorrow. The dataset is **Unbounded**.

To handle unbounded data in a Batch paradigm, engineers logically artificially chop the data into fixed time slices (e.g., "Run a batch job at 11:59 PM to process today's logs"). 
*   **The Problem:** Daily batch processing introduces a massive 24-hour latency. If you need faster reactions, you can chop the time slices smaller (e.g., hourly, or even minutely batches). 
*   **The Solution:** Eventually, if you keep shrinking the time slices to zero, you abandon the concept of "slices" entirely and simply process every single event the exact microsecond it happens. This is **Stream Processing**.

A "stream" refers to data that is incrementally made available over time (conceptually similar to Unix `stdin/stdout`, TCP connections, or video streaming). In the data management context, event streams are exactly the unbounded, continuous counterpart to batch processing files.