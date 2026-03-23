When you run a Unix tool, three things are happening on the single machine:
1.  **Storage:** Data is read from/written to the local file system.
2.  **Scheduler:** The OS decides which CPU cores run the program.
3.  **Communication:** Unix pipes connect the `stdout` of one program directly into the `stdin` of the next.

A **Distributed Processing Framework** (like Hadoop or Spark) is literally just an Operating System scaled out across thousands of machines. It mirrors these exact three components: it has a Distributed Filesystem, a Distributed Scheduler, and Distributed Communication channels.
