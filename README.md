# zquery
 简单的ZabbixQuery,用以改善官方api落后版本以及Server api性能不佳的问题

#### Example
<pre>
[INFO] [07/25/2017 15:06:10.280] [pool-1-thread-2] [akka.actor.ActorSystemImpl(default)] Sending ZQuery({
  "auth" : "b6981fa6537626e12e719dd2394d18a7",
  "method" : "event.get",
  "params" : {
    "eventids" : "11154506"
  },
  "jsonrpc" : "2.0",
  "id" : 1
}).
[INFO] [07/25/2017 15:06:10.309] [pool-1-thread-2] [akka.actor.ActorSystemImpl(default)] {"jsonrpc":"2.0","result":[{"eventid":"11154506","source":"0","object":"0","objectid":"33171","clock":"1500964972","value":"1","acknowledged":"0","ns":"114091769","r_eventid":"0","c_eventid":"0","correlationid":"0","userid":"0"}],"id":1}
[INFO] [07/25/2017 15:06:10.317] [scala-execution-context-global-20] [akka.actor.ActorSystemImpl(default)] ZQuery recovery succeed.
[INFO] [07/25/2017 15:06:10.319] [pool-1-thread-2] [akka.actor.ActorSystemImpl(default)] ZQuery successfully completed.
[INFO] [07/25/2017 15:06:10.322] [scala-execution-context-global-20] [akka.actor.ActorSystemImpl(default)] RecoveryClock(2017-07-25T06:42:52)

Process finished with exit code 130 (interrupted by signal 2: SIGINT)
</pre>

#### 更新日志

* 增加了redmine API. （redmine api的文档很糟糕~)
