Modern distributed data architectures are rapidly replacing raw Distributed Filesystems with **Object Storage** systems like Amazon S3, Google Cloud Storage, or Azure Blob Storage. 
While they may seem like simple file systems, there are drastic architectural differences beneath the hood that engineers must keep in mind:

#### Immutability
Unlike a standard file where you can open a file handle, seek to byte 500, and overwrite 3 lines of text, **objects are fully immutable**.
Once an object is written, it is locked in stone forever. To "update" an object, you must download it, modify it locally, and perform an entirely new `put` operation to completely overwrite the old object with the new one. 

#### No True Directories
Object stores do not naturally understand the concept of a "Folder" or "Directory". They are strictly flat key-value stores.
When you see an object path like `s3://bucket/2025/photo.png`, all of those slashes are literally just characters in the object's Key string.
This leads to two major architectural quirks:
1.  **Empty Directories are Impossible:** If you delete all the objects inside the `/2025/` path, the folder itself instantly ceases to exist. (To bypass this, developers often hack the system by creating an empty 0-byte file named `/2025/` just to force the folder to render).
2.  **No Atomic Renames:** In Linux, you can instantly rename an entire folder containing 10,000 files with a single `mv` command. In an Object Store, because directories don't exist, to "rename" a folder, you must individually issue 10,000 separate `copy` network requests to duplicate the objects into a new key string, and then issue 10,000 `delete` requests to clean up the old ones.

#### Decoupling Storage and Compute
A primary feature of the older Hadoop (HDFS) ecosystem was "Data Locality". Hadoop was incredibly smart: instead of sending a 500 GB file over the network to a processing server, it would physically run the processing code on the exact server where the file was already sitting on the hard drive. 

Object Stores (like S3) completely abandon Data Locality in favor of **Decoupled Storage and Compute**. Compute servers are entirely stateless, and storage servers are entirely dumb. 
This means that *every single byte of data* must be streamed over the network to be processed. While this sounds slow, modern datacenter network switches are so fast that the bandwidth bottleneck is largely irrelevant. The massive advantage is elasticity: you can instantly scale up 1,000 compute CPUs to crunch a batch job without needing to buy 1,000 new storage hard drives alongside them.