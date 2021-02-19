+++
title = "Processing Queue"
weight = 80
[extra]
mktoc = true
+++


The page *Processing Queue* shows the current state of document
processing for your uploads.

At the top of the page a list of running jobs is shown. Below that,
the left column shows jobs that wait to be picked up by the job
executor. On the right are finished jobs. The number of finished jobs
is cut to some maximum and is also restricted by a date range. The
page refreshes itself automatically to show the progress.

Example screenshot:

{{ figure(file="processing-queue.png") }}

You can cancel running jobs or remove waiting ones from the queue. If
you click on the small file symbol on finished jobs, you can inspect
its log messages again. A running job displays the job executor id
that executes the job.

The jobs listed here are all long-running tasks for your collective.
Most of the time it executes the document processing tasks. But user
defined tasks, like "import mailbox", are also visible here.

Since job executors are shared among all collectives, it may happen
that a job is some time waiting until it is picked up by a job
executor. You can always start more job executors to help out.

If a job fails, it is retried after some time. Only if it fails too
often (can be configured), it then is finished with *failed* state.

For the document-processing task, if processing finally fails or a job
is cancelled, the item is still created, just without suggestions.
