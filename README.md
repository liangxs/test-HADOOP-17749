
This project is used for test [https://github.com/apache/hadoop/pull/3080](https://github.com/apache/hadoop/pull/3080)

```
$ mvn package

## start server on 100.100.148.76, with port range [13000, 13099]
$ java -cp target/lock-contention-jar-with-dependencies.jar HadoopSocketPerfTest server 100.100.148.76 13000

## start client: connect to 100.100.148.76 with 100 threads
$ java -cp target/lock-contention-jar-with-dependencies.jar HadoopSocketPerfTest client 100.100.148.76 13000 100
```


